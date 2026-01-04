package net.democracycraft.democracyLib.internal.bootstrap;

import net.democracycraft.democracyLib.api.DemocracyLibApi;
import net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeContract;
import net.democracycraft.democracyLib.api.bootstrap.contract.BridgeContractVersion;
import net.democracycraft.democracyLib.internal.bootstrap.service.DemocracyLibReflectiveApi;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Bootstrap initializer for shaded usage.
 *<p>
 * It is responsible for leader election via a JVM-global anchor and returning either:
 * - the leader implementation (real shared runtime), or
 * - a follower proxy (reflection bridge to that leader).
 */
@BridgeContractVersion(1)
public final class DemocracyBootstrap {

    public static final String KEY_LEADER = GeneratedBridgeContract.AnchorKeys.LEADER;
    public static final String KEY_PROTOCOL = GeneratedBridgeContract.AnchorKeys.PROTOCOL;
    public static final String KEY_LEADER_PLUGIN = GeneratedBridgeContract.AnchorKeys.LEADER_PLUGIN;
    public static final String KEY_LEADER_PLUGIN_REF = GeneratedBridgeContract.AnchorKeys.LEADER_PLUGIN_REF;
    public static final String KEY_LEADER_CLASS = GeneratedBridgeContract.AnchorKeys.LEADER_CLASS;
    public static final String KEY_LEADER_SERVICE_MANAGER = GeneratedBridgeContract.AnchorKeys.LEADER_SERVICE_MANAGER;
    public static final String KEY_PROVIDER_FACTORY = GeneratedBridgeContract.AnchorKeys.PROVIDER_FACTORY;

    private DemocracyBootstrap() {
    }

    /**
     * Protocol version for the reflection bridge.
     * Increment only when the cross-classloader invocation contract changes.
     */
    private static final int PROTOCOL_VERSION = loadProtocolVersion();

    private static int loadProtocolVersion() {
        try (InputStream inputStream = DemocracyBootstrap.class.getClassLoader().getResourceAsStream("democracylib.properties")) {
            if (inputStream == null) return 1;
            Properties properties = new Properties();
            properties.load(inputStream);
            String protocolVersion = properties.getProperty("protocolVersion");
            if (protocolVersion == null || protocolVersion.isBlank()) return 1;
            return Integer.parseInt(protocolVersion.trim());
        } catch (Exception ignored) {
            return 1;
        }
    }

    public static @NotNull DemocracyLibApi init(@NotNull JavaPlugin plugin,
                                                @NotNull ProviderFactory providerFactory,
                                                boolean logging) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(providerFactory, "providerFactory");

        DemocracyBootstrapLogger log = new DemocracyBootstrapLogger(logging, plugin);

        Map<String, Object> anchor = DemocracyLibJvmAnchor.anchorMap();
        Object lock = DemocracyLibJvmAnchor.lock();

        synchronized (lock) {
            // Keep a provider factory reference in the anchor for on-demand re-election.
            // Important: this is only used within the same classloader that installed it.
            anchor.putIfAbsent(KEY_PROVIDER_FACTORY, providerFactory);

            Object leader = anchor.get(KEY_LEADER);
            Object protocol = anchor.get(KEY_PROTOCOL);
            Object leaderPluginNameObj = anchor.get(KEY_LEADER_PLUGIN);
            String leaderPluginName = (leaderPluginNameObj != null) ? String.valueOf(leaderPluginNameObj) : "<unknown>";

            if (leader != null) {
                int leaderProtocol = (protocol instanceof Integer i) ? i : -1;
                if (leaderProtocol != PROTOCOL_VERSION) {
                    log.warn("Protocol mismatch (leader=" + leaderProtocol + ", local=" + PROTOCOL_VERSION + "). Falling back to isolated mode.");
                    return providerFactory.createLeader(plugin);
                }

                DemocracyLibApi api = DemocracyLibReflectiveApi.create(plugin, leader, providerFactory, logging);
                DemocracyLibApiRegistry.registerFollower(anchor, api, plugin);

                if (logging) {
                    int followers = DemocracyLibApiRegistry.followerCount(anchor);
                    String leaderFingerprint = "@" + Integer.toHexString(System.identityHashCode(leader));
                    log.info("Connected as follower. leaderPlugin=" + leaderPluginName + " leader=" + leaderFingerprint + ", protocol=" + PROTOCOL_VERSION + ", followers=" + followers);
                    log.info("Shared resources check: Mojang cache + thread pool are owned by the leader runtime. " +
                            "If both plugins point to the same leader fingerprint, they are sharing those resources.");
                }

                return api;
            }

            DemocracyLibApi createdLeader = providerFactory.createLeader(plugin);
            publishLeaderState(anchor, plugin, createdLeader, providerFactory);

            if (logging) {
                String leaderFingerprint = "@" + Integer.toHexString(System.identityHashCode(createdLeader));
                log.info("Elected as leader. leaderPlugin=" + plugin.getClass().getName() + " leaderPrint=" + leaderFingerprint + ", protocol=" + PROTOCOL_VERSION);
                log.info("Shared resources initialized in leader runtime: commonPool (ExecutorService) + caches.");
            }

            return createdLeader;
        }
    }

    public static @NotNull Object ensureLeader(@NotNull JavaPlugin caller,
                                        @NotNull ProviderFactory providerFactory,
                                        boolean logging) {
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(providerFactory, "providerFactory");

        DemocracyBootstrapLogger log = new DemocracyBootstrapLogger(logging, caller);

        Map<String, Object> anchor = DemocracyLibJvmAnchor.anchorMap();
        Object lock = DemocracyLibJvmAnchor.lock();

        synchronized (lock) {
            Object leader = anchor.get(KEY_LEADER);
            Object protocol = anchor.get(KEY_PROTOCOL);

            if (leader != null && protocol instanceof Integer i && i == PROTOCOL_VERSION) {
                return leader;
            }

            log.warn("No compatible leader found. Electing a new leader in caller classloader.");

            // Either no leader or protocol mismatch: create a new leader in the caller's classloader.
            DemocracyLibApi createdLeader = providerFactory.createLeader(caller);
            publishLeaderState(anchor, caller, createdLeader, providerFactory);
            return createdLeader;
        }
    }

    private static void publishLeaderState(@NotNull Map<String, Object> anchor,
                                          @NotNull JavaPlugin plugin,
                                          @NotNull DemocracyLibApi createdLeader,
                                          @NotNull ProviderFactory providerFactory) {
        anchor.put(KEY_LEADER, createdLeader);
        anchor.put(KEY_PROTOCOL, PROTOCOL_VERSION);
        anchor.put(KEY_LEADER_PLUGIN, plugin.getName());
        anchor.put(KEY_LEADER_PLUGIN_REF, plugin);
        anchor.put(KEY_LEADER_CLASS, createdLeader.getClass().getName());
        anchor.put(KEY_PROVIDER_FACTORY, providerFactory);

        // Cache the leader's service manager object so proxies don't have to reflectively fetch it each time.
        try {
            anchor.put(KEY_LEADER_SERVICE_MANAGER, createdLeader.getServiceManager());
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    public interface ProviderFactory {
        @NotNull DemocracyLibApi createLeader(@NotNull JavaPlugin plugin);
    }
}

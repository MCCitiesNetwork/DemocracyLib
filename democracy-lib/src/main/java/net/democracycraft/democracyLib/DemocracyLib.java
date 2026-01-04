package net.democracycraft.democracyLib;

import net.democracycraft.democracyLib.api.DemocracyLibApi;
import net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeContract;
import net.democracycraft.democracyLib.api.config.DemocracyConfigManager;
import net.democracycraft.democracyLib.api.config.github.GitHubGistConfiguration;
import net.democracycraft.democracyLib.api.service.engine.DemocracyServiceManager;
import net.democracycraft.democracyLib.api.service.engine.PluginBoundDemocracyService;
import net.democracycraft.democracyLib.api.service.github.GitHubGistService;
import net.democracycraft.democracyLib.api.service.mojang.MojangService;
import net.democracycraft.democracyLib.internal.config.DemocracyConfigManagerImpl;
import net.democracycraft.democracyLib.internal.config.GitHubGistConfigurationImpl;
import net.democracycraft.democracyLib.internal.runtime.DemocracyLibRuntime;
import net.democracycraft.democracyLib.internal.service.engine.DemocracyServiceManagerImpl;
import net.democracycraft.democracyLib.internal.service.github.GitHubGistServiceImpl;
import net.democracycraft.democracyLib.internal.service.mojang.MojangServiceImpl;
import net.democracycraft.democracyLib.internal.bootstrap.DemocracyLibApiRegistry;
import net.democracycraft.democracyLib.internal.bootstrap.DemocracyBootstrap;
import net.democracycraft.democracyLib.internal.bootstrap.DemocracyLibJvmAnchor;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * DemocracyLib core implementation.
 * <p>
 * This is a shaded library class (NOT a Bukkit plugin). In bridge mode, the first plugin to call
 * shared runtime (thread pool, caches, and HTTP client).
 */
public final class DemocracyLib implements DemocracyLibApi {

    private final DemocracyLibRuntime runtime;
    private final DemocracyServiceManager serviceManager;
    private final DemocracyConfigManager configManager;
    private final Map<String, Object> pluginServiceLockByName = new ConcurrentHashMap<>();

    public DemocracyLib() {
        this(new DemocracyLibRuntime());
    }

    public DemocracyLib(@NotNull DemocracyLibRuntime runtime) {
        this.runtime = runtime;
        this.serviceManager = new DemocracyServiceManagerImpl();
        // Avoid capturing a Bukkit logger at construction time.
        // A per-plugin logger-backed manager will be created lazily in getConfigManager().
        this.configManager = new DemocracyConfigManagerImpl(fallbackLogger());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <PluginType extends Plugin> @NotNull MojangService<PluginType> getMojangService(@NotNull PluginType plugin) {

        Object pluginLock = pluginServiceLockByName.computeIfAbsent(plugin.getName(), k -> new Object());
        synchronized (pluginLock) {
            MojangService<PluginType> existing = (MojangService<PluginType>) serviceManager.getServiceForPlugin(plugin, MojangService.class);
            if (existing != null) return existing;

            MojangService<PluginType> byScan = (MojangService<PluginType>) findPluginBoundServiceByPluginName(MojangService.class, plugin.getName());
            if (byScan != null) return byScan;

            MojangServiceImpl<PluginType> created = new MojangServiceImpl<>(plugin, runtime.getMojangCache());
            serviceManager.registerService(created);
            return created;
        }
    }

    @Override
    public <PluginType extends Plugin> @NotNull GitHubGistService<PluginType> getGitHubGistService(@NotNull PluginType plugin) {

        Object pluginLock = pluginServiceLockByName.computeIfAbsent(plugin.getName(), k -> new Object());
        synchronized (pluginLock) {
            GitHubGistConfiguration cfg = getConfigManager().createConfig(plugin, GitHubGistConfigurationImpl.class);
            return getGitHubGistService(plugin, cfg);
        }
    }

    @Override
    public <PluginType extends Plugin> @NotNull GitHubGistService<PluginType> getGitHubGistService(@NotNull PluginType plugin, @NotNull GitHubGistConfiguration configuration) {
        @SuppressWarnings("unchecked")
        GitHubGistService<PluginType> existing = (GitHubGistService<PluginType>) serviceManager.getServiceForPlugin(plugin, GitHubGistService.class);
        if (existing != null) {
            return existing;
        }

        @SuppressWarnings("unchecked")
        GitHubGistService<PluginType> byScan = (GitHubGistService<PluginType>) findPluginBoundServiceByPluginName(GitHubGistService.class, plugin.getName());
        if (byScan != null) return byScan;

        GitHubGistServiceImpl<PluginType> created = new GitHubGistServiceImpl<>(plugin, runtime.getCommonPool(), configuration, runtime.getHttpClient());
        serviceManager.registerService(created);
        return created;
    }

    @Override
    public @NotNull DemocracyServiceManager getServiceManager() {
        return serviceManager;
    }

    private static @NotNull Logger fallbackLogger() {
        // Fallback only (used if Bukkit/Plugin logger not available). We keep it stable and non-null.
        return Logger.getLogger("DemocracyLib");
    }

    @Override
    public @NotNull DemocracyConfigManager getConfigManager() {
        // Prefer the leader plugin logger when available.
        try {
            var anchor = DemocracyLibJvmAnchor.anchorMap();
            Object leaderPluginRef = anchor.get(DemocracyBootstrap.KEY_LEADER_PLUGIN_REF);
            if (leaderPluginRef instanceof Plugin p) {
                return new DemocracyConfigManagerImpl(p.getLogger());
            }
        } catch (Throwable ignored) {
        }
        return configManager;
    }

    @Override
    public @NotNull String getServiceName() {
        return "DemocracyAPI";
    }

    public @NotNull DemocracyLibRuntime getRuntime() {
        return runtime;
    }

    @Override
    public void shutdown() {
        Map<String, Object> anchor;
        Object lock;
        try {
            anchor = DemocracyLibJvmAnchor.anchorMap();
            lock = DemocracyLibJvmAnchor.lock();
        } catch (Throwable t) {
            // As a leader instance, still try to close local resources.
            runtime.shutdown();
            return;
        }

        synchronized (lock) {
            // Followers are stored in ApiRegistry using Plugin identity as the key.
            // We persist the leader's Plugin instance in the JVM anchor so leader shutdown can unregister safely.
            try {
                Object leaderPluginRef = anchor.get(DemocracyBootstrap.KEY_LEADER_PLUGIN_REF);
                if (leaderPluginRef instanceof Plugin p) {
                    DemocracyLibApiRegistry.unregisterFollower(anchor, p);
                }
            } catch (Throwable ignored) {

            }

            int followers = DemocracyLibApiRegistry.followerCount(anchor);

            // Clear leader state first to force immediate on-demand re-election.
            anchor.remove(DemocracyBootstrap.KEY_LEADER);
            anchor.remove(DemocracyBootstrap.KEY_LEADER_SERVICE_MANAGER);
            anchor.remove(DemocracyBootstrap.KEY_LEADER_PLUGIN);
            anchor.remove(DemocracyBootstrap.KEY_LEADER_PLUGIN_REF);
            anchor.remove(DemocracyBootstrap.KEY_LEADER_CLASS);
            anchor.remove(DemocracyBootstrap.KEY_PROTOCOL);

            if (followers > 0) {
                // Failover mode: do NOT attempt to preserve caches. Runtime will be recreated by the next leader.
                // We still shut down our own runtime to avoid leaked threads.
                runtime.shutdown();
                return;
            }

            // No followers -> full shutdown: clear the entire anchor.
            runtime.shutdown();
            try {
                // Preserve the lock object so concurrent callers don't NPE; they'll just recreate state.
                Object existingLock = anchor.get(GeneratedBridgeContract.AnchorKeys.LOCK);
                anchor.clear();
                if (existingLock != null) {
                    anchor.put(GeneratedBridgeContract.AnchorKeys.LOCK, existingLock);
                }
            } catch (Throwable ignored) {
            }
        }
    }


    @SuppressWarnings("unchecked")
    private @Nullable <ApiType> ApiType findPluginBoundServiceByPluginName(@NotNull Class<ApiType> apiType, @NotNull String pluginName) {
        for (var svc : serviceManager.getAllServices()) {
            if (!(svc instanceof PluginBoundDemocracyService<?> bound)) continue;

            boolean typeMatches;
            try {
                typeMatches = apiType.isInstance(svc);
            } catch (LinkageError | ClassCastException ignored) {
                typeMatches = false;
            }
            if (!typeMatches) continue;

            String boundName;
            try {
                boundName = bound.getBoundPlugin().getName();
            } catch (Throwable t) {
                boundName = null;
            }

            if (boundName != null && boundName.equalsIgnoreCase(pluginName)) {
                return (ApiType) svc;
            }
        }
        return null;
    }
}

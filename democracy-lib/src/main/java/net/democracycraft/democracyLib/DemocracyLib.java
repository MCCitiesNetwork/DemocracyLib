package net.democracycraft.democracyLib;

import net.democracycraft.democracyLib.api.DemocracyLibApi;
import net.democracycraft.democracyLib.api.config.DemocracyConfigLoader;
import net.democracycraft.democracyLib.api.config.github.GitHubGistConfiguration;
import net.democracycraft.democracyLib.api.service.engine.DemocracyServiceManager;
import net.democracycraft.democracyLib.api.service.github.GitHubGistService;
import net.democracycraft.democracyLib.api.service.mojang.MojangService;
import net.democracycraft.democracyLib.internal.config.DemocracyConfigLoaderImpl;
import net.democracycraft.democracyLib.internal.config.GitHubGistConfigurationImpl;
import net.democracycraft.democracyLib.internal.runtime.DemocracyLibRuntime;
import net.democracycraft.democracyLib.internal.service.engine.DemocracyServiceManagerImpl;
import net.democracycraft.democracyLib.internal.service.github.GitHubGistServiceImpl;
import net.democracycraft.democracyLib.internal.service.mojang.MojangServiceImpl;
import net.democracycraft.democracyLib.internal.bootstrap.ApiRegistry;
import net.democracycraft.democracyLib.internal.bootstrap.BridgeContract;
import net.democracycraft.democracyLib.internal.bootstrap.DemocracyBootstrap;
import net.democracycraft.democracyLib.internal.bootstrap.JvmAnchor;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * DemocracyLib core implementation.
 * <p>
 * This is a shaded library class (NOT a Bukkit plugin). In bridge mode, the first plugin to call
 * shared runtime (thread pool, caches, and HTTP client).
 */
public final class DemocracyLib implements DemocracyLibApi {

    private final DemocracyLibRuntime runtime;
    private final DemocracyServiceManager serviceManager;

    public DemocracyLib() {
        this(new DemocracyLibRuntime());
    }

    public DemocracyLib(@NotNull DemocracyLibRuntime runtime) {
        this.runtime = runtime;
        this.serviceManager = new DemocracyServiceManagerImpl();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <PluginType extends Plugin> @NotNull MojangService<PluginType> getMojangService(@NotNull PluginType plugin) {
        MojangService<PluginType> existing = (MojangService<PluginType>) serviceManager.getServiceForPlugin(plugin, MojangService.class);
        if (existing != null) return existing;

        MojangServiceImpl<PluginType> created = new MojangServiceImpl<>(plugin, runtime.getMojangCache());
        serviceManager.registerService(created);
        return created;
    }

    @Override
    public <PluginType extends Plugin> @NotNull GitHubGistService<PluginType> getGitHubGistService(@NotNull PluginType plugin) {
        DemocracyConfigLoader loader = new DemocracyConfigLoaderImpl(plugin.getLogger());
        GitHubGistConfiguration cfg = loader.loadOrCreate(plugin, "github-gist.yml", GitHubGistConfigurationImpl.class);
        return getGitHubGistService(plugin, cfg);
    }

    @Override
    public <PluginType extends Plugin> @NotNull GitHubGistService<PluginType> getGitHubGistService(@NotNull PluginType plugin, @NotNull GitHubGistConfiguration configuration) {
        @SuppressWarnings("unchecked")
        GitHubGistService<PluginType> existing = (GitHubGistService<PluginType>) serviceManager.getServiceForPlugin(plugin, GitHubGistService.class);
        if (existing != null) {
            return existing;
        }

        GitHubGistServiceImpl<PluginType> created = new GitHubGistServiceImpl<>(plugin, runtime.getCommonPool(), configuration, runtime.getHttpClient());
        serviceManager.registerService(created);
        return created;
    }

    @Override
    public @NotNull DemocracyServiceManager getServiceManager() {
        return serviceManager;
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
            anchor = JvmAnchor.anchorMap();
            lock = JvmAnchor.lock();
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
                    ApiRegistry.unregisterFollower(anchor, p);
                }
            } catch (Throwable ignored) {

            }

            int followers = ApiRegistry.followerCount(anchor);

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
                Object existingLock = anchor.get(BridgeContract.AnchorKeys.LOCK);
                anchor.clear();
                if (existingLock != null) {
                    anchor.put(BridgeContract.AnchorKeys.LOCK, existingLock);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}

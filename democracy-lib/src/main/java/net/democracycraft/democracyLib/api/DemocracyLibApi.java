package net.democracycraft.democracyLib.api;

import net.democracycraft.democracyLib.DemocracyLib;
import net.democracycraft.democracyLib.internal.bootstrap.DemocracyBootstrap;
import net.democracycraft.democracyLib.api.bootstrap.contract.BridgeStability;
import net.democracycraft.democracyLib.api.config.github.GitHubGistConfiguration;
import net.democracycraft.democracyLib.api.service.engine.DemocracyServiceManager;
import net.democracycraft.democracyLib.api.service.engine.SyncDemocracyService;
import net.democracycraft.democracyLib.api.service.github.GitHubGistService;
import net.democracycraft.democracyLib.api.service.mojang.MojangService;
import net.democracycraft.democracyLib.internal.runtime.DemocracyLibRuntime;
import net.democracycraft.democracyLib.api.bootstrap.contract.BridgeApi;
import net.democracycraft.democracyLib.api.bootstrap.contract.BridgeMethod;
import net.democracycraft.democracyLib.api.bootstrap.contract.BridgeNamespace;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import net.democracycraft.democracyLib.internal.bootstrap.BridgeContract;

@BridgeApi(BridgeNamespace.DEMOCRACY_LIB_API)
public interface DemocracyLibApi extends SyncDemocracyService {

    /**
     * Creates an instance of the Mojang Service bound to the provided plugin.
     * @param plugin The plugin to bind the service to.
     * @param <PluginType> The type of the plugin.
     * @return An instance of the Mojang Service.
     */
    @BridgeMethod(stability = BridgeStability.STABLE_ID, id = BridgeContract.Ids.LibApi.GET_MOJANG_SERVICE)
    <PluginType extends Plugin> @NotNull MojangService<PluginType> getMojangService(@NotNull PluginType plugin);

    /**
     * Creates an instance of the GitHub Gist Service bound to the provided plugin.
     * @param plugin The plugin to bind the service to.
     * @param <PluginType> The type of the plugin.
     * @return An instance of the GitHub Gist Service.
     */
    @BridgeMethod(stability = BridgeStability.STABLE_ID, id = BridgeContract.Ids.LibApi.GET_GITHUB_GIST_SERVICE)
    <PluginType extends Plugin> @NotNull GitHubGistService<PluginType> getGitHubGistService(@NotNull PluginType plugin);

    /**
     * Creates an instance of the GitHub Gist Service bound to the provided plugin with the provided configuration.
     * @param plugin The plugin to bind the service to.
     * @param configuration The configuration to use for the service.
     * @param <PluginType> The type of the plugin.
     * @return An instance of the GitHub Gist Service.
     */
    @BridgeMethod(stability = BridgeStability.STABLE_ID, id = BridgeContract.Ids.LibApi.GET_GITHUB_GIST_SERVICE_WITH_CONFIG)
    <PluginType extends Plugin> @NotNull GitHubGistService<PluginType> getGitHubGistService(@NotNull PluginType plugin, @NotNull GitHubGistConfiguration configuration);


    /**
     * Gets the Democracy Service Manager.
     * @return The Democracy Service Manager.
     */
    @BridgeMethod(stability = BridgeStability.STABLE_ID, id = BridgeContract.Ids.LibApi.GET_SERVICE_MANAGER)
    @NotNull DemocracyServiceManager getServiceManager();


    /**
     * Shuts down this API instance.
     * <p>
     * In shaded bridge mode:
     * <ul>
     *     <li>Followers: only unregister from the JVM-wide follower registry (does not stop shared runtime).</li>
     *     <li>Leader: if no followers remain, closes shared runtime and clears the JVM anchor.
     *     Otherwise, clears leader state to force on-demand re-election on the next call.</li>
     * </ul>
     */
    @BridgeMethod(stability = BridgeStability.STABLE_ID, id = BridgeContract.Ids.LibApi.SHUTDOWN)
    void shutdown();

    /**
     * Shaded entry point.
     * The first caller becomes the leader (shared pool/cache/http). Subsequent callers receive a reflection bridge.
     */
    static @NotNull DemocracyLibApi instance(@NotNull JavaPlugin plugin) {
        return instance(plugin, false);
    }

    /**
     * Shaded entry point with optional bootstrap logging.
     */
    static @NotNull DemocracyLibApi instance(@NotNull JavaPlugin plugin, boolean logging) {
        return DemocracyBootstrap.init(plugin, javaPlugin -> new DemocracyLib(new DemocracyLibRuntime()), logging);
    }

}

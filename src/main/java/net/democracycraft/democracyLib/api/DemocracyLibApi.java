package net.democracycraft.democracyLib.api;

import net.democracycraft.democracyLib.DemocracyLib;
import net.democracycraft.democracyLib.api.config.github.GitHubGistConfiguration;
import net.democracycraft.democracyLib.api.service.engine.DemocracyServiceManager;
import net.democracycraft.democracyLib.api.service.engine.SyncDemocracyService;
import net.democracycraft.democracyLib.api.service.github.GitHubGistService;
import net.democracycraft.democracyLib.api.service.mojang.MojangService;
import net.democracycraft.democracyLib.api.service.worldguard.WorldGuardService;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public interface DemocracyLibApi extends SyncDemocracyService {

    /**
     * Creates an instance of the Mojang Service bound to the provided plugin.
     * @param plugin The plugin to bind the service to.
     * @param <PluginType> The type of the plugin.
     * @return An instance of the Mojang Service.
     */
    <PluginType extends Plugin> @NotNull MojangService<PluginType> getMojangService(@NotNull PluginType plugin);

    /**
     * Creates an instance of the GitHub Gist Service bound to the provided plugin.
     * @param plugin The plugin to bind the service to.
     * @param <PluginType> The type of the plugin.
     * @return An instance of the GitHub Gist Service.
     */
    <PluginType extends Plugin> @NotNull GitHubGistService<PluginType> getGitHubGistService(@NotNull PluginType plugin);

    /**
     * Creates an instance of the GitHub Gist Service bound to the provided plugin with the provided configuration.
     * @param plugin The plugin to bind the service to.
     * @param configuration The configuration to use for the service.
     * @param <PluginType> The type of the plugin.
     * @return An instance of the GitHub Gist Service.
     */
    <PluginType extends Plugin> @NotNull GitHubGistService<PluginType> getGitHubGistService(@NotNull PluginType plugin, @NotNull GitHubGistConfiguration configuration);

    /**
     * Gets the WorldGuard Service.
     * @return The WorldGuard Service.
     */
    @NotNull WorldGuardService getWorldGuardService();

    /**
     * Gets the Democracy Service Manager.
     * @return The Democracy Service Manager.
     */
    @NotNull DemocracyServiceManager getServiceManager();


    static DemocracyLibApi getInstance() {
        return DemocracyLib.getInstance();
    }

}

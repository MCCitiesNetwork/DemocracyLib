package net.democracycraft.democracyLib;

import net.democracycraft.democracyLib.api.DemocracyLibApi;
import net.democracycraft.democracyLib.api.cache.MojangServiceDemocracyCache;
import net.democracycraft.democracyLib.internal.config.ConfigFolder;
import net.democracycraft.democracyLib.api.config.DemocracyConfigLoader;
import net.democracycraft.democracyLib.api.config.github.GitHubGistConfiguration;
import net.democracycraft.democracyLib.api.service.engine.DemocracyServiceManager;
import net.democracycraft.democracyLib.api.service.github.GitHubGistService;
import net.democracycraft.democracyLib.api.service.mojang.MojangService;
import net.democracycraft.democracyLib.api.service.worldguard.WorldGuardService;
import net.democracycraft.democracyLib.internal.cache.MojangServiceDemocracyCacheImpl;
import net.democracycraft.democracyLib.internal.config.DemocracyConfigLoaderImpl;
import net.democracycraft.democracyLib.internal.config.GitHubGistConfigurationImpl;
import net.democracycraft.democracyLib.internal.service.engine.DemocracyServiceManagerImpl;
import net.democracycraft.democracyLib.internal.service.github.GitHubGistServiceImpl;
import net.democracycraft.democracyLib.internal.service.mojang.MojangServiceImpl;
import net.democracycraft.democracyLib.internal.service.worldguard.WorldGuardServiceImpl;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DemocracyLib extends JavaPlugin implements DemocracyLibApi {

    private static DemocracyLib instance;

    private ExecutorService commonPool = null;

    private MojangServiceDemocracyCache mojangServiceDemocracyCache;

    private DemocracyServiceManager democracyServiceManager;

    private WorldGuardService worldGuardService;

    private DemocracyConfigLoader localLoader;

    private GitHubGistConfiguration defaultGitHubGistConfiguration = null;

    @Override
    public void onLoad() {
        instance = this;
        getDataFolder().mkdir();
        ConfigFolder.createFolders();

        localLoader = new DemocracyConfigLoaderImpl(this);
        democracyServiceManager = new DemocracyServiceManagerImpl();
        commonPool = Executors.newCachedThreadPool();

        mojangServiceDemocracyCache = new MojangServiceDemocracyCacheImpl(commonPool);
        defaultGitHubGistConfiguration = getDefaultGitHubGistConfiguration();

        getLogger().info("DemocracyLib has been loaded");
    }

    @Override
    public void onEnable() {
        getLogger().info("DemocracyLib has been enabled.");
    }

    @Override
    public void onDisable() {
        commonPool.shutdown();
        getLogger().info("DemocracyLib has been disabled.");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <PluginType extends Plugin> @NotNull MojangService<PluginType> getMojangService(@NotNull PluginType plugin) {
        MojangService<PluginType> existingService = (MojangService<PluginType>) getServiceManager()
                .getServiceForPlugin(plugin, MojangService.class);

        if (existingService != null) {
            return existingService;
        }

        MojangServiceImpl<PluginType> newService = new MojangServiceImpl<>(plugin, mojangServiceDemocracyCache);

        getServiceManager().registerService(newService);

        return newService;
    }

    @Override
    public @NotNull <PluginType extends Plugin> GitHubGistService<PluginType> getGitHubGistService(@NotNull PluginType plugin) {
        return getPluginTypeGitHubGistService(plugin, defaultGitHubGistConfiguration);
    }

    @Override
    public @NotNull <PluginType extends Plugin> GitHubGistService<PluginType> getGitHubGistService(@NotNull PluginType plugin, @NotNull GitHubGistConfiguration configuration) {
        return getPluginTypeGitHubGistService(plugin, configuration);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private <PluginType extends Plugin> GitHubGistService<PluginType> getPluginTypeGitHubGistService(PluginType plugin, GitHubGistConfiguration configuration) {
        GitHubGistService<PluginType> existingService = (GitHubGistService<PluginType>) getServiceManager()
                .getServiceForPlugin(plugin, GitHubGistService.class);

        if (existingService != null) {
            return existingService;
        }

        GitHubGistService<PluginType> newService = new GitHubGistServiceImpl<>(plugin,commonPool, configuration);

        getServiceManager().registerService(newService);

        return newService;
    }

    @Override
    public @NotNull WorldGuardService getWorldGuardService() {
        if(worldGuardService == null) {
            worldGuardService = new WorldGuardServiceImpl();
        }
        return worldGuardService;
    }

    @Override
    public @NotNull DemocracyServiceManager getServiceManager() {
        if(democracyServiceManager == null) {
            democracyServiceManager = new DemocracyServiceManagerImpl();
        }
        return democracyServiceManager;
    }

    @Override
    public @NotNull String getServiceName() {
        return "DemocracyAPI";
    }

    public @NotNull ExecutorService getCommonPool() {
        if (commonPool == null) {
            commonPool = Executors.newCachedThreadPool();
        }
        return commonPool;
    }

    private GitHubGistConfiguration getDefaultGitHubGistConfiguration() {
        if (defaultGitHubGistConfiguration == null) {
            defaultGitHubGistConfiguration = localLoader.loadOrCreate(ConfigFolder.GITHUB, "github_gist.yml", GitHubGistConfigurationImpl.class);
        }
        return defaultGitHubGistConfiguration;
    }

    public static DemocracyLib getInstance() {
        return instance;
    }
}

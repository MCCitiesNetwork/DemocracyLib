package net.democracycraft.democracyLib.internal.bootstrap.service;

import net.democracycraft.democracyLib.api.DemocracyLibApi;
import net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeContract;
import net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeIds;
import net.democracycraft.democracyLib.api.config.DemocracyConfigManager;
import net.democracycraft.democracyLib.api.config.github.GitHubGistConfiguration;
import net.democracycraft.democracyLib.api.service.engine.DemocracyServiceManager;
import net.democracycraft.democracyLib.api.service.github.GitHubGistService;
import net.democracycraft.democracyLib.api.service.mojang.MojangService;
import net.democracycraft.democracyLib.internal.bootstrap.*;
import net.democracycraft.democracyLib.internal.bootstrap.proxy.DemocracyServiceProxies;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local DemocracyLibApi implementation that delegates to the leader instance via reflection.
 */
public class DemocracyLibReflectiveApi implements DemocracyLibApi {

    private final JavaPlugin caller;
    private final DemocracyBootstrap.ProviderFactory providerFactory;
    private final boolean logging;

    private final Map<String, MethodHandle> mhCache = new ConcurrentHashMap<>();

    private volatile DemocracyServiceManager serviceManagerProxy;
    private volatile DemocracyConfigManager configManagerProxy;

    private DemocracyLibReflectiveApi(JavaPlugin caller, DemocracyBootstrap.ProviderFactory providerFactory, boolean logging) {
        this.caller = caller;
        this.providerFactory = providerFactory;
        this.logging = logging;
    }

    public static @NotNull DemocracyLibApi create(@NotNull JavaPlugin caller,
                                          @NotNull Object leader,
                                          @NotNull DemocracyBootstrap.ProviderFactory providerFactory) {
        return create(caller, leader, providerFactory, false);
    }

    public  static @NotNull DemocracyLibApi create(@NotNull JavaPlugin caller,
                                          @NotNull Object leader,
                                          @NotNull DemocracyBootstrap.ProviderFactory providerFactory,
                                          boolean logging) {
        // leader param kept for binary compatibility with older call sites; current implementation re-reads via anchor.
        return new DemocracyLibReflectiveApi(caller, providerFactory, logging);
    }

    @Override
    public <PluginType extends Plugin> @NotNull MojangService<PluginType> getMojangService(@NotNull PluginType plugin) {
        Object leaderService = invokeLeaderByContractId(GeneratedBridgeIds.DemocracyLibApi.getMojangService__Plugin, new Object[]{plugin});
        return DemocracyServiceProxies.mojangProxy(leaderService);
    }

    @Override
    public <PluginType extends Plugin> @NotNull GitHubGistService<PluginType> getGitHubGistService(@NotNull PluginType plugin) {
        Object leaderService = invokeLeaderByContractId(GeneratedBridgeIds.DemocracyLibApi.getGitHubGistService__Plugin, new Object[]{plugin});
        return DemocracyServiceProxies.githubProxy(leaderService);
    }

    @Override
    public <PluginType extends Plugin> @NotNull GitHubGistService<PluginType> getGitHubGistService(@NotNull PluginType plugin, @NotNull GitHubGistConfiguration configuration) {
        Object leaderService = invokeLeaderByContractId(GeneratedBridgeIds.DemocracyLibApi.getGitHubGistService__Plugin__GitHubGistConfiguration, new Object[]{plugin, configuration});
        return DemocracyServiceProxies.githubProxy(leaderService);
    }

    @Override
    public @NotNull DemocracyServiceManager getServiceManager() {
        DemocracyServiceManager existing = serviceManagerProxy;
        if (existing != null) return existing;

        synchronized (this) {
            if (serviceManagerProxy != null) return serviceManagerProxy;

            Object leaderMgr = null;
            try {
                var anchor = DemocracyLibJvmAnchor.anchorMap();
                leaderMgr = anchor.get(DemocracyBootstrap.KEY_LEADER_SERVICE_MANAGER);
            } catch (Throwable ignored) {
            }
            if (leaderMgr == null) {
                leaderMgr = invokeLeaderByContractId(GeneratedBridgeIds.DemocracyLibApi.getServiceManager, new Object[]{});
            }

            serviceManagerProxy = new DemocracyBridgeServiceManager(leaderMgr);
            return serviceManagerProxy;
        }
    }

    @Override
    public @NotNull DemocracyConfigManager getConfigManager() {
        DemocracyConfigManager existing = configManagerProxy;
        if (existing != null) return existing;

        synchronized (this) {
            if (configManagerProxy != null) return configManagerProxy;

            Object leaderConfigMgr = invokeLeaderByContractId(GeneratedBridgeIds.DemocracyLibApi.getConfigManager, new Object[]{});
            configManagerProxy = new DemocracyBridgeConfigManager(leaderConfigMgr);
            return configManagerProxy;
        }
    }

    @Override
    public void shutdown() {
        // Follower shutdown: detach only.
        try {
            Map<String, Object> anchor = DemocracyLibJvmAnchor.anchorMap();
            DemocracyLibApiRegistry.unregisterFollower(anchor, caller);
        } catch (Throwable ignored) {
        }

        // Clear local caches.
        serviceManagerProxy = null;
        configManagerProxy = null;
        mhCache.clear();
    }

    @Override
    public @NotNull String getServiceName() {
        return "DemocracyAPI-Bridge(" + caller.getName() + ")";
    }

    private Object invokeLeaderByContractId(@NotNull String contractId, Object[] args) {
        Objects.requireNonNull(contractId, "contractId");

        Object leader = DemocracyBootstrap.ensureLeader(caller, providerFactory, logging);
        Object[] actualArgs = args == null ? new Object[0] : args;

        GeneratedBridgeContract.Spec spec = DemocracyBootstrapReflection.loadGeneratedSpec(contractId);
        String key = DemocracyBootstrapReflection.cacheKeyByContractId(contractId);

        MethodHandle methodHandle = mhCache.computeIfAbsent(key, k -> {
            Method targetMethod = DemocracyBootstrapReflection.resolveByGeneratedSpec(leader.getClass(), spec);
            try {
                return MethodHandles.publicLookup().unreflect(targetMethod);
            } catch (IllegalAccessException e) {
                try {
                    targetMethod.setAccessible(true);
                    return MethodHandles.lookup().unreflect(targetMethod);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        try {
            Object[] full = new Object[actualArgs.length + 1];
            full[0] = leader;
            System.arraycopy(actualArgs, 0, full, 1, actualArgs.length);
            return methodHandle.invokeWithArguments(full);
        } catch (Throwable t) {
            try {
                mhCache.remove(key);
            } catch (Throwable ignored) {
            }
            Object retryLeader = DemocracyBootstrap.ensureLeader(caller, providerFactory, logging);
            try {
                Method retryMethod = DemocracyBootstrapReflection.resolveByGeneratedSpec(retryLeader.getClass(), spec);
                MethodHandle retryHandle;
                try {
                    retryHandle = MethodHandles.publicLookup().unreflect(retryMethod);
                } catch (IllegalAccessException e) {
                    retryMethod.setAccessible(true);
                    retryHandle = MethodHandles.lookup().unreflect(retryMethod);
                }

                Object[] full = new Object[actualArgs.length + 1];
                full[0] = retryLeader;
                System.arraycopy(actualArgs, 0, full, 1, actualArgs.length);
                return retryHandle.invokeWithArguments(full);
            } catch (Throwable t2) {
                throw new RuntimeException("Failed invoking leader contract id: " + contractId, t2);
            }
        }
    }
}

package net.democracycraft.democracyLib.internal.bootstrap;

import net.democracycraft.democracyLib.api.DemocracyLibApi;
import net.democracycraft.democracyLib.api.config.github.GitHubGistConfiguration;
import net.democracycraft.democracyLib.api.service.engine.DemocracyServiceManager;
import net.democracycraft.democracyLib.api.service.github.GitHubGistService;
import net.democracycraft.democracyLib.api.service.mojang.MojangService;
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
final class ReflectionBridgeApi implements DemocracyLibApi {

    private final JavaPlugin caller;
    private final DemocracyBootstrap.ProviderFactory providerFactory;

    private final Map<String, MethodHandle> mhCache = new ConcurrentHashMap<>();

    private volatile DemocracyServiceManager serviceManagerProxy;

    private ReflectionBridgeApi(JavaPlugin caller, DemocracyBootstrap.ProviderFactory providerFactory) {
        this.caller = caller;
        this.providerFactory = providerFactory;
    }

    static @NotNull DemocracyLibApi create(@NotNull JavaPlugin caller, @NotNull Object leader, @NotNull DemocracyBootstrap.ProviderFactory providerFactory) {
        // leader param kept for binary compatibility with older call sites; current implementation re-reads via anchor.
        return new ReflectionBridgeApi(caller, providerFactory);
    }

    @Override
    public <PluginType extends Plugin> @NotNull MojangService<PluginType> getMojangService(@NotNull PluginType plugin) {
        Object leaderService = invokeLeaderByContractId(BridgeContract.Ids.LibApi.GET_MOJANG_SERVICE, new Object[]{plugin});
        return ServiceProxies.mojangProxy(leaderService);
    }

    @Override
    public <PluginType extends Plugin> @NotNull GitHubGistService<PluginType> getGitHubGistService(@NotNull PluginType plugin) {
        Object leaderService = invokeLeaderByContractId(BridgeContract.Ids.LibApi.GET_GITHUB_GIST_SERVICE, new Object[]{plugin});
        return ServiceProxies.githubProxy(leaderService);
    }

    @Override
    public <PluginType extends Plugin> @NotNull GitHubGistService<PluginType> getGitHubGistService(@NotNull PluginType plugin, @NotNull GitHubGistConfiguration configuration) {
        Object leaderService = invokeLeaderByContractId(BridgeContract.Ids.LibApi.GET_GITHUB_GIST_SERVICE_WITH_CONFIG, new Object[]{plugin, configuration});
        return ServiceProxies.githubProxy(leaderService);
    }

    @Override
    public @NotNull DemocracyServiceManager getServiceManager() {
        DemocracyServiceManager existing = serviceManagerProxy;
        if (existing != null) return existing;

        synchronized (this) {
            if (serviceManagerProxy != null) return serviceManagerProxy;

            Object leaderMgr = null;
            try {
                var anchor = JvmAnchor.anchorMap();
                leaderMgr = anchor.get(DemocracyBootstrap.KEY_LEADER_SERVICE_MANAGER);
            } catch (Throwable ignored) {
            }
            if (leaderMgr == null) {
                leaderMgr = invokeLeaderByContractId(BridgeContract.Ids.LibApi.GET_SERVICE_MANAGER, new Object[]{});
            }

            serviceManagerProxy = new BridgeServiceManager(leaderMgr);
            return serviceManagerProxy;
        }
    }

    @Override
    public void shutdown() {
        // Follower shutdown: detach only.
        try {
            Map<String, Object> anchor = JvmAnchor.anchorMap();
            ApiRegistry.unregisterFollower(anchor, caller);
        } catch (Throwable ignored) {
        }

        // Clear local caches.
        serviceManagerProxy = null;
        mhCache.clear();
    }

    @Override
    public @NotNull String getServiceName() {
        return "DemocracyAPI-Bridge(" + caller.getName() + ")";
    }

    private Object invokeLeaderByContractId(@NotNull String contractId, Object[] args) {
        Objects.requireNonNull(contractId, "contractId");

        Object leader = DemocracyBootstrap.ensureLeader(caller, providerFactory);
        Object[] actualArgs = args == null ? new Object[0] : args;

        Object spec = BootstrapReflection.loadGeneratedSpec(contractId);
        String key = BootstrapReflection.cacheKeyByContractId(contractId);

        MethodHandle methodHandle = mhCache.computeIfAbsent(key, k -> {
            Method targetMethod = BootstrapReflection.resolveByGeneratedSpec(leader.getClass(), spec);
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
            Object retryLeader = DemocracyBootstrap.ensureLeader(caller, providerFactory);
            try {
                Method retryMethod = BootstrapReflection.resolveByGeneratedSpec(retryLeader.getClass(), spec);
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

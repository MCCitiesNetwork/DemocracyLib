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
import net.democracycraft.democracyLib.internal.bootstrap.bridge.BridgeValueAdapter;
import net.democracycraft.democracyLib.internal.bootstrap.proxy.DemocracyServiceProxies;
import net.democracycraft.democracyLib.internal.config.DemocracyConfigManagerImpl;
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

    private final Map<String, ResolvedLeaderCall> callCache = new ConcurrentHashMap<>();

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

    public static @NotNull DemocracyLibApi create(@NotNull JavaPlugin caller,
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

            // Configs are per-plugin files, not shared runtime state, so followers own their manager.
            // Bridging it is also unsound: configurate is relocated per copy, so the leader's
            // ObjectMapper cannot see a follower class's @ConfigSerializable annotations.
            configManagerProxy = new DemocracyConfigManagerImpl(caller.getLogger());
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
        callCache.clear();
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

        ResolvedLeaderCall call = callCache.computeIfAbsent(key, k ->
                resolveLeaderCall(leader.getClass(), spec));

        try {
            return invokeAdapted(call, leader, actualArgs);
        } catch (Throwable t) {
            try {
                callCache.remove(key);
            } catch (Throwable ignored) {
            }
            Object retryLeader = DemocracyBootstrap.ensureLeader(caller, providerFactory, logging);
            try {
                ResolvedLeaderCall retryCall = resolveLeaderCall(retryLeader.getClass(), spec);
                return invokeAdapted(retryCall, retryLeader, actualArgs);
            } catch (Throwable t2) {
                throw new RuntimeException("Failed invoking leader contract id: " + contractId, t2);
            }
        }
    }

    private Object invokeAdapted(@NotNull ResolvedLeaderCall call, @NotNull Object leader, Object[] args) throws Throwable {
        // Adapt arguments to types the leader's classloader can hold (library-typed args become proxies).
        Object[] adapted = BridgeValueAdapter.adaptArguments(call.method(), args, getClass().getClassLoader());
        Object[] full = new Object[adapted.length + 1];
        full[0] = leader;
        System.arraycopy(adapted, 0, full, 1, adapted.length);
        return call.handle().invokeWithArguments(full);
    }

    private static @NotNull ResolvedLeaderCall resolveLeaderCall(@NotNull Class<?> leaderClass,
                                                                 @NotNull GeneratedBridgeContract.Spec spec) {
        Method targetMethod = DemocracyBootstrapReflection.resolveByGeneratedSpec(leaderClass, spec);
        try {
            return new ResolvedLeaderCall(targetMethod, MethodHandles.publicLookup().unreflect(targetMethod));
        } catch (IllegalAccessException e) {
            try {
                targetMethod.setAccessible(true);
                return new ResolvedLeaderCall(targetMethod, MethodHandles.lookup().unreflect(targetMethod));
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private record ResolvedLeaderCall(Method method, MethodHandle handle) {
    }
}

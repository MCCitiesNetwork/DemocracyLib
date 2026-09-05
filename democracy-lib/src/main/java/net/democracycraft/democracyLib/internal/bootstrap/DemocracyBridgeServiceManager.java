package net.democracycraft.democracyLib.internal.bootstrap;

import net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeContract;
import net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeIds;
import net.democracycraft.democracyLib.api.service.engine.DemocracyService;
import net.democracycraft.democracyLib.api.service.engine.DemocracyServiceManager;
import net.democracycraft.democracyLib.api.service.engine.PluginBoundDemocracyService;
import net.democracycraft.democracyLib.internal.bootstrap.bridge.BridgeTypeContract;
import net.democracycraft.democracyLib.internal.bootstrap.bridge.BridgeValueAdapter;
import net.democracycraft.democracyLib.internal.bootstrap.proxy.DemocracyServiceProxyFactory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection bridge for the leader's DemocracyServiceManager.
 * <p>
 * Returned services are follower-local proxies ({@link DemocracyServiceProxyFactory}); arguments
 * are adapted by {@link BridgeValueAdapter} so the leader can hold them. {@code Class} tokens used
 * as <b>filters</b> are translated to the leader's classloader first — the leader's
 * {@code isInstance} checks would otherwise silently never match a follower's Class object.
 */
public class DemocracyBridgeServiceManager implements DemocracyServiceManager {

    private final Object leaderServiceManager;
    private final Map<String, ResolvedLeaderCall> callCache = new ConcurrentHashMap<>();

    private record ResolvedLeaderCall(Method method, MethodHandle handle) {
    }

    public DemocracyBridgeServiceManager(@NotNull Object leaderServiceManager) {
        this.leaderServiceManager = Objects.requireNonNull(leaderServiceManager, "leaderServiceManager");
    }

    @Override
    public @NotNull List<DemocracyService> getAllServices() {
        Object raw = invokeLeaderByContractId(GeneratedBridgeIds.DemocracyServiceManager.getAllServices, new Object[]{});
        if (!(raw instanceof List<?> list)) return List.of();

        List<DemocracyService> out = new ArrayList<>(list.size());
        for (Object leaderService : list) {
            if (leaderService == null) continue;
            out.add(DemocracyServiceProxyFactory.proxyAs(DemocracyService.class, leaderService));
        }
        return List.copyOf(out);
    }

    @Override
    public @NotNull <T extends DemocracyService> List<T> getServicesByType(@NotNull Class<T> serviceType) {
        Object raw = invokeLeaderByContractId(GeneratedBridgeIds.DemocracyServiceManager.getServicesByType__Class,
                new Object[]{translateFilterClass(serviceType)});
        if (!(raw instanceof List<?> list)) return List.of();

        List<T> out = new ArrayList<>(list.size());
        for (Object leaderService : list) {
            if (leaderService == null) continue;
            out.add(DemocracyServiceProxyFactory.proxyAs(serviceType, leaderService));
        }
        return List.copyOf(out);
    }

    @Override
    public @Nullable DemocracyService getService(@NotNull String name) {
        Object leaderService = invokeLeaderByContractId(GeneratedBridgeIds.DemocracyServiceManager.getService__String, new Object[]{name});
        if (leaderService == null) return null;

        // We don't know the interface type here; returning a proxy of DemocracyService is still useful for name/introspection.
        return DemocracyServiceProxyFactory.proxyAs(DemocracyService.class, leaderService);
    }

    @Override
    public @NotNull <P extends Plugin> List<PluginBoundDemocracyService<P>> getPluginBoundServices(@NotNull P plugin) {
        Object raw = invokeLeaderByContractId(GeneratedBridgeIds.DemocracyServiceManager.getPluginBoundServices__Plugin, new Object[]{plugin});
        if (!(raw instanceof List<?> list)) return List.of();

        @SuppressWarnings("unchecked")
        Class<PluginBoundDemocracyService<P>> api = (Class<PluginBoundDemocracyService<P>>) (Class<?>) PluginBoundDemocracyService.class;

        List<PluginBoundDemocracyService<P>> out = new ArrayList<>(list.size());
        for (Object leaderService : list) {
            if (leaderService == null) continue;
            out.add(DemocracyServiceProxyFactory.proxyAs(api, leaderService));
        }
        return List.copyOf(out);
    }

    @Override
    public <DemocracyServiceType extends DemocracyService> void registerService(@NotNull DemocracyServiceType service) {
        invokeLeaderByContractId(GeneratedBridgeIds.DemocracyServiceManager.registerService__DemocracyService, new Object[]{service});
    }

    @Override
    public <P extends Plugin, T extends PluginBoundDemocracyService<?>> boolean hasRegisteredService(@NotNull P plugin, @NotNull Class<T> serviceType) {
        Object raw = invokeLeaderByContractId(GeneratedBridgeIds.DemocracyServiceManager.hasRegisteredService__Plugin__Class,
                new Object[]{plugin, translateFilterClass(serviceType)});
        return raw instanceof Boolean b && b;
    }

    @Override
    public @Nullable <T extends PluginBoundDemocracyService<?>> T getServiceForPlugin(@NotNull Plugin plugin, @NotNull Class<T> serviceClass) {
        Object leaderService = invokeLeaderByContractId(GeneratedBridgeIds.DemocracyServiceManager.getServiceForPlugin__Plugin__Class,
                new Object[]{plugin, translateFilterClass(serviceClass)});
        if (leaderService == null) return null;
        return DemocracyServiceProxyFactory.proxyAs(serviceClass, leaderService);
    }

    /**
     * Translates a library Class token to the leader's classloader so leader-side
     * {@code isInstance} filters can match. Falls back to the local token (matching nothing,
     * as before) when the leader's copy does not have the type.
     */
    private @NotNull Class<?> translateFilterClass(@NotNull Class<?> localType) {
        if (!BridgeTypeContract.isLibraryType(localType)) return localType;
        try {
            return Class.forName(localType.getName(), false, leaderServiceManager.getClass().getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return localType;
        }
    }

    private Object invokeLeaderByContractId(@NotNull String contractId, Object[] args) {
        Objects.requireNonNull(contractId, "contractId");

        Object[] actualArgs = args == null ? new Object[0] : args;

        GeneratedBridgeContract.Spec spec = DemocracyBootstrapReflection.loadGeneratedSpec(contractId);
        String key = DemocracyBootstrapReflection.cacheKeyByContractId(contractId);

        ResolvedLeaderCall call = callCache.computeIfAbsent(key, k -> {
            Method targetMethod = DemocracyBootstrapReflection.resolveByGeneratedSpec(leaderServiceManager.getClass(), spec);
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
        });

        try {
            Object[] adapted = BridgeValueAdapter.adaptArguments(call.method(), actualArgs, getClass().getClassLoader());
            Object[] full = new Object[adapted.length + 1];
            full[0] = leaderServiceManager;
            System.arraycopy(adapted, 0, full, 1, adapted.length);
            return call.handle().invokeWithArguments(full);
        } catch (Throwable t) {
            throw new RuntimeException("Failed invoking leader service manager contract id: " + contractId, t);
        }
    }
}

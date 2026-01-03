package net.democracycraft.democracyLib.internal.bootstrap;

import net.democracycraft.democracyLib.api.service.engine.DemocracyService;
import net.democracycraft.democracyLib.api.service.engine.DemocracyServiceManager;
import net.democracycraft.democracyLib.api.service.engine.PluginBoundDemocracyService;
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
 * Note: returning DemocracyService instances across classloaders is unsafe. For retrieval we provide
 * best-effort adapters (dynamic proxies) only for services whose API interface is visible to the caller.
 */
class BridgeServiceManager implements DemocracyServiceManager {

    private final Object leaderServiceManager;
    private final Map<String, MethodHandle> methodHandleCache = new ConcurrentHashMap<>();

    BridgeServiceManager(@NotNull Object leaderServiceManager) {
        this.leaderServiceManager = Objects.requireNonNull(leaderServiceManager, "leaderServiceManager");
    }

    @Override
    public @NotNull List<DemocracyService> getAllServices() {
        Object raw = invokeLeaderByContractId(BridgeContract.Ids.ServiceManager.GET_ALL_SERVICES, new Object[]{});
        if (!(raw instanceof List<?> list)) return List.of();

        List<DemocracyService> out = new ArrayList<>(list.size());
        for (Object leaderService : list) {
            if (leaderService == null) continue;
            out.add(ServiceProxyFactory.proxyAs(DemocracyService.class, leaderService));
        }
        return List.copyOf(out);
    }

    @Override
    public @NotNull <T extends DemocracyService> List<T> getServicesByType(@NotNull Class<T> serviceType) {
        Object raw = invokeLeaderByContractId(BridgeContract.Ids.ServiceManager.GET_SERVICES_BY_TYPE, new Object[]{serviceType});
        if (!(raw instanceof List<?> list)) return List.of();

        List<T> out = new ArrayList<>(list.size());
        for (Object leaderService : list) {
            if (leaderService == null) continue;
            out.add(ServiceProxyFactory.proxyAs(serviceType, leaderService));
        }
        return List.copyOf(out);
    }

    @Override
    public @Nullable DemocracyService getService(@NotNull String name) {
        Object leaderService = invokeLeaderByContractId(BridgeContract.Ids.ServiceManager.GET_SERVICE, new Object[]{name});
        if (leaderService == null) return null;

        // We don't know the interface type here; returning a proxy of DemocracyService is still useful for name/introspection.
        return ServiceProxyFactory.proxyAs(DemocracyService.class, leaderService);
    }

    @Override
    public @NotNull <P extends Plugin> List<PluginBoundDemocracyService<P>> getPluginBoundServices(@NotNull P plugin) {
        Object raw = invokeLeaderByContractId(BridgeContract.Ids.ServiceManager.GET_PLUGIN_BOUND_SERVICES, new Object[]{plugin});
        if (!(raw instanceof List<?> list)) return List.of();

        @SuppressWarnings("unchecked")
        Class<PluginBoundDemocracyService<P>> api = (Class<PluginBoundDemocracyService<P>>) (Class<?>) PluginBoundDemocracyService.class;

        List<PluginBoundDemocracyService<P>> out = new ArrayList<>(list.size());
        for (Object leaderService : list) {
            if (leaderService == null) continue;
            out.add(ServiceProxyFactory.proxyAs(api, leaderService));
        }
        return List.copyOf(out);
    }

    @Override
    public <DemocracyServiceType extends DemocracyService> void registerService(@NotNull DemocracyServiceType service) {
        invokeLeaderByContractId(BridgeContract.Ids.ServiceManager.REGISTER_SERVICE, new Object[]{service});
    }

    @Override
    public <P extends Plugin, T extends PluginBoundDemocracyService<?>> boolean hasRegisteredService(@NotNull P plugin, @NotNull Class<T> serviceType) {
        Object raw = invokeLeaderByContractId(BridgeContract.Ids.ServiceManager.HAS_REGISTERED_SERVICE, new Object[]{plugin, serviceType});
        return raw instanceof Boolean b && b;
    }

    @Override
    public @Nullable <T extends PluginBoundDemocracyService<?>> T getServiceForPlugin(@NotNull Plugin plugin, @NotNull Class<T> serviceClass) {
        Object leaderService = invokeLeaderByContractId(BridgeContract.Ids.ServiceManager.GET_SERVICE_FOR_PLUGIN, new Object[]{plugin, serviceClass});
        if (leaderService == null) return null;
        return ServiceProxyFactory.proxyAs(serviceClass, leaderService);
    }

    private Object invokeLeaderByContractId(@NotNull String contractId, Object[] args) {
        Objects.requireNonNull(contractId, "contractId");

        Object[] actualArgs = args == null ? new Object[0] : args;

        Object spec = BootstrapReflection.loadGeneratedSpec(contractId);
        String key = BootstrapReflection.cacheKeyByContractId(contractId);

        MethodHandle methodHandle = methodHandleCache.computeIfAbsent(key, k -> {
            Method targetMethod = BootstrapReflection.resolveByGeneratedSpec(leaderServiceManager.getClass(), spec);
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
            full[0] = leaderServiceManager;
            System.arraycopy(actualArgs, 0, full, 1, actualArgs.length);
            return methodHandle.invokeWithArguments(full);
        } catch (Throwable t) {
            throw new RuntimeException("Failed invoking leader service manager contract id: " + contractId, t);
        }
    }
}

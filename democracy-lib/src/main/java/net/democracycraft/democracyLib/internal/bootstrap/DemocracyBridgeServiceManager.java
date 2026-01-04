package net.democracycraft.democracyLib.internal.bootstrap;

import net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeContract;
import net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeIds;
import net.democracycraft.democracyLib.api.service.engine.DemocracyService;
import net.democracycraft.democracyLib.api.service.engine.DemocracyServiceManager;
import net.democracycraft.democracyLib.api.service.engine.PluginBoundDemocracyService;
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
 * Note: returning DemocracyService instances across classloaders is unsafe. For retrieval we provide
 * best-effort adapters (dynamic proxies) only for services whose API interface is visible to the caller.
 */
public class DemocracyBridgeServiceManager implements DemocracyServiceManager {

    private final Object leaderServiceManager;
    private final Map<String, MethodHandle> methodHandleCache = new ConcurrentHashMap<>();

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
        Object raw = invokeLeaderByContractId(GeneratedBridgeIds.DemocracyServiceManager.getServicesByType__Class, new Object[]{serviceType});
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
        Object raw = invokeLeaderByContractId(GeneratedBridgeIds.DemocracyServiceManager.hasRegisteredService__Plugin__Class, new Object[]{plugin, serviceType});
        return raw instanceof Boolean b && b;
    }

    @Override
    public @Nullable <T extends PluginBoundDemocracyService<?>> T getServiceForPlugin(@NotNull Plugin plugin, @NotNull Class<T> serviceClass) {
        Object leaderService = invokeLeaderByContractId(GeneratedBridgeIds.DemocracyServiceManager.getServiceForPlugin__Plugin__Class, new Object[]{plugin, serviceClass});
        if (leaderService == null) return null;
        return DemocracyServiceProxyFactory.proxyAs(serviceClass, leaderService);
    }

    private Object invokeLeaderByContractId(@NotNull String contractId, Object[] args) {
        Objects.requireNonNull(contractId, "contractId");

        Object[] actualArgs = args == null ? new Object[0] : args;

        GeneratedBridgeContract.Spec spec = DemocracyBootstrapReflection.loadGeneratedSpec(contractId);
        String key = DemocracyBootstrapReflection.cacheKeyByContractId(contractId);

        MethodHandle methodHandle = methodHandleCache.computeIfAbsent(key, k -> {
            Method targetMethod = DemocracyBootstrapReflection.resolveByGeneratedSpec(leaderServiceManager.getClass(), spec);
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

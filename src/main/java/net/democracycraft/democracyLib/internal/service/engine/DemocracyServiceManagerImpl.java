package net.democracycraft.democracyLib.internal.service.engine;

import net.democracycraft.democracyLib.api.service.engine.DemocracyService;
import net.democracycraft.democracyLib.api.service.engine.DemocracyServiceManager;
import net.democracycraft.democracyLib.api.service.engine.PluginBoundDemocracyService;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DemocracyServiceManagerImpl implements DemocracyServiceManager {

    private final List<DemocracyService> services;

    private final Map<Plugin, List<PluginBoundDemocracyService<? extends Plugin>>> servicesByPluginIndex;

    public DemocracyServiceManagerImpl() {
        this.services = new ArrayList<>();
        this.servicesByPluginIndex = new HashMap<>();
    }

    @Override
    public @NotNull List<DemocracyService> getAllServices() {
        return List.copyOf(services);
    }

    @Override
    public @NotNull <T extends DemocracyService> List<T> getServicesByType(@NotNull Class<T> serviceType) {
        return services.stream()
                .filter(serviceType::isInstance)
                .map(serviceType::cast)
                .toList();
    }

    @Override
    public @Nullable DemocracyService getService(@NotNull String name) {
        return services.stream()
                .filter(service -> service.getServiceName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull <P extends Plugin> List<PluginBoundDemocracyService<P>> getPluginBoundServices(@NotNull P plugin) {
        List<PluginBoundDemocracyService<?>> pluginServices = servicesByPluginIndex.get(plugin);

        if (pluginServices == null) {
            return Collections.emptyList();
        }

        return (List<PluginBoundDemocracyService<P>>) (List<?>) List.copyOf(pluginServices);
    }

    @Override
    public <DemocracyServiceType extends DemocracyService> void registerService(@NotNull DemocracyServiceType service) {
        if (service instanceof PluginBoundDemocracyService<?> boundService) {
            Plugin plugin = boundService.getBoundPlugin();

            boolean exists = hasRegisteredService(plugin, boundService.getClass());

            if (exists) {
                throw new IllegalArgumentException(String.format(
                        "Plugin '%s' already has a registered service of type '%s'",
                        plugin.getName(), service.getClass().getSimpleName()
                ));
            }

            servicesByPluginIndex.computeIfAbsent(plugin, k -> new ArrayList<>()).add(boundService);
        }

        if (!services.contains(service)) {
            services.add(service);
        }
    }

    @Override
    public <P extends Plugin, T extends PluginBoundDemocracyService<?>> boolean hasRegisteredService(@NotNull P plugin, @NotNull Class<T> serviceType) {
        List<PluginBoundDemocracyService<?>> pluginServices = servicesByPluginIndex.get(plugin);

        if (pluginServices == null || pluginServices.isEmpty()) {
            return false;
        }

        return pluginServices.stream()
                .anyMatch(serviceType::isInstance);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable <T extends PluginBoundDemocracyService<?>> T getServiceForPlugin(@NotNull Plugin plugin, @NotNull Class<T> serviceClass) {
        List<PluginBoundDemocracyService<?>> services = servicesByPluginIndex.get(plugin);

        if (services == null || services.isEmpty()) {
            return null;
        }

        for (PluginBoundDemocracyService<?> service : services) {
            if (serviceClass.isInstance(service)) {
                return (T) service;
            }
        }

        return null;
    }
}
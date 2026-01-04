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

    /**
     * Cross-classloader stable de-duplication index.
     * Key is derived from plugin + serviceName + implementation class name.
     */
    private final Set<String> pluginServiceKeys;

    public DemocracyServiceManagerImpl() {
        this.services = new ArrayList<>();
        this.servicesByPluginIndex = new HashMap<>();
        this.pluginServiceKeys = new HashSet<>();
    }

    @Override
    public @NotNull List<DemocracyService> getAllServices() {
        return List.copyOf(services);
    }

    @Override
    public @NotNull <T extends DemocracyService> List<T> getServicesByType(@NotNull Class<T> serviceType) {
        // Cross-classloader safety: never trust Class#isInstance when the provided Class can originate from
        // another PluginClassLoader. Instead, only use it when it is loadable/compatible and swallow linkage issues.
        List<T> out = new ArrayList<>();
        for (DemocracyService service : services) {
            if (service == null) continue;
            try {
                if (serviceType.isInstance(service)) {
                    out.add(serviceType.cast(service));
                }
            } catch (LinkageError | ClassCastException ignored) {
                // Not shareable across classloaders; treat as not matching.
            }
        }
        return List.copyOf(out);
    }

    @Override
    public @Nullable DemocracyService getService(@NotNull String name) {
        for (DemocracyService service : services) {
            if (service == null) continue;
            try {
                if (service.getServiceName().equalsIgnoreCase(name)) {
                    return service;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
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

            String key = serviceKey(plugin, service);
            if (!pluginServiceKeys.add(key)) {
                throw new IllegalArgumentException(String.format(
                        "Plugin '%s' already has a registered service '%s'",
                        plugin.getName(), safeServiceName(service)
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
        if (pluginServices == null || pluginServices.isEmpty()) return false;

        // Safe check: use isInstance but never allow it to blow up.
        for (PluginBoundDemocracyService<?> s : pluginServices) {
            try {
                if (serviceType.isInstance(s)) return true;
            } catch (LinkageError | ClassCastException ignored) {
                // The requested type isn't compatible for this caller; report as not registered.
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable <T extends PluginBoundDemocracyService<?>> T getServiceForPlugin(@NotNull Plugin plugin, @NotNull Class<T> serviceClass) {
        List<PluginBoundDemocracyService<?>> services = servicesByPluginIndex.get(plugin);
        if (services == null || services.isEmpty()) return null;

        for (PluginBoundDemocracyService<?> service : services) {
            try {
                if (serviceClass.isInstance(service)) {
                    return (T) service;
                }
            } catch (LinkageError | ClassCastException ignored) {
                // Not shareable/compatible.
            }
        }

        return null;
    }

    private static @NotNull String serviceKey(@NotNull Plugin plugin, @NotNull DemocracyService service) {
        String pluginName;
        try {
            pluginName = plugin.getName();
        } catch (Throwable t) {
            pluginName = "<unknown-plugin>";
        }

        String serviceName = safeServiceName(service);
        String impl;
        try {
            impl = service.getClass().getName();
        } catch (Throwable t) {
            impl = "<unknown-class>";
        }

        // Intentionally does not depend on Class identity.
        return pluginName + "|" + serviceName.toLowerCase(Locale.ROOT) + "|" + impl;
    }

    private static @NotNull String safeServiceName(@NotNull DemocracyService service) {
        try {
            String serviceName = service.getServiceName();
            return serviceName.isBlank() ? service.getClass().getSimpleName() : serviceName;
        } catch (Throwable t) {
            return service.getClass().getSimpleName();
        }
    }
}
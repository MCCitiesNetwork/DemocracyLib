package net.democracycraft.democracyLib.api.service.engine;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface DemocracyServiceManager {

    @NotNull List<DemocracyService> getAllServices();

    @NotNull <T extends DemocracyService> List<T> getServicesByType(@NotNull Class<T> serviceType);

    @Nullable DemocracyService getService(@NotNull String name);

    @NotNull <P extends Plugin> List<PluginBoundDemocracyService<P>> getPluginBoundServices(@NotNull P plugin);

    <DemocracyServiceType extends DemocracyService> void registerService(@NotNull DemocracyServiceType service);

    <P extends Plugin, T extends PluginBoundDemocracyService<?>> boolean hasRegisteredService(@NotNull P plugin, @NotNull Class<T> serviceType);

    @Nullable <T extends PluginBoundDemocracyService<?>> T getServiceForPlugin(@NotNull Plugin plugin, @NotNull Class<T> serviceClass);
}

package net.democracycraft.democracyLib.api.service.engine;

import net.democracycraft.democracyLib.api.bootstrap.contract.BridgeApi;
import net.democracycraft.democracyLib.api.bootstrap.contract.BridgeMethod;
import net.democracycraft.democracyLib.api.bootstrap.contract.BridgeNamespace;
import net.democracycraft.democracyLib.api.bootstrap.contract.BridgeStability;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@BridgeApi(BridgeNamespace.DEMOCRACY_SERVICE_MANAGER)
public interface DemocracyServiceManager {

    @BridgeMethod(stability = BridgeStability.DERIVED_ID)
    @NotNull List<DemocracyService> getAllServices();

    @BridgeMethod(stability = BridgeStability.DERIVED_ID)
    @NotNull <ServiceType extends DemocracyService> List<ServiceType> getServicesByType(@NotNull Class<ServiceType> serviceType);

    @BridgeMethod(stability = BridgeStability.DERIVED_ID)
    @Nullable DemocracyService getService(@NotNull String name);

    @BridgeMethod(stability = BridgeStability.DERIVED_ID)
    @NotNull <PluginType extends Plugin> List<PluginBoundDemocracyService<PluginType>> getPluginBoundServices(@NotNull PluginType plugin);

    @BridgeMethod(stability = BridgeStability.DERIVED_ID)
    <DemocracyServiceType extends DemocracyService> void registerService(@NotNull DemocracyServiceType service);

    @BridgeMethod(stability = BridgeStability.DERIVED_ID)
    <PluginType extends Plugin, PluginBoundServiceType extends PluginBoundDemocracyService<?>> boolean hasRegisteredService(@NotNull PluginType plugin, @NotNull Class<PluginBoundServiceType> serviceType);

    @BridgeMethod(stability = BridgeStability.DERIVED_ID)
    @Nullable <PluginBoundServiceType extends PluginBoundDemocracyService<?>> PluginBoundServiceType getServiceForPlugin(@NotNull Plugin plugin, @NotNull Class<PluginBoundServiceType> serviceClass);
}

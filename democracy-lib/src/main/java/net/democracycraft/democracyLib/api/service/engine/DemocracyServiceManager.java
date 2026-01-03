package net.democracycraft.democracyLib.api.service.engine;

import net.democracycraft.democracyLib.internal.bootstrap.BridgeContract;
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

    @BridgeMethod(stability = BridgeStability.STABLE_ID, id = BridgeContract.Ids.ServiceManager.GET_ALL_SERVICES)
    @NotNull List<DemocracyService> getAllServices();

    @BridgeMethod(stability = BridgeStability.STABLE_ID, id = BridgeContract.Ids.ServiceManager.GET_SERVICES_BY_TYPE)
    @NotNull <T extends DemocracyService> List<T> getServicesByType(@NotNull Class<T> serviceType);

    @BridgeMethod(stability = BridgeStability.STABLE_ID, id = BridgeContract.Ids.ServiceManager.GET_SERVICE)
    @Nullable DemocracyService getService(@NotNull String name);

    @BridgeMethod(stability = BridgeStability.STABLE_ID, id = BridgeContract.Ids.ServiceManager.GET_PLUGIN_BOUND_SERVICES)
    @NotNull <P extends Plugin> List<PluginBoundDemocracyService<P>> getPluginBoundServices(@NotNull P plugin);

    @BridgeMethod(stability = BridgeStability.STABLE_ID, id = BridgeContract.Ids.ServiceManager.REGISTER_SERVICE)
    <DemocracyServiceType extends DemocracyService> void registerService(@NotNull DemocracyServiceType service);

    @BridgeMethod(stability = BridgeStability.STABLE_ID, id = BridgeContract.Ids.ServiceManager.HAS_REGISTERED_SERVICE)
    <P extends Plugin, T extends PluginBoundDemocracyService<?>> boolean hasRegisteredService(@NotNull P plugin, @NotNull Class<T> serviceType);

    @BridgeMethod(stability = BridgeStability.STABLE_ID, id = BridgeContract.Ids.ServiceManager.GET_SERVICE_FOR_PLUGIN)
    @Nullable <T extends PluginBoundDemocracyService<?>> T getServiceForPlugin(@NotNull Plugin plugin, @NotNull Class<T> serviceClass);
}

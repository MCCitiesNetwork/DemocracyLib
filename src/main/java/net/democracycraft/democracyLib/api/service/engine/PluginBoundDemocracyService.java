package net.democracycraft.democracyLib.api.service.engine;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public interface PluginBoundDemocracyService<PluginType extends Plugin> extends DemocracyService {

    @NotNull PluginType getBoundPlugin();

}

package net.democracycraft.democracyLib.api.service.engine;

import net.democracycraft.democracyLib.api.config.DemocracyConfig;

public interface ConfigurableDemocracyService<ConfigurationType extends DemocracyConfig> extends DemocracyService {

    void setConfiguration(ConfigurationType configuration);

    ConfigurationType getConfiguration();

}

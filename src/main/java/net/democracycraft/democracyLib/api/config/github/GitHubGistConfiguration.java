package net.democracycraft.democracyLib.api.config.github;

import net.democracycraft.democracyLib.api.config.DemocracyConfig;
import org.jetbrains.annotations.NotNull;

public interface GitHubGistConfiguration extends DemocracyConfig {

    String getToken();

    @NotNull String getApiBaseUrl();

    boolean isPublic();

}

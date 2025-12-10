package net.democracycraft.democracyLib.api.service.github;

import com.google.gson.JsonObject;
import net.democracycraft.democracyLib.api.config.github.GitHubGistConfiguration;
import net.democracycraft.democracyLib.api.service.engine.AsyncDemocracyService;
import net.democracycraft.democracyLib.api.service.engine.ConfigurableDemocracyService;

import java.util.concurrent.CompletableFuture;

public interface GitHubGistService extends AsyncDemocracyService, ConfigurableDemocracyService<GitHubGistConfiguration> {

    CompletableFuture<String> publish(String fileName, JsonObject jsonObject);

    CompletableFuture<String> publish(String fileName, String markdown);

    CompletableFuture<String> publish(String fileName, JsonObject jsonObject, String markdownFileName, String markdown);

}

package net.democracycraft.democracyLib.api.service.github;

import com.google.gson.JsonObject;
import net.democracycraft.democracyLib.api.config.github.GitHubGistConfiguration;
import net.democracycraft.democracyLib.api.service.engine.AsyncDemocracyService;
import net.democracycraft.democracyLib.api.service.engine.ConfigurableDemocracyService;
import net.democracycraft.democracyLib.api.service.engine.PluginBoundDemocracyService;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;

public interface GitHubGistService<PluginType extends Plugin> extends AsyncDemocracyService, ConfigurableDemocracyService<GitHubGistConfiguration>, PluginBoundDemocracyService<PluginType> {

    CompletableFuture<String> publish(String fileName, JsonObject jsonObject);

    CompletableFuture<String> publish(String fileName, String markdown);

    CompletableFuture<String> publish(String fileName, JsonObject jsonObject, String markdownFileName, String markdown);

    CompletableFuture<String> publish(String fileName, JsonObject jsonObject, String description);

    CompletableFuture<String> publish(String fileName, String markdown, String description);

    CompletableFuture<String> publish(String fileName, JsonObject jsonObject, String markdownFileName, String markdown, String description);

}

package net.democracycraft.democracyLib.api.service.github;

import com.google.gson.JsonObject;
import net.democracycraft.democracyLib.api.service.engine.AsyncDemocracyService;

import java.util.concurrent.CompletableFuture;

public interface GitHubGistService extends AsyncDemocracyService {

    CompletableFuture<String> publish(String fileName, JsonObject jsonObject);

    CompletableFuture<String> publish(String fileName, String markdown);

    CompletableFuture<String> publish(String fileName, JsonObject jsonObject, String markdownFileName, String markdown);

}

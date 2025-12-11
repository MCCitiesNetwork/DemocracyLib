package net.democracycraft.democracyLib.internal.service.github;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.democracycraft.democracyLib.api.config.github.GitHubGistConfiguration;
import net.democracycraft.democracyLib.api.service.github.GitHubGistService;
import net.democracycraft.democracyLib.internal.service.engine.AsyncDemocracyServiceImpl;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

public class GitHubGistServiceImpl<PluginType extends Plugin> extends AsyncDemocracyServiceImpl implements GitHubGistService<PluginType> {

    private final PluginType plugin;
    private GitHubGistConfiguration config;
    private final HttpClient client;

    public GitHubGistServiceImpl(@NotNull PluginType plugin, @NotNull ExecutorService sharedExecutor, @NotNull GitHubGistConfiguration config) {
        super(sharedExecutor);
        this.plugin = plugin;
        this.config = config;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }


    @Override
    public CompletableFuture<String> publish(String fileName, JsonObject jsonObject) {
        return publish(fileName, jsonObject, null, null);
    }

    @Override
    public CompletableFuture<String> publish(String fileName, String markdown) {
        return publish(null, null, fileName, markdown);
    }

    @Override
    public CompletableFuture<String> publish(@Nullable String jsonFileName, @Nullable JsonObject jsonContent, @Nullable String markdownFileName, @Nullable String markdownContent) {

        if (config.getToken() == null || config.getToken().isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("GitHub Gist personalAccessToken is not configured for plugin " + plugin.getName()));
        }

        return this.supplyAsync(() -> {
            try {
                return executeUpload(jsonFileName, jsonContent, markdownFileName, markdownContent, null);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to upload Gist to GitHub", e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<String> publish(String fileName, JsonObject jsonObject, String description) {
        return publish(fileName, jsonObject, null, null, description);
    }

    @Override
    public CompletableFuture<String> publish(String fileName, String markdown, String description) {
        return publish(null, null, fileName, markdown, description);
    }

    @Override
    public CompletableFuture<String> publish(String fileName, JsonObject jsonObject, String markdownFileName, String markdown, String description) {
        if (config.getToken() == null || config.getToken().isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("GitHub Gist personalAccessToken is not configured for plugin " + plugin.getName()));
        }
        return this.supplyAsync(() -> {
            try {
                return executeUpload(fileName, jsonObject, markdownFileName, markdown, description);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to upload Gist to GitHub", e);
                throw new RuntimeException(e);
            }
        });
    }

    private String executeUpload(@Nullable String jsonFileName, @Nullable JsonObject jsonContent, @Nullable String markdownFileName, @Nullable String markdownContent, @Nullable String description) throws Exception {

        JsonObject root = new JsonObject();
        String defaultDescription = "Data exported from " + plugin.getName();
        String finalDescription = (description == null || description.isBlank()) ? defaultDescription : description;
        root.addProperty("description", finalDescription);
        root.addProperty("public", config.isPublic());

        JsonObject files = new JsonObject();

        if (jsonContent != null) {
            JsonObject jsonFileContent = new JsonObject();
            jsonFileContent.addProperty("content", jsonContent.toString()); // Gson toString
            String safeJsonName = (jsonFileName == null || jsonFileName.isBlank()) ? "data.json" : jsonFileName;
            files.add(safeJsonName, jsonFileContent);
        }

        // process markdown
        if (markdownContent != null) {
            JsonObject mdFileContent = new JsonObject();
            mdFileContent.addProperty("content", markdownContent);
            String safeMdName;
            
            if (markdownFileName != null && !markdownFileName.isBlank()) {
                safeMdName = markdownFileName;
            } else {
                // derive from jsonFileName
                String base = (jsonFileName == null || jsonFileName.isBlank()) ? "data" : jsonFileName;
                if (base.endsWith(".json")) base = base.substring(0, base.length() - 5);
                safeMdName = base + ".md";
            }
            files.add(safeMdName, mdFileContent);
        }

        root.add("files", files);

        // request setup
        config.getApiBaseUrl();
        String apiBase = config.getApiBaseUrl().isBlank()
                ? "https://api.github.com"
                : config.getApiBaseUrl().trim().replaceAll("/$", "");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/gists"))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DemocracyLib/" + plugin.getName())
                .header("Authorization", "Bearer " + config.getToken().trim())
                .POST(HttpRequest.BodyPublishers.ofString(root.toString(), StandardCharsets.UTF_8))
                .build();

        // send request
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();

        if (status != 201) {
            throw new IllegalStateException("GitHub API returned error status: " + status + " | Body: " + response.body());
        }

        // parse response
        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        if (responseJson.has("html_url")) {
            return responseJson.get("html_url").getAsString();
        } else {
            throw new IllegalStateException("GitHub response missing 'html_url' field.");
        }
    }

    @Override
    public @NotNull String getServiceName() {
        return "GitHubGistService_" + plugin.getName();
    }

    @Override
    public @NotNull PluginType getBoundPlugin() {
        return plugin;
    }

    @Override
    public @NotNull ExecutorService getExecutorService() {
        return executor;
    }

    @Override
    public void setConfiguration(GitHubGistConfiguration configuration) {
        this.config = configuration;
    }

    @Override
    public GitHubGistConfiguration getConfiguration() {
        return config;
    }
}

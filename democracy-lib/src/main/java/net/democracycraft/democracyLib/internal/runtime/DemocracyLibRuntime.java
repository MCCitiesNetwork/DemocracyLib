package net.democracycraft.democracyLib.internal.runtime;

import net.democracycraft.democracyLib.api.cache.MojangServiceDemocracyCache;
import net.democracycraft.democracyLib.api.config.github.GitHubGistConfiguration;
import net.democracycraft.democracyLib.internal.cache.MojangServiceDemocracyCacheImpl;
import net.democracycraft.democracyLib.internal.config.GitHubGistConfigurationImpl;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared heavy resources (pool/cache/http) for shaded bridge leader.
 */
public final class DemocracyLibRuntime {

    private final ExecutorService commonPool;
    private final MojangServiceDemocracyCache mojangCache;
    private final HttpClient httpClient;
    private final GitHubGistConfiguration defaultGitHubGistConfiguration;

    public DemocracyLibRuntime() {
        this.commonPool = Executors.newCachedThreadPool();
        this.mojangCache = new MojangServiceDemocracyCacheImpl(commonPool);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.defaultGitHubGistConfiguration = new GitHubGistConfigurationImpl();
    }

    public ExecutorService getCommonPool() {
        return commonPool;
    }

    public MojangServiceDemocracyCache getMojangCache() {
        return mojangCache;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public GitHubGistConfiguration getDefaultGitHubGistConfiguration() {
        return defaultGitHubGistConfiguration;
    }

    public void shutdown() {
        commonPool.shutdown();
    }
}

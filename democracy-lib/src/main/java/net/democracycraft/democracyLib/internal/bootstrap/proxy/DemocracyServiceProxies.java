package net.democracycraft.democracyLib.internal.bootstrap.proxy;

import net.democracycraft.democracyLib.api.service.github.GitHubGistService;
import net.democracycraft.democracyLib.api.service.mojang.MojangService;
import net.democracycraft.democracyLib.internal.bootstrap.bridge.BridgeValueAdapter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class DemocracyServiceProxies {

    private DemocracyServiceProxies() {}

    @SuppressWarnings("unchecked")
    public static <PluginType extends Plugin> @NotNull MojangService<PluginType> mojangProxy(@NotNull Object leaderService) {
        // adapt() only returns null for null input, and leaderService is non-null.
        return (MojangService<PluginType>) Objects.requireNonNull(BridgeValueAdapter.adapt(
                MojangService.class, leaderService, leaderService.getClass().getClassLoader()));
    }

    @SuppressWarnings("unchecked")
    public static <P extends Plugin> @NotNull GitHubGistService<P> githubProxy(@NotNull Object leaderService) {
        return (GitHubGistService<P>) Objects.requireNonNull(BridgeValueAdapter.adapt(
                GitHubGistService.class, leaderService, leaderService.getClass().getClassLoader()));
    }
}

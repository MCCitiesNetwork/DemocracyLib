package net.democracycraft.democracyLib.internal.bootstrap.bridge.fixture;

import net.democracycraft.democracyLib.api.service.engine.PluginBoundDemocracyService;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Library-namespace fixture so {@code ShadedCopyClassLoader} can define a per-loader copy;
 * used to register a follower-owned service into the leader's service manager.
 */
public final class FixtureFollowerService implements PluginBoundDemocracyService<Plugin> {

    private final Plugin plugin;

    public FixtureFollowerService(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getServiceName() {
        return "FixtureFollowerService";
    }

    @Override
    public @NotNull Plugin getBoundPlugin() {
        return plugin;
    }
}

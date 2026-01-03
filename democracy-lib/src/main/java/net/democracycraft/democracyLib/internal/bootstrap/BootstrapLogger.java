package net.democracycraft.democracyLib.internal.bootstrap;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Small helper to avoid sprinkling boolean checks across the bootstrap.
 */
final class BootstrapLogger {

    private final boolean enabled;
    private final Plugin plugin;

    BootstrapLogger(boolean enabled, @NotNull Plugin plugin) {
        this.enabled = enabled;
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    void info(@NotNull String message) {
        if (!enabled) return;
        plugin.getLogger().info("[DemocracyLib] " + message);
    }

    void warn(@NotNull String message) {
        if (!enabled) return;
        plugin.getLogger().warning("[DemocracyLib] " + message);
    }

    void warn(@NotNull String message, @NotNull Throwable t) {
        if (!enabled) return;
        plugin.getLogger().log(Level.WARNING, "[DemocracyLib] " + message, t);
    }
}


package net.democracycraft.democracyLib.internal.config;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Folder wrapper used by the configuration loader.
 *
 * Note: DemocracyLib is a shaded library (not a Bukkit plugin). In library mode we pick a stable
 * base directory by anchoring to a loaded plugin's data folder.
 */
public final class ConfigFolder {

    private final File folder;

    public ConfigFolder(@NotNull File folder) {
        this.folder = folder;
    }

    public @NotNull File getFolder() {
        return folder;
    }

    /**
     * Creates a default folder for DemocracyLib config storage.
     */
    public static @NotNull ConfigFolder defaultFolder() {
        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
        if (plugins.length == 0) {
            throw new IllegalStateException("No Bukkit plugins loaded; cannot resolve data folder for DemocracyLib configuration.");
        }

        File pluginDataFolder = plugins[0].getDataFolder();
        return new ConfigFolder(new File(pluginDataFolder, "DemocracyLib"));
    }
}
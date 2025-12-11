package net.democracycraft.democracyLib.api.config;

import net.democracycraft.democracyLib.internal.config.ConfigFolder;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public interface DemocracyConfigLoader {

    @NotNull
    <ConfigType extends DemocracyConfig> ConfigType loadOrCreate(@NotNull ConfigFolder folder, @NotNull String fileName, @NotNull Class<ConfigType> configClass);


    @NotNull
    <ConfigType extends DemocracyConfig> ConfigType loadOrCreate(@NotNull Plugin plugin, @NotNull String fileName, @NotNull Class<ConfigType> configClass);


    @NotNull
    <ConfigType extends DemocracyConfig> ConfigType loadOrCreate(@NotNull File directory, @NotNull String fileName, @NotNull Class<ConfigType> configClass);
}
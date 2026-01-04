package net.democracycraft.democracyLib.internal.config;

import net.democracycraft.democracyLib.api.config.DemocracyConfig;
import net.democracycraft.democracyLib.api.config.DemocracyConfigLoader;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.util.logging.Logger;

public class DemocracyConfigLoaderImpl implements DemocracyConfigLoader {

    private final Logger logger;

    public DemocracyConfigLoaderImpl(Logger logger) {
        this.logger = logger;
    }

    @Override
    public <ConfigType extends DemocracyConfig> @NotNull ConfigType loadOrCreate(@NotNull ConfigFolder folder, @NotNull String fileName, @NotNull Class<ConfigType> configClass) {
        return loadOrCreate(folder.getFolder(), fileName, configClass);
    }

    /**
     * Legacy overload kept for source compatibility.
     * Prefer {@link #loadOrCreate(File, String, Class)} in library-only mode.
     */
    @Override
    public <ConfigType extends DemocracyConfig> @NotNull ConfigType loadOrCreate(@NotNull Plugin plugin, @NotNull String fileName, @NotNull Class<ConfigType> configClass) {
        return loadOrCreate(plugin.getDataFolder(), fileName, configClass);
    }

    @Override
    public <ConfigType extends DemocracyConfig> @NotNull ConfigType loadOrCreate(@NotNull File directory, @NotNull String fileName, @NotNull Class<ConfigType> configClass) {

        File configFile = new File(directory, fileName);

        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                logger.warning("Failed to create directory: " + directory.getAbsolutePath());
            }
        }

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(configFile.toPath())
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .build();

        try {
            CommentedConfigurationNode root = loader.load();
            ConfigType config = root.get(configClass);

            if (config == null) {
                config = configClass.getDeclaredConstructor().newInstance(); // ConfigType must have a no-arg constructor
            }

            root.set(configClass, config);
            loader.save(root);

            return config;

        } catch (Exception e) {
            logger.severe("Severe error loading config file: " + configFile.getAbsolutePath());
            throw new RuntimeException("Failed to load configuration: " + fileName, e);
        }
    }
}
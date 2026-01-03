package net.democracycraft.democracyLib.internal.config;

import net.democracycraft.democracyLib.api.config.DemocracyConfig;
import net.democracycraft.democracyLib.api.config.DemocracyConfigLoader;
import net.democracycraft.democracyLib.api.config.DemocracyConfigManager;
import net.democracycraft.democracyLib.api.config.DemocracyConfigName;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Default {@link DemocracyConfigManager} implementation backed by {@link DemocracyConfigLoader}.
 */
public final class DemocracyConfigManagerImpl implements DemocracyConfigManager {

    private final DemocracyConfigLoader loader;

    public DemocracyConfigManagerImpl(@NotNull Logger logger) {
        this.loader = new DemocracyConfigLoaderImpl(logger);
    }

    @Override
    public @NotNull <ConfigType extends DemocracyConfig> ConfigType createConfig(@NotNull Plugin plugin, @NotNull Class<ConfigType> configClass) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(configClass, "configClass");
        String fileName = resolveFileName(configClass);
        return loader.loadOrCreate(plugin, fileName, configClass);
    }

    @Override
    public @NotNull <ConfigType extends DemocracyConfig> ConfigType createConfig(@NotNull Plugin plugin, @NotNull String fileName, @NotNull Class<ConfigType> configClass) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(configClass, "configClass");
        return loader.loadOrCreate(plugin, fileName, configClass);
    }

    private static @NotNull String resolveFileName(@NotNull Class<?> configClass) {
        DemocracyConfigName name = configClass.getAnnotation(DemocracyConfigName.class);
        if (name != null && name.value() != null && !name.value().isBlank()) {
            return name.value().trim();
        }
        return defaultFileName(configClass);
    }

    /**
     * Generate a default file name from the config class name.
     * Uses kebab-case and strips common suffixes like "Config", "Configuration", and "Impl".
     * Only used if the config class is not annotated with {@link DemocracyConfigName}.
     */
    private static @NotNull String defaultFileName(@NotNull Class<?> configClass) {
        String simple = configClass.getSimpleName();
        String base = simple
                .replaceAll("ConfigurationImpl$", "")
                .replaceAll("Config(uration)?$", "")
                .replaceAll("Impl$", "");

        if (base.isBlank()) base = simple;

        String kebab = base
                .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .toLowerCase(Locale.ROOT);

        return kebab + ".yml";
    }
}

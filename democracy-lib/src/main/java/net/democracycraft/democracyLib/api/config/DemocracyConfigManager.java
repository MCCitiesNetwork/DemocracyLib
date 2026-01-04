package net.democracycraft.democracyLib.api.config;

import net.democracycraft.democracyLib.api.bootstrap.contract.BridgeApi;
import net.democracycraft.democracyLib.api.bootstrap.contract.BridgeMethod;
import net.democracycraft.democracyLib.api.bootstrap.contract.BridgeNamespace;
import net.democracycraft.democracyLib.api.bootstrap.contract.BridgeStability;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Creates and loads configuration objects on-demand.
 * <p>
 * This API exists to keep plugin-facing configuration surfaces minimal:
 * a plugin only creates the configuration types it actually needs.
 * <p>
 * Implementations must remain bridgeable across classloaders.
 */
@BridgeApi(BridgeNamespace.DEMOCRACY_CONFIG_MANAGER)
public interface DemocracyConfigManager {

    /**
     * Creates (or loads) the given configuration type, using a default file name derived from the
     * configuration class.
     * <p>
     * The file is created inside the bound plugin's data folder.
     *
     * @param plugin The plugin that owns the configuration file.
     * @param configClass The configuration DTO class.
     * @param <ConfigType> Configuration type.
     * @return Loaded (or newly created) configuration instance.
     */
    @BridgeMethod(stability = BridgeStability.DERIVED_ID)
    @NotNull
    <ConfigType extends DemocracyConfig> ConfigType createConfig(@NotNull Plugin plugin, @NotNull Class<ConfigType> configClass);

    /**
     * Creates (or loads) the given configuration type using the provided file name.
     * <p>
     * The file is created inside the bound plugin's data folder.
     *
     * @param plugin The plugin that owns the configuration file.
     * @param fileName Target file name (e.g. {@code "github-gist.yml"}).
     * @param configClass The configuration DTO class.
     * @param <ConfigType> Configuration type.
     * @return Loaded (or newly created) configuration instance.
     */
    @BridgeMethod(stability = BridgeStability.DERIVED_ID)
    @NotNull
    <ConfigType extends DemocracyConfig> ConfigType createConfig(@NotNull Plugin plugin, @NotNull String fileName, @NotNull Class<ConfigType> configClass);
}

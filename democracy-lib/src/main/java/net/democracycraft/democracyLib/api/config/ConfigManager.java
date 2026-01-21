package net.democracycraft.democracyLib.api.config;

import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manages the lifecycle and retrieval of configuration objects.
 */
public class ConfigManager {

    private final Map<Class<?>, Object> loadedConfigs = new HashMap<>(); // Consider concurrent map if async access
    private final Plugin plugin;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers (loads or creates) a configuration based on the @Configurable class.
     * Use this method to initialize your configs at startup.
     *
     * @param configurableClass The class annotated with @Configurable.
     * @return The loaded configuration instance.
     */
    public <T> Object register(Class<T> configurableClass) {
        return register(configurableClass, null);
    }

    /**
     * Registers (loads or creates) a configuration with a specific filename.
     *
     * @param configurableClass The class annotated with @Configurable.
     * @param fileName Optional filename override.
     * @return The loaded configuration instance.
     */
    public <T> Object register(Class<T> configurableClass, String fileName) {
        // Delegate creation to factory (which handles caching of constructors)
        Object configInstance = fileName == null ?
            ConfigFactory.create(plugin, configurableClass) :
            ConfigFactory.create(plugin, configurableClass, fileName);

        synchronized (loadedConfigs) {
            loadedConfigs.put(configurableClass, configInstance);
        }
        return configInstance;
    }

    /**
     * Registers (loads or creates) a configuration asynchronously.
     *
     * @param configurableClass The class annotated with @Configurable.
     * @return A future completing with the loaded configuration instance.
     */
    public <T> CompletableFuture<Object> registerAsync(Class<T> configurableClass) {
        return registerAsync(configurableClass, null);
    }

    /**
     * Registers (loads or creates) a configuration asynchronously with a specific filename.
     *
     * @param configurableClass The class annotated with @Configurable.
     * @param fileName Optional filename override.
     * @return A future completing with the loaded configuration instance.
     */
    public <T> CompletableFuture<Object> registerAsync(Class<T> configurableClass, String fileName) {
        CompletableFuture<Object> future = fileName == null ?
            ConfigFactory.createAsync(plugin, configurableClass) :
            ConfigFactory.createAsync(plugin, configurableClass, fileName);

        return future.thenApply(configInstance -> {
            synchronized (loadedConfigs) {
                loadedConfigs.put(configurableClass, configInstance);
            }
            return configInstance;
        });
    }

    /**
     * Retrieves a previously registered configuration.
     *
     * @param configurableClass The class used to register the config.
     * @return The configuration instance, or null if not registered.
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(Class<?> configurableClass) {
        return (T) loadedConfigs.get(configurableClass);
    }
}


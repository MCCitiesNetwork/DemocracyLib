package net.democracycraft.democracyLib.api.config;

import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigFactory {

    private static final Map<Class<?>, MethodHandle> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    /**
     * Creates and loads a configuration object based on the schema class annotated with @Configurable.
     *
     * @param plugin The plugin instance owner of the file.
     * @param configurableClass The class annotated with @Configurable defining the schema.
     * @return The generated configuration object.
     */
    public static @NonNull Object create(Plugin plugin, Class<?> configurableClass) {
        return createInternal(plugin, configurableClass, null, false).join();
    }

    /**
     * Creates and loads a configuration object with a specific filename.
     *
     * @param plugin The plugin instance owner of the file.
     * @param configurableClass The class annotated with @Configurable defining the schema.
     * @param fileName The specific filename to use (overrides annotation).
     * @return The generated configuration object.
     */
    public static @NonNull Object create(Plugin plugin, Class<?> configurableClass, String fileName) {
        return createInternal(plugin, configurableClass, fileName, false).join();
    }

    /**
     * Creates and loads a configuration object asynchronously.
     *
     * @param plugin The plugin instance owner of the file.
     * @param configurableClass The class annotated with @Configurable defining the schema.
     * @return A future completing with the generated configuration object.
     */
    public static CompletableFuture<Object> createAsync(Plugin plugin, Class<?> configurableClass) {
        return createInternal(plugin, configurableClass, null, true);
    }

    /**
     * Creates and loads a configuration object asynchronously with a specific filename.
     *
     * @param plugin The plugin instance owner of the file.
     * @param configurableClass The class annotated with @Configurable defining the schema.
     * @param fileName The specific filename to use (overrides annotation).
     * @return A future completing with the generated configuration object.
     */
    public static CompletableFuture<Object> createAsync(Plugin plugin, Class<?> configurableClass, String fileName) {
        return createInternal(plugin, configurableClass, fileName, true);
    }

    private static CompletableFuture<Object> createInternal(Plugin plugin, @NonNull Class<?> configurableClass, String fileNameOverride, boolean async) {
        Configurable annotation = configurableClass.getAnnotation(Configurable.class);
        if (annotation == null) {
             throw new IllegalArgumentException("Class " + configurableClass.getName() + " is not annotated with @Configurable.");
        }
        String fileName = fileNameOverride != null ? fileNameOverride : annotation.name() + ".yml";

        try {
            MethodHandle constructor = CONSTRUCTOR_CACHE.computeIfAbsent(configurableClass, clazz -> {
                String generatedClassName = getGeneratedClassName(clazz);
                try {
                    Class<?> generatedClass = Class.forName(generatedClassName);

                    if (!GeneratedConfig.class.isAssignableFrom(generatedClass)) {
                        throw new RuntimeException("Generated class " + generatedClassName + " does not implement GeneratedConfig interface.");
                    }
                    // Look for public no-arg constructor
                    return MethodHandles.publicLookup().findConstructor(generatedClass, MethodType.methodType(void.class));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to resolve generated config for " + clazz.getName(), e);
                }
            });

            File configFile = new File(plugin.getDataFolder(), fileName);

            GeneratedConfig configInstance = (GeneratedConfig) constructor.invoke();
            configInstance.init(configFile);

            if (async) {
                return configInstance.loadOrCreateAsync().thenApply(v -> configInstance);
            } else {
                configInstance.loadOrCreate();
                return CompletableFuture.completedFuture(configInstance);
            }

        } catch (Throwable e) {
             CompletableFuture<Object> failed = new CompletableFuture<>();
             failed.completeExceptionally(new RuntimeException("Failed to initialize configuration for " + configurableClass.getName(), e));
             return failed;
        }
    }

    private static @NonNull String getGeneratedClassName(@NonNull Class<?> configurableClass) {
        // Validation already performed in create, but kept for method contract
        if (!configurableClass.isAnnotationPresent(Configurable.class)) {
            throw new IllegalArgumentException("Class " + configurableClass.getName() + " is not annotated with @Configurable.");
        }

        return getClassName(configurableClass);
    }

    static @NonNull String getClassName(@NonNull Class<?> configurableClass) {
        Configurable annotation = configurableClass.getAnnotation(Configurable.class);
        String configName = annotation.name();
        String targetPackage = annotation.targetPackage();

        if (targetPackage.isEmpty()) {
            targetPackage = configurableClass.getPackageName();
        }
        return targetPackage + "." + configName;
    }
}


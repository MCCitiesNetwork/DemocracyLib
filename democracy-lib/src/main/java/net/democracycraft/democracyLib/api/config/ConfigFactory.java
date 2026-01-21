package net.democracycraft.democracyLib.api.config;

import org.jspecify.annotations.NonNull;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ConfigFactory {

    private static final Map<Class<?>, MethodHandle> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    /**
     * Loads or creates a generated configuration object type-safely.
     * Use this if you have a specific File object (e.g. outside the plugin data folder or subfolders).
     *
     * @param file The file to load/create.
     * @param generatedConfigClass The generated configuration class.
     * @param <T> The type of the generated config.
     * @return The generated configuration object.
     */
    public static <T extends GeneratedConfig> @NonNull T loadOrCreate(File file, Class<T> generatedConfigClass) {
        return createInternalFile(file, generatedConfigClass, false).join();
    }

    /**
     * Loads or creates a generated configuration object type-safely asynchronously.
     * Use this if you have a specific File object (e.g. outside the plugin data folder or subfolders).
     *
     * @param file The file to load/create.
     * @param generatedConfigClass The generated configuration class.
     * @param <T> The type of the generated config.
     * @return A future completing with the generated configuration object.
     */
    public static <T extends GeneratedConfig> CompletableFuture<T> loadOrCreateAsync(File file, Class<T> generatedConfigClass) {
        return createInternalFile(file, generatedConfigClass, true);
    }

    /**
     * Loads all configuration files from a directory, interpreting them as the given generated config class.
     * Returns a map of filename (without path) to the loaded config instance.
     * This is useful for data-driven systems where users define multiple objects in separate files.
     * Non-matching files (extension) or invalid files are skipped or handled gracefully.
     *
     * @param directory The directory to scan.
     * @param generatedConfigClass The generated configuration class.
     * @param <T> The type of the generated config.
     * @return Map of fileName -> ConfigInstance
     */
    public static <T extends GeneratedConfig> @NonNull Map<String, T> loadAllFromDirectory(File directory, Class<T> generatedConfigClass) {
        return loadAllFromDirectoryAsync(directory, generatedConfigClass).join();
    }

    /**
     * Loads all configuration files from a directory asynchronously.
     *
     * @param directory The directory to scan.
     * @param generatedConfigClass The generated configuration class.
     * @param <T> The type of the generated config.
     * @return Future of Map of fileName -> ConfigInstance
     */
    public static <T extends GeneratedConfig> CompletableFuture<Map<String, T>> loadAllFromDirectoryAsync(File directory, Class<T> generatedConfigClass) {
        if (!directory.exists() || !directory.isDirectory()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".json"));
        if (files == null) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        List<CompletableFuture<Map.Entry<String, T>>> futures = Arrays.stream(files)
                .map(file -> loadOrCreateAsync(file, generatedConfigClass)
                        .thenApply(config -> Map.entry(file.getName(), config))
                        .exceptionally(throwable -> null)) // Skip failed loads
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(unused -> {
                    Map<String, T> result = new ConcurrentHashMap<>();
                    for (CompletableFuture<Map.Entry<String, T>> entryCompletableFuture : futures) {
                        Map.Entry<String, T> entry = entryCompletableFuture.join();
                        if (entry != null) {
                            result.put(entry.getKey(), entry.getValue());
                        }
                    }
                    return result;
                });
    }

    @SuppressWarnings("unchecked")
    private static <T extends GeneratedConfig> CompletableFuture<T> createInternalFile(File configFile, @NonNull Class<T> generatedClass, boolean async) {
        if (configFile.getParentFile() != null && !configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }

        try {
            MethodHandle constructor = CONSTRUCTOR_CACHE.computeIfAbsent(generatedClass, clazz -> {
                try {
                     return MethodHandles.publicLookup().findConstructor(clazz, MethodType.methodType(void.class));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to resolve constructor for " + clazz.getName(), e);
                }
            });

            T configInstance = (T) constructor.invoke();
            configInstance.init(configFile);

            if (async) {
                return configInstance.loadOrCreateAsync().thenApply(v -> configInstance);
            } else {
                configInstance.loadOrCreate();
                return CompletableFuture.completedFuture(configInstance);
            }

        } catch (Throwable e) {
             CompletableFuture<T> failed = new CompletableFuture<>();
             failed.completeExceptionally(new RuntimeException("Failed to initialize configuration for " + generatedClass.getName(), e));
             return failed;
        }
    }
}

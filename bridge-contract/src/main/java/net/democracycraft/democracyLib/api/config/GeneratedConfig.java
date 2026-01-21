package net.democracycraft.democracyLib.api.config;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public interface GeneratedConfig {
    void init(File file);

    void load();
    void save();
    void loadOrCreate();

    CompletableFuture<Void> loadAsync();
    CompletableFuture<Void> saveAsync();
    CompletableFuture<Void> loadOrCreateAsync();
}


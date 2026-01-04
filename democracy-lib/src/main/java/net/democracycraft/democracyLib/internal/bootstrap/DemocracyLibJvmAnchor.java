package net.democracycraft.democracyLib.internal.bootstrap;

import net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeContract;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Global JVM anchor shared across all PluginClassLoaders.
 * <p>
 * Uses System properties to store a bootstrap-loaded ConcurrentHashMap,
 * which can safely be used as a cross-classloader rendezvous point.
 */
public final class DemocracyLibJvmAnchor {

    private DemocracyLibJvmAnchor() {}

    /**
     * Versioned key to reduce collision risk.
     */
    static final String ANCHOR_KEY_V1 = "net.democracycraft.democracyLib.anchor.v1";

    @SuppressWarnings("unchecked")
    public static @NotNull Map<String, Object> anchorMap() {
        Properties props = System.getProperties();
        synchronized (props) {
            Object existing = props.get(ANCHOR_KEY_V1);

            if (existing instanceof ConcurrentMap<?, ?> concurrentMap) {
                ConcurrentMap<String, Object> typed = (ConcurrentMap<String, Object>) concurrentMap;
                typed.putIfAbsent(GeneratedBridgeContract.AnchorKeys.LOCK, new Object());
                props.put(ANCHOR_KEY_V1, typed);
                return typed;
            }

            if (existing instanceof Map<?, ?> map) {
                // defensive migration: dont trust mutability/thread-safety of foreign maps.
                ConcurrentMap<String, Object> migrated = new ConcurrentHashMap<>((Map<String, Object>) map);
                migrated.putIfAbsent(GeneratedBridgeContract.AnchorKeys.LOCK, new Object());
                props.put(ANCHOR_KEY_V1, migrated);
                return migrated;
            }

            ConcurrentMap<String, Object> created = new ConcurrentHashMap<>();
            created.put(GeneratedBridgeContract.AnchorKeys.LOCK, new Object());
            props.put(ANCHOR_KEY_V1, created);
            return created;
        }
    }

    public static @NotNull Object lock() {
        Map<String, Object> map = anchorMap();
        return map.computeIfAbsent(GeneratedBridgeContract.AnchorKeys.LOCK, k -> new Object());
    }
}

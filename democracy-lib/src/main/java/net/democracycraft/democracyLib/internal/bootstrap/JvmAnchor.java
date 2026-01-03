package net.democracycraft.democracyLib.internal.bootstrap;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global JVM anchor shared across all PluginClassLoaders.
 * <p>
 * Uses System properties to store a bootstrap-loaded ConcurrentHashMap,
 * which can safely be used as a cross-classloader rendezvous point.
 */
public final class JvmAnchor {

    private JvmAnchor() {}

    static final String ANCHOR_KEY = "democracylib.anchor";

    @SuppressWarnings("unchecked")
    public static @NotNull Map<String, Object> anchorMap() {
        Properties props = System.getProperties();
        synchronized (props) {
            Object existing = props.get(ANCHOR_KEY);
            if (existing instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            Map<String, Object> created = new ConcurrentHashMap<>();
            created.put(BridgeContract.AnchorKeys.LOCK, new Object());
            props.put(ANCHOR_KEY, created);
            return created;
        }
    }

    public static @NotNull Object lock() {
        Map<String, Object> map = anchorMap();
        return map.computeIfAbsent(BridgeContract.AnchorKeys.LOCK, k -> new Object());
    }
}

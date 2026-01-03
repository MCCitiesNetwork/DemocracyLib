package net.democracycraft.democracyLib.internal.bootstrap;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JVM-wide registry for non-leader API instances.
 * <p>
 * This is used for diagnostics/coordination only and is intentionally weak-referenced to avoid leaks.
 */
public final class ApiRegistry {

    public static final String FOLLOWERS_KEY = BridgeContract.AnchorKeys.FOLLOWERS;

    private ApiRegistry() {
    }

    @SuppressWarnings("unchecked")
    private static Map<Plugin, WeakReference<Object>> followersMap(@NotNull Map<String, Object> anchor) {
        return (Map<Plugin, WeakReference<Object>>) anchor.computeIfAbsent(
                FOLLOWERS_KEY,
                k -> new ConcurrentHashMap<Plugin, WeakReference<Object>>()
        );
    }

    public static void registerFollower(@NotNull Map<String, Object> anchor, @NotNull Object api, @NotNull Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        followersMap(anchor).put(plugin, new WeakReference<>(api));
    }

    public static void unregisterFollower(@NotNull Map<String, Object> anchor, @NotNull Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        Object obj = anchor.get(FOLLOWERS_KEY);
        if (!(obj instanceof Map<?, ?> map)) return;
        @SuppressWarnings("unchecked")
        Map<Plugin, WeakReference<Object>> followers = (Map<Plugin, WeakReference<Object>>) map;
        followers.remove(plugin);
    }

    public static int followerCount(@NotNull Map<String, Object> anchor) {
        return followers(anchor).size();
    }

    public static @NotNull Set<Plugin> followers(@NotNull Map<String, Object> anchor) {
        Object obj = anchor.get(FOLLOWERS_KEY);
        if (!(obj instanceof Map<?, ?> map)) return Set.of();

        @SuppressWarnings("unchecked")
        Map<Plugin, WeakReference<Object>> followers = (Map<Plugin, WeakReference<Object>>) map;

        // Prune dead refs while enumerating.
        Set<Plugin> out = new HashSet<>();
        for (var it = followers.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            WeakReference<Object> ref = e.getValue();
            if (ref == null || ref.get() == null) {
                it.remove();
                continue;
            }
            out.add(e.getKey());
        }
        return Set.copyOf(out);
    }
}

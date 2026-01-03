package net.democracycraft.democracyLib.internal.bootstrap;

import net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DemocracyLibJvmAnchorTest {

    @AfterEach
    void cleanup() {
        // Avoid leaking state across tests/JVM runs.
        System.getProperties().remove(DemocracyLibJvmAnchor.ANCHOR_KEY_V1);
    }

    @Test
    void anchorMap_createsConcurrentMapAndLock() {
        Map<String, Object> anchor = DemocracyLibJvmAnchor.anchorMap();
        assertNotNull(anchor);
        assertNotNull(DemocracyLibJvmAnchor.lock());
        assertSame(DemocracyLibJvmAnchor.lock(), anchor.get(GeneratedBridgeContract.AnchorKeys.LOCK));
    }

    @Test
    void anchorMap_migratesForeignNonConcurrentMap() {
        Map<String, Object> foreign = new HashMap<>();
        foreign.put("foo", "bar");
        System.getProperties().put(DemocracyLibJvmAnchor.ANCHOR_KEY_V1, foreign);

        Map<String, Object> anchor = DemocracyLibJvmAnchor.anchorMap();
        assertNotNull(anchor);
        assertEquals("bar", anchor.get("foo"));
        assertNotSame(foreign, anchor);
        assertNotNull(anchor.get(GeneratedBridgeContract.AnchorKeys.LOCK));
    }
}


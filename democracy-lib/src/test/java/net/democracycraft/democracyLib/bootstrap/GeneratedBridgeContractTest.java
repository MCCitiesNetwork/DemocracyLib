package net.democracycraft.democracyLib.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ensures the annotation processor runs for democracy-lib and generates the contract class.
 */
class GeneratedBridgeContractTest {

    @Test
    void generatedContractIsLoadable() throws Exception {
        Class<?> generated = Class.forName("net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeContract");
        assertNotNull(generated);

        int protocol = generated.getField("PROTOCOL_VERSION").getInt(null);
        assertTrue(protocol >= 1);
    }

    @Test
    void generatedAnchorKeysContainLeader() throws Exception {
        Class<?> anchorKeys = Class.forName("net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeContract$AnchorKeys");
        String leaderKey = (String) anchorKeys.getField("LEADER").get(null);
        assertNotNull(leaderKey);
        assertFalse(leaderKey.isBlank());
    }
}


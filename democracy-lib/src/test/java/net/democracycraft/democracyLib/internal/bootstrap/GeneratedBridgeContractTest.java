package net.democracycraft.democracyLib.internal.bootstrap;

import net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeContract;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ensures the annotation processor runs for democracy-lib and generates the contract class.
 */
class GeneratedBridgeContractTest {

    @Test
    void generatedContractIsLoadable() {
        assertNotNull(GeneratedBridgeContract.class);
        assertTrue(GeneratedBridgeContract.PROTOCOL_VERSION >= 1);
    }

    @Test
    void generatedAnchorKeysContainLeader() {
        String leaderKey = GeneratedBridgeContract.AnchorKeys.LEADER;
        assertNotNull(leaderKey);
        assertFalse(leaderKey.isBlank());
    }
}

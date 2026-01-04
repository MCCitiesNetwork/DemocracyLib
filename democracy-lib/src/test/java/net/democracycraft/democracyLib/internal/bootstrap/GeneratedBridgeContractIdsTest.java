package net.democracycraft.democracyLib.internal.bootstrap;

import net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeContract;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QA validation: ensures every generated method id has a resolvable Spec in the generated contract.
 */
class GeneratedBridgeContractIdsTest {
    @Test
    void everyGeneratedMethodIdHasASpec() {
        Map<String, GeneratedBridgeContract.Spec> specs = GeneratedBridgeContract.Methods.SPECS;
        assertNotNull(specs);
        assertFalse(specs.isEmpty(), "SPECS map should not be empty");

        for (String id : specs.keySet()) {
            assertNotNull(id);
            assertFalse(id.isBlank());

            GeneratedBridgeContract.Spec s = GeneratedBridgeContract.Methods.spec(id);
            assertNotNull(s, "Missing spec for generated method id: " + id);
        }
    }
}

package net.democracycraft.democracyLib.bootstrap;

import net.democracycraft.democracyLib.internal.bootstrap.BridgeContract;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QA validation: ensures the stable contract IDs used by the runtime exist in the generated contract.
 */
class GeneratedBridgeContractIdsTest {

    private static Object spec(String id) throws Exception {
        Class<?> methods = Class.forName("net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeContract$Methods");
        Method spec = methods.getMethod("spec", String.class);
        return spec.invoke(null, id);
    }

    @Test
    void allStableIdsUsedAtRuntimeExistInGeneratedContract() throws Exception {
        String[] ids = {
                // DemocracyLibApi
                BridgeContract.Ids.LibApi.GET_MOJANG_SERVICE,
                BridgeContract.Ids.LibApi.GET_GITHUB_GIST_SERVICE,
                BridgeContract.Ids.LibApi.GET_GITHUB_GIST_SERVICE_WITH_CONFIG,
                BridgeContract.Ids.LibApi.GET_SERVICE_MANAGER,
                BridgeContract.Ids.LibApi.SHUTDOWN,

                // DemocracyServiceManager
                BridgeContract.Ids.ServiceManager.GET_ALL_SERVICES,
                BridgeContract.Ids.ServiceManager.GET_SERVICES_BY_TYPE,
                BridgeContract.Ids.ServiceManager.GET_SERVICE,
                BridgeContract.Ids.ServiceManager.GET_PLUGIN_BOUND_SERVICES,
                BridgeContract.Ids.ServiceManager.REGISTER_SERVICE,
                BridgeContract.Ids.ServiceManager.HAS_REGISTERED_SERVICE,
                BridgeContract.Ids.ServiceManager.GET_SERVICE_FOR_PLUGIN
        };

        for (String id : ids) {
            Object s = spec(id);
            assertNotNull(s, "Missing spec for contract id: " + id);
        }
    }
}

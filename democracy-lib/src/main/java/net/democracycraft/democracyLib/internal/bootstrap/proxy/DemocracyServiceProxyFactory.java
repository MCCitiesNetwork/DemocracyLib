package net.democracycraft.democracyLib.internal.bootstrap.proxy;

import net.democracycraft.democracyLib.internal.bootstrap.bridge.BridgeValueAdapter;

/**
 * Creates best-effort proxies for leader-owned service objects.
 * <p></p>
 * This must only be used when the requested API/interface type is visible to the caller's classloader.
 */
public class DemocracyServiceProxyFactory {

    private DemocracyServiceProxyFactory() {
    }

    @SuppressWarnings("unchecked")
    public static <ApiType> ApiType proxyAs(Class<ApiType> api, Object leaderService) {
        return (ApiType) BridgeValueAdapter.adapt(api, leaderService, leaderService.getClass().getClassLoader());
    }
}

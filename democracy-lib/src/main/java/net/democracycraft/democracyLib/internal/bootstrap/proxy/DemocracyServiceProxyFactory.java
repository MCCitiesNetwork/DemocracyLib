package net.democracycraft.democracyLib.internal.bootstrap.proxy;

import net.democracycraft.democracyLib.internal.bootstrap.handler.GenericDemocracyBootstrapHandler;

import java.lang.reflect.Proxy;

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
        if (api.isInstance(leaderService)) {
            return (ApiType) leaderService;
        }

        return (ApiType) Proxy.newProxyInstance(
                api.getClassLoader(),
                new Class[]{api},
                new GenericDemocracyBootstrapHandler(leaderService)
        );
    }
}

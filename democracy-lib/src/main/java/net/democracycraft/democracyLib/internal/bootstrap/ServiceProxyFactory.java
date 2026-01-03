package net.democracycraft.democracyLib.internal.bootstrap;

import net.democracycraft.democracyLib.internal.bootstrap.handler.GenericDemocracyBootstrapHandler;

import java.lang.reflect.Proxy;

/**
 * Creates best-effort proxies for leader-owned service objects.
 *
 * This must only be used when the requested API/interface type is visible to the caller's classloader.
 */
final class ServiceProxyFactory {

    private ServiceProxyFactory() {
    }

    @SuppressWarnings("unchecked")
    static <T> T proxyAs(Class<T> api, Object leaderService) {
        if (api.isInstance(leaderService)) {
            return (T) leaderService;
        }

        return (T) Proxy.newProxyInstance(
                api.getClassLoader(),
                new Class[]{api},
                new GenericDemocracyBootstrapHandler(leaderService)
        );
    }
}

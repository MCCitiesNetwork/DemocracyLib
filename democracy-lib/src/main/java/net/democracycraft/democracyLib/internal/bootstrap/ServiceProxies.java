package net.democracycraft.democracyLib.internal.bootstrap;

import net.democracycraft.democracyLib.api.service.github.GitHubGistService;
import net.democracycraft.democracyLib.api.service.mojang.MojangService;
import net.democracycraft.democracyLib.internal.bootstrap.handler.GenericDemocracyBootstrapHandler;
import net.democracycraft.democracyLib.internal.bootstrap.handler.MojangDemocracyBootstrapHandler;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

final class ServiceProxies {

    private ServiceProxies() {}

    static <P extends Plugin> @NotNull MojangService<P> mojangProxy(@NotNull Object leaderService) {
        return proxy(MojangService.class, new MojangDemocracyBootstrapHandler(leaderService));
    }

    static <P extends Plugin> @NotNull GitHubGistService<P> githubProxy(@NotNull Object leaderService) {
        return proxy(GitHubGistService.class, new GenericDemocracyBootstrapHandler(leaderService));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> api, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(api.getClassLoader(), new Class[]{api}, handler);
    }
}

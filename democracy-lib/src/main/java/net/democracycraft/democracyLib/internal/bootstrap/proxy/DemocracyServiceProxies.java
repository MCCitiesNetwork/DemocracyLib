package net.democracycraft.democracyLib.internal.bootstrap.proxy;

import net.democracycraft.democracyLib.api.service.github.GitHubGistService;
import net.democracycraft.democracyLib.api.service.mojang.MojangService;
import net.democracycraft.democracyLib.internal.bootstrap.handler.GenericDemocracyBootstrapHandler;
import net.democracycraft.democracyLib.internal.bootstrap.handler.MojangDemocracyBootstrapHandler;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public final class DemocracyServiceProxies {

    private DemocracyServiceProxies() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <PluginType extends Plugin> @NotNull MojangService<PluginType> mojangProxy(@NotNull Object leaderService) {
        return (MojangService<PluginType>) proxy((Class) MojangService.class, new MojangDemocracyBootstrapHandler(leaderService));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <P extends Plugin> @NotNull GitHubGistService<P> githubProxy(@NotNull Object leaderService) {
        return (GitHubGistService<P>) proxy((Class) GitHubGistService.class, new GenericDemocracyBootstrapHandler(leaderService));
    }

    private static <T> @NotNull T proxy(Class<T> api, InvocationHandler handler) {
        @SuppressWarnings("unchecked")
        T instance = (T) Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api}, handler);
        return instance;
    }
}

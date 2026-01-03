package net.democracycraft.democracyLib.internal.bootstrap.handler;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;

/**
 * Invocation handler contract used by cross-classloader bootstrap proxies.
 */
public interface DemocracyBootstrapHandler extends InvocationHandler {

    @NotNull Object target();
}


package net.democracycraft.democracyLib.internal.bootstrap.handler;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic handler based on reflection/MethodHandle.
 */
public class GenericDemocracyBootstrapHandler implements DemocracyBootstrapHandler {

    protected final Object target;
    protected final Map<Method, MethodHandle> cache = new ConcurrentHashMap<>();

    public GenericDemocracyBootstrapHandler(@NotNull Object target) {
        this.target = target;
    }

    @Override
    public @NotNull Object target() {
        return target;
    }

    @Override
    public Object invoke(Object proxy, @NotNull Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        MethodHandle mh = cache.computeIfAbsent(method, m -> {
            Method tm = resolveByNameAndArity(target.getClass(), m.getName(), m.getParameterCount());
            try {
                return MethodHandles.publicLookup().unreflect(tm);
            } catch (IllegalAccessException e) {
                try {
                    tm.setAccessible(true);
                    return MethodHandles.lookup().unreflect(tm);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        Object[] actualArgs = args == null ? new Object[0] : args;
        Object[] full = new Object[actualArgs.length + 1];
        full[0] = target;
        System.arraycopy(actualArgs, 0, full, 1, actualArgs.length);
        return mh.invokeWithArguments(full);
    }

    protected static Method resolveByNameAndArity(Class<?> type, String name, int arity) {
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == arity) {
                return m;
            }
        }
        throw new IllegalStateException("Could not find method " + name + "/" + arity + " on " + type.getName());
    }
}

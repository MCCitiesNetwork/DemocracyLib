package net.democracycraft.democracyLib.internal.bootstrap.handler;

import net.democracycraft.democracyLib.internal.bootstrap.bridge.BridgeValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic handler based on reflection/MethodHandle.
 * <p>
 * Every call is adapted at the classloader boundary by {@link BridgeValueAdapter}:
 * arguments against the target method's declared parameter types (so the foreign side can
 * hold them), and the result against the invoked interface method's declared return type
 * (so the local side can hold it).
 */
public class GenericDemocracyBootstrapHandler implements DemocracyBootstrapHandler {

    protected final Object target;
    private final Map<Method, ResolvedCall> cache = new ConcurrentHashMap<>();

    private record ResolvedCall(Method targetMethod, MethodHandle handle) {
    }

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

        ResolvedCall call = cache.computeIfAbsent(method, m -> {
            Method targetMethod = resolveByNameAndArity(target.getClass(), m.getName(), m.getParameterCount());
            try {
                return new ResolvedCall(targetMethod, MethodHandles.publicLookup().unreflect(targetMethod));
            } catch (IllegalAccessException e) {
                try {
                    targetMethod.setAccessible(true);
                    return new ResolvedCall(targetMethod, MethodHandles.lookup().unreflect(targetMethod));
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        ClassLoader callerLoader = method.getDeclaringClass().getClassLoader();
        Object[] adaptedArgs = BridgeValueAdapter.adaptArguments(call.targetMethod(), args, callerLoader);

        Object[] full = new Object[adaptedArgs.length + 1];
        full[0] = target;
        System.arraycopy(adaptedArgs, 0, full, 1, adaptedArgs.length);
        Object result = call.handle().invokeWithArguments(full);

        return BridgeValueAdapter.adapt(method.getGenericReturnType(), result, target.getClass().getClassLoader());
    }

    protected static @NonNull Method resolveByNameAndArity(@NonNull Class<?> type, String name, int arity) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arity) {
                return method;
            }
        }
        throw new IllegalStateException("Could not find method " + name + "/" + arity + " on " + type.getName());
    }
}

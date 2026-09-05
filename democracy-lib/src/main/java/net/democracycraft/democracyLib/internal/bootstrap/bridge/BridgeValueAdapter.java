package net.democracycraft.democracyLib.internal.bootstrap.bridge;

import net.democracycraft.democracyLib.internal.bootstrap.handler.DemocracyBootstrapHandler;
import net.democracycraft.democracyLib.internal.bootstrap.handler.GenericDemocracyBootstrapHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

/**
 * Adapts values crossing the leader/follower classloader boundary so that the receiving side
 * only ever holds objects it can actually type.
 * <p>
 * Adaptation is driven by the <b>declared</b> type on the receiving side of the call
 * (a follower interface method's return type, or a leader method's parameter type):
 * <ul>
 *     <li>value already holdable through the declared type → passed through unchanged;</li>
 *     <li>registered value types ({@link BridgeTypeContract#VALUE_TYPE_FQNS}) → re-created locally
 *     from their parent-visible components;</li>
 *     <li>library interfaces → wrapped in a JDK proxy created in the declared type's classloader,
 *     implementing every library interface of the foreign object that is loadable there
 *     (so downcasts like {@code (MojangServiceDemocracyCache) service.getCache()} keep working);</li>
 *     <li>{@link CompletableFuture}/{@link List}/{@link Map} with library-typed elements → elements
 *     adapted (maps become live translating views, futures adapt on completion);</li>
 *     <li>anything else → {@link IllegalStateException}, never a latent {@link ClassCastException}.
 *     {@code BridgeSurfaceContractTest} prevents such signatures from being added at build time.</li>
 * </ul>
 */
public final class BridgeValueAdapter {

    private BridgeValueAdapter() {
    }

    /**
     * Adapts call arguments against the resolved target method's declared parameter types
     * (the target method belongs to the foreign side, so this adapts local values into it).
     *
     * @param sourceLoader classloader of the side that produced the arguments.
     */
    public static Object @NotNull [] adaptArguments(@NotNull Method targetMethod,
                                                    Object @Nullable [] args,
                                                    @Nullable ClassLoader sourceLoader) {
        if (args == null || args.length == 0) return new Object[0];

        Type[] declaredParams = targetMethod.getGenericParameterTypes();
        Object[] adapted = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            adapted[i] = i < declaredParams.length ? adapt(declaredParams[i], args[i], sourceLoader) : args[i];
        }
        return adapted;
    }

    /**
     * Adapts {@code value} (produced by a foreign classloader) so it can be held through
     * {@code declaredType}, which belongs to the receiving side.
     *
     * @param sourceLoader classloader of the side that produced {@code value}; used to create
     *                     foreign counterparts when writing back through translating views.
     */
    public static @Nullable Object adapt(@NotNull Type declaredType, @Nullable Object value, @Nullable ClassLoader sourceLoader) {
        if (value == null) return null;

        Class<?> declared = BridgeTypeContract.erase(declaredType);

        // JDK containers are holdable by anyone but may carry library-typed elements.
        if (value instanceof CompletableFuture<?> future && CompletableFuture.class.isAssignableFrom(declared)) {
            Type elementType = typeArgument(declaredType, 0);
            if (elementType == null || !mentionsLibraryType(elementType)) return value;
            return future.thenApply(element -> adapt(elementType, element, sourceLoader));
        }

        if (value instanceof Map<?, ?> map && Map.class.isAssignableFrom(declared)) {
            Type keyType = typeArgument(declaredType, 0);
            Type valueType = typeArgument(declaredType, 1);
            boolean keysNeedAdaptation = keyType != null && mentionsLibraryType(keyType);
            boolean valuesNeedAdaptation = valueType != null && mentionsLibraryType(valueType);
            // No library-typed elements: hand the live map over so state stays genuinely shared.
            if (!keysNeedAdaptation && !valuesNeedAdaptation) return map;

            @SuppressWarnings("unchecked")
            Map<Object, Object> backing = (Map<Object, Object>) map;
            return new BridgeAdaptingMap(
                    backing,
                    keysNeedAdaptation,
                    elementReader(keysNeedAdaptation, keyType, sourceLoader),
                    elementWriter(keysNeedAdaptation, keyType, sourceLoader),
                    elementReader(valuesNeedAdaptation, valueType, sourceLoader),
                    elementWriter(valuesNeedAdaptation, valueType, sourceLoader)
            );
        }

        if (value instanceof Collection<?> collection && Collection.class.isAssignableFrom(declared)) {
            Type elementType = typeArgument(declaredType, 0);
            if (elementType == null || !mentionsLibraryType(elementType)) return value;
            List<Object> adapted = new ArrayList<>(collection.size());
            for (Object element : collection) {
                adapted.add(adapt(elementType, element, sourceLoader));
            }
            return Set.class.isAssignableFrom(declared)
                    ? Collections.unmodifiableSet(new LinkedHashSet<>(adapted))
                    : Collections.unmodifiableList(adapted);
        }

        if (BridgeTypeContract.canHold(declared, value)) return value;

        // If the value is one of our own bridge proxies, unwrap it instead of stacking proxies.
        Object unwrapped = unwrapBridgeProxy(value);
        if (unwrapped != value) {
            if (BridgeTypeContract.canHold(declared, unwrapped)) return unwrapped;
            value = unwrapped;
        }

        if (BridgeTypeContract.isValueType(declared)) {
            return recreateValueType(declared, value);
        }

        if (declared.isInterface() && BridgeTypeContract.isLibraryType(declared)) {
            return crossProxy(declared, value);
        }

        throw new IllegalStateException(
                "Bridge contract violation: a value of type " + value.getClass().getName()
                        + " (loader " + value.getClass().getClassLoader() + ") cannot cross the classloader boundary as "
                        + declared.getName() + " (loader " + declared.getClassLoader() + "). "
                        + "Bridged signatures may only expose parent-visible types, library interfaces, "
                        + "or registered value types (see BridgeTypeContract).");
    }

    /**
     * Creates a proxy in {@code declaredInterface}'s classloader implementing every library
     * interface of {@code foreign} that is loadable there.
     */
    private static @NotNull Object crossProxy(@NotNull Class<?> declaredInterface, @NotNull Object foreign) {
        ClassLoader receiverLoader = declaredInterface.getClassLoader();

        LinkedHashSet<Class<?>> interfaces = new LinkedHashSet<>();
        interfaces.add(declaredInterface);
        for (Class<?> foreignInterface : allInterfacesOf(foreign.getClass())) {
            if (!BridgeTypeContract.isLibraryType(foreignInterface)) continue;
            try {
                Class<?> local = Class.forName(foreignInterface.getName(), false, receiverLoader);
                if (local.isInterface()) interfaces.add(local);
            } catch (ClassNotFoundException | LinkageError ignored) {
                // Older library copy without this interface: proxy what is loadable.
            }
        }

        return Proxy.newProxyInstance(
                receiverLoader,
                interfaces.toArray(new Class<?>[0]),
                new GenericDemocracyBootstrapHandler(foreign)
        );
    }

    /**
     * Whether {@link #adapt} has a re-creation branch for this value type. Kept in sync with
     * {@link BridgeTypeContract#VALUE_TYPE_FQNS} by the bridge surface contract test.
     */
    public static boolean supportsValueType(@NotNull String fqn) {
        return "net.democracycraft.democracyLib.api.data.SkinDto".equals(fqn);
    }

    private static @NotNull Object recreateValueType(@NotNull Class<?> declared, @NotNull Object foreign) {
        try {
            if ("net.democracycraft.democracyLib.api.data.SkinDto".equals(declared.getName())) {
                Object value = foreign.getClass().getMethod("value").invoke(foreign);
                Object signature = foreign.getClass().getMethod("signature").invoke(foreign);
                return declared.getMethod("of", String.class, String.class)
                        .invoke(null, (String) value, (String) signature);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to re-create bridge value type " + declared.getName()
                    + " from " + foreign.getClass().getName(), e);
        }
        throw new IllegalStateException("No bridge value adapter registered for " + declared.getName()
                + "; add one here and to BridgeTypeContract.VALUE_TYPE_FQNS.");
    }

    private static @NotNull UnaryOperator<Object> elementReader(boolean needsAdaptation,
                                                                @Nullable Type declaredElementType,
                                                                @Nullable ClassLoader sourceLoader) {
        if (!needsAdaptation || declaredElementType == null) return UnaryOperator.identity();
        return element -> adapt(declaredElementType, element, sourceLoader);
    }

    private static @NotNull UnaryOperator<Object> elementWriter(boolean needsAdaptation,
                                                                @Nullable Type declaredElementType,
                                                                @Nullable ClassLoader sourceLoader) {
        if (!needsAdaptation || declaredElementType == null) return UnaryOperator.identity();
        Class<?> localErasure = BridgeTypeContract.erase(declaredElementType);
        return element -> {
            if (element == null) return null;
            if (!BridgeTypeContract.isLibraryType(localErasure)) return element;
            if (sourceLoader == null) {
                throw new IllegalStateException("Cannot write " + localErasure.getName()
                        + " back through the bridge: owning classloader unknown.");
            }
            Class<?> foreignCounterpart;
            try {
                foreignCounterpart = Class.forName(localErasure.getName(), false, sourceLoader);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Cannot write " + localErasure.getName()
                        + " back through the bridge: type not present in the owning classloader.", e);
            }
            return adapt(foreignCounterpart, element, localErasure.getClassLoader());
        };
    }

    private static @NotNull Object unwrapBridgeProxy(@NotNull Object value) {
        if (!Proxy.isProxyClass(value.getClass())) return value;
        InvocationHandler handler = Proxy.getInvocationHandler(value);
        if (handler instanceof DemocracyBootstrapHandler bootstrapHandler) {
            return bootstrapHandler.target();
        }
        // A bridge proxy created by another library copy: same handler contract, foreign class.
        if (BridgeTypeContract.isLibraryName(handler.getClass().getName())) {
            try {
                return handler.getClass().getMethod("target").invoke(handler);
            } catch (ReflectiveOperationException ignored) {
                // Not one of ours after all.
            }
        }
        return value;
    }

    private static @Nullable Type typeArgument(@NotNull Type type, int index) {
        if (type instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (index < arguments.length) return arguments[index];
        }
        return null;
    }

    private static boolean mentionsLibraryType(@NotNull Type type) {
        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) return mentionsLibraryType(clazz.getComponentType());
            return BridgeTypeContract.isLibraryType(clazz);
        }
        if (type instanceof ParameterizedType parameterized) {
            if (mentionsLibraryType(parameterized.getRawType())) return true;
            for (Type argument : parameterized.getActualTypeArguments()) {
                if (mentionsLibraryType(argument)) return true;
            }
            return false;
        }
        if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                if (mentionsLibraryType(bound)) return true;
            }
            for (Type bound : wildcard.getLowerBounds()) {
                if (mentionsLibraryType(bound)) return true;
            }
            return false;
        }
        if (type instanceof TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) {
                if (mentionsLibraryType(bound)) return true;
            }
            return false;
        }
        if (type instanceof GenericArrayType array) {
            return mentionsLibraryType(array.getGenericComponentType());
        }
        return false;
    }

    private static @NotNull Set<Class<?>> allInterfacesOf(@NotNull Class<?> type) {
        LinkedHashSet<Class<?>> found = new LinkedHashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            pending.addAll(List.of(current.getInterfaces()));
        }
        while (!pending.isEmpty()) {
            Class<?> next = pending.poll();
            if (found.add(next)) {
                pending.addAll(List.of(next.getInterfaces()));
            }
        }
        return found;
    }
}

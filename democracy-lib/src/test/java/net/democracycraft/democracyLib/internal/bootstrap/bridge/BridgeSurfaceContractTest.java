package net.democracycraft.democracyLib.internal.bootstrap.bridge;

import net.democracycraft.democracyLib.api.DemocracyLibApi;
import net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeContract;
import net.democracycraft.democracyLib.api.bootstrap.contract.BridgeApi;
import net.democracycraft.democracyLib.api.config.DemocracyConfigManager;
import net.democracycraft.democracyLib.api.service.engine.DemocracyServiceManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Build-time structural guard for the leader/follower boundary.
 * <p>
 * Walks the whole reachable bridge surface — every library interface transitively mentioned in a
 * signature starting from the {@link BridgeApi} roots — and fails if any signature exposes a
 * library type that {@code BridgeValueAdapter} cannot adapt (i.e. anything that is neither a
 * parent-visible type, a library interface, nor a registered value type).
 * <p>
 * If this test fails after you added a method: return/accept an interface or a registered value
 * type instead of a concrete library class. A follower can never hold the leader's concrete class.
 */
class BridgeSurfaceContractTest {

    /** Maps every @BridgeApi interface to its generated contract namespace. */
    private static final Map<Class<?>, String> BRIDGE_ROOTS = Map.of(
            DemocracyLibApi.class, "DEMOCRACY_LIB_API",
            DemocracyServiceManager.class, "DEMOCRACY_SERVICE_MANAGER",
            DemocracyConfigManager.class, "DEMOCRACY_CONFIG_MANAGER"
    );

    @Test
    void bridgeRootsMatchTheGeneratedContract() {
        // @BridgeApi has SOURCE retention, so the generated contract is the runtime truth.
        // If a new @BridgeApi interface appears, a new namespace shows up here and this test
        // fails until BRIDGE_ROOTS includes it — keeping the surface walk below exhaustive.
        Set<String> generatedNamespaces = GeneratedBridgeContract.Methods.SPECS.values().stream()
                .map(GeneratedBridgeContract.Spec::namespace)
                .collect(Collectors.toSet());
        assertEquals(Set.copyOf(BRIDGE_ROOTS.values()), generatedNamespaces,
                "BRIDGE_ROOTS is out of sync with the @BridgeApi interfaces in the generated contract");
    }

    @Test
    void generatedWireContractOnlyMentionsBridgeSafeTypes() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, GeneratedBridgeContract.Spec> entry : GeneratedBridgeContract.Methods.SPECS.entrySet()) {
            GeneratedBridgeContract.Spec spec = entry.getValue();
            List<String> mentioned = new ArrayList<>(spec.paramTypeFqns());
            mentioned.add(spec.returnTypeFqn());
            for (String fqn : mentioned) {
                if (!BridgeTypeContract.isLibraryName(fqn)) continue;
                Class<?> type = Class.forName(fqn);
                if (!BridgeTypeContract.isBridgeSafe(type)) {
                    violations.add(entry.getKey() + " mentions " + fqn);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Generated wire contract mentions non-bridgeable library types:\n  - "
                        + String.join("\n  - ", violations));
    }

    @Test
    void everyRegisteredValueTypeHasAnAdapter() {
        for (String fqn : BridgeTypeContract.VALUE_TYPE_FQNS) {
            assertTrue(BridgeValueAdapter.supportsValueType(fqn),
                    "BridgeTypeContract.VALUE_TYPE_FQNS contains " + fqn
                            + " but BridgeValueAdapter has no re-creation branch for it");
        }
    }

    @Test
    void everyBridgedSignatureIsHoldableByAFollower() {
        Set<Class<?>> surface = reachableBridgeSurface();
        List<String> violations = new ArrayList<>();

        for (Class<?> surfaceInterface : surface) {
            for (Method method : surfaceInterface.getMethods()) {
                checkType(method.getGenericReturnType(), method, "return type", violations);
                for (Type parameter : method.getGenericParameterTypes()) {
                    checkType(parameter, method, "parameter", violations);
                }
            }
            for (Type genericInterface : surfaceInterface.getGenericInterfaces()) {
                if (!BridgeTypeContract.isBridgeSafe(genericInterface)) {
                    violations.add(surfaceInterface.getName() + " extends " + genericInterface
                            + " which mentions a non-bridgeable library type");
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "Bridged signatures expose library types a follower cannot hold across classloaders:\n  - "
                        + String.join("\n  - ", violations)
                        + "\nExpose an interface or a registered value type (BridgeTypeContract.VALUE_TYPE_FQNS) instead.");
    }

    private static void checkType(Type type, Method method, String position, List<String> violations) {
        if (!BridgeTypeContract.isBridgeSafe(type)) {
            violations.add(method.getDeclaringClass().getName() + "#" + method.getName()
                    + " — " + position + " " + type.getTypeName());
        }
    }

    /**
     * The bridge roots plus every library interface transitively mentioned in surface signatures
     * (method returns/parameters and generic superinterface arguments).
     */
    private static Set<Class<?>> reachableBridgeSurface() {
        Set<Class<?>> surface = new LinkedHashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>(BRIDGE_ROOTS.keySet());

        while (!pending.isEmpty()) {
            Class<?> current = pending.poll();
            if (!surface.add(current)) continue;

            LinkedHashSet<Class<?>> mentioned = new LinkedHashSet<>();
            for (Method method : current.getMethods()) {
                collectLibraryClasses(method.getGenericReturnType(), mentioned);
                for (Type parameter : method.getGenericParameterTypes()) {
                    collectLibraryClasses(parameter, mentioned);
                }
            }
            for (Type genericInterface : current.getGenericInterfaces()) {
                collectLibraryClasses(genericInterface, mentioned);
            }

            for (Class<?> clazz : mentioned) {
                if (clazz.isInterface()) pending.add(clazz);
            }
        }
        return surface;
    }

    private static void collectLibraryClasses(Type type, Set<Class<?>> out) {
        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) {
                collectLibraryClasses(clazz.getComponentType(), out);
            } else if (BridgeTypeContract.isLibraryType(clazz)) {
                out.add(clazz);
            }
        } else if (type instanceof ParameterizedType parameterized) {
            collectLibraryClasses(parameterized.getRawType(), out);
            for (Type argument : parameterized.getActualTypeArguments()) {
                collectLibraryClasses(argument, out);
            }
        } else if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) collectLibraryClasses(bound, out);
            for (Type bound : wildcard.getLowerBounds()) collectLibraryClasses(bound, out);
        } else if (type instanceof TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) collectLibraryClasses(bound, out);
        } else if (type instanceof GenericArrayType array) {
            collectLibraryClasses(array.getGenericComponentType(), out);
        }
    }
}

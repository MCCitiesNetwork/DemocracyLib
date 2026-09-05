package net.democracycraft.democracyLib.internal.bootstrap.bridge;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Set;

/**
 * The structural rules of what may cross the leader/follower classloader boundary.
 * <p>
 * Because DemocracyLib is shaded unrelocated into every consuming plugin, the same
 * fully-qualified library name resolves to a different {@link Class} per plugin classloader.
 * A follower can therefore only ever hold:
 * <ul>
 *     <li><b>parent-visible types</b> — JDK, Bukkit/Paper, Gson, … (loaded by a shared parent loader);</li>
 *     <li><b>library interfaces</b> — held as a follower-local JDK proxy over the leader's instance;</li>
 *     <li><b>registered value types</b> — DTOs re-created locally from parent-visible data (see {@link #VALUE_TYPE_FQNS}).</li>
 * </ul>
 * {@link BridgeValueAdapter} implements the adaptation; {@code BridgeSurfaceContractTest} walks the whole
 * bridged surface at build time and fails if a signature exposes a library type outside these rules.
 */
public final class BridgeTypeContract {

    public static final String LIBRARY_PACKAGE_PREFIX = "net.democracycraft.democracyLib.";

    /**
     * Concrete-valued library types the adapter knows how to re-create on the receiving side.
     * Adding a name here requires a matching branch in {@link BridgeValueAdapter}; the surface
     * contract test asserts both stay in sync.
     */
    public static final Set<String> VALUE_TYPE_FQNS = Set.of(
            "net.democracycraft.democracyLib.api.data.SkinDto"
    );

    private BridgeTypeContract() {
    }

    public static boolean isLibraryName(@NotNull String className) {
        return className.startsWith(LIBRARY_PACKAGE_PREFIX);
    }

    public static boolean isLibraryType(@Nullable Class<?> type) {
        return type != null && isLibraryName(type.getName());
    }

    public static boolean isValueType(@NotNull Class<?> type) {
        return VALUE_TYPE_FQNS.contains(type.getName());
    }

    /**
     * Whether {@code value} can be held through a reference of type {@code declared}
     * without risking a cross-classloader {@link ClassCastException}.
     */
    public static boolean canHold(@NotNull Class<?> declared, @Nullable Object value) {
        if (value == null) return true;
        if (declared.isPrimitive()) return true;
        try {
            return declared.isInstance(value);
        } catch (LinkageError e) {
            return false;
        }
    }

    /**
     * Whether a declared signature type is allowed on the bridged surface: every library type it
     * mentions (including generic arguments) must be an interface or a registered value type.
     */
    public static boolean isBridgeSafe(@NotNull Type type) {
        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) return isBridgeSafe(clazz.getComponentType());
            if (!isLibraryType(clazz)) return true;
            return clazz.isInterface() || isValueType(clazz);
        }
        if (type instanceof ParameterizedType parameterized) {
            if (!isBridgeSafe(parameterized.getRawType())) return false;
            for (Type argument : parameterized.getActualTypeArguments()) {
                if (!isBridgeSafe(argument)) return false;
            }
            return true;
        }
        if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                if (!isBridgeSafe(bound)) return false;
            }
            for (Type bound : wildcard.getLowerBounds()) {
                if (!isBridgeSafe(bound)) return false;
            }
            return true;
        }
        if (type instanceof TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) {
                if (!isBridgeSafe(bound)) return false;
            }
            return true;
        }
        if (type instanceof GenericArrayType array) {
            return isBridgeSafe(array.getGenericComponentType());
        }
        return false;
    }

    /**
     * Erasure of a declared generic type; type variables and wildcards erase to their first bound.
     */
    public static @NotNull Class<?> erase(@NotNull Type type) {
        if (type instanceof Class<?> clazz) return clazz;
        if (type instanceof ParameterizedType parameterized) return erase(parameterized.getRawType());
        if (type instanceof TypeVariable<?> variable) {
            Type[] bounds = variable.getBounds();
            return bounds.length == 0 ? Object.class : erase(bounds[0]);
        }
        if (type instanceof WildcardType wildcard) {
            Type[] bounds = wildcard.getUpperBounds();
            return bounds.length == 0 ? Object.class : erase(bounds[0]);
        }
        if (type instanceof GenericArrayType array) {
            return erase(array.getGenericComponentType()).arrayType();
        }
        return Object.class;
    }
}

package net.democracycraft.democracyLib.internal.dialog.factory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

final class DialogReflection {

    private static final Logger LOGGER = LoggerFactory.getLogger(DialogReflection.class);

    private DialogReflection() {
    }

    /**
     * Collect all methods from the given class, its superclasses and interfaces.
     * In case of method overrides, the most specific method is kept.
     *
     * @param type class to collect methods from
     * @return map of method keys to methods
     */
    static @NotNull Map<String, Method> collectAllMethods(Class<?> type) {
        LinkedHashMap<String, Method> methods = new LinkedHashMap<>();
        Set<Class<?>> visitedInterfaces = new HashSet<>();

        for (Class<?> clazz = type; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {

            for (Method method : clazz.getDeclaredMethods()) {
                methods.putIfAbsent(methodKey(method), method);
            }

            collectInterfaceMethods(clazz.getInterfaces(), methods, visitedInterfaces);
        }

        return methods;
    }

    /**
     * Collect methods from interfaces using BFS
     * @param interfaces array of interfaces to start from
     * @param methods map to collect methods into
     * @param visited set of already visited interfaces
     */
    private static void collectInterfaceMethods(Class<?> @NotNull [] interfaces, Map<String, Method> methods, Set<Class<?>> visited) {
        if (interfaces.length == 0) return;

        Deque<Class<?>> queue = new ArrayDeque<>(Arrays.asList(interfaces));

        while (!queue.isEmpty()) {
            Class<?> iface = queue.poll();

            if (!visited.add(iface)) continue;

            for (Method method : iface.getDeclaredMethods()) {
                methods.putIfAbsent(methodKey(method), method);
            }

            Collections.addAll(queue, iface.getInterfaces());
        }
    }

    static <AnnotationType extends Annotation> @Nullable AnnotationType findAnnotation(@NotNull Method method, Class<?> concreteType, Class<AnnotationType> annotationType) {
        AnnotationType annotation = method.getAnnotation(annotationType);
        if (annotation != null) return annotation;
        return findAnnotationInSupertypes(method, concreteType, annotationType);
    }

    /**
     * Find annotation in supertypes
     * @param method method to find annotation for
     * @param concreteType concrete class to start searching from
     * @param annType annotation type to find
     * @return found annotation or null
     * @param <AnnotationType> annotation type
     */
    private static <AnnotationType extends Annotation> @Nullable AnnotationType findAnnotationInSupertypes(@NotNull Method method, Class<?> concreteType, Class<AnnotationType> annType) {
        Set<Class<?>> visitedInterfaces = new HashSet<>();

        String name = method.getName();
        Class<?>[] params = method.getParameterTypes();

        Class<?> current = concreteType;

        while (current != null && current != Object.class) {
            try {
                Method declaredMethod = current.getDeclaredMethod(name, params);
                AnnotationType methodAnnotation = declaredMethod.getAnnotation(annType);
                if (methodAnnotation != null) return methodAnnotation;
            } catch (NoSuchMethodException ignored) {
            }

            AnnotationType annotationOnInterfaces = findAnnotationOnInterfaces(
                    current.getInterfaces(), name, params, annType, visitedInterfaces
            );

            if (annotationOnInterfaces != null) return annotationOnInterfaces;

            current = current.getSuperclass();
        }

        return null;
    }

    /**
     * Find annotation on interfaces using BFS
     * @param interfaces array of interfaces to start searching from
     * @param name method name
     * @param params method parameter types
     * @param annType annotation type to find
     * @param visited set of already visited interfaces
     * @return found annotation or null
     * @param <AnnotationType> annotation type
     */
    private static <AnnotationType extends Annotation> @Nullable AnnotationType findAnnotationOnInterfaces(
            Class<?> @NotNull [] interfaces,
            String name,
            Class<?>[] params,
            Class<AnnotationType> annType,
            Set<Class<?>> visited
    ) {
        Queue<Class<?>> queue = new ArrayDeque<>(Arrays.asList(interfaces));

        while (!queue.isEmpty()) {
            Class<?> iface = queue.poll();

            if (!visited.add(iface)) continue;

            try {
                Method method = iface.getDeclaredMethod(name, params);
                AnnotationType annotation = method.getAnnotation(annType);
                if (annotation != null) return annotation;
            } catch (NoSuchMethodException ignored) {
            }

            Collections.addAll(queue, iface.getInterfaces());
        }
        return null;
    }

    private static @NotNull String methodKey(@NotNull Method method) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(method.getName()).append('(');
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) stringBuilder.append(',');
            stringBuilder.append(parameterTypes[i].getName());
        }
        stringBuilder.append(')');
        return stringBuilder.toString();
    }

    static @NotNull String signature(@NotNull Method method) {
        return method.getDeclaringClass().getName() + "#" + method.getName() + "()";
    }

    static @Nullable Field findFieldWithAnnotation(Class<?> type, Class<? extends Annotation> annotationClass) {
        Field found = null;
        for (Class<?> clazz = type; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(annotationClass)) {
                    if (found != null) {
                        throw new IllegalArgumentException("Multiple fields annotated with @" + annotationClass.getSimpleName() + " found in hierarchy of " + type.getName());
                    }
                    found = field;
                }
            }
        }
        return found;
    }

    static <T> T invoke(Object controller, @NotNull Method method, Class<T> expectedType) {
        try {
            // ensure accessible if not already
            if (!method.canAccess(controller)) {
                method.setAccessible(true);
            }
            Object[] args = defaultArgs(method.getParameterTypes());
            Object value = method.invoke(controller, args);
            if (value == null) {
                throw new IllegalStateException("Method returned null: " + signature(method));
            }
            if (!expectedType.isInstance(value)) {
                throw new IllegalStateException("Method returned invalid type. Expected " + expectedType.getName() + " but got " + value.getClass().getName() + ": " + signature(method));
            }
            return expectedType.cast(value);
        } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            String msg = "Failed to invoke " + signature(method);
            LOGGER.error(msg, e);
            throw new IllegalStateException(msg, e);
        }
    }

    private static Object @NotNull [] defaultArgs(Class<?> @NotNull [] parameterTypes) {
        if (parameterTypes.length == 0) return new Object[0];

        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            args[i] = defaultValue(parameterTypes[i]);
        }
        return args;
    }

    private static @Nullable Object defaultValue(@NotNull Class<?> type) {
        if (!type.isPrimitive()) return null;

        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return (char) 0;

        return null;
    }
}

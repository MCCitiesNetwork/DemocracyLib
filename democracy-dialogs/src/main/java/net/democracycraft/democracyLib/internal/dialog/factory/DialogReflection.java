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

    static @NotNull Map<String, Method> collectAllMethods(Class<?> type) {
        LinkedHashMap<String, Method> methods = new LinkedHashMap<>();

        // Class hierarchy
        for (Class<?> clazz = type; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Method method : clazz.getDeclaredMethods()) {
                methods.putIfAbsent(methodKey(method), method);
            }
        }

        // Interfaces
        Deque<Class<?>> queue = new ArrayDeque<>();
        Set<Class<?>> seen = new LinkedHashSet<>();

        queue.add(type);
        while (!queue.isEmpty()) {
            Class<?> current = queue.removeFirst();
            if (!seen.add(current)) continue;

            for (Class<?> iface : current.getInterfaces()) {
                if (seen.add(iface)) {
                    for (Method m : iface.getDeclaredMethods()) {
                        methods.putIfAbsent(methodKey(m), m);
                    }
                    queue.addLast(iface);
                }
            }

            Class<?> superClass = current.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                queue.addLast(superClass);
            }
        }

        return methods;
    }

    static <A extends Annotation> @Nullable A findAnnotation(Method method, Class<?> concreteType, Class<A> annotationType) {
        A annotation = method.getAnnotation(annotationType);
        if (annotation != null) return annotation;
        return findAnnotationInSupertypes(method, concreteType, annotationType);
    }

    private static <A extends Annotation> @Nullable A findAnnotationInSupertypes(Method method, Class<?> concreteType, Class<A> annType) {
        try {
            var name = method.getName();
            var params = method.getParameterTypes();

            for (Class<?> clazz = concreteType; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                // interfaces
                A annotationOnInterfaces = findAnnotationOnInterfaces(clazz.getInterfaces(), name, params, annType);
                if (annotationOnInterfaces != null) return annotationOnInterfaces;

                // superclass declared method
                Class<?> superclass = clazz.getSuperclass();
                if (superclass != null && superclass != Object.class) {
                    try {
                        Method declaredMethod = superclass.getDeclaredMethod(name, params);
                        A methodAnnotation = declaredMethod.getAnnotation(annType);
                        if (methodAnnotation != null) return methodAnnotation;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to resolve inherited annotation {} for method {}#{}", annType.getName(), method.getDeclaringClass().getName(), method.getName(), e);
        }

        return null;
    }

    private static <A extends Annotation> @Nullable A findAnnotationOnInterfaces(Class<?>[] interfaces, String name, Class<?>[] params, Class<A> annType) {
        for (Class<?> iface : interfaces) {
            try {
                Method method = iface.getDeclaredMethod(name, params);
                A annotation = method.getAnnotation(annType);
                if (annotation != null) return annotation;
            } catch (NoSuchMethodException ignored) {
            }

            A deep = findAnnotationOnInterfaces(iface.getInterfaces(), name, params, annType);
            if (deep != null) return deep;
        }
        return null;
    }

    private static String methodKey(Method method) {
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

    static String signature(Method method) {
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

package net.democracycraft.democracyLib.internal.bootstrap;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * Shared helpers for reflection/MethodHandle based bridges.
 */
final class BootstrapReflection {

    private static final String GENERATED_CONTRACT_FQCN = "net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeContract";

    private BootstrapReflection() {
    }

    @NotNull
    static String cacheKey(@NotNull String methodName, Object[] args) {
        int arity = args == null ? 0 : args.length;
        String signature = arity == 0
                ? "()"
                : Arrays.stream(args)
                .map(arg -> arg == null ? "null" : arg.getClass().getName())
                .toList()
                .toString();

        return methodName + "#" + signature;
    }

    static @NotNull String cacheKeyByContractId(@NotNull String contractId) {
        return "contract:" + contractId;
    }

    static @NotNull Object loadGeneratedSpec(@NotNull String contractId) {
        try {
            Class<?> generated = Class.forName(GENERATED_CONTRACT_FQCN, true, BootstrapReflection.class.getClassLoader());
            Class<?> methods = Class.forName(GENERATED_CONTRACT_FQCN + "$Methods", true, BootstrapReflection.class.getClassLoader());
            Method specMethod = methods.getMethod("spec", String.class);
            Object spec = specMethod.invoke(null, contractId);
            if (spec == null) {
                throw new IllegalStateException("No spec found for contract id: " + contractId);
            }
            return spec;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed loading generated bridge contract.", e);
        }
    }

    static @NotNull Method resolveByGeneratedSpec(@NotNull Class<?> targetType,
                                                 @NotNull Object generatedSpec) {
        try {
            Method javaNameMethod = generatedSpec.getClass().getMethod("javaName");
            Method paramTypeFqnsMethod = generatedSpec.getClass().getMethod("paramTypeFqns");
            Method namespaceMethod = generatedSpec.getClass().getMethod("namespace");

            String expectedJavaName = (String) javaNameMethod.invoke(generatedSpec);
            @SuppressWarnings("unchecked")
            List<String> expectedParamTypeNames = (List<String>) paramTypeFqnsMethod.invoke(generatedSpec);
            String namespace = (String) namespaceMethod.invoke(generatedSpec);

            for (Method candidateMethod : targetType.getMethods()) {
                if (!candidateMethod.getName().equals(expectedJavaName)) continue;
                if (candidateMethod.getParameterCount() != expectedParamTypeNames.size()) continue;

                Class<?>[] candidateParamTypes = candidateMethod.getParameterTypes();
                boolean parametersMatch = true;
                for (int parameterIndex = 0; parameterIndex < candidateParamTypes.length; parameterIndex++) {
                    String actualParamTypeName = candidateParamTypes[parameterIndex].getName();
                    if (!actualParamTypeName.equals(expectedParamTypeNames.get(parameterIndex))) {
                        parametersMatch = false;
                        break;
                    }
                }

                if (parametersMatch) {
                    return candidateMethod;
                }
            }

            throw new IllegalStateException(
                    "Could not find method for contract id '" + namespace + "#" + expectedJavaName +
                            "' with params " + expectedParamTypeNames + " on " + targetType.getName());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed resolving method by generated spec.", e);
        }
    }
}

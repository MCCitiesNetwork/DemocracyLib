package net.democracycraft.democracyLib.internal.bootstrap;

import net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeContract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * Shared helpers for reflection/MethodHandle based bridges.
 */
public class DemocracyBootstrapReflection {

    private DemocracyBootstrapReflection() {}

    public static @NotNull String cacheKey(@NotNull String methodName, Object[] args) {
        int arity = args == null ? 0 : args.length;
        String signature = arity == 0
                ? "()"
                : Arrays.stream(args)
                .map(arg -> arg == null ? "null" : arg.getClass().getName())
                .toList()
                .toString();

        return methodName + "#" + signature;
    }

    public static @NotNull String cacheKeyByContractId(@NotNull String contractId) {
        return "contract:" + contractId;
    }

    public static @NotNull GeneratedBridgeContract.Spec loadGeneratedSpec(@NotNull String contractId) {
        GeneratedBridgeContract.Spec spec = GeneratedBridgeContract.Methods.spec(contractId);
        if (spec == null) {
            throw new IllegalStateException("No spec found for contract id: " + contractId);
        }
        return spec;
    }

    public static @NotNull Method resolveByGeneratedSpec(@NotNull Class<?> targetType,
                                                        @NotNull GeneratedBridgeContract.Spec generatedSpec) {
        String expectedJavaName = generatedSpec.javaName();
        List<String> expectedParamTypeNames = generatedSpec.paramTypeFqns();
        String namespace = generatedSpec.namespace();

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
    }
}

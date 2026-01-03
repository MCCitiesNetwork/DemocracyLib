package net.democracycraft.democracyLib.internal.bootstrap.service;

import net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeContract;
import net.democracycraft.democracyLib.api.bootstrap.GeneratedBridgeIds;
import net.democracycraft.democracyLib.api.config.DemocracyConfig;
import net.democracycraft.democracyLib.api.config.DemocracyConfigManager;
import net.democracycraft.democracyLib.internal.bootstrap.DemocracyBootstrapReflection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection bridge for the leader's {@link DemocracyConfigManager}.
 */
final class DemocracyBridgeConfigManager implements DemocracyConfigManager {

    private final Object leaderConfigManager;
    private final Map<String, MethodHandle> methodHandleCache = new ConcurrentHashMap<>();

    DemocracyBridgeConfigManager(@NotNull Object leaderConfigManager) {
        this.leaderConfigManager = Objects.requireNonNull(leaderConfigManager, "leaderConfigManager");
    }

    @Override
    public @NotNull <ConfigType extends DemocracyConfig> ConfigType createConfig(@NotNull Plugin plugin, @NotNull Class<ConfigType> configClass) {
        @SuppressWarnings("unchecked")
        ConfigType cfg = (ConfigType) invokeLeaderByContractId(
                GeneratedBridgeIds.DemocracyConfigManager.createConfig__Plugin__Class,
                new Object[]{plugin, configClass}
        );
        return cfg;
    }

    @Override
    public @NotNull <ConfigType extends DemocracyConfig> ConfigType createConfig(@NotNull Plugin plugin, @NotNull String fileName, @NotNull Class<ConfigType> configClass) {
        @SuppressWarnings("unchecked")
        ConfigType cfg = (ConfigType) invokeLeaderByContractId(
                GeneratedBridgeIds.DemocracyConfigManager.createConfig__Plugin__String__Class,
                new Object[]{plugin, fileName, configClass}
        );
        return cfg;
    }

    private Object invokeLeaderByContractId(@NotNull String contractId, Object[] args) {
        Objects.requireNonNull(contractId, "contractId");

        Object[] actualArgs = args == null ? new Object[0] : args;

        GeneratedBridgeContract.Spec spec = DemocracyBootstrapReflection.loadGeneratedSpec(contractId);
        String key = DemocracyBootstrapReflection.cacheKeyByContractId(contractId);

        MethodHandle methodHandle = methodHandleCache.computeIfAbsent(key, k -> {
            Method targetMethod = DemocracyBootstrapReflection.resolveByGeneratedSpec(leaderConfigManager.getClass(), spec);
            try {
                return MethodHandles.publicLookup().unreflect(targetMethod);
            } catch (IllegalAccessException e) {
                try {
                    targetMethod.setAccessible(true);
                    return MethodHandles.lookup().unreflect(targetMethod);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        try {
            Object[] full = new Object[actualArgs.length + 1];
            full[0] = leaderConfigManager;
            System.arraycopy(actualArgs, 0, full, 1, actualArgs.length);
            return methodHandle.invokeWithArguments(full);
        } catch (Throwable t) {
            throw new RuntimeException("Failed invoking leader config manager contract id: " + contractId, t);
        }
    }
}

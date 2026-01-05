package net.democracycraft.democracyLib.internal.dialog.factory;

import net.democracycraft.democracyLib.api.dialog.factory.DialogConfig;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;

/**
 * JVM-only representation of an annotated dialog controller.
 * <p>
 * This is intentionally Paper-runtime free so it can be asserted in unit tests
 */
public record DialogDefinition(
        @NotNull Class<?> controllerType,
        boolean canCloseWithEscape,
        @NotNull Component title,
        @Nullable DialogConfig config,
        @NotNull List<InputMethod> inputs,
        @NotNull List<ButtonMethod> buttons,
        @NotNull List<ButtonHandlerMethod> handlers
) {

    public record InputMethod(@NotNull String id, int order, @NotNull Method method) {
    }

    public record ButtonMethod(@NotNull String id, int order, @NotNull Method method) {
    }

    public record ButtonHandlerMethod(@NotNull String buttonId, int uses, @NotNull Method method) {
    }
}


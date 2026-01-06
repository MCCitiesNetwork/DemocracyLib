package net.democracycraft.democracyLib.internal.dialog.factory;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.InlinedRegistryBuilderProvider;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogInstancesProvider;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.democracycraft.democracyLib.api.dialog.DialogBody;
import net.democracycraft.democracyLib.api.dialog.DialogButton;
import net.democracycraft.democracyLib.api.dialog.DialogButtonHandler;
import net.democracycraft.democracyLib.api.dialog.DialogConfigProvider;
import net.democracycraft.democracyLib.api.dialog.DialogInput;
import net.democracycraft.democracyLib.api.dialog.factory.DialogConfig;
import net.democracycraft.democracyLib.api.dialog.factory.DialogContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds Paper dialogs from annotated controller objects.
 */
public final class DialogFactoryImp {

    private static final Logger LOGGER = LoggerFactory.getLogger(DialogFactoryImp.class);

    private DialogFactoryImp() {
    }

    /**
     * Extracts a runtime-free definition from a controller instance.
     * <p>
     * This method must not touch Paper registries and is safe to call in unit tests.
     */
    public static @NotNull DialogDefinition parse(@NotNull Object controller) {
        Class<?> type = controller.getClass();

        net.democracycraft.democracyLib.api.dialog.Dialog dialogAnn = type.getAnnotation(net.democracycraft.democracyLib.api.dialog.Dialog.class);
        if (dialogAnn == null) {
            throw new IllegalArgumentException("Controller class is missing @Dialog: " + type.getName());
        }

        List<DialogDefinition.BodyMethod> body = new ArrayList<>();
        List<DialogDefinition.InputMethod> inputs = new ArrayList<>();
        List<DialogDefinition.ButtonMethod> buttons = new ArrayList<>();
        List<DialogDefinition.ButtonHandlerMethod> handlers = new ArrayList<>();

        Map<String, Method> handlerMethodByButtonId = new HashMap<>();

        for (Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(DialogBody.class)) {
                if (!io.papermc.paper.registry.data.dialog.body.DialogBody.class.isAssignableFrom(method.getReturnType())) {
                    logAndThrow("@DialogBody can only be used on methods returning Paper DialogBody (or subclass): " + sig(method));
                }
                DialogBody dialogBodyAnnotation = method.getAnnotation(DialogBody.class);
                method.setAccessible(true);
                body.add(new DialogDefinition.BodyMethod(dialogBodyAnnotation.id(), dialogBodyAnnotation.order(), method));
            }

            if (method.isAnnotationPresent(DialogInput.class)) {
                if (!io.papermc.paper.registry.data.dialog.input.DialogInput.class.isAssignableFrom(method.getReturnType())) {
                    logAndThrow("@DialogInput can only be used on methods returning Paper DialogInput (or subclass): " + sig(method));
                }
                DialogInput inputAnnotation = method.getAnnotation(DialogInput.class);
                method.setAccessible(true);
                inputs.add(new DialogDefinition.InputMethod(inputAnnotation.id(), inputAnnotation.order(), method));
            }

            if (method.isAnnotationPresent(DialogButton.class)) {
                if (!ActionButton.class.isAssignableFrom(method.getReturnType())) {
                    logAndThrow("@DialogButton can only be used on methods returning ActionButton (or subclass): " + sig(method));
                }
                DialogButton dialogButtonAnnotation = method.getAnnotation(DialogButton.class);
                method.setAccessible(true);
                buttons.add(new DialogDefinition.ButtonMethod(dialogButtonAnnotation.id(), dialogButtonAnnotation.order(), method));
            }

            if (method.isAnnotationPresent(DialogButtonHandler.class)) {
                DialogButtonHandler dialogButtonHandlerAnnotation = method.getAnnotation(DialogButtonHandler.class);
                validateHandlerSignature(method);
                method.setAccessible(true);

                Method previous = handlerMethodByButtonId.put(dialogButtonHandlerAnnotation.buttonId(), method);
                if (previous != null) {
                    logAndThrow("Duplicate @DialogButtonHandler for buttonId='" + dialogButtonHandlerAnnotation.buttonId() + "': " + sig(previous) + " and " + sig(method));
                }

                handlers.add(new DialogDefinition.ButtonHandlerMethod(
                        dialogButtonHandlerAnnotation.buttonId(),
                        dialogButtonHandlerAnnotation.uses(),
                        method
                ));
            }
        }

        body.sort(Comparator.comparingInt(DialogDefinition.BodyMethod::order));
        inputs.sort(Comparator.comparingInt(DialogDefinition.InputMethod::order));
        buttons.sort(Comparator.comparingInt(DialogDefinition.ButtonMethod::order));

        DialogConfig dialogConfig = resolveDialogConfig(controller);
        Component title = resolveTitle(controller);

        return new DialogDefinition(
                type,
                dialogAnn.canBeClosedWithEscape(),
                title,
                dialogConfig,
                List.copyOf(body),
                List.copyOf(inputs),
                List.copyOf(buttons),
                List.copyOf(handlers)
        );
    }

    public static @NotNull Dialog create(@NotNull Object controller) {
        DialogDefinition definition = parse(controller);

        // Build base
        DialogBase.Builder base = DialogBase.builder(definition.title())
                .canCloseWithEscape(definition.canCloseWithEscape())
                .pause(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE);

        if (!definition.body().isEmpty()) {
            List<io.papermc.paper.registry.data.dialog.body.DialogBody> builtBody = new ArrayList<>();
            for (DialogDefinition.BodyMethod bm : definition.body()) {
                builtBody.add(invoke(controller, bm.method(), io.papermc.paper.registry.data.dialog.body.DialogBody.class));
            }
            base.body(builtBody);
        }

        // Inputs first (Minecraft UI: inputs render at the top of the input section)
        if (!definition.inputs().isEmpty()) {
            List<io.papermc.paper.registry.data.dialog.input.DialogInput> builtInputs = new ArrayList<>();
            for (DialogDefinition.InputMethod im : definition.inputs()) {
                builtInputs.add(invoke(controller, im.method(), io.papermc.paper.registry.data.dialog.input.DialogInput.class));
            }
            base.inputs(builtInputs);
        }

        Map<String, DialogDefinition.ButtonHandlerMethod> handlerByButtonId = new HashMap<>();
        for (DialogDefinition.ButtonHandlerMethod hm : definition.handlers()) {
            DialogDefinition.ButtonHandlerMethod previous = handlerByButtonId.put(hm.buttonId(), hm);
            if (previous != null) {
                // Should already be caught by parse(), but keep protections in create()
                logAndThrow("Duplicate handler for buttonId='" + hm.buttonId() + "'");
            }
        }

        List<ActionButton> builtButtons = new ArrayList<>();
        for (DialogDefinition.ButtonMethod buttonMethod : definition.buttons()) {
            ActionButton rawButton = invoke(controller, buttonMethod.method(), ActionButton.class);
            DialogDefinition.ButtonHandlerMethod handlerMethod = handlerByButtonId.get(buttonMethod.id());

            if (handlerMethod != null) {
                Method handler = handlerMethod.method();

                DialogActionCallback callback = (response, audience) -> {
                    DialogContext ctx = new DialogContextImp(audience, response);
                    try {
                        handler.invoke(controller, ctx);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        LOGGER.error("Failed to execute button handler {}", sig(handler), e);
                    }
                };

                int uses = handlerMethod.uses();
                if (uses < 0) {
                    LOGGER.warn("Invalid uses={} for buttonId='{}' on handler {}. Falling back to uses=1.", uses, buttonMethod.id(), sig(handler));
                    uses = 1;
                }

                DialogAction action = DialogInstancesProvider.instance().register(
                        callback,
                        ClickCallback.Options.builder().uses(uses).build()
                );

                ActionButton replaced = ActionButton.builder(rawButton.label())
                        .tooltip(rawButton.tooltip())
                        .action(action)
                        .build();
                builtButtons.add(replaced);
            } else {
                builtButtons.add(rawButton);
            }
        }

        return InlinedRegistryBuilderProvider.instance().createDialog(factory -> {
            var builder = factory.empty();
            builder.base(base.build());
            if (!builtButtons.isEmpty()) {
                builder.type(DialogType.multiAction(builtButtons).build());
            } else {
                builder.type(DialogInstancesProvider.instance().notice());
            }
        });
    }

    @Contract("_ -> new")
    private static @NotNull Component resolveTitle(@NotNull Object controller) {
        DialogConfig dialogConfig = resolveDialogConfig(controller);
        if (dialogConfig != null && dialogConfig.title() != null) {
            return dialogConfig.title();
        }
        return Component.text(controller.getClass().getSimpleName());
    }

    /**
     * Returns the DialogConfig provided by the controller (if any)
     */
    private static @Nullable DialogConfig resolveDialogConfig(@NotNull Object controller) {
        Field configField = null;
        for (Field field : controller.getClass().getDeclaredFields()) {
            if (!field.isAnnotationPresent(DialogConfigProvider.class)) continue;

            if (configField != null) {
                logAndThrow("Multiple @DialogConfigProvider fields found in: " + controller.getClass().getName());
            }

            if (!DialogConfig.class.isAssignableFrom(field.getType())) {
                logAndThrow("@DialogConfigProvider field must be assignable to DialogConfig: " + controller.getClass().getName() + "#" + field.getName());
            }

            configField = field;
        }

        if (configField == null) return null;

        try {
            configField.setAccessible(true);
            Object value = configField.get(controller);
            if (value == null) {
                LOGGER.error("@DialogConfigProvider field is null: {}#{}", controller.getClass().getName(), configField.getName());
                return null;
            }
            return (DialogConfig) value;
        } catch (IllegalAccessException e) {
            LOGGER.error("Failed to read @DialogConfigProvider field: {}#{}", controller.getClass().getName(), configField.getName(), e);
            return null;
        }
    }

    private static void validateHandlerSignature(@NotNull Method m) {
        if (m.getReturnType() != void.class) {
            logAndThrow("@DialogButtonHandler method must be void: " + sig(m));
        }
        if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != DialogContext.class) {
            logAndThrow("@DialogButtonHandler method must accept exactly one parameter of type DialogContext: " + sig(m));
        }
    }

    private static <T> T invoke(Object controller, @NotNull Method method, Class<T> expectedType) {
        try {
            Object[] args = defaultArgs(method.getParameterTypes());
            Object value = method.invoke(controller, args);
            if (value == null) {
                throw new IllegalStateException("Method returned null: " + sig(method));
            }
            if (!expectedType.isInstance(value)) {
                throw new IllegalStateException("Method returned invalid type. Expected " + expectedType.getName() + " but got " + value.getClass().getName() + ": " + sig(method));
            }
            return expectedType.cast(value);
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOGGER.error("Failed to invoke {}", sig(method), e);
            throw new IllegalStateException("Failed to invoke: " + sig(method), e);
        }
    }

    private static Object[] defaultArgs(Class<?>[] parameterTypes) {
        if (parameterTypes.length == 0) return new Object[0];

        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            args[i] = defaultValue(parameterTypes[i]);
        }
        return args;
    }

    private static Object defaultValue(Class<?> type) {
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

    private static void logAndThrow(String message) {
        LOGGER.error(message);
        throw new IllegalArgumentException(message);
    }

    private static @NotNull String sig(@NotNull Method method) {
        return method.getDeclaringClass().getName() + "#" + method.getName() + "()";
    }
}

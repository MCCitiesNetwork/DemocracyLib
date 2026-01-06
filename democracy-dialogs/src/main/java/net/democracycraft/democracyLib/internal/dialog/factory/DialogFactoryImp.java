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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.democracycraft.democracyLib.internal.dialog.factory.DialogReflection.invoke;
import static net.democracycraft.democracyLib.internal.dialog.factory.DialogReflection.signature;

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

        for (Method method : DialogReflection.collectAllMethods(type).values()) {
            DialogBody dialogBodyAnnotation = DialogReflection.findAnnotation(method, type, DialogBody.class);
            if (dialogBodyAnnotation != null) {
                if (!io.papermc.paper.registry.data.dialog.body.DialogBody.class.isAssignableFrom(method.getReturnType())) {
                    logAndThrow("@DialogBody can only be used on methods returning Paper DialogBody (or subclass): " + signature(method));
                }
                method.setAccessible(true);
                body.add(new DialogDefinition.BodyMethod(dialogBodyAnnotation.id(), dialogBodyAnnotation.order(), method));
            }

            DialogInput inputAnnotation = DialogReflection.findAnnotation(method, type, DialogInput.class);
            if (inputAnnotation != null) {
                if (!io.papermc.paper.registry.data.dialog.input.DialogInput.class.isAssignableFrom(method.getReturnType())) {
                    logAndThrow("@DialogInput can only be used on methods returning Paper DialogInput (or subclass): " + signature(method));
                }
                method.setAccessible(true);
                inputs.add(new DialogDefinition.InputMethod(inputAnnotation.id(), inputAnnotation.order(), method));
            }

            DialogButton dialogButtonAnnotation = DialogReflection.findAnnotation(method, type, DialogButton.class);
            if (dialogButtonAnnotation != null) {
                if (!ActionButton.class.isAssignableFrom(method.getReturnType())) {
                    logAndThrow("@DialogButton can only be used on methods returning ActionButton (or subclass): " + signature(method));
                }
                method.setAccessible(true);
                buttons.add(new DialogDefinition.ButtonMethod(dialogButtonAnnotation.id(), dialogButtonAnnotation.order(), method));
            }

            DialogButtonHandler handlerAnnotation = DialogReflection.findAnnotation(method, type, DialogButtonHandler.class);
            if (handlerAnnotation != null) {
                validateHandlerSignature(method);
                method.setAccessible(true);

                Method previous = handlerMethodByButtonId.put(handlerAnnotation.buttonId(), method);
                if (previous != null) {
                    logAndThrow("Duplicate @DialogButtonHandler for buttonId='" + handlerAnnotation.buttonId() + "': " + signature(previous) + " and " + signature(method));
                }

                handlers.add(new DialogDefinition.ButtonHandlerMethod(
                        handlerAnnotation.buttonId(),
                        handlerAnnotation.uses(),
                        method
                ));
            }
        }

        validateUniqueIds(body, DialogDefinition.BodyMethod::id, "@DialogBody");
        validateUniqueIds(inputs, DialogDefinition.InputMethod::id, "@DialogInput");
        validateUniqueIds(buttons, DialogDefinition.ButtonMethod::id, "@DialogButton");

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
                        LOGGER.error("Failed to execute button handler {}", signature(handler), e);
                    }
                };

                int uses = handlerMethod.uses();
                if (uses < 0) {
                    LOGGER.warn("Invalid uses={} for buttonId='{}' on handler {}. Falling back to uses=1.", uses, buttonMethod.id(), signature(handler));
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
        Field configField = DialogReflection.findSingleFieldWithAnnotation(controller.getClass(), DialogConfigProvider.class);

        if (configField == null) return null;

        if (!DialogConfig.class.isAssignableFrom(configField.getType())) {
            logAndThrow("@DialogConfigProvider field must be assignable to DialogConfig (did you forget 'extends DialogConfig' in your generic?): " + configField.getDeclaringClass().getName() + "#" + configField.getName());
        }

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
            logAndThrow("@DialogButtonHandler method must be void: " + signature(m));
        }
        if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != DialogContext.class) {
            logAndThrow("@DialogButtonHandler method must accept exactly one parameter of type DialogContext: " + signature(m));
        }
    }

    private static <T> void validateUniqueIds(List<T> items, java.util.function.Function<T, String> idExtractor, String source) {
        Set<String> ids = new LinkedHashSet<>();
        for (T item : items) {
            String id = idExtractor.apply(item);
            if (!ids.add(id)) {
                logAndThrow("Duplicate id '" + id + "' found for " + source);
            }
        }
    }

    private static void logAndThrow(String message) {
        LOGGER.error(message);
        throw new IllegalArgumentException(message);
    }
}

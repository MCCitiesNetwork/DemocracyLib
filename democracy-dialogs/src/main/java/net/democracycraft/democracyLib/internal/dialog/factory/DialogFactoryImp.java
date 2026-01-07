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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

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
                if (!isValidReturnType(method, io.papermc.paper.registry.data.dialog.body.DialogBody.class)) {
                    logAndThrow("@DialogBody method must return DialogBody (or subclass) or List<DialogBody>: " + signature(method));
                }
                method.setAccessible(true);
                body.add(new DialogDefinition.BodyMethod(dialogBodyAnnotation.id(), dialogBodyAnnotation.order(), method));
            }

            DialogInput inputAnnotation = DialogReflection.findAnnotation(method, type, DialogInput.class);
            if (inputAnnotation != null) {
                if (!isValidReturnType(method, io.papermc.paper.registry.data.dialog.input.DialogInput.class)) {
                    logAndThrow("@DialogInput method must return DialogInput (or subclass) or List<DialogInput>: " + signature(method));
                }
                method.setAccessible(true);
                inputs.add(new DialogDefinition.InputMethod(inputAnnotation.id(), inputAnnotation.order(), method));
            }

            DialogButton dialogButtonAnnotation = DialogReflection.findAnnotation(method, type, DialogButton.class);
            if (dialogButtonAnnotation != null) {
                // Allowed: ActionButton.Builder, ActionButton, List<ActionButton>
                // Disallowed: List<ActionButton.Builder> because button lists should already come with their callbacks
                boolean isBuilder = ActionButton.Builder.class.isAssignableFrom(method.getReturnType());
                boolean isButton = ActionButton.class.isAssignableFrom(method.getReturnType());
                boolean isButtonList = isGenericListType(method, ActionButton.class);

                if (!isBuilder && !isButton && !isButtonList) {
                    logAndThrow("@DialogButton method must return ActionButton.Builder, ActionButton, or List<ActionButton> (List<Builder> is not supported): " + signature(method));
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

        // resolve order collisions across class hierarchy
        rebaseOrders(type, body, DialogDefinition.BodyMethod::method, DialogDefinition.BodyMethod::order, DialogDefinition.BodyMethod::withOrder, DialogBody.class);
        rebaseOrders(type, inputs, DialogDefinition.InputMethod::method, DialogDefinition.InputMethod::order, DialogDefinition.InputMethod::withOrder, DialogInput.class);
        rebaseOrders(type, buttons, DialogDefinition.ButtonMethod::method, DialogDefinition.ButtonMethod::order, DialogDefinition.ButtonMethod::withOrder, DialogButton.class);

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
                Object result = invoke(controller, bm.method(), Object.class);
                switch (result) {
                    case null -> {
                        // Conditional rendering (no rendering if null)
                    }
                    case io.papermc.paper.registry.data.dialog.body.DialogBody b -> builtBody.add(b);
                    case Collection<?> list -> {
                        for (Object item : list) {
                            if (item instanceof io.papermc.paper.registry.data.dialog.body.DialogBody b) {
                                builtBody.add(b);
                            }
                        }
                    }
                    default -> {
                    }
                }

            }
            base.body(builtBody);
        }

        // Inputs first (Minecraft UI: inputs render at the top of the input section)
        if (!definition.inputs().isEmpty()) {
            List<io.papermc.paper.registry.data.dialog.input.DialogInput> builtInputs = new ArrayList<>();
            for (DialogDefinition.InputMethod im : definition.inputs()) {
                Object result = invoke(controller, im.method(), Object.class);
                if (result == null) continue; // Conditional rendering

                if (result instanceof io.papermc.paper.registry.data.dialog.input.DialogInput i) {
                    builtInputs.add(i);
                } else if (result instanceof Collection<?> list) {
                    for (Object item : list) {
                        if (item instanceof io.papermc.paper.registry.data.dialog.input.DialogInput i) {
                            builtInputs.add(i);
                        }
                    }
                }
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
            DialogDefinition.ButtonHandlerMethod handlerMethod = handlerByButtonId.get(buttonMethod.id());

            Object result = invoke(controller, buttonMethod.method(), Object.class);
            if (result == null) continue; // Conditional rendering

            if (result instanceof Collection<?> list) {
                if (handlerMethod != null) {
                    LOGGER.warn("Handler defined for buttonId='{}' but method returned a List. Handlers are only supported for single ActionButton.Builder. The handler will be ignored.", buttonMethod.id());
                }
                for (Object item : list) {
                    if (item instanceof ActionButton b) {
                        builtButtons.add(b);
                    } else {
                         LOGGER.warn("Method {} returned a collection containing non-ActionButton: {}", signature(buttonMethod.method()), item != null ? item.getClass().getName() : "null");
                    }
                }
            } else if (result instanceof ActionButton button) {
                if (handlerMethod != null) {
                    LOGGER.warn("Handler defined for buttonId='{}' but method returned a pre-built ActionButton. Handlers are only supported for ActionButton.Builder. The handler will be ignored.", buttonMethod.id());
                }
                builtButtons.add(button);
            } else if (result instanceof ActionButton.Builder builder) {
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
                    builder.action(action);
                }
                builtButtons.add(builder.build());
            } else {
               throw new IllegalStateException("Method " + signature(buttonMethod.method()) + " returned unexpected type: " + result.getClass().getName());
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
        Field configField = DialogReflection.findFieldWithAnnotation(controller.getClass(), DialogConfigProvider.class);

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

    private static void validateHandlerSignature(@NotNull Method method) {
        if (method.getReturnType() != void.class) {
            logAndThrow("@DialogButtonHandler method must be void: " + signature(method));
        }
        if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != DialogContext.class) {
            logAndThrow("@DialogButtonHandler method must accept exactly one parameter of type DialogContext: " + signature(method));
        }
    }

    private static <T> void validateUniqueIds(@NotNull List<T> items, Function<T, String> idExtractor, String source) {
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

    private static int classDepthFromConcrete(@NotNull Class<?> concrete, @NotNull Class<?> declaring) {
        int depth = 0;
        for (Class<?> clazz = concrete; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            if (clazz == declaring) return depth;
            depth++;
        }
        // Interfaces: treat as farthest (lowest priority) to avoid stealing orders from concrete classes.
        return Integer.MAX_VALUE;
    }

    private static <T> void rebaseOrders(
            @NotNull Class<?> concreteType,
            @NotNull List<T> items,
            @NotNull Function<T, Method> methodExtractor,
            @NotNull Function<T, Integer> orderExtractor,
            @NotNull java.util.function.BiFunction<T, Integer, T> withOrder,
            @NotNull Class<? extends java.lang.annotation.Annotation> annotationType
    ) {
        if (items.size() <= 1) return;

        // Sort by: most-derived first; within same declaring class, use the declared order, then signature.
        items.sort((a, b) -> {
            Method ma = methodExtractor.apply(a);
            Method mb = methodExtractor.apply(b);

            Method annotatedMa = DialogReflection.findAnnotatedMethod(ma, concreteType, annotationType);
            Method annotatedMb = DialogReflection.findAnnotatedMethod(mb, concreteType, annotationType);

            Class<?> declA = annotatedMa != null ? annotatedMa.getDeclaringClass() : ma.getDeclaringClass();
            Class<?> declB = annotatedMb != null ? annotatedMb.getDeclaringClass() : mb.getDeclaringClass();

            int da = classDepthFromConcrete(concreteType, declA);
            int db = classDepthFromConcrete(concreteType, declB);

            if (da != db) return Integer.compare(da, db);
            int oa = orderExtractor.apply(a);
            int ob = orderExtractor.apply(b);
            if (oa != ob) return Integer.compare(oa, ob);
            return DialogReflection.signature(ma).compareTo(DialogReflection.signature(mb));
        });

        TreeSet<Integer> used = new TreeSet<>();

        for (int i = 0; i < items.size(); i++) {
            T item = items.get(i);
            int desired = orderExtractor.apply(item);
            int assigned = desired;
            while (used.contains(assigned)) {
                assigned++;
            }
            used.add(assigned);
            if (assigned != desired) {
                items.set(i, withOrder.apply(item, assigned));
            }
        }
    }

    private static boolean isValidReturnType(Method method, Class<?> targetType) {
        if (targetType.isAssignableFrom(method.getReturnType())) {
            return true;
        }
        return isGenericListType(method, targetType);
    }

    private static boolean isGenericListType(Method method, Class<?> componentType) {
        if (!List.class.isAssignableFrom(method.getReturnType())) {
            return false;
        }
        Type genericReturnType = method.getGenericReturnType();
        if (genericReturnType instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length == 1 && args[0] instanceof Class<?> genericClass) {
                return componentType.isAssignableFrom(genericClass);
            }
        }
        return false;
    }
}

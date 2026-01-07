package net.democracycraft.democracyLib.internal.dialog.factory;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody;
import net.democracycraft.democracyLib.api.dialog.*;
import net.democracycraft.democracyLib.api.dialog.factory.DialogContext;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests focusing on inheritance, annotation resolution across hierarchy, and order collision shifting.
 */
class DialogFactoryHierarchyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DialogFactoryHierarchyTest.class);

    @BeforeEach
    void setup(@NotNull TestInfo info) {
        LOGGER.info(">>> STARTING TEST: {}", info.getDisplayName());
    }

    @AfterEach
    void tearDown(@NotNull TestInfo info) {
        LOGGER.info("<<< FINISHED TEST: {}", info.getDisplayName());
    }

    @Test
    void shouldResolveAnnotations_fromInterfaceToConcreteClass() {
        @SuppressWarnings("unused")
        interface I {
            @DialogButton(id = "i-btn", order = 0)
            ActionButton.Builder button();

            @DialogButtonHandler(buttonId = "i-btn")
            void onClick(DialogContext ctx);
        }

        @Dialog(canBeClosedWithEscape = false)
        final class Controller implements I {
            @Contract(" -> new")
            @Override
            public ActionButton.@NotNull Builder button() {
                return ActionButton.builder(Component.text("I"));
            }

            @Override
            public void onClick(DialogContext ctx) {
                // no-op
            }
        }

        DialogDefinition def = DialogFactoryImp.parse(new Controller());
        logDefinitionDetails(def);

        assertEquals(1, def.buttons().size());
        assertEquals("i-btn", def.buttons().getFirst().id());
        assertEquals(1, def.handlers().size());
        assertEquals("i-btn", def.handlers().getFirst().buttonId());
    }

    @Test
    void shouldResolveAnnotations_fromAbstractClassToConcreteClass() {
        abstract class Base {
            @DialogBody(id = "abs-body", order = 0)
            abstract io.papermc.paper.registry.data.dialog.body.DialogBody body();

            @DialogButton(id = "abs-btn", order = 0)
            abstract ActionButton.Builder button();

            @DialogButtonHandler(buttonId = "abs-btn")
            public void onClick(DialogContext ctx) {
                // no-op
            }
        }

        @Dialog(canBeClosedWithEscape = false)
        final class Controller extends Base {
            @Contract(" -> new")
            @Override
            public @NotNull PlainMessageDialogBody body() {
                return io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(Component.text("Hello"));
            }

            @Contract(" -> new")
            @Override
            public ActionButton.@NotNull Builder button() {
                return ActionButton.builder(Component.text("A"));
            }
        }

        DialogDefinition def = DialogFactoryImp.parse(new Controller());
        logDefinitionDetails(def);

        assertEquals(1, def.body().size());
        assertEquals("abs-body", def.body().getFirst().id());

        assertEquals(1, def.buttons().size());
        assertEquals("abs-btn", def.buttons().getFirst().id());

        assertEquals(1, def.handlers().size());
        assertEquals("abs-btn", def.handlers().getFirst().buttonId());
    }

    @Test
    void shouldResolveAnnotations_fromInterfaceToAbstractToConcreteClass() {
        @SuppressWarnings("unused")
        interface I {
            @DialogInput(id = "i-in", order = 0)
            io.papermc.paper.registry.data.dialog.input.DialogInput input();
        }

        abstract class Base implements I {
            @Override
            public io.papermc.paper.registry.data.dialog.input.DialogInput input() {
                return null;
            }
        }

        @Dialog
        final class Controller extends Base {
        }

        DialogDefinition def = DialogFactoryImp.parse(new Controller());
        logDefinitionDetails(def);

        assertEquals(1, def.inputs().size());
        assertEquals("i-in", def.inputs().getFirst().id());
    }

    @Test
    void shouldShiftAbstractOrders_whenConcreteUsesSameOrder() {
        LOGGER.info("Testing Order Collision: Concrete vs Abstract (Both declared order=2)");

        abstract class Base {
            @DialogBody(id = "base-body", order = 2)
            abstract io.papermc.paper.registry.data.dialog.body.DialogBody baseBody();
        }

        @Dialog
        final class Controller extends Base {
            @Contract(" -> new")
            @DialogBody(id = "concrete-body", order = 2)
            public @NotNull PlainMessageDialogBody concreteBody() {
                return io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(Component.text("Concrete"));
            }

            @Contract(" -> new")
            @Override
            public @NotNull PlainMessageDialogBody baseBody() {
                return io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(Component.text("Base"));
            }
        }

        DialogDefinition def = DialogFactoryImp.parse(new Controller());
        logDefinitionDetails(def);

        assertEquals(2, def.body().size());

        // Concrete wins order 2
        assertEquals("concrete-body", def.body().getFirst().id());
        assertEquals(2, def.body().getFirst().order());

        // Base gets shifted to 3
        assertEquals("base-body", def.body().get(1).id());
        assertEquals(3, def.body().get(1).order());
    }

    @Test
    void shouldPreserveOrderWhenMixingListsAndSingles() {
        LOGGER.info("Testing Order Stability with Lists");

        @Dialog(canBeClosedWithEscape = true)
        class MixedOrderController {
            @Contract(" -> new")
            @DialogBody(id = "body-1", order = 1)
            public @NotNull PlainMessageDialogBody body1() {
                return io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(Component.text("1"));
            }

            @Contract(" -> new")
            @DialogBody(id = "body-2", order = 2)
            public @NotNull @Unmodifiable List<PlainMessageDialogBody> body2() {
                return List.of(
                        io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(Component.text("2a")),
                        io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(Component.text("2b"))
                );
            }

            @Contract(" -> new")
            @DialogBody(id = "body-3", order = 3)
            public @NotNull PlainMessageDialogBody body3() {
                return io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(Component.text("3"));
            }
        }

        DialogDefinition def = DialogFactoryImp.parse(new MixedOrderController());
        logDefinitionDetails(def);

        assertEquals(3, def.body().size());

        // Order 1
        assertEquals("body-1", def.body().getFirst().id());
        assertEquals(1, def.body().getFirst().order());

        // Order 2 (returns list)
        assertEquals("body-2", def.body().get(1).id());
        assertEquals(2, def.body().get(1).order());

        // Order 3
        assertEquals("body-3", def.body().get(2).id());
        assertEquals(3, def.body().get(2).order());
    }

    private void logDefinitionDetails(@NotNull DialogDefinition def) {
        LOGGER.info("Parsed Definition for {}:", def.controllerType().getSimpleName());
        LOGGER.info("  Bodies ({}) :", def.body().size());
        def.body().forEach(b -> LOGGER.info("    - ID: {}, Order: {}, Method: {}", b.id(), b.order(), b.method().getName()));

        LOGGER.info("  Inputs ({}) :", def.inputs().size());
        def.inputs().forEach(i -> LOGGER.info("    - ID: {}, Order: {}, Method: {}", i.id(), i.order(), i.method().getName()));

        LOGGER.info("  Buttons ({}):", def.buttons().size());
        def.buttons().forEach(b -> LOGGER.info("    - ID: {}, Order: {}, Method: {}", b.id(), b.order(), b.method().getName()));
    }
}


package net.democracycraft.democracyLib.internal.dialog.factory;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import net.democracycraft.democracyLib.api.dialog.*;
import net.democracycraft.democracyLib.api.dialog.factory.DialogConfig;
import net.democracycraft.democracyLib.api.dialog.factory.DialogContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.junit.jupiter.api.Assertions.*;

class DialogFactoryImpTest {

    private static final class TestDialogConfig implements DialogConfig {
        @Contract(value = " -> new", pure = true)
        @Override
        public @NotNull Component title() {
            return Component.text("Test Title");
        }
    }

    @Dialog(canBeClosedWithEscape = true)
    private static final class TestController {

        @DialogConfigProvider
        private final TestDialogConfig config = new TestDialogConfig();

        @Contract(" -> new")
        @DialogButton(id = "ok", order = 0)
        public @NotNull ActionButton okButton() {
            // For unit tests we only need a valid button instance; use a static action.
            return ActionButton.builder(Component.text("OK"))
                    .action(DialogAction.commandTemplate("help"))
                    .build();
        }

        @DialogBody(id = "body-1", order = 0)
        public io.papermc.paper.registry.data.dialog.body.DialogBody body() {
            return io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(Component.text("Hello"));
        }

        @DialogButtonHandler(buttonId = "ok", uses = 1)
        public void onOk(DialogContext ctx) {
            assertNotNull(ctx);
            assertNotNull(ctx.getAudience());
            assertNotNull(ctx.dialogResponseView());
        }
    }

    @AfterEach
    void logTestPassed(TestInfo testInfo) {
        System.out.println("[TEST PASS] " + testInfo.getDisplayName());
    }

    @AfterAll
    static void logClassPassed() {
        System.out.println("[TEST PASS] DialogFactoryImpTest");
    }

    @Test
    void parse_shouldExtractDefinitionFromAnnotations() {
        DialogDefinition def = DialogFactoryImp.parse(new TestController());

        assertEquals(TestController.class, def.controllerType());
        assertTrue(def.canCloseWithEscape());

        assertEquals("Test Title", PlainTextComponentSerializer.plainText().serialize(def.title()));

        assertEquals(1, def.buttons().size());
        assertEquals("ok", def.buttons().getFirst().id());

        assertEquals(1, def.body().size());
        assertEquals("body-1", def.body().getFirst().id());

        assertEquals(1, def.handlers().size());
        DialogDefinition.ButtonHandlerMethod handler = def.handlers().getFirst();
        assertEquals("ok", handler.buttonId());
        assertEquals(1, handler.uses());
    }

    @Test
    void parse_shouldRejectMissingDialogAnnotation() {
        class MissingDialogAnnotationController {
        }

        assertThrows(IllegalArgumentException.class, () -> DialogFactoryImp.parse(new MissingDialogAnnotationController()));
    }

    /**
     * Interface method is annotated, concrete class implements it without repeating the annotation.
     */
    @Test
    void parse_shouldResolveAnnotations_fromInterfaceToConcreteClass() {
        @SuppressWarnings("unused")
        interface I {
            @DialogButton(id = "i-btn", order = 0)
            ActionButton button();

            @DialogButtonHandler(buttonId = "i-btn")
            void onClick(DialogContext ctx);
        }

        @Dialog(canBeClosedWithEscape = true)
        final class Controller implements I {
            @Override
            public ActionButton button() {
                return ActionButton.builder(Component.text("I"))
                        .action(DialogAction.commandTemplate("help"))
                        .build();
            }

            @Override
            public void onClick(DialogContext ctx) {
                // no-op
            }
        }

        DialogDefinition def = DialogFactoryImp.parse(new Controller());
        assertEquals(1, def.buttons().size());
        assertEquals("i-btn", def.buttons().getFirst().id());
        assertEquals(1, def.handlers().size());
        assertEquals("i-btn", def.handlers().getFirst().buttonId());
    }

    /**
     * Abstract base declares the annotation, concrete overrides without repeating it.
     */
    @Test
    void parse_shouldResolveAnnotations_fromAbstractClassToConcreteClass() {
        abstract class Base {
            @DialogBody(id = "abs-body", order = 0)
            abstract io.papermc.paper.registry.data.dialog.body.DialogBody body();

            @DialogButton(id = "abs-btn", order = 0)
            abstract ActionButton button();

            @DialogButtonHandler(buttonId = "abs-btn")
            public void onClick(DialogContext ctx) {
                // no-op
            }
        }

        @Dialog(canBeClosedWithEscape = true)
        final class Controller extends Base {
            @Override
            public io.papermc.paper.registry.data.dialog.body.DialogBody body() {
                return io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(Component.text("Hello"));
            }

            @Override
            public ActionButton button() {
                return ActionButton.builder(Component.text("A"))
                        .action(DialogAction.commandTemplate("help"))
                        .build();
            }
        }

        DialogDefinition def = DialogFactoryImp.parse(new Controller());
        assertEquals(1, def.body().size());
        assertEquals("abs-body", def.body().getFirst().id());

        assertEquals(1, def.buttons().size());
        assertEquals("abs-btn", def.buttons().getFirst().id());

        assertEquals(1, def.handlers().size());
        assertEquals("abs-btn", def.handlers().getFirst().buttonId());
    }

    /**
     * Annotation declared on interface, implemented by abstract class, then concrete class.
     */
    @Test
    void parse_shouldResolveAnnotations_fromInterfaceToAbstractToConcreteClass() {
        @SuppressWarnings("unused")
        interface I {
            @DialogInput(id = "i-in", order = 0)
            io.papermc.paper.registry.data.dialog.input.DialogInput input();
        }

        abstract class Base implements I {
            @Override
            public io.papermc.paper.registry.data.dialog.input.DialogInput input() {
                // parse() only inspects the annotation + return type; no runtime instance is required.
                return null;
            }
        }

        @Dialog(canBeClosedWithEscape = true)
        final class Controller extends Base {
        }

        DialogDefinition def = DialogFactoryImp.parse(new Controller());
        assertEquals(1, def.inputs().size());
        assertEquals("i-in", def.inputs().getFirst().id());
    }
}

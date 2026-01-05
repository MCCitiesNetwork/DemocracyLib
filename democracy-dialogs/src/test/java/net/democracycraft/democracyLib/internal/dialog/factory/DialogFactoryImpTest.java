package net.democracycraft.democracyLib.internal.dialog.factory;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import net.democracycraft.democracyLib.api.dialog.Dialog;
import net.democracycraft.democracyLib.api.dialog.DialogButton;
import net.democracycraft.democracyLib.api.dialog.DialogButtonHandler;
import net.democracycraft.democracyLib.api.dialog.DialogConfigProvider;
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

}

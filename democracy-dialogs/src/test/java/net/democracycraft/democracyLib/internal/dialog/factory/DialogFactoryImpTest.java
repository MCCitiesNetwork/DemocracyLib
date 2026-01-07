package net.democracycraft.democracyLib.internal.dialog.factory;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for DialogFactoryImp parsing and validation.
 */
class DialogFactoryImpTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DialogFactoryImpTest.class);

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
        public @NotNull ActionButton.Builder okButton() {
            return ActionButton.builder(Component.text("OK"));
        }

        @Contract(" -> new")
        @DialogBody(id = "body-1", order = 0)
        public @NotNull PlainMessageDialogBody body() {
            return io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(Component.text("Hello"));
        }

        @DialogButtonHandler(buttonId = "ok", uses = 1)
        public void onOk(DialogContext ctx) {
            assertNotNull(ctx);
        }
    }

    @AfterEach
    void logTestPassed(@NotNull TestInfo testInfo) {
        LOGGER.info("[TEST PASS] {}", testInfo.getDisplayName());
    }

    @AfterAll
    static void logClassPassed() {
        LOGGER.info("[ALL TESTS PASSED] DialogFactoryImpTest");
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

    @Test
    void parse_shouldSupportActionButtonBuilder() {
        @Dialog(canBeClosedWithEscape = true)
        class BuilderController {
            @Contract(" -> new")
            @DialogButton(id = "builder-btn", order = 0)
            public ActionButton.@NotNull Builder button() {
                return ActionButton.builder(Component.text("Builder"));
            }

            @DialogButtonHandler(buttonId = "builder-btn")
            public void onClick(DialogContext ctx) {
            }
        }

        DialogDefinition def = DialogFactoryImp.parse(new BuilderController());
        assertEquals(1, def.buttons().size());
        assertEquals("builder-btn", def.buttons().getFirst().id());
        assertEquals(1, def.handlers().size());
        assertEquals("builder-btn", def.handlers().getFirst().buttonId());
    }

    @Test
    void parse_shouldAcceptListReturnTypes() {
        @Dialog(canBeClosedWithEscape = true)
        class ListController {
            @DialogBody(id = "list-body", order = 0)
            public List<io.papermc.paper.registry.data.dialog.body.DialogBody> bodies() {
                return Collections.emptyList();
            }

            @DialogButton(id = "list-btn", order = 0)
            public List<ActionButton> buttons() {
                return Collections.emptyList();
            }

            @DialogInput(id = "list-input", order = 0)
            public List<io.papermc.paper.registry.data.dialog.input.DialogInput> inputs() {
                return Collections.emptyList();
            }
        }

        DialogDefinition def = DialogFactoryImp.parse(new ListController());

        assertEquals(1, def.body().size());
        assertEquals("list-body", def.body().getFirst().id());

        assertEquals(1, def.buttons().size());
        assertEquals("list-btn", def.buttons().getFirst().id());

        assertEquals(1, def.inputs().size());
        assertEquals("list-input", def.inputs().getFirst().id());
    }

    @Test
    void parse_shouldAcceptRawActionButton() {
        @Dialog(canBeClosedWithEscape = true)
        class RawButtonController {
            @DialogButton(id = "raw-btn", order = 0)
            public ActionButton button() {
                return ActionButton.builder(Component.text("Raw")).build();
            }
        }

        DialogDefinition def = DialogFactoryImp.parse(new RawButtonController());
        assertEquals(1, def.buttons().size());
        assertEquals("raw-btn", def.buttons().getFirst().id());
    }

    @Test
    void parse_shouldRejectListOfBuilders() {
        @Dialog(canBeClosedWithEscape = true)
        class InvalidListController {
            @DialogButton(id = "invalid-btn", order = 0)
            public List<ActionButton.Builder> buttons() {
                return Collections.emptyList();
            }
        }

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> DialogFactoryImp.parse(new InvalidListController()));
        assertTrue(ex.getMessage().contains("List<Builder> is not supported"));
    }
}

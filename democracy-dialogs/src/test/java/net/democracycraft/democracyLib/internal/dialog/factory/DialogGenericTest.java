package net.democracycraft.democracyLib.internal.dialog.factory;

import net.democracycraft.democracyLib.api.dialog.Dialog;
import net.democracycraft.democracyLib.api.dialog.DialogConfigProvider;
import net.democracycraft.democracyLib.api.dialog.factory.DialogConfig;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DialogGenericTest {

    static class BaseConfig implements DialogConfig {
        @Override
        public @NotNull Component title() {
            return Component.text("Base");
        }
    }

    static class FinalConfig extends BaseConfig {
        @Override
        public @NotNull Component title() {
            return Component.text("Final");
        }
    }

    // Bounded Generic
    static abstract class BoundedBaseController<C extends DialogConfig> {
        @DialogConfigProvider
        protected final C config;

        BoundedBaseController(C config) {
            this.config = config;
        }
    }

    @Dialog
    static class BoundedConcreteController extends BoundedBaseController<FinalConfig> {
        BoundedConcreteController() {
            super(new FinalConfig());
        }
    }

    // Unbounded Generic (Erasure is Object)
    static abstract class UnboundedBaseController<C> {
        @DialogConfigProvider
        protected final C config;

        UnboundedBaseController(C config) {
            this.config = config;
        }
    }

    @Dialog
    static class UnboundedConcreteController extends UnboundedBaseController<FinalConfig> {
        UnboundedConcreteController() {
            super(new FinalConfig());
        }
    }

    @Test
    void testBoundedGenericWorks() {
        BoundedConcreteController controller = new BoundedConcreteController();
        DialogDefinition def = DialogFactoryImp.parse(controller);
        Assertions.assertNotNull(def.config());
        Assertions.assertTrue(def.config() instanceof FinalConfig);
        Assertions.assertEquals(Component.text("Final"), def.config().title());
    }

    @Test
    void testUnboundedGenericFails() {
        // This is expected to fail because T erases to Object, and Object is not assignable to DialogConfig
        UnboundedConcreteController controller = new UnboundedConcreteController();
        Exception ex = Assertions.assertThrows(IllegalArgumentException.class, () -> DialogFactoryImp.parse(controller));
        System.out.println("Expected error message: " + ex.getMessage());
        Assertions.assertTrue(ex.getMessage().contains("must be assignable to DialogConfig"));
    }
}


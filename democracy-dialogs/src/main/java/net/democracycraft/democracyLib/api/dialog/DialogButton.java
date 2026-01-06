package net.democracycraft.democracyLib.api.dialog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as an action button provider.
 * <p>
 * Any parameters (if present) are ignored by the factory.
 * The annotated method must return {@link io.papermc.paper.registry.data.dialog.ActionButton}
 * (or a subtype).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DialogButton {
    String id();
    int order();
}

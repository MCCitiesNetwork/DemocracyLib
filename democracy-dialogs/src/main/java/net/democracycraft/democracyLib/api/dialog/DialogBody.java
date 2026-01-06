package net.democracycraft.democracyLib.api.dialog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as a dialog body element provider.
 * <p>
 * Any parameters (if present) are ignored by the factory.
 * The annotated method must return {@link io.papermc.paper.registry.data.dialog.body.DialogBody}
 * (or a subtype).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DialogBody {

    String id();

    int order();
}

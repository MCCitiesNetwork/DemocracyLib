package net.democracycraft.democracyLib.api.database.annotations;

import java.lang.annotation.*;

/**
 * Container for repeatable {@link Cascade}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Cascades {
    Cascade[] value();
}


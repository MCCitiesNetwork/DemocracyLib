package net.democracycraft.democracyLib.api.database.annotations;

import java.lang.annotation.*;

/**
 * Container for repeatable {@link Index}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Indexes {
    Index[] value();
}


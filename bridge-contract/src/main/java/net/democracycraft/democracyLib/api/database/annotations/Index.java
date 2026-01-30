package net.democracycraft.democracyLib.api.database.annotations;

import java.lang.annotation.*;

/**
 * Declares an index for a DAO column.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Indexes.class)
public @interface Index {

    /** @return index name (optional). */
    String name() default "";

    /** @return true for unique index. */
    boolean unique() default false;
}


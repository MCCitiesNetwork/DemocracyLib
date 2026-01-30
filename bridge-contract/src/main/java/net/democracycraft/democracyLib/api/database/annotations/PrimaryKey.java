package net.democracycraft.democracyLib.api.database.annotations;

import java.lang.annotation.*;

/**
 * Marks a DAO getter method as the primary key column.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PrimaryKey {

    /** @return true to auto-increment when the type is numeric. */
    boolean autoIncrement() default false;
}


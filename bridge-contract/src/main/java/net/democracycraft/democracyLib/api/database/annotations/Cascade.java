package net.democracycraft.democracyLib.api.database.annotations;

import java.lang.annotation.*;

/**
 * Declares cascade behavior between DAOs at the application layer.
 * <p>
 * This is not a SQL cascade. It can be used by higher-level code to decide when to delete/update
 * dependent rows by invoking other DAOs.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(Cascades.class)
public @interface Cascade {

    /** @return dependent DAO type. */
    Class<?> childDao();

    /** @return column name on child table that references the parent primary key. */
    String childFkColumn();
}


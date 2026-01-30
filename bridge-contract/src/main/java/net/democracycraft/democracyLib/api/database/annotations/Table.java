package net.democracycraft.democracyLib.api.database.annotations;

import java.lang.annotation.*;

/**
 * Declares the physical table name for a DAO-backed table.
 * <p>
 * This annotation marks a DAO interface as a concrete table. The annotation processor will generate:
 * <ul>
 *   <li>An implementation class for the DAO interface</li>
 *   <li>A repository class for CRUD operations</li>
 * </ul>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Table {

    /** @return the table name in MySQL. */
    String value();
}

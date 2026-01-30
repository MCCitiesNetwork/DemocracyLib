package net.democracycraft.democracyLib.api.database;

import net.democracycraft.democracyLib.api.database.annotations.*;

import java.util.UUID;

/**
 * Base DAO interface demonstrating inheritance pattern.
 * <p>
 * This is NOT marked with @Table - it's meant to be inherited.
 * The @Column annotations here will be inherited by child DAOs.
 */
public interface KeyedDao extends Dao {

    @Column(name = "id", type = SqlType.INT)
    @PrimaryKey(autoIncrement = true)
    int getId();
}


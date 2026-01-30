package net.democracycraft.democracyLib.api.database;

import net.democracycraft.democracyLib.api.database.annotations.*;

import java.util.UUID;

/**
 * Example DAO interface demonstrating the ORM annotation system.
 * <p>
 * This interface shows how to define a table with:
 * <ul>
 *   <li>Primary key with auto-increment</li>
 *   <li>Various column types (UUID, String, Integer, etc.)</li>
 *   <li>Indexes</li>
 *   <li>Nullable and non-nullable columns</li>
 * </ul>
 * <p>
 * The annotation processor will generate:
 * <ul>
 *   <li>ExampleUserDaoImpl - implementation class with getters/setters/builder</li>
 *   <li>ExampleUserDaoRepository - CRUD repository extending DaoCrud</li>
 * </ul>
 */
@Table("example_users")
public interface ExampleUserDao extends Dao {

    @Column(name = "id", type = SqlType.INT)
    @PrimaryKey(autoIncrement = true)
    int getId();


    @Column(name = "uuid", type = SqlType.VARCHAR, length = 36, nullable = false)
    @Index(unique = true)
    UUID getUniqueId();

    @Column(name = "username", type = SqlType.VARCHAR, length = 64, nullable = false)
    @Index
    String getUsername();

    @Column(name = "display_name", type = SqlType.VARCHAR, length = 128, nullable = true)
    String getDisplayName();

    @Column(name = "balance", type = SqlType.DOUBLE, nullable = false)
    double getBalance();

    @Column(name = "is_active", type = SqlType.BOOLEAN, nullable = false)
    boolean isActive();

    @Column(name = "created_at", type = SqlType.BIGINT, nullable = false)
    long getCreatedAt();

    @Column(name = "last_login", type = SqlType.BIGINT, nullable = true)
    Long getLastLogin();
}


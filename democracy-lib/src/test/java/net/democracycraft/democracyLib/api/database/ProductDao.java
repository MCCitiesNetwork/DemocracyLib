package net.democracycraft.democracyLib.api.database;

import net.democracycraft.democracyLib.api.database.annotations.*;

import java.util.UUID;

/**
 * Example DAO demonstrating inheritance from KeyedDao.
 * <p>
 * This interface inherits the 'id' column from KeyedDao and adds its own columns.
 * When processed, all columns (inherited + own) will be included in the generated code.
 */
@Table("products")
public interface ProductDao extends KeyedDao {

    @Column(name = "uuid", type = SqlType.VARCHAR, length = 36, nullable = false)
    @Index(unique = true)
    UUID getProductUuid();

    @Column(name = "name", type = SqlType.VARCHAR, length = 128, nullable = false)
    String getName();

    @Column(name = "description", type = SqlType.TEXT, nullable = true)
    String getDescription();

    @Column(name = "price", type = SqlType.DOUBLE, nullable = false)
    double getPrice();

    @Column(name = "stock", type = SqlType.INT, nullable = false)
    int getStock();

    @Column(name = "category", type = SqlType.VARCHAR, length = 64, nullable = true)
    @Index
    String getCategory();
}


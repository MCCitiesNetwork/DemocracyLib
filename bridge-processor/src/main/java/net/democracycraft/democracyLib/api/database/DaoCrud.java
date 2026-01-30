package net.democracycraft.democracyLib.api.database;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * JDBC base implementation for annotated DAO interfaces.
 * <p>
 * This abstract class provides all the SQL generation and execution logic.
 * Generated repository classes will extend this class.
 * <p>
 * <strong>All database operations are asynchronous and return CompletableFuture.</strong>
 * <p>
 * Features:
 * <ul>
 *   <li>Automatic table creation</li>
 *   <li>Async CRUD operations (insertAsync, updateAsync, deleteAsync, findAsync)</li>
 *   <li>Query by column support</li>
 *   <li>Transaction support</li>
 * </ul>
 *
 * @param <T> The DAO interface type
 * @param <K> The primary key type
 */
public abstract class DaoCrud<T, K> {

    protected final MySQLManager mysqlManager;
    protected final Class<T> daoInterfaceClass;
    protected final String tableName;
    protected final List<ColumnMetadata> columnMetadataList;
    protected final ColumnMetadata primaryKeyColumnMetadata;
    protected final Map<String, ColumnMetadata> columnMetadataByName;
    private volatile boolean tableCreated = false;

    /**
     * Metadata for a column derived from DAO method annotations.
     */
    public static class ColumnMetadata {
        public final String columnName;
        public final String sqlTypeDefinition;
        public final boolean isNullable;
        public final boolean isUnique;
        public final boolean isPrimaryKey;
        public final boolean isAutoIncrement;
        public final String defaultExpression;
        public final Method getterMethod;
        public final String getterMethodName;
        public final Class<?> javaType;

        // Foreign key info (optional)
        public final String foreignKeyReferencesTable;
        public final String foreignKeyReferencesColumn;
        public final String foreignKeyOnDeleteAction;
        public final String foreignKeyOnUpdateAction;

        // Index info
        public final List<IndexMetadata> indexMetadataList;

        public ColumnMetadata(String columnName, String sqlTypeDefinition, boolean isNullable, boolean isUnique,
                              boolean isPrimaryKey, boolean isAutoIncrement, String defaultExpression,
                              Method getterMethod, Class<?> javaType,
                              String foreignKeyReferencesTable, String foreignKeyReferencesColumn,
                              String foreignKeyOnDeleteAction, String foreignKeyOnUpdateAction,
                              List<IndexMetadata> indexMetadataList) {
            this.columnName = columnName;
            this.sqlTypeDefinition = sqlTypeDefinition;
            this.isNullable = isNullable;
            this.isUnique = isUnique;
            this.isPrimaryKey = isPrimaryKey;
            this.isAutoIncrement = isAutoIncrement;
            this.defaultExpression = defaultExpression;
            this.getterMethod = getterMethod;
            this.getterMethodName = getterMethod.getName();
            this.javaType = javaType;
            this.foreignKeyReferencesTable = foreignKeyReferencesTable;
            this.foreignKeyReferencesColumn = foreignKeyReferencesColumn;
            this.foreignKeyOnDeleteAction = foreignKeyOnDeleteAction;
            this.foreignKeyOnUpdateAction = foreignKeyOnUpdateAction;
            this.indexMetadataList = indexMetadataList != null ? indexMetadataList : Collections.emptyList();
        }
    }

    /**
     * Metadata for an index on a column.
     */
    public static class IndexMetadata {
        public final String indexName;
        public final boolean isUnique;

        public IndexMetadata(String indexName, boolean isUnique) {
            this.indexName = indexName;
            this.isUnique = isUnique;
        }
    }

    // Legacy aliases for backward compatibility with generated code
    public static class ColumnMeta extends ColumnMetadata {
        public ColumnMeta(String columnName, String sqlTypeDefinition, boolean isNullable, boolean isUnique,
                          boolean isPrimaryKey, boolean isAutoIncrement, String defaultExpression,
                          Method getterMethod, Class<?> javaType,
                          String foreignKeyReferencesTable, String foreignKeyReferencesColumn,
                          String foreignKeyOnDeleteAction, String foreignKeyOnUpdateAction,
                          List<IndexInfo> indexInfoList) {
            super(columnName, sqlTypeDefinition, isNullable, isUnique, isPrimaryKey, isAutoIncrement,
                  defaultExpression, getterMethod, javaType, foreignKeyReferencesTable,
                  foreignKeyReferencesColumn, foreignKeyOnDeleteAction, foreignKeyOnUpdateAction,
                  indexInfoList != null ? indexInfoList.stream()
                      .map(info -> new IndexMetadata(info.name, info.unique))
                      .toList() : null);
        }
    }

    public static class IndexInfo {
        public final String name;
        public final boolean unique;

        public IndexInfo(String name, boolean unique) {
            this.name = name;
            this.unique = unique;
        }
    }

    protected DaoCrud(MySQLManager mysqlManager, Class<T> daoInterfaceClass, String tableName,
                      List<ColumnMetadata> columnMetadataList) {
        this.mysqlManager = mysqlManager;
        this.daoInterfaceClass = daoInterfaceClass;
        this.tableName = tableName;
        this.columnMetadataList = Collections.unmodifiableList(columnMetadataList);

        Map<String, ColumnMetadata> metadataByName = new LinkedHashMap<>();
        ColumnMetadata primaryKey = null;
        for (ColumnMetadata columnMetadata : columnMetadataList) {
            metadataByName.put(columnMetadata.columnName, columnMetadata);
            if (columnMetadata.isPrimaryKey) {
                primaryKey = columnMetadata;
            }
        }
        this.columnMetadataByName = Collections.unmodifiableMap(metadataByName);
        this.primaryKeyColumnMetadata = primaryKey;
    }

    // Legacy constructor for backward compatibility
    protected DaoCrud(MySQLManager mysqlManager, Class<T> daoInterfaceClass, String tableName,
                      List<ColumnMeta> columnMetaList, boolean legacy) {
        this(mysqlManager, daoInterfaceClass, tableName, (List<ColumnMetadata>) (List<?>) columnMetaList);
    }

    // ================= Table Creation =================

    /**
     * Creates the table asynchronously if it does not exist.
     * This method is idempotent and thread-safe.
     *
     * @return a CompletableFuture that completes when the table is created
     */
    public CompletableFuture<Void> createTableIfNotExistsAsync() {
        if (tableCreated) {
            return CompletableFuture.completedFuture(null);
        }

        return mysqlManager.runAsync(() -> {
            if (tableCreated) return;

            synchronized (this) {
                if (tableCreated) return;

                mysqlManager.executeWithConnection(connection -> {
                    String ddlStatement = generateCreateTableDDL();
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(ddlStatement);
                    }
                    return null;
                });
                tableCreated = true;
            }
        });
    }

    /**
     * Generates CREATE TABLE IF NOT EXISTS statement.
     */
    protected String generateCreateTableDDL() {
        StringBuilder ddlBuilder = new StringBuilder();
        ddlBuilder.append("CREATE TABLE IF NOT EXISTS `").append(tableName).append("` (\n");

        List<String> columnDefinitions = new ArrayList<>();
        List<String> constraintDefinitions = new ArrayList<>();

        for (ColumnMetadata columnMetadata : columnMetadataList) {
            StringBuilder columnDefinition = new StringBuilder();
            columnDefinition.append("  `").append(columnMetadata.columnName).append("` ")
                           .append(columnMetadata.sqlTypeDefinition);

            if (!columnMetadata.isNullable) {
                columnDefinition.append(" NOT NULL");
            }

            if (columnMetadata.isAutoIncrement) {
                columnDefinition.append(" AUTO_INCREMENT");
            }

            if (columnMetadata.defaultExpression != null && !columnMetadata.defaultExpression.isEmpty()) {
                columnDefinition.append(" DEFAULT ").append(columnMetadata.defaultExpression);
            }

            columnDefinitions.add(columnDefinition.toString());

            // Primary key constraint
            if (columnMetadata.isPrimaryKey) {
                constraintDefinitions.add("  PRIMARY KEY (`" + columnMetadata.columnName + "`)");
            }

            // Unique constraint from Column annotation
            if (columnMetadata.isUnique && !columnMetadata.isPrimaryKey) {
                constraintDefinitions.add("  UNIQUE KEY `uk_" + tableName + "_" + columnMetadata.columnName +
                        "` (`" + columnMetadata.columnName + "`)");
            }

            // Foreign key constraint
            if (columnMetadata.foreignKeyReferencesTable != null &&
                    !columnMetadata.foreignKeyReferencesTable.isEmpty()) {
                String foreignKeyName = "fk_" + tableName + "_" + columnMetadata.columnName;
                StringBuilder foreignKeyDefinition = new StringBuilder();
                foreignKeyDefinition.append("  CONSTRAINT `").append(foreignKeyName).append("` FOREIGN KEY (`")
                        .append(columnMetadata.columnName).append("`) REFERENCES `")
                        .append(columnMetadata.foreignKeyReferencesTable).append("` (`")
                        .append(columnMetadata.foreignKeyReferencesColumn).append("`)");
                if (columnMetadata.foreignKeyOnDeleteAction != null &&
                        !columnMetadata.foreignKeyOnDeleteAction.isEmpty()) {
                    foreignKeyDefinition.append(" ON DELETE ").append(columnMetadata.foreignKeyOnDeleteAction);
                }
                if (columnMetadata.foreignKeyOnUpdateAction != null &&
                        !columnMetadata.foreignKeyOnUpdateAction.isEmpty()) {
                    foreignKeyDefinition.append(" ON UPDATE ").append(columnMetadata.foreignKeyOnUpdateAction);
                }
                constraintDefinitions.add(foreignKeyDefinition.toString());
            }

            // Index constraints
            for (IndexMetadata indexMetadata : columnMetadata.indexMetadataList) {
                String indexName = indexMetadata.indexName.isEmpty()
                        ? "idx_" + tableName + "_" + columnMetadata.columnName
                        : indexMetadata.indexName;
                String indexType = indexMetadata.isUnique ? "UNIQUE KEY" : "KEY";
                constraintDefinitions.add("  " + indexType + " `" + indexName + "` (`" +
                        columnMetadata.columnName + "`)");
            }
        }

        ddlBuilder.append(String.join(",\n", columnDefinitions));
        if (!constraintDefinitions.isEmpty()) {
            ddlBuilder.append(",\n").append(String.join(",\n", constraintDefinitions));
        }

        ddlBuilder.append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        return ddlBuilder.toString();
    }

    // ================= Async CRUD Operations =================

    /**
     * Insert a new record asynchronously.
     *
     * @param entity the DAO instance to insert
     * @return a CompletableFuture with the generated key (if auto-increment), or null
     */
    public CompletableFuture<K> insertAsync(T entity) {
        return mysqlManager.supplyAsync(() -> executeInsert(entity));
    }

    /**
     * Update an existing record by primary key asynchronously.
     *
     * @param entity the DAO instance with updated values
     * @return a CompletableFuture with true if a row was updated
     */
    public CompletableFuture<Boolean> updateAsync(T entity) {
        if (primaryKeyColumnMetadata == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot update without a primary key defined"));
        }
        return mysqlManager.supplyAsync(() -> executeUpdate(entity));
    }

    /**
     * Save (insert or update) a record asynchronously.
     * If the primary key is auto-increment and null/0, inserts. Otherwise updates or inserts based on existence.
     *
     * @param entity the DAO instance
     * @return a CompletableFuture with the primary key value
     */
    public CompletableFuture<K> saveAsync(T entity) {
        if (primaryKeyColumnMetadata == null) {
            return insertAsync(entity);
        }

        Object primaryKeyValue = getValueFromEntity(entity, primaryKeyColumnMetadata);
        boolean isNewEntity = primaryKeyValue == null ||
                (primaryKeyValue instanceof Number && ((Number) primaryKeyValue).longValue() == 0);

        if (isNewEntity && primaryKeyColumnMetadata.isAutoIncrement) {
            return insertAsync(entity);
        } else {
            @SuppressWarnings("unchecked")
            K typedPrimaryKey = (K) primaryKeyValue;
            return existsAsync(typedPrimaryKey).thenCompose(exists -> {
                if (exists) {
                    return updateAsync(entity).thenApply(updated -> typedPrimaryKey);
                } else {
                    return insertAsync(entity).thenApply(insertedKey ->
                        insertedKey != null ? insertedKey : typedPrimaryKey);
                }
            });
        }
    }

    /**
     * Delete a record by primary key asynchronously.
     *
     * @param primaryKey the primary key value
     * @return a CompletableFuture with true if a row was deleted
     */
    public CompletableFuture<Boolean> deleteByKeyAsync(K primaryKey) {
        if (primaryKeyColumnMetadata == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot delete by key without a primary key defined"));
        }
        return mysqlManager.supplyAsync(() -> executeDeleteByKey(primaryKey));
    }

    /**
     * Delete a record using the entity's primary key asynchronously.
     *
     * @param entity the DAO instance
     * @return a CompletableFuture with true if a row was deleted
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> deleteAsync(T entity) {
        if (primaryKeyColumnMetadata == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot delete without a primary key defined"));
        }
        K primaryKey = (K) getValueFromEntity(entity, primaryKeyColumnMetadata);
        return deleteByKeyAsync(primaryKey);
    }

    /**
     * Load (find) a record by primary key asynchronously.
     *
     * @param primaryKey the primary key value
     * @return a CompletableFuture with Optional containing the entity, or empty if not found
     */
    public CompletableFuture<Optional<T>> loadAsync(K primaryKey) {
        if (primaryKeyColumnMetadata == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot find by key without a primary key defined"));
        }
        return mysqlManager.supplyAsync(() -> executeFindByKey(primaryKey));
    }

    /**
     * Check if a record exists by primary key asynchronously.
     *
     * @param primaryKey the primary key value
     * @return a CompletableFuture with true if exists
     */
    public CompletableFuture<Boolean> existsAsync(K primaryKey) {
        if (primaryKeyColumnMetadata == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot check existence without a primary key defined"));
        }
        return mysqlManager.supplyAsync(() -> executeExists(primaryKey));
    }

    /**
     * Load all records from the table asynchronously.
     *
     * @return a CompletableFuture with list of all entities
     */
    public CompletableFuture<List<T>> loadAllAsync() {
        return mysqlManager.supplyAsync(this::executeFindAll);
    }

    /**
     * Count all records in the table asynchronously.
     *
     * @return a CompletableFuture with the count, which always completes
     *         with a non-null {@link Long} value (the count is never {@code null})
     */
    public CompletableFuture<Long> countAsync() {
        return mysqlManager.supplyAsync(this::executeCount);
    }

    /**
     * Delete all records in the table asynchronously.
     *
     * @return a CompletableFuture with the number of deleted rows
     */
    public CompletableFuture<Integer> deleteAllAsync() {
        return mysqlManager.supplyAsync(this::executeDeleteAll);
    }

    /**
     * Find records matching a column value asynchronously.
     *
     * @param columnName the column to filter
     * @param value the value to match
     * @return a CompletableFuture with list of matching entities
     */
    public CompletableFuture<List<T>> findByColumnAsync(String columnName, Object value) {
        ColumnMetadata columnMetadata = columnMetadataByName.get(columnName);
        if (columnMetadata == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Unknown column: " + columnName));
        }
        return mysqlManager.supplyAsync(() -> executeFindByColumn(columnName, value, columnMetadata));
    }

    /**
     * Find a single record matching a column value asynchronously.
     *
     * @param columnName the column to filter
     * @param value the value to match
     * @return a CompletableFuture with Optional containing the first match, or empty
     */
    public CompletableFuture<Optional<T>> findOneByColumnAsync(String columnName, Object value) {
        return findByColumnAsync(columnName, value)
            .thenApply(results -> results.isEmpty() ? Optional.empty() : Optional.of(results.get(0)));
    }

    // ================= Internal Execution Methods (run on async thread) =================

    private K executeInsert(T entity) {
        return mysqlManager.executeWithConnection(connection -> {
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("INSERT INTO `").append(tableName).append("` (");

            List<ColumnMetadata> columnsToInsert = new ArrayList<>();
            for (ColumnMetadata columnMetadata : columnMetadataList) {
                if (!columnMetadata.isAutoIncrement) {
                    columnsToInsert.add(columnMetadata);
                }
            }

            sqlBuilder.append(String.join(", ", columnsToInsert.stream()
                    .map(column -> "`" + column.columnName + "`")
                    .toArray(String[]::new)));
            sqlBuilder.append(") VALUES (");
            sqlBuilder.append(String.join(", ", columnsToInsert.stream()
                    .map(column -> "?")
                    .toArray(String[]::new)));
            sqlBuilder.append(")");

            try (PreparedStatement preparedStatement = connection.prepareStatement(sqlBuilder.toString(),
                    primaryKeyColumnMetadata != null && primaryKeyColumnMetadata.isAutoIncrement
                            ? Statement.RETURN_GENERATED_KEYS
                            : Statement.NO_GENERATED_KEYS)) {

                int parameterIndex = 1;
                for (ColumnMetadata columnMetadata : columnsToInsert) {
                    Object value = getValueFromEntity(entity, columnMetadata);
                    setStatementParameter(preparedStatement, parameterIndex++, value, columnMetadata.javaType);
                }

                preparedStatement.executeUpdate();

                if (primaryKeyColumnMetadata != null && primaryKeyColumnMetadata.isAutoIncrement) {
                    try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            @SuppressWarnings("unchecked")
                            K generatedKey = (K) getResultSetValue(generatedKeys, 1,
                                    primaryKeyColumnMetadata.javaType);
                            return generatedKey;
                        }
                    }
                }
                return null;
            }
        });
    }

    private boolean executeUpdate(T entity) {
        return mysqlManager.executeWithConnection(connection -> {
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("UPDATE `").append(tableName).append("` SET ");

            List<ColumnMetadata> columnsToUpdate = new ArrayList<>();
            for (ColumnMetadata columnMetadata : columnMetadataList) {
                if (!columnMetadata.isPrimaryKey) {
                    columnsToUpdate.add(columnMetadata);
                }
            }

            sqlBuilder.append(String.join(", ", columnsToUpdate.stream()
                    .map(column -> "`" + column.columnName + "` = ?")
                    .toArray(String[]::new)));
            sqlBuilder.append(" WHERE `").append(primaryKeyColumnMetadata.columnName).append("` = ?");

            try (PreparedStatement preparedStatement = connection.prepareStatement(sqlBuilder.toString())) {
                int parameterIndex = 1;
                for (ColumnMetadata columnMetadata : columnsToUpdate) {
                    Object value = getValueFromEntity(entity, columnMetadata);
                    setStatementParameter(preparedStatement, parameterIndex++, value, columnMetadata.javaType);
                }

                Object primaryKeyValue = getValueFromEntity(entity, primaryKeyColumnMetadata);
                setStatementParameter(preparedStatement, parameterIndex, primaryKeyValue,
                        primaryKeyColumnMetadata.javaType);

                return preparedStatement.executeUpdate() > 0;
            }
        });
    }

    private boolean executeDeleteByKey(K primaryKey) {
        return mysqlManager.executeWithConnection(connection -> {
            String sqlStatement = "DELETE FROM `" + tableName + "` WHERE `" +
                    primaryKeyColumnMetadata.columnName + "` = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sqlStatement)) {
                setStatementParameter(preparedStatement, 1, primaryKey, primaryKeyColumnMetadata.javaType);
                return preparedStatement.executeUpdate() > 0;
            }
        });
    }

    private Optional<T> executeFindByKey(K primaryKey) {
        return mysqlManager.executeWithConnection(connection -> {
            String sqlStatement = "SELECT * FROM `" + tableName + "` WHERE `" +
                    primaryKeyColumnMetadata.columnName + "` = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sqlStatement)) {
                setStatementParameter(preparedStatement, 1, primaryKey, primaryKeyColumnMetadata.javaType);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        return Optional.of(mapResultSetToEntity(resultSet));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    private boolean executeExists(K primaryKey) {
        return mysqlManager.executeWithConnection(connection -> {
            String sqlStatement = "SELECT 1 FROM `" + tableName + "` WHERE `" +
                    primaryKeyColumnMetadata.columnName + "` = ? LIMIT 1";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sqlStatement)) {
                setStatementParameter(preparedStatement, 1, primaryKey, primaryKeyColumnMetadata.javaType);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    return resultSet.next();
                }
            }
        });
    }

    private List<T> executeFindAll() {
        return mysqlManager.executeWithConnection(connection -> {
            String sqlStatement = "SELECT * FROM `" + tableName + "`";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sqlStatement);
                 ResultSet resultSet = preparedStatement.executeQuery()) {
                List<T> resultList = new ArrayList<>();
                while (resultSet.next()) {
                    resultList.add(mapResultSetToEntity(resultSet));
                }
                return resultList;
            }
        });
    }

    private long executeCount() {
        return mysqlManager.executeWithConnection(connection -> {
            String sqlStatement = "SELECT COUNT(*) FROM `" + tableName + "`";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sqlStatement);
                 ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
                return 0L;
            }
        });
    }

    private int executeDeleteAll() {
        return mysqlManager.executeWithConnection(connection -> {
            String sqlStatement = "DELETE FROM `" + tableName + "`";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sqlStatement)) {
                return preparedStatement.executeUpdate();
            }
        });
    }

    private List<T> executeFindByColumn(String columnName, Object value, ColumnMetadata columnMetadata) {
        return mysqlManager.executeWithConnection(connection -> {
            String sqlStatement = "SELECT * FROM `" + tableName + "` WHERE `" + columnName + "` = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sqlStatement)) {
                setStatementParameter(preparedStatement, 1, value, columnMetadata.javaType);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    List<T> resultList = new ArrayList<>();
                    while (resultSet.next()) {
                        resultList.add(mapResultSetToEntity(resultSet));
                    }
                    return resultList;
                }
            }
        });
    }

    // ================= Entity Mapping =================

    /**
     * Maps a ResultSet row to a DAO proxy instance.
     */
    protected T mapResultSetToEntity(ResultSet resultSet) throws SQLException {
        Map<String, Object> valuesByGetterName = new LinkedHashMap<>();

        for (ColumnMetadata columnMetadata : columnMetadataList) {
            Object value = getResultSetValue(resultSet, columnMetadata.columnName, columnMetadata.javaType);
            valuesByGetterName.put(columnMetadata.getterMethodName, value);
        }

        return createEntityProxy(valuesByGetterName);
    }

    /**
     * Creates a proxy implementing the DAO interface with the given values.
     */
    @SuppressWarnings("unchecked")
    protected T createEntityProxy(Map<String, Object> valuesByGetterName) {
        return (T) Proxy.newProxyInstance(
                daoInterfaceClass.getClassLoader(),
                new Class<?>[] { daoInterfaceClass },
                new DaoInvocationHandler(valuesByGetterName)
        );
    }

    /**
     * Creates a mutable builder for the DAO.
     *
     * @return a builder for creating DAO instances
     */
    public EntityBuilder<T> builder() {
        return new EntityBuilder<>(this);
    }

    /**
     * Builder for creating DAO instances.
     */
    public static class EntityBuilder<T> {
        private final DaoCrud<T, ?> repositoryCrud;
        private final Map<String, Object> valuesByGetterName = new LinkedHashMap<>();

        EntityBuilder(DaoCrud<T, ?> repositoryCrud) {
            this.repositoryCrud = repositoryCrud;
        }

        /**
         * Set a column value by getter method name.
         */
        public EntityBuilder<T> set(String getterMethodName, Object value) {
            valuesByGetterName.put(getterMethodName, value);
            return this;
        }

        /**
         * Build the DAO instance.
         */
        public T build() {
            return repositoryCrud.createEntityProxy(new LinkedHashMap<>(valuesByGetterName));
        }
    }

    /**
     * InvocationHandler for DAO proxies.
     */
    private class DaoInvocationHandler implements InvocationHandler {
        private final Map<String, Object> valuesByGetterName;

        DaoInvocationHandler(Map<String, Object> valuesByGetterName) {
            this.valuesByGetterName = new LinkedHashMap<>(valuesByGetterName);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            String methodName = method.getName();

            // Handle Object methods
            if ("equals".equals(methodName) && arguments != null && arguments.length == 1) {
                return proxy == arguments[0];
            }
            if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(methodName)) {
                return daoInterfaceClass.getSimpleName() + valuesByGetterName;
            }

            // Handle getter
            if (valuesByGetterName.containsKey(methodName)) {
                return valuesByGetterName.get(methodName);
            }

            // Handle setter (setXxx)
            if (methodName.startsWith("set") && arguments != null && arguments.length == 1) {
                String getterMethodName = "get" + methodName.substring(3);
                valuesByGetterName.put(getterMethodName, arguments[0]);
                return null;
            }

            return null;
        }
    }

    // ================= JDBC Utility Methods =================

    /**
     * Gets a value from the DAO entity using the column's getter.
     */
    protected Object getValueFromEntity(T entity, ColumnMetadata columnMetadata) {
        try {
            return columnMetadata.getterMethod.invoke(entity);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to get value for column " + columnMetadata.columnName, exception);
        }
    }

    /**
     * Sets a parameter on a PreparedStatement.
     */
    protected void setStatementParameter(PreparedStatement preparedStatement, int parameterIndex,
                                         Object value, Class<?> javaType) throws SQLException {
        if (value == null) {
            preparedStatement.setNull(parameterIndex, Types.NULL);
            return;
        }

        if (value instanceof String stringValue) {
            preparedStatement.setString(parameterIndex, stringValue);
        } else if (value instanceof Integer integerValue) {
            preparedStatement.setInt(parameterIndex, integerValue);
        } else if (value instanceof Long longValue) {
            preparedStatement.setLong(parameterIndex, longValue);
        } else if (value instanceof Double doubleValue) {
            preparedStatement.setDouble(parameterIndex, doubleValue);
        } else if (value instanceof Float floatValue) {
            preparedStatement.setFloat(parameterIndex, floatValue);
        } else if (value instanceof Boolean booleanValue) {
            preparedStatement.setBoolean(parameterIndex, booleanValue);
        } else if (value instanceof java.util.UUID uuidValue) {
            preparedStatement.setString(parameterIndex, uuidValue.toString());
        } else if (value instanceof java.util.Date dateValue) {
            preparedStatement.setTimestamp(parameterIndex, new Timestamp(dateValue.getTime()));
        } else if (value instanceof java.time.Instant instantValue) {
            preparedStatement.setTimestamp(parameterIndex, Timestamp.from(instantValue));
        } else if (value instanceof java.time.LocalDateTime localDateTimeValue) {
            preparedStatement.setTimestamp(parameterIndex, Timestamp.valueOf(localDateTimeValue));
        } else if (value instanceof java.time.LocalDate localDateValue) {
            preparedStatement.setDate(parameterIndex, java.sql.Date.valueOf(localDateValue));
        } else if (value instanceof byte[] bytesValue) {
            preparedStatement.setBytes(parameterIndex, bytesValue);
        } else if (value instanceof Enum<?> enumValue) {
            preparedStatement.setString(parameterIndex, enumValue.name());
        } else {
            // Fallback: serialize as JSON
            preparedStatement.setString(parameterIndex, mysqlManager.gson.toJson(value));
        }
    }

    /**
     * Gets a value from a ResultSet by column name.
     */
    protected Object getResultSetValue(ResultSet resultSet, String columnName,
                                       Class<?> javaType) throws SQLException {
        return getResultSetValueInternal(resultSet, columnName, javaType);
    }

    /**
     * Gets a value from a ResultSet by column index.
     */
    protected Object getResultSetValue(ResultSet resultSet, int columnIndex,
                                       Class<?> javaType) throws SQLException {
        return getResultSetValueInternal(resultSet, columnIndex, javaType);
    }

    private Object getResultSetValueInternal(ResultSet resultSet, Object columnReference,
                                             Class<?> javaType) throws SQLException {
        if (javaType == String.class) {
            return columnReference instanceof String
                    ? resultSet.getString((String) columnReference)
                    : resultSet.getString((Integer) columnReference);
        } else if (javaType == int.class || javaType == Integer.class) {
            int value = columnReference instanceof String
                    ? resultSet.getInt((String) columnReference)
                    : resultSet.getInt((Integer) columnReference);
            return resultSet.wasNull() ? null : value;
        } else if (javaType == long.class || javaType == Long.class) {
            long value = columnReference instanceof String
                    ? resultSet.getLong((String) columnReference)
                    : resultSet.getLong((Integer) columnReference);
            return resultSet.wasNull() ? null : value;
        } else if (javaType == double.class || javaType == Double.class) {
            double value = columnReference instanceof String
                    ? resultSet.getDouble((String) columnReference)
                    : resultSet.getDouble((Integer) columnReference);
            return resultSet.wasNull() ? null : value;
        } else if (javaType == float.class || javaType == Float.class) {
            float value = columnReference instanceof String
                    ? resultSet.getFloat((String) columnReference)
                    : resultSet.getFloat((Integer) columnReference);
            return resultSet.wasNull() ? null : value;
        } else if (javaType == boolean.class || javaType == Boolean.class) {
            boolean value = columnReference instanceof String
                    ? resultSet.getBoolean((String) columnReference)
                    : resultSet.getBoolean((Integer) columnReference);
            return resultSet.wasNull() ? null : value;
        } else if (javaType == java.util.UUID.class) {
            String stringValue = columnReference instanceof String
                    ? resultSet.getString((String) columnReference)
                    : resultSet.getString((Integer) columnReference);
            return stringValue == null ? null : java.util.UUID.fromString(stringValue);
        } else if (javaType == java.util.Date.class) {
            Timestamp timestampValue = columnReference instanceof String
                    ? resultSet.getTimestamp((String) columnReference)
                    : resultSet.getTimestamp((Integer) columnReference);
            return timestampValue == null ? null : new java.util.Date(timestampValue.getTime());
        } else if (javaType == java.time.Instant.class) {
            Timestamp timestampValue = columnReference instanceof String
                    ? resultSet.getTimestamp((String) columnReference)
                    : resultSet.getTimestamp((Integer) columnReference);
            return timestampValue == null ? null : timestampValue.toInstant();
        } else if (javaType == java.time.LocalDateTime.class) {
            Timestamp timestampValue = columnReference instanceof String
                    ? resultSet.getTimestamp((String) columnReference)
                    : resultSet.getTimestamp((Integer) columnReference);
            return timestampValue == null ? null : timestampValue.toLocalDateTime();
        } else if (javaType == java.time.LocalDate.class) {
            java.sql.Date dateValue = columnReference instanceof String
                    ? resultSet.getDate((String) columnReference)
                    : resultSet.getDate((Integer) columnReference);
            return dateValue == null ? null : dateValue.toLocalDate();
        } else if (javaType == byte[].class) {
            return columnReference instanceof String
                    ? resultSet.getBytes((String) columnReference)
                    : resultSet.getBytes((Integer) columnReference);
        } else if (javaType.isEnum()) {
            String stringValue = columnReference instanceof String
                    ? resultSet.getString((String) columnReference)
                    : resultSet.getString((Integer) columnReference);
            if (stringValue == null) return null;
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object enumValue = Enum.valueOf((Class<Enum>) javaType, stringValue);
            return enumValue;
        } else {
            // Fallback: deserialize from JSON
            String jsonValue = columnReference instanceof String
                    ? resultSet.getString((String) columnReference)
                    : resultSet.getString((Integer) columnReference);
            return jsonValue == null ? null : mysqlManager.gson.fromJson(jsonValue, javaType);
        }
    }

    // ================= Accessors =================

    public String getTableName() {
        return tableName;
    }

    public Class<T> getDaoInterfaceClass() {
        return daoInterfaceClass;
    }

    public List<ColumnMetadata> getColumnMetadataList() {
        return columnMetadataList;
    }

    public ColumnMetadata getPrimaryKeyColumnMetadata() {
        return primaryKeyColumnMetadata;
    }

    public MySQLManager getMysqlManager() {
        return mysqlManager;
    }
}

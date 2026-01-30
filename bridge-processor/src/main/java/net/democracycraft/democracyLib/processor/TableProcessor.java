package net.democracycraft.democracyLib.processor;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Annotation processor for @Table annotated DAO interfaces.
 * <p>
 * Generates:
 * <ul>
 *   <li>{DaoName}Impl - implementation of the DAO interface</li>
 *   <li>{DaoName}Repository - repository class extending DaoCrud</li>
 * </ul>
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("net.democracycraft.democracyLib.api.database.annotations.Table")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class TableProcessor extends AbstractProcessor {

    private static final String TABLE_ANNOTATION = "net.democracycraft.democracyLib.api.database.annotations.Table";
    private static final String COLUMN_ANNOTATION = "net.democracycraft.democracyLib.api.database.annotations.Column";
    private static final String PRIMARY_KEY_ANNOTATION = "net.democracycraft.democracyLib.api.database.annotations.PrimaryKey";
    private static final String FOREIGN_KEY_ANNOTATION = "net.democracycraft.democracyLib.api.database.annotations.ForeignKey";
    private static final String INDEX_ANNOTATION = "net.democracycraft.democracyLib.api.database.annotations.Index";
    private static final String INDEXES_ANNOTATION = "net.democracycraft.democracyLib.api.database.annotations.Indexes";
    private static final String SQL_TYPE_ENUM = "net.democracycraft.democracyLib.api.database.annotations.SqlType";

    private Types typeUtils;
    private Elements elementUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.typeUtils = processingEnv.getTypeUtils();
        this.elementUtils = processingEnv.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            if (!annotation.getQualifiedName().contentEquals(TABLE_ANNOTATION)) {
                continue;
            }

            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element.getKind() != ElementKind.INTERFACE) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "@Table can only be applied to interfaces",
                            element
                    );
                    continue;
                }

                TypeElement interfaceElement = (TypeElement) element;
                try {
                    processTableInterface(interfaceElement);
                } catch (Exception e) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "Failed to process @Table: " + e.getMessage(),
                            element
                    );
                    e.printStackTrace();
                }
            }
        }
        return true;
    }

    private void processTableInterface(TypeElement interfaceElement) throws IOException {
        String tableName = getTableName(interfaceElement);
        String packageName = elementUtils.getPackageOf(interfaceElement).getQualifiedName().toString();
        String interfaceSimpleName = interfaceElement.getSimpleName().toString();

        // Collect all columns from this interface and inherited interfaces
        List<ColumnInfo> columns = collectColumns(interfaceElement);

        // Generate implementation class
        generateImplementation(packageName, interfaceSimpleName, interfaceElement, columns);

        // Generate repository class
        generateRepository(packageName, interfaceSimpleName, interfaceElement, tableName, columns);
    }

    /**
     * Collects all @Column annotated methods from the interface and its superinterfaces.
     */
    private List<ColumnInfo> collectColumns(TypeElement interfaceElement) {
        Map<String, ColumnInfo> columnsByMethod = new LinkedHashMap<>();
        collectColumnsRecursive(interfaceElement, columnsByMethod);
        return new ArrayList<>(columnsByMethod.values());
    }

    private void collectColumnsRecursive(TypeElement interfaceElement, Map<String, ColumnInfo> columns) {
        // First process superinterfaces (so child can override)
        for (TypeMirror superInterface : interfaceElement.getInterfaces()) {
            if (superInterface.getKind() == TypeKind.DECLARED) {
                TypeElement superElement = (TypeElement) ((DeclaredType) superInterface).asElement();
                collectColumnsRecursive(superElement, columns);
            }
        }

        // Process methods in this interface
        for (Element enclosed : interfaceElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) continue;

            ExecutableElement method = (ExecutableElement) enclosed;
            AnnotationMirror columnAnn = getAnnotationMirror(method, COLUMN_ANNOTATION);
            if (columnAnn == null) continue;

            ColumnInfo info = extractColumnInfo(method, columnAnn);
            columns.put(method.getSimpleName().toString(), info);
        }
    }

    private ColumnInfo extractColumnInfo(ExecutableElement method, AnnotationMirror columnAnn) {
        ColumnInfo info = new ColumnInfo();
        info.methodName = method.getSimpleName().toString();
        info.returnType = method.getReturnType();
        info.returnTypeString = getTypeString(method.getReturnType());

        // Extract @Column values
        info.columnName = getAnnotationValueString(columnAnn, "name", "");
        if (info.columnName.isEmpty()) {
            info.columnName = camelToSnake(info.methodName.replaceFirst("^(get|is)", ""));
        }

        String sqlTypeEnum = getAnnotationValueEnum(columnAnn, "type");
        String customSqlType = getAnnotationValueString(columnAnn, "sqlType", "");
        int length = getAnnotationValueInt(columnAnn, "length", -1);

        info.sqlType = resolveSqlType(sqlTypeEnum, customSqlType, length, info.returnType);
        info.nullable = getAnnotationValueBoolean(columnAnn, "nullable", true);
        info.unique = getAnnotationValueBoolean(columnAnn, "unique", false);
        info.defaultExpr = getAnnotationValueString(columnAnn, "defaultExpr", "");

        // Check for @PrimaryKey
        AnnotationMirror pkAnn = getAnnotationMirror(method, PRIMARY_KEY_ANNOTATION);
        if (pkAnn != null) {
            info.primaryKey = true;
            info.autoIncrement = getAnnotationValueBoolean(pkAnn, "autoIncrement", false);
        }

        // Check for @ForeignKey
        AnnotationMirror fkAnn = getAnnotationMirror(method, FOREIGN_KEY_ANNOTATION);
        if (fkAnn != null) {
            info.hasForeignKey = true;
            TypeMirror refDaoType = getAnnotationValueClass(fkAnn, "referencesDao");
            if (refDaoType != null) {
                info.fkReferencesDao = refDaoType.toString();
                info.fkReferencesTable = resolveTableName(refDaoType);
            }
            info.fkReferencesColumn = getAnnotationValueString(fkAnn, "referencesColumn", "id");
            info.fkOnDelete = getAnnotationValueEnum(fkAnn, "onDelete");
            info.fkOnUpdate = getAnnotationValueEnum(fkAnn, "onUpdate");
        }

        // Check for @Index / @Indexes
        List<IndexInfo> indexes = new ArrayList<>();
        AnnotationMirror indexAnn = getAnnotationMirror(method, INDEX_ANNOTATION);
        if (indexAnn != null) {
            indexes.add(new IndexInfo(
                    getAnnotationValueString(indexAnn, "name", ""),
                    getAnnotationValueBoolean(indexAnn, "unique", false)
            ));
        }
        AnnotationMirror indexesAnn = getAnnotationMirror(method, INDEXES_ANNOTATION);
        if (indexesAnn != null) {
            List<AnnotationMirror> indexList = getAnnotationValueArray(indexesAnn, "value");
            for (AnnotationMirror idx : indexList) {
                indexes.add(new IndexInfo(
                        getAnnotationValueString(idx, "name", ""),
                        getAnnotationValueBoolean(idx, "unique", false)
                ));
            }
        }
        info.indexes = indexes;

        return info;
    }

    private String resolveSqlType(String sqlTypeEnum, String customSqlType, int length, TypeMirror javaType) {
        if ("CUSTOM".equals(sqlTypeEnum) && !customSqlType.isEmpty()) {
            return customSqlType;
        }

        if (sqlTypeEnum != null && !"CUSTOM".equals(sqlTypeEnum)) {
            // Handle types that need length
            if (("VARCHAR".equals(sqlTypeEnum) || "CHAR".equals(sqlTypeEnum)) && length > 0) {
                return sqlTypeEnum + "(" + length + ")";
            }
            return sqlTypeEnum;
        }

        // Infer from Java type
        String typeStr = javaType.toString();
        return switch (typeStr) {
            case "java.lang.String" -> length > 0 ? "VARCHAR(" + length + ")" : "VARCHAR(255)";
            case "java.util.UUID" -> "VARCHAR(36)";
            case "int", "java.lang.Integer" -> "INT";
            case "long", "java.lang.Long" -> "BIGINT";
            case "double", "java.lang.Double" -> "DOUBLE";
            case "float", "java.lang.Float" -> "FLOAT";
            case "boolean", "java.lang.Boolean" -> "BOOLEAN";
            case "java.util.Date", "java.time.Instant", "java.time.LocalDateTime" -> "DATETIME";
            case "java.time.LocalDate" -> "DATE";
            case "byte[]" -> "BLOB";
            default -> "TEXT";
        };
    }

    private String resolveTableName(TypeMirror daoType) {
        if (daoType.getKind() != TypeKind.DECLARED) return "";
        TypeElement element = (TypeElement) ((DeclaredType) daoType).asElement();
        AnnotationMirror tableAnn = getAnnotationMirror(element, TABLE_ANNOTATION);
        if (tableAnn != null) {
            return getAnnotationValueString(tableAnn, "value", "");
        }
        // Fallback: derive from class name
        return camelToSnake(element.getSimpleName().toString().replaceAll("(Table)?Dao$", ""));
    }

    private String getTableName(TypeElement element) {
        AnnotationMirror tableAnn = getAnnotationMirror(element, TABLE_ANNOTATION);
        if (tableAnn != null) {
            String name = getAnnotationValueString(tableAnn, "value", "");
            if (!name.isEmpty()) return name;
        }
        return camelToSnake(element.getSimpleName().toString().replaceAll("(Table)?Dao$", ""));
    }

    /**
     * Generates the implementation class for the DAO interface.
     */
    private void generateImplementation(String packageName, String interfaceSimpleName,
                                        TypeElement interfaceElement, List<ColumnInfo> columns) throws IOException {
        String implClassName = interfaceSimpleName + "Impl";
        String qualifiedName = packageName.isEmpty() ? implClassName : packageName + "." + implClassName;

        JavaFileObject file = processingEnv.getFiler().createSourceFile(qualifiedName, interfaceElement);
        try (PrintWriter out = new PrintWriter(file.openWriter())) {
            if (!packageName.isEmpty()) {
                out.println("package " + packageName + ";");
                out.println();
            }

            // Imports
            Set<String> imports = new TreeSet<>();
            imports.add("java.util.Objects");
            for (ColumnInfo col : columns) {
                addImport(imports, col.returnType);
            }
            for (String imp : imports) {
                out.println("import " + imp + ";");
            }
            if (!imports.isEmpty()) out.println();

            // Class declaration
            out.println("/**");
            out.println(" * Generated implementation of " + interfaceSimpleName + ".");
            out.println(" * This class is mutable and can be used to create/modify DAO instances.");
            out.println(" */");
            out.println("public class " + implClassName + " implements " + interfaceSimpleName + " {");
            out.println();

            // Fields
            for (ColumnInfo col : columns) {
                out.println("    private " + col.returnTypeString + " " + toFieldName(col.methodName) + ";");
            }
            out.println();

            // Default constructor
            out.println("    public " + implClassName + "() {");
            out.println("    }");
            out.println();

            // All-args constructor
            out.print("    public " + implClassName + "(");
            StringJoiner constructorParams = new StringJoiner(", ");
            for (ColumnInfo col : columns) {
                constructorParams.add(col.returnTypeString + " " + toFieldName(col.methodName));
            }
            out.print(constructorParams.toString());
            out.println(") {");
            for (ColumnInfo col : columns) {
                String field = toFieldName(col.methodName);
                out.println("        this." + field + " = " + field + ";");
            }
            out.println("    }");
            out.println();

            // Getters (implementing interface)
            for (ColumnInfo col : columns) {
                out.println("    @Override");
                out.println("    public " + col.returnTypeString + " " + col.methodName + "() {");
                out.println("        return this." + toFieldName(col.methodName) + ";");
                out.println("    }");
                out.println();
            }

            // Setters
            for (ColumnInfo col : columns) {
                String field = toFieldName(col.methodName);
                String setterName = "set" + capitalize(field);
                out.println("    public " + implClassName + " " + setterName + "(" + col.returnTypeString + " " + field + ") {");
                out.println("        this." + field + " = " + field + ";");
                out.println("        return this;");
                out.println("    }");
                out.println();
            }

            // Builder static method
            out.println("    public static Builder builder() {");
            out.println("        return new Builder();");
            out.println("    }");
            out.println();

            // Builder inner class
            out.println("    public static class Builder {");
            out.println("        private final " + implClassName + " instance = new " + implClassName + "();");
            out.println();
            for (ColumnInfo col : columns) {
                String field = toFieldName(col.methodName);
                out.println("        public Builder " + field + "(" + col.returnTypeString + " value) {");
                out.println("            instance." + field + " = value;");
                out.println("            return this;");
                out.println("        }");
                out.println();
            }
            out.println("        public " + implClassName + " build() {");
            out.println("            return instance;");
            out.println("        }");
            out.println("    }");
            out.println();

            // equals, hashCode, toString
            ColumnInfo pkCol = columns.stream().filter(c -> c.primaryKey).findFirst().orElse(null);

            out.println("    @Override");
            out.println("    public boolean equals(Object o) {");
            out.println("        if (this == o) return true;");
            out.println("        if (!(o instanceof " + interfaceSimpleName + " that)) return false;");
            if (pkCol != null) {
                String field = toFieldName(pkCol.methodName);
                out.println("        return Objects.equals(" + field + ", that." + pkCol.methodName + "());");
            } else {
                out.println("        return true; // No primary key defined");
            }
            out.println("    }");
            out.println();

            out.println("    @Override");
            out.println("    public int hashCode() {");
            if (pkCol != null) {
                out.println("        return Objects.hash(" + toFieldName(pkCol.methodName) + ");");
            } else {
                out.println("        return super.hashCode();");
            }
            out.println("    }");
            out.println();

            out.println("    @Override");
            out.println("    public String toString() {");
            out.println("        return \"" + implClassName + "{\" +");
            boolean first = true;
            for (ColumnInfo col : columns) {
                String field = toFieldName(col.methodName);
                if (first) {
                    out.println("                \"" + field + "=\" + " + field + " +");
                    first = false;
                } else {
                    out.println("                \", " + field + "=\" + " + field + " +");
                }
            }
            out.println("                \"}\";");
            out.println("    }");

            out.println("}");
        }
    }

    /**
     * Generates the repository class for the DAO.
     */
    private void generateRepository(String packageName, String interfaceSimpleName,
                                    TypeElement interfaceElement, String tableName,
                                    List<ColumnInfo> columns) throws IOException {
        String repoClassName = interfaceSimpleName + "Repository";
        String implClassName = interfaceSimpleName + "Impl";
        String qualifiedName = packageName.isEmpty() ? repoClassName : packageName + "." + repoClassName;

        // Find primary key type
        ColumnInfo pkCol = columns.stream().filter(c -> c.primaryKey).findFirst().orElse(null);
        String pkType = pkCol != null ? boxedType(pkCol.returnTypeString) : "Void";

        JavaFileObject file = processingEnv.getFiler().createSourceFile(qualifiedName, interfaceElement);
        try (PrintWriter out = new PrintWriter(file.openWriter())) {
            if (!packageName.isEmpty()) {
                out.println("package " + packageName + ";");
                out.println();
            }

            // Imports
            out.println("import net.democracycraft.democracyLib.api.database.DaoCrud;");
            out.println("import net.democracycraft.democracyLib.api.database.DaoCrud.ColumnMetadata;");
            out.println("import net.democracycraft.democracyLib.api.database.DaoCrud.ColumnMeta;");
            out.println("import net.democracycraft.democracyLib.api.database.DaoCrud.IndexInfo;");
            out.println("import net.democracycraft.democracyLib.api.database.MySQLManager;");
            out.println();
            out.println("import java.lang.reflect.Method;");
            out.println("import java.util.ArrayList;");
            out.println("import java.util.List;");
            out.println("import java.util.Optional;");
            out.println("import java.util.UUID;");
            out.println("import java.util.concurrent.CompletableFuture;");
            out.println();

            // Class declaration
            out.println("/**");
            out.println(" * Generated repository for " + interfaceSimpleName + ".");
            out.println(" * <p>");
            out.println(" * This repository provides async CRUD operations for the '" + tableName + "' table.");
            out.println(" * <strong>All database operations are asynchronous and return CompletableFuture.</strong>");
            out.println(" * <p>");
            out.println(" * Usage:");
            out.println(" * <pre>");
            out.println(" * MySQLManager mysqlManager = MySQLManager.fromConfig(plugin);");
            out.println(" * mysqlManager.setupDatabaseAsync().thenRun(() -> {");
            out.println(" *     " + repoClassName + " repository = new " + repoClassName + "(mysqlManager);");
            out.println(" *     repository.createTableIfNotExistsAsync().thenRun(() -> {");
            out.println(" *         // Repository is ready to use");
            out.println(" *     });");
            out.println(" * });");
            out.println(" * ");
            out.println(" * // Create and save a new entity");
            out.println(" * " + implClassName + " entity = repository.newBuilder()");
            out.println(" *     // .fieldName(value)");
            out.println(" *     .build();");
            out.println(" * ");
            out.println(" * repository.saveAsync(entity).thenAccept(id -> {");
            out.println(" *     System.out.println(\"Saved with ID: \" + id);");
            out.println(" * });");
            out.println(" * ");
            out.println(" * // Load an entity");
            out.println(" * repository.loadAsync(id).thenAccept(optional -> {");
            out.println(" *     optional.ifPresent(loaded -> System.out.println(loaded));");
            out.println(" * });");
            out.println(" * </pre>");
            out.println(" */");
            out.println("public class " + repoClassName + " extends DaoCrud<" + interfaceSimpleName + ", " + pkType + "> {");
            out.println();

            // Constructor
            out.println("    /**");
            out.println("     * Creates a new repository.");
            out.println("     * <p>");
            out.println("     * Call {@link #createTableIfNotExistsAsync()} after construction to ensure the table exists.");
            out.println("     *");
            out.println("     * @param mysqlManager the MySQL manager for database operations");
            out.println("     */");
            out.println("    public " + repoClassName + "(MySQLManager mysqlManager) {");
            out.println("        super(mysqlManager, " + interfaceSimpleName + ".class, \"" + tableName + "\", buildColumnMetadataList());");
            out.println("    }");
            out.println();

            // Static method to build column metadata
            out.println("    @SuppressWarnings(\"unchecked\")");
            out.println("    private static List<ColumnMetadata> buildColumnMetadataList() {");
            out.println("        List<ColumnMetadata> columnMetadataList = new ArrayList<>();");
            out.println("        try {");

            for (ColumnInfo columnInfo : columns) {
                out.println("            columnMetadataList.add(new ColumnMeta(");
                out.println("                \"" + columnInfo.columnName + "\",");
                out.println("                \"" + columnInfo.sqlType + "\",");
                out.println("                " + columnInfo.nullable + ",");
                out.println("                " + columnInfo.unique + ",");
                out.println("                " + columnInfo.primaryKey + ",");
                out.println("                " + columnInfo.autoIncrement + ",");
                out.println("                " + (columnInfo.defaultExpr.isEmpty() ? "\"\"" : "\"" + escapeString(columnInfo.defaultExpr) + "\"") + ",");
                out.println("                " + interfaceSimpleName + ".class.getMethod(\"" + columnInfo.methodName + "\"),");
                out.println("                " + toClassLiteral(columnInfo.returnType) + ",");

                // Foreign key info
                if (columnInfo.hasForeignKey) {
                    out.println("                \"" + columnInfo.fkReferencesTable + "\",");
                    out.println("                \"" + columnInfo.fkReferencesColumn + "\",");
                    out.println("                \"" + formatEnumToSql(columnInfo.fkOnDelete) + "\",");
                    out.println("                \"" + formatEnumToSql(columnInfo.fkOnUpdate) + "\",");
                } else {
                    out.println("                null, null, null, null,");
                }

                // Indexes
                if (columnInfo.indexes.isEmpty()) {
                    out.println("                null");
                } else {
                    out.println("                java.util.List.of(");
                    StringJoiner indexJoiner = new StringJoiner(",\n");
                    for (IndexInfo indexInfo : columnInfo.indexes) {
                        indexJoiner.add("                    new IndexInfo(\"" + indexInfo.name + "\", " + indexInfo.unique + ")");
                    }
                    out.println(indexJoiner.toString());
                    out.println("                )");
                }
                out.println("            ));");
            }

            out.println("        } catch (NoSuchMethodException exception) {");
            out.println("            throw new RuntimeException(\"Failed to build column metadata\", exception);");
            out.println("        }");
            out.println("        return columnMetadataList;");
            out.println("    }");
            out.println();

            // Convenience factory method for creating new instances
            out.println("    /**");
            out.println("     * Creates a new builder for " + interfaceSimpleName + ".");
            out.println("     *");
            out.println("     * @return a new builder instance");
            out.println("     */");
            out.println("    public " + implClassName + ".Builder newBuilder() {");
            out.println("        return " + implClassName + ".builder();");
            out.println("    }");
            out.println();

            // Convenience method to create empty instance
            out.println("    /**");
            out.println("     * Creates a new empty instance of " + interfaceSimpleName + ".");
            out.println("     *");
            out.println("     * @return a new empty instance");
            out.println("     */");
            out.println("    public " + implClassName + " newInstance() {");
            out.println("        return new " + implClassName + "();");
            out.println("    }");

            // Generate async findBy methods for indexed columns
            for (ColumnInfo columnInfo : columns) {
                boolean hasIndex = !columnInfo.indexes.isEmpty() || columnInfo.primaryKey || columnInfo.unique;
                if (hasIndex && !columnInfo.primaryKey) {
                    String methodSuffix = capitalize(toFieldName(columnInfo.methodName));

                    // Async findBy method
                    out.println();
                    out.println("    /**");
                    out.println("     * Find all records by " + columnInfo.columnName + " asynchronously.");
                    out.println("     *");
                    out.println("     * @param value the value to search for");
                    out.println("     * @return CompletableFuture with list of matching entities");
                    out.println("     */");
                    out.println("    public CompletableFuture<List<" + interfaceSimpleName + ">> findBy" + methodSuffix + "Async(" + columnInfo.returnTypeString + " value) {");
                    out.println("        return findByColumnAsync(\"" + columnInfo.columnName + "\", value);");
                    out.println("    }");

                    if (columnInfo.unique) {
                        // Async findOneBy method
                        out.println();
                        out.println("    /**");
                        out.println("     * Find a single record by " + columnInfo.columnName + " asynchronously (unique).");
                        out.println("     *");
                        out.println("     * @param value the value to search for");
                        out.println("     * @return CompletableFuture with Optional containing the entity");
                        out.println("     */");
                        out.println("    public CompletableFuture<Optional<" + interfaceSimpleName + ">> findOneBy" + methodSuffix + "Async(" + columnInfo.returnTypeString + " value) {");
                        out.println("        return findOneByColumnAsync(\"" + columnInfo.columnName + "\", value);");
                        out.println("    }");
                    }
                }
            }

            out.println("}");
        }
    }

    // ================= Utility Methods =================

    private AnnotationMirror getAnnotationMirror(Element element, String annotationType) {
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            if (am.getAnnotationType().toString().equals(annotationType)) {
                return am;
            }
        }
        return null;
    }

    private String getAnnotationValueString(AnnotationMirror am, String key, String defaultValue) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : am.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(key)) {
                Object value = entry.getValue().getValue();
                return value != null ? value.toString() : defaultValue;
            }
        }
        return defaultValue;
    }

    private int getAnnotationValueInt(AnnotationMirror am, String key, int defaultValue) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : am.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(key)) {
                Object value = entry.getValue().getValue();
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
            }
        }
        return defaultValue;
    }

    private boolean getAnnotationValueBoolean(AnnotationMirror am, String key, boolean defaultValue) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : am.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(key)) {
                Object value = entry.getValue().getValue();
                if (value instanceof Boolean) {
                    return (Boolean) value;
                }
            }
        }
        return defaultValue;
    }

    private String getAnnotationValueEnum(AnnotationMirror am, String key) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : am.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(key)) {
                Object value = entry.getValue().getValue();
                if (value instanceof VariableElement) {
                    return ((VariableElement) value).getSimpleName().toString();
                }
            }
        }
        return null;
    }

    private TypeMirror getAnnotationValueClass(AnnotationMirror am, String key) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : am.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(key)) {
                Object value = entry.getValue().getValue();
                if (value instanceof TypeMirror) {
                    return (TypeMirror) value;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<AnnotationMirror> getAnnotationValueArray(AnnotationMirror am, String key) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : am.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(key)) {
                Object value = entry.getValue().getValue();
                if (value instanceof List<?>) {
                    List<AnnotationMirror> result = new ArrayList<>();
                    for (Object item : (List<?>) value) {
                        if (item instanceof AnnotationValue) {
                            Object av = ((AnnotationValue) item).getValue();
                            if (av instanceof AnnotationMirror) {
                                result.add((AnnotationMirror) av);
                            }
                        }
                    }
                    return result;
                }
            }
        }
        return List.of();
    }

    private String getTypeString(TypeMirror type) {
        String str = type.toString();
        // Simplify common types
        return str.replace("java.lang.", "")
                  .replace("java.util.", "");
    }

    private void addImport(Set<String> imports, TypeMirror type) {
        String typeStr = type.toString();
        if (typeStr.startsWith("java.util.") || typeStr.startsWith("java.time.")) {
            imports.add(typeStr);
        }
    }

    private String toFieldName(String methodName) {
        String name = methodName;
        if (name.startsWith("get") && name.length() > 3) {
            name = name.substring(3);
        } else if (name.startsWith("is") && name.length() > 2) {
            name = name.substring(2);
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String camelToSnake(String camel) {
        if (camel == null || camel.isEmpty()) return camel;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) result.append('_');
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String boxedType(String type) {
        return switch (type) {
            case "int" -> "Integer";
            case "long" -> "Long";
            case "double" -> "Double";
            case "float" -> "Float";
            case "boolean" -> "Boolean";
            case "byte" -> "Byte";
            case "short" -> "Short";
            case "char" -> "Character";
            default -> type;
        };
    }

    private String toClassLiteral(TypeMirror type) {
        String typeStr = type.toString();
        if (type.getKind().isPrimitive()) {
            return typeStr + ".class";
        }
        return typeStr + ".class";
    }

    private String escapeString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String formatEnumToSql(String enumValue) {
        if (enumValue == null) return "";
        return enumValue.replace("_", " ");
    }

    // ================= Inner Classes =================

    private static class ColumnInfo {
        String methodName;
        TypeMirror returnType;
        String returnTypeString;
        String columnName;
        String sqlType;
        boolean nullable;
        boolean unique;
        String defaultExpr;
        boolean primaryKey;
        boolean autoIncrement;
        boolean hasForeignKey;
        String fkReferencesDao;
        String fkReferencesTable;
        String fkReferencesColumn;
        String fkOnDelete;
        String fkOnUpdate;
        List<IndexInfo> indexes = new ArrayList<>();
    }

    private static class IndexInfo {
        String name;
        boolean unique;

        IndexInfo(String name, boolean unique) {
            this.name = name;
            this.unique = unique;
        }
    }
}


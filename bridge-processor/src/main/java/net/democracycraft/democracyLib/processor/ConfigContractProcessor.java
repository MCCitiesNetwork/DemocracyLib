package net.democracycraft.democracyLib.processor;


import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

@SupportedAnnotationTypes("net.democracycraft.democracyLib.api.config.Configurable")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class ConfigContractProcessor extends AbstractProcessor {

    private static final String CONFIGURABLE_ANNOTATION_TYPE = "net.democracycraft.democracyLib.api.config.Configurable";

    private Types types;
    private Elements elements;
    private TypeElement listElement;

    @Override
    public synchronized void init(javax.annotation.processing.ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
        this.listElement = this.elements.getTypeElement("java.util.List");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            if (annotation.getQualifiedName().contentEquals(CONFIGURABLE_ANNOTATION_TYPE)) {
                processAnnotation(annotation, roundEnv);
                return true;
            }
        }
        return false;
    }

    private void processAnnotation(TypeElement annotationType, RoundEnvironment roundEnv) {
        String packageName = processingEnv.getElementUtils().getPackageOf(annotationType).getQualifiedName().toString();
        String configValueName = packageName + ".ConfigValue";
        String generatedConfigName = packageName + ".GeneratedConfig";

        for (Element element : roundEnv.getElementsAnnotatedWith(annotationType)) {
            if (element.getKind() != ElementKind.CLASS) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "@Configurable can only be applied to classes", element);
                continue;
            }
            try {
                processConfigClass((TypeElement) element, annotationType.getQualifiedName().toString(), configValueName, generatedConfigName);
            } catch (Exception e) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate config for " + element.getSimpleName() + ": " + e.getMessage(), element);
            }
        }
    }

    private void processConfigClass(TypeElement classElement, String configurableName, String configValueName, String generatedConfigName) throws IOException {
        AnnotationMirror annotationMirror = getAnnotationMirror(classElement, configurableName);
        if (annotationMirror == null) {
            return;
        }

        String configName = getAnnotationValue(annotationMirror, "name", String.class);
        String targetPackage = getAnnotationValue(annotationMirror, "targetPackage", String.class);

        if (configName == null) configName = classElement.getSimpleName() + "Config";
        if (targetPackage == null) targetPackage = "";

        // Format handling
        Object formatObj = getAnnotationValue(annotationMirror, "format", Object.class);
        String extension = "YAML".equalsIgnoreCase(getEnumName(formatObj)) || "JSON".equalsIgnoreCase(getEnumName(formatObj)) ?
                (getEnumName(formatObj).equalsIgnoreCase("JSON") ? ".json" : ".yml") : ".yml";


        if (targetPackage.isEmpty()) {
            targetPackage = processingEnv.getElementUtils().getPackageOf(classElement).getQualifiedName().toString();
        }

        List<VariableElement> configFields = collectConfigurableFields(classElement, configValueName);

        Set<TypeElement> dependencyTypes = new HashSet<>();
        collectDependencies(configFields, dependencyTypes, configurableName);

        generateClass(targetPackage, configName, configFields, extension, configValueName, generatedConfigName, dependencyTypes);
    }

    private List<VariableElement> collectConfigurableFields(TypeElement classElement, String configValueName) {
        List<VariableElement> configFields = new ArrayList<>();
        Set<String> processedFieldNames = new HashSet<>();
        TypeElement currentElement = classElement;

        while (currentElement != null) {
            List<VariableElement> declaredFields = new ArrayList<>();

            for (Element enclosed : currentElement.getEnclosedElements()) {
                if (enclosed.getKind() == ElementKind.FIELD) {
                    AnnotationMirror fieldAnn = getAnnotationMirror(enclosed, configValueName);
                    if (fieldAnn != null) {
                        declaredFields.add((VariableElement) enclosed);
                    }
                }
            }

            for (VariableElement field : declaredFields) {
                String fieldName = field.getSimpleName().toString();
                if (processedFieldNames.add(fieldName)) {
                    configFields.add(field);
                }
            }

            TypeMirror superClassType = currentElement.getSuperclass();
            if (superClassType.getKind() == TypeKind.NONE) {
                break;
            }
            currentElement = (TypeElement) processingEnv.getTypeUtils().asElement(superClassType);
        }
        return configFields;
    }

    private void collectDependencies( List<VariableElement> fields, Set<TypeElement> dependencies, String configurableName) {
        for (VariableElement field : fields) {
            TypeMirror fieldType = field.asType();
            TypeElement typeElement = getTypeElement(fieldType);

            if (typeElement != null && !dependencies.contains(typeElement)) {
                if (isConfigurable(typeElement, configurableName)) {
                    dependencies.add(typeElement);
                    collectDependencies(collectConfigurableFields(typeElement, configurableName.replace("Configurable", "ConfigValue")), dependencies, configurableName);
                }
            }

            // Check if List of Configurable
            if (fieldType.getKind() == TypeKind.DECLARED) {
                DeclaredType declaredType = (DeclaredType) fieldType;
                if (types.isSameType(types.erasure(declaredType), types.erasure(listElement.asType())) && !declaredType.getTypeArguments().isEmpty()) {
                    TypeMirror genericType = declaredType.getTypeArguments().getFirst();
                    TypeElement genericElement = getTypeElement(genericType);
                    if (genericElement != null && !dependencies.contains(genericElement) && isConfigurable(genericElement, configurableName)) {
                        dependencies.add(genericElement);
                        collectDependencies(collectConfigurableFields(genericElement, configurableName.replace("Configurable", "ConfigValue")), dependencies, configurableName);
                    }
                }
            }
        }
    }

    private TypeElement getTypeElement(TypeMirror typeMirror) {
        if (typeMirror.getKind() == TypeKind.DECLARED) {
            return (TypeElement) ((DeclaredType) typeMirror).asElement();
        }
        return null;
    }

    private boolean isConfigurable(TypeElement typeElement, String configurableName) {
        return getAnnotationMirror(typeElement, configurableName) != null;
    }

    private void generateClass(String packageName, String className, List<VariableElement> fields, String extension, String configValueName, String generatedConfigName, Set<TypeElement> dependencyTypes) throws IOException {
        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(packageName + "." + className);
        try (PrintWriter printWriter = new PrintWriter(builderFile.openWriter())) {

            printWriter.println("package " + packageName + ";");
            printWriter.println();

            Set<String> imports = new TreeSet<>();
            imports.add("org.bukkit.configuration.file.YamlConfiguration");
            imports.add("org.bukkit.configuration.InvalidConfigurationException");
            imports.add("org.bukkit.configuration.ConfigurationSection");
            imports.add("java.io.File");
            imports.add("java.io.IOException");
            imports.add("java.util.List");
            imports.add("java.util.ArrayList");
            imports.add("java.util.Map");
            imports.add("java.util.HashMap");
            imports.add("java.util.LinkedHashMap");
            imports.add("java.util.concurrent.CompletableFuture");
            imports.add("java.lang.invoke.MethodHandles");
            imports.add("java.lang.invoke.VarHandle");
            imports.add(generatedConfigName);

            for (VariableElement field : fields) {
                extractImports(field.asType().toString(), imports);
            }
            for (TypeElement dep : dependencyTypes) {
                imports.add(dep.getQualifiedName().toString());
                imports.add(getGeneratedConfigClassName(dep));
            }

            for (String importString : imports) {
                printWriter.println("import " + importString + ";");
            }
            printWriter.println();

            printWriter.println("public class " + className + " implements GeneratedConfig {");
            printWriter.println();
            printWriter.println("    private File file;");
            printWriter.println("    private YamlConfiguration yamlConfiguration;");
            printWriter.println();
            printWriter.println("    public static final String DEFAULT_FILENAME = \"" + className + extension + "\";");
            printWriter.println();

            // VarHandles
            for (TypeElement dep : dependencyTypes) {
                List<VariableElement> depFields = collectConfigurableFields(dep, configValueName);
                for (VariableElement field : depFields) {
                    String handleName = getHandleName(dep, field);
                    printWriter.println("    private static final VarHandle " + handleName + ";");
                }
            }
            if (!dependencyTypes.isEmpty()) {
                printWriter.println();
                printWriter.println("    static {");
                printWriter.println("        MethodHandles.Lookup lookup = MethodHandles.lookup();");
                printWriter.println("        try {");

                for (TypeElement dep : dependencyTypes) {
                    String depClassName = dep.getQualifiedName().toString();
                    List<VariableElement> depFields = collectConfigurableFields(dep, configValueName);
                    for (VariableElement field : depFields) {
                        String handleName = getHandleName(dep, field);
                        String fieldName = field.getSimpleName().toString();
                        // Assume we can access private fields via privateLookupIn if Java 9+, OR use traditional reflection set accessible if handles fail?
                        // But users requested MethodHandles using privateLookupIn or plain if accessible.
                        // Since we are in potentially different package, we need privateLookupIn.
                        printWriter.println("            " + handleName + " = MethodHandles.privateLookupIn(" + depClassName + ".class, lookup).findVarHandle(" + depClassName + ".class, \"" + fieldName + "\", " + types.erasure(field.asType()).toString() + ".class);");
                    }
                }

                printWriter.println("        } catch (ReflectiveOperationException exception) {");
                printWriter.println("            throw new ExceptionInInitializerError(exception);");
                printWriter.println("        }");
                printWriter.println("    }");
            }
            printWriter.println();

            // Fields
            for (VariableElement field : fields) {
                String type = simplifyType(field.asType().toString());
                printWriter.println("    private " + type + " " + field.getSimpleName() + ";");
            }
            printWriter.println();

            // Constructor
            printWriter.println("    public " + className + "() {");
            printWriter.println("    }");
            printWriter.println();

            // Init
            printWriter.println("    @Override");
            printWriter.println("    public void init(File file) {");
            printWriter.println("        this.file = file;");
            printWriter.println("        this.yamlConfiguration = new YamlConfiguration();");
            printWriter.println("    }");
            printWriter.println();

            // Factory
            printWriter.println("    public static " + className + " create(File file) {");
            printWriter.println("        " + className + " configuration = new " + className + "();");
            printWriter.println("        configuration.init(file);");
            printWriter.println("        return configuration;");
            printWriter.println("    }");
            printWriter.println();

            // Getters
            for (VariableElement field : fields) {
                String fieldName = field.getSimpleName().toString();
                String type = simplifyType(field.asType().toString());
                String getterPrefix = type.equalsIgnoreCase("boolean") ? "is" : "get";
                String getterName = getterPrefix + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

                printWriter.println("    public " + type + " " + getterName + "() {");
                printWriter.println("        return this." + fieldName + ";");
                printWriter.println("    }");
            }
            printWriter.println();

            // Serialization Helpers
            for (TypeElement dep : dependencyTypes) {
                generateDependencyHelpers(printWriter, dep, configValueName);
            }

            // Load Method (Sync)
            printWriter.println("    public void load() {");
            printWriter.println("        try {");
            printWriter.println("            if (file.exists()) {");
            printWriter.println("                yamlConfiguration.load(file);");
            printWriter.println("            }");
            printWriter.println("        } catch (IOException | InvalidConfigurationException exception) {");
            printWriter.println("            exception.printStackTrace();");
            printWriter.println("        }");
            printWriter.println();

            for (VariableElement field : fields) {
                generateFieldLoad(printWriter, field, configValueName, dependencyTypes);
            }

            printWriter.println("    }");
            printWriter.println();

            // Load Async
            printWriter.println("    public CompletableFuture<Void> loadAsync() {");
            printWriter.println("        return CompletableFuture.runAsync(this::load);");
            printWriter.println("    }");
            printWriter.println();

            // Save Method (Sync)
            printWriter.println("    public void save() {");
            for (VariableElement field : fields) {
                generateFieldSave(printWriter, field, configValueName, dependencyTypes);
            }
            printWriter.println("        try {");
            printWriter.println("            yamlConfiguration.save(file);");
            printWriter.println("        } catch (IOException exception) {");
            printWriter.println("            exception.printStackTrace();");
            printWriter.println("        }");
            printWriter.println("    }");
            printWriter.println();

            // Save Async
            printWriter.println("    public CompletableFuture<Void> saveAsync() {");
            printWriter.println("        return CompletableFuture.runAsync(this::save);");
            printWriter.println("    }");
            printWriter.println();

            // LoadOrCreate Method (Sync)
            printWriter.println("    public void loadOrCreate() {");
            printWriter.println("        if (file.exists()) {");
            printWriter.println("            load();");
            printWriter.println("        } else {");
            printWriter.println("            save();");
            printWriter.println("        }");
            printWriter.println("    }");
            printWriter.println();

             // LoadOrCreate Async
            printWriter.println("    public CompletableFuture<Void> loadOrCreateAsync() {");
            printWriter.println("        return CompletableFuture.runAsync(this::loadOrCreate);");
            printWriter.println("    }");

            printWriter.println("}");
        }
    }

    private String getHandleName(TypeElement type, VariableElement field) {
        String typeName = type.getQualifiedName().toString().replace(".", "_");
        return "HANDLE_" + typeName + "_" + field.getSimpleName();
    }

    private void generateDependencyHelpers(PrintWriter printWriter, TypeElement typeElement, String configValueName) {
        String typeName = typeElement.getSimpleName().toString();
        String qualifiedName = typeElement.getQualifiedName().toString();
        List<VariableElement> fields = collectConfigurableFields(typeElement, configValueName);

        // Serialize
        printWriter.println("    private Map<String, Object> serialize" + typeName + "(" + qualifiedName + " instance) {");
        printWriter.println("        Map<String, Object> map = new LinkedHashMap<>();");
        for (VariableElement field : fields) {
            AnnotationMirror annotationMirror = getAnnotationMirror(field, configValueName);
            if(annotationMirror == null) continue;
            String path = getAnnotationValue(annotationMirror, "fieldName", String.class);
            String handleName = getHandleName(typeElement, field);

            // Nested check
            TypeMirror fieldType = field.asType();
            TypeElement fieldTypeElement = getTypeElement(fieldType);
            boolean isNested = fieldTypeElement != null && isConfigurable(fieldTypeElement, configValueName.replace("ConfigValue", "Configurable"));

            if (isNested) {
                 printWriter.println("        map.put(\"" + path + "\", serialize" + fieldTypeElement.getSimpleName() + "((" + fieldTypeElement.getQualifiedName() + ") " + handleName + ".get(instance)));");
            } else {
                 printWriter.println("        map.put(\"" + path + "\", " + handleName + ".get(instance));");
            }
        }
        printWriter.println("        return map;");
        printWriter.println("    }");
        printWriter.println();

        // Deserialize
        printWriter.println("    private " + qualifiedName + " deserialize" + typeName + "(Map<String, Object> map) {");
        // We need a way to instantiate. Assume no-args constructor
        printWriter.println("        " + qualifiedName + " instance = new " + qualifiedName + "();");
        printWriter.println("        if (map == null) return instance;");

        for (VariableElement field : fields) {
            AnnotationMirror annotationMirror = getAnnotationMirror(field, configValueName);
            if(annotationMirror == null) continue;
            String path = getAnnotationValue(annotationMirror, "fieldName", String.class);
            String handleName = getHandleName(typeElement, field);
            String fieldTypeStr = field.asType().toString();

             // Nested check
            TypeMirror fieldType = field.asType();
            TypeElement fieldTypeElement = getTypeElement(fieldType);
            boolean isNested = fieldTypeElement != null && isConfigurable(fieldTypeElement, configValueName.replace("ConfigValue", "Configurable"));

            printWriter.println("        if (map.containsKey(\"" + path + "\")) {");
            if (isNested) {
                 // Map<String, Object> nestedMap = (Map) map.get(path)
                 printWriter.println("            " + handleName + ".set(instance, deserialize" + fieldTypeElement.getSimpleName() + "((Map<String, Object>) map.get(\"" + path + "\")));");
            } else if (fieldTypeStr.equals("int") || fieldTypeStr.equals("java.lang.Integer")) {
                 printWriter.println("            " + handleName + ".set(instance, (int) map.get(\"" + path + "\"));");
            } // ... add other types ... simplified for brevity, using casts mainly
            else {
                 printWriter.println("            " + handleName + ".set(instance, (" + simplifyType(fieldTypeStr) + ") map.get(\"" + path + "\"));");
            }
            printWriter.println("        }");
        }
        printWriter.println("        return instance;");
        printWriter.println("    }");
        printWriter.println();
    }

    private void generateFieldLoad(PrintWriter printWriter, VariableElement field, String configValueName, Set<TypeElement> dependencyTypes) {
        AnnotationMirror cv = getAnnotationMirror(field, configValueName);
        if (cv == null) return;
        String path = getAnnotationValue(cv, "fieldName", String.class);
        String fieldName = field.getSimpleName().toString();
        String typeFqn = field.asType().toString();

        // Check if nested
        TypeElement typeElement = getTypeElement(field.asType());
        boolean isNested = typeElement != null && dependencyTypes.contains(typeElement);

        // Check if List<Nested>
        boolean isListNested = false;
        TypeElement listGenericType = null;
        if (isListType(field.asType())) {
            DeclaredType dt = (DeclaredType) field.asType();
             if (!dt.getTypeArguments().isEmpty()) {
                 listGenericType = getTypeElement(dt.getTypeArguments().getFirst());
                 if (listGenericType != null && dependencyTypes.contains(listGenericType)) {
                     isListNested = true;
                 }
             }
        }

        printWriter.println("        if (yamlConfiguration.contains(\"" + path + "\")) {");
        if (isNested) {
             printWriter.println("            this." + fieldName + " = deserialize" + typeElement.getSimpleName() + "(yamlConfiguration.getConfigurationSection(\"" + path + "\").getValues(false));");
        } else if (isListNested) {
             printWriter.println("            List<Map<?, ?>> listMaps = yamlConfiguration.getMapList(\"" + path + "\");");
             printWriter.println("            List<" + listGenericType.getQualifiedName() + "> listObjects = new ArrayList<>();");
             printWriter.println("            for (Map<?, ?> map : listMaps) {");
             printWriter.println("                listObjects.add(deserialize" + listGenericType.getSimpleName() + "((Map<String, Object>) map));");
             printWriter.println("            }");
             printWriter.println("            this." + fieldName + " = listObjects;");
        } else if (typeFqn.equals("int") || typeFqn.equals("java.lang.Integer")) {
             printWriter.println("            this." + fieldName + " = yamlConfiguration.getInt(\"" + path + "\");");
        } else if (typeFqn.equals("double") || typeFqn.equals("java.lang.Double")) {
            printWriter.println("            this." + fieldName + " = yamlConfiguration.getDouble(\"" + path + "\");");
        } else if (typeFqn.equals("boolean") || typeFqn.equals("java.lang.Boolean")) {
            printWriter.println("            this." + fieldName + " = yamlConfiguration.getBoolean(\"" + path + "\");");
        } else if (typeFqn.equals("long") || typeFqn.equals("java.lang.Long")) {
            printWriter.println("            this." + fieldName + " = yamlConfiguration.getLong(\"" + path + "\");");
        } else if (typeFqn.equals("java.lang.String")) {
            printWriter.println("            this." + fieldName + " = yamlConfiguration.getString(\"" + path + "\");");
        } else if (isListType(field.asType())) {
             printWriter.println("            this." + fieldName + " = (List) yamlConfiguration.getList(\"" + path + "\");");
        } else {
             printWriter.println("            this." + fieldName + " = (" + simplifyType(typeFqn) + ") yamlConfiguration.get(\"" + path + "\");");
        }
        printWriter.println("        }");
    }

    private void generateFieldSave(PrintWriter printWriter, VariableElement field, String configValueName, Set<TypeElement> dependencyTypes) {
        AnnotationMirror cv = getAnnotationMirror(field, configValueName);
        if (cv == null) return;
        String path = getAnnotationValue(cv, "fieldName", String.class);

        // Check nested
        TypeElement typeElement = getTypeElement(field.asType());
        boolean isNested = typeElement != null && dependencyTypes.contains(typeElement);

        // Check List<Nested>
        boolean isListNested = false;
        TypeElement listGenericType = null;
        if (isListType(field.asType())) {
            DeclaredType dt = (DeclaredType) field.asType();
             if (!dt.getTypeArguments().isEmpty()) {
                 listGenericType = getTypeElement(dt.getTypeArguments().getFirst());
                 if (listGenericType != null && dependencyTypes.contains(listGenericType)) {
                     isListNested = true;
                 }
             }
        }

        if (isNested) {
            printWriter.println("        if (this." + field.getSimpleName() + " != null) {");
            printWriter.println("            yamlConfiguration.set(\"" + path + "\", serialize" + typeElement.getSimpleName() + "(this." + field.getSimpleName() + "));");
            printWriter.println("        }");
        } else if (isListNested) {
             printWriter.println("        if (this." + field.getSimpleName() + " != null) {");
             printWriter.println("            List<Map<String, Object>> listMaps = new ArrayList<>();");
             printWriter.println("            for (" + listGenericType.getQualifiedName() + " item : this." + field.getSimpleName() + ") {");
             printWriter.println("                listMaps.add(serialize" + listGenericType.getSimpleName() + "(item));");
             printWriter.println("            }");
             printWriter.println("            yamlConfiguration.set(\"" + path + "\", listMaps);");
             printWriter.println("        }");
        } else {
            printWriter.println("        yamlConfiguration.set(\"" + path + "\", this." + field.getSimpleName() + ");");
        }
    }

    private void extractImports(String typeString, Set<String> imports) {
        String[] parts = typeString.split("[<>,\\s]+");
        for (String part : parts) {
            if (part.contains(".") && !part.startsWith("java.lang.")) {
                imports.add(part);
            }
        }
    }

    private String simplifyType(String typeString) {
        return typeString.replaceAll("\\b(?:[a-z0-9_]+\\.)+([A-Z]\\w*)", "$1");
    }

    // Helper methods
    private AnnotationMirror getAnnotationMirror(Element element, String annotationName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (((TypeElement) mirror.getAnnotationType().asElement()).getQualifiedName().toString().equals(annotationName)) {
                return mirror;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T getAnnotationValue(AnnotationMirror mirror, String key, Class<T> type) {
        // Iterate over the element values to find the one matching the key
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().toString().equals(key)) {
                Object value = entry.getValue().getValue();
                if (type.isInstance(value)) {
                    return type.cast(value);
                }
                return (T) value;
            }
        }
        return null;
    }

    private String getEnumName(Object obj) {
        if (obj == null) return "YAML";
        return obj.toString().substring(obj.toString().lastIndexOf('.') + 1);
    }

    private String getGeneratedConfigClassName(TypeElement element) {
        String packageName = getGeneratedConfigPackage(element);
        String simpleName = getGeneratedSimpleClassName(element);
        return packageName + "." + simpleName;
    }

    private String getGeneratedSimpleClassName(TypeElement element) {

        // This is a bit tricky since we don't have the name passed to this method
        // However, we can look for the annotation matching by name.

        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
             if (mirror.getAnnotationType().asElement().getSimpleName().contentEquals("Configurable")) {
                 String name = getAnnotationValue(mirror, "name", String.class);
                 if (name != null && !name.isEmpty()) return name;
             }
        }
        return element.getSimpleName() + "Config";
    }

    private String getGeneratedConfigPackage(TypeElement element) {
         for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
             if (mirror.getAnnotationType().asElement().getSimpleName().contentEquals("Configurable")) {
                 String targetPackage = getAnnotationValue(mirror, "targetPackage", String.class);
                 if (targetPackage != null && !targetPackage.isEmpty()) return targetPackage;
             }
        }
        return processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
    }

    private boolean isListType(TypeMirror typeMirror) {
         if (typeMirror.getKind() == TypeKind.DECLARED) {
             DeclaredType declaredType = (DeclaredType) typeMirror;
             return types.isSameType(types.erasure(declaredType), types.erasure(listElement.asType()));
         }
         return false;
    }
}

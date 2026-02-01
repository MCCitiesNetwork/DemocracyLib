package net.democracycraft.democracyLib.processor;


import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
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

@AutoService(Processor.class)
@SupportedAnnotationTypes("net.democracycraft.democracyLib.api.config.Configurable")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class ConfigContractProcessor extends AbstractProcessor {

    private static final String CONFIGURABLE_ANNOTATION_TYPE = "net.democracycraft.democracyLib.api.config.Configurable";

    private Types types;
    private TypeElement listElement;

    @Override
    public synchronized void init(javax.annotation.processing.ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.types = processingEnv.getTypeUtils();
        Elements elements = processingEnv.getElementUtils();
        this.listElement = elements.getTypeElement("java.util.List");
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
                e.printStackTrace();
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

        Map<String, ConfigItem> configItems = new java.util.LinkedHashMap<>();
        collectConfigItems(classElement, configItems, configValueName);

        generateClass(classElement, targetPackage, configName, configItems, extension, configValueName, generatedConfigName);
    }

    private static class ConfigItem {
        String name;
        String comment;
        TypeMirror type;
        String fieldName; // Internal variable name
        Element sourceElement;

        ConfigItem(String name, String comment, TypeMirror type, String fieldName, Element sourceElement) {
            this.name = name;
            this.comment = comment;
            this.type = type;
            this.fieldName = fieldName;
            this.sourceElement = sourceElement;
        }
    }

    private void collectConfigItems(TypeElement element, Map<String, ConfigItem> items, String configValueName) {
        // Recursive search in Interfaces
        for (TypeMirror iface : element.getInterfaces()) {
            collectConfigItems((TypeElement) ((DeclaredType) iface).asElement(), items, configValueName);
        }

        // Recursive search in Superclass
        TypeMirror superclass = element.getSuperclass();
        if (superclass.getKind() != TypeKind.NONE && !superclass.toString().equals("java.lang.Object")) {
            collectConfigItems((TypeElement) ((DeclaredType) superclass).asElement(), items, configValueName);
        }

        for (Element enclosed : element.getEnclosedElements()) {
            AnnotationMirror ann = getAnnotationMirror(enclosed, configValueName);
            if (ann != null) {
                String name = getAnnotationValue(ann, "fieldName", String.class);
                String comment = getAnnotationValue(ann, "comment", String.class);
                if (comment == null) comment = "";

                if (enclosed.getKind() == ElementKind.METHOD) {
                    ExecutableElement method = (ExecutableElement) enclosed;
                    if (name == null || name.isEmpty()) {
                        name = method.getSimpleName().toString();
                    }
                    // For methods, we use the method name as the key if not specified,
                    // and also as the internal field name (to avoid conflicts, maybe prefix?)
                    // if we implement the interface, the method name is fixed
                    items.put(name, new ConfigItem(name, comment, method.getReturnType(), method.getSimpleName().toString(), enclosed));
                } else if (enclosed.getKind() == ElementKind.FIELD) {
                    VariableElement field = (VariableElement) enclosed;
                    if (name == null || name.isEmpty()) {
                        name = field.getSimpleName().toString();
                    }
                    items.put(name, new ConfigItem(name, comment, field.asType(), field.getSimpleName().toString(), enclosed));
                }
            }
        }
    }

    private void generateClass(TypeElement sourceElement, String packageName, String className, Map<String, ConfigItem> items, String extension, String configValueName, String generatedConfigName) throws IOException {
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
            imports.add("java.util.concurrent.CompletableFuture");
            imports.add(generatedConfigName);

            // Import types from items
            for (ConfigItem item : items.values()) {
                extractImports(item.type.toString(), imports);
            }
            // Import source element if different package
             if (!processingEnv.getElementUtils().getPackageOf(sourceElement).getQualifiedName().toString().equals(packageName)) {
                imports.add(sourceElement.getQualifiedName().toString());
            }

            for (String importString : imports) {
                printWriter.println("import " + importString + ";");
            }
            printWriter.println();

            if (sourceElement.getKind() == ElementKind.INTERFACE) {
                printWriter.println("public class " + className + " implements " + sourceElement.getSimpleName() + ", GeneratedConfig {");
            } else {
                printWriter.println("public class " + className + " implements GeneratedConfig {");
            }
            printWriter.println();
            printWriter.println("    private File file;");
            printWriter.println("    private YamlConfiguration yamlConfiguration;");
            printWriter.println();
            printWriter.println("    public static final String DEFAULT_FILENAME = \"" + className + extension + "\";");
            printWriter.println();

            // Fields
            for (ConfigItem item : items.values()) {
                printWriter.println("    private " + item.type.toString() + " " + item.fieldName + ";");
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

            // Implementations (Getters/Methods)
            for (ConfigItem item : items.values()) {
                if (item.sourceElement.getKind() == ElementKind.METHOD) {
                    if (sourceElement.getKind() == ElementKind.INTERFACE) {
                        printWriter.println("    @Override");
                    }
                    printWriter.println("    public " + item.type.toString() + " " + item.sourceElement.getSimpleName() + "() {");
                    printWriter.println("        return this." + item.fieldName + ";");
                    printWriter.println("    }");
                } else {
                    String fieldName = item.fieldName;
                    String type = item.type.toString();
                    String getterPrefix = type.equalsIgnoreCase("boolean") ? "is" : "get";
                    String getterName = getterPrefix + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

                    printWriter.println("    public " + type + " " + getterName + "() {");
                    printWriter.println("        return this." + item.fieldName + ";");
                    printWriter.println("    }");
                }
            }
            printWriter.println();

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

            for (ConfigItem item : items.values()) {
                generateItemLoad(printWriter, item);
            }

            printWriter.println("    }");
            printWriter.println();

            // Load Async
            printWriter.println("    public CompletableFuture<Void> loadAsync() {");
            printWriter.println("        return CompletableFuture.runAsync(this::load);");
            printWriter.println("    }");
            printWriter.println();

            // Save Method (Sync) with COMMENTS
            printWriter.println("    public void save() {");
            for (ConfigItem item : items.values()) {
                generateItemSave(printWriter, item);
                if (item.comment != null && !item.comment.isEmpty()) {
                     // Bukkit API for comments
                     printWriter.println("        yamlConfiguration.setComments(\"" + item.name + "\", java.util.List.of(\"" + item.comment + "\"));");
                }
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

    private void generateItemLoad(PrintWriter printWriter, ConfigItem item) {
        String path = item.name;
        String fieldName = item.fieldName;
        String typeFqn = item.type.toString(); // Full Qualified Name or Simple if imported

        // Since we are creating source code, we rely on the imports and simplified names used in field declarations
        // But typeFqn from TypeMirror is usually fully qualified
        // We have to match what Bukkit YamlConfiguration returns

        printWriter.println("        if (yamlConfiguration.contains(\"" + path + "\")) {");

        // Basic Types
        if (typeFqn.equals("int") || typeFqn.equals("java.lang.Integer")) {
            printWriter.println("            this." + fieldName + " = yamlConfiguration.getInt(\"" + path + "\");");
        } else if (typeFqn.equals("double") || typeFqn.equals("java.lang.Double")) {
            printWriter.println("            this." + fieldName + " = yamlConfiguration.getDouble(\"" + path + "\");");
        } else if (typeFqn.equals("boolean") || typeFqn.equals("java.lang.Boolean")) {
            printWriter.println("            this." + fieldName + " = yamlConfiguration.getBoolean(\"" + path + "\");");
        } else if (typeFqn.equals("long") || typeFqn.equals("java.lang.Long")) {
            printWriter.println("            this." + fieldName + " = yamlConfiguration.getLong(\"" + path + "\");");
        } else if (typeFqn.equals("java.lang.String")) {
            printWriter.println("            this." + fieldName + " = yamlConfiguration.getString(\"" + path + "\");");
        } else if (isListType(item.type)) {
             printWriter.println("            this." + fieldName + " = (List) yamlConfiguration.getList(\"" + path + "\");");
        } else {
            // Fallback for serializable objects
            printWriter.println("            this." + fieldName + " = (" + item.type.toString() + ") yamlConfiguration.get(\"" + path + "\");");
        }
        printWriter.println("        }");
    }

    private void generateItemSave(PrintWriter printWriter, ConfigItem item) {
        String path = item.name;
        String fieldName = item.fieldName;
        String typeFqn = item.type.toString();

        // For primitive types, just set the value directly (they have default values like 0, false, etc.)
        if (isPrimitiveType(typeFqn)) {
            printWriter.println("        yamlConfiguration.set(\"" + path + "\", this." + fieldName + ");");
        } else {
            // For reference types (String, objects, lists, etc.), we need to ensure the key is always written
            // even when the value is null, so the comment appears in the file.
            // When null, we write an empty string "" so the field appears with its comment.
            printWriter.println("        if (this." + fieldName + " != null) {");
            printWriter.println("            yamlConfiguration.set(\"" + path + "\", this." + fieldName + ");");
            printWriter.println("        } else {");
            printWriter.println("            yamlConfiguration.set(\"" + path + "\", \"\");");
            printWriter.println("        }");
        }
    }

    private boolean isPrimitiveType(String typeFqn) {
        return typeFqn.equals("int") || typeFqn.equals("double") || typeFqn.equals("boolean") ||
               typeFqn.equals("long") || typeFqn.equals("float") || typeFqn.equals("short") ||
               typeFqn.equals("byte") || typeFqn.equals("char");
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
        // However, we can look for the annotation matching by name
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




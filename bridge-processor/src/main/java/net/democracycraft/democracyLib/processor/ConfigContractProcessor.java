package net.democracycraft.democracyLib.processor;

import net.democracycraft.democracyLib.api.config.Configurable;
import net.democracycraft.democracyLib.api.config.ConfigValue;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.type.TypeKind;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class ConfigContractProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            if (isConfigurableAnnotation(annotation)) {
                processAnnotation(annotation, roundEnv);
            }
        }
        return false;
    }

    private boolean isConfigurableAnnotation(TypeElement annotation) {
        if (!annotation.getSimpleName().contentEquals("Configurable")) {
            return false;
        }
        boolean hasName = false;
        boolean hasTargetPackage = false;
        boolean hasFormat = false;

        for (Element enclosed : annotation.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                String name = enclosed.getSimpleName().toString();
                if (name.equals("name")) hasName = true;
                else if (name.equals("targetPackage")) hasTargetPackage = true;
                else if (name.equals("format")) hasFormat = true;
            }
        }
        return hasName && hasTargetPackage && hasFormat;
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
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate config for " + element.getSimpleName() + ": " + e.getMessage());
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
        String formatName = formatObj != null ? formatObj.toString() : "YAML";
        String extension = "YAML".equalsIgnoreCase(getEnumName(formatObj)) || "JSON".equalsIgnoreCase(getEnumName(formatObj)) ?
                (getEnumName(formatObj).equalsIgnoreCase("JSON") ? ".json" : ".yml") : ".yml";


        if (targetPackage.isEmpty()) {
            targetPackage = processingEnv.getElementUtils().getPackageOf(classElement).getQualifiedName().toString();
        }

        List<VariableElement> configFields = new ArrayList<>();
        Set<String> processedFieldNames = new HashSet<>();
        TypeElement currentElement = classElement;

        while (currentElement != null) {
            String qualifiedName = currentElement.getQualifiedName().toString();
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


        generateClass(targetPackage, configName, configFields, extension, configValueName, generatedConfigName);
    }

    private void generateClass(String packageName, String className, List<VariableElement> fields, String extension, String configValueName, String generatedConfigName) throws IOException {
        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(packageName + "." + className);
        try (PrintWriter printWriter = new PrintWriter(builderFile.openWriter())) {

            printWriter.println("package " + packageName + ";");
            printWriter.println();

            Set<String> imports = new TreeSet<>();
            imports.add("org.bukkit.configuration.file.YamlConfiguration");
            imports.add("org.bukkit.configuration.InvalidConfigurationException");
            imports.add("java.io.File");
            imports.add("java.io.IOException");
            imports.add("java.util.List");
            imports.add("java.util.concurrent.CompletableFuture");
            imports.add(generatedConfigName);

            for (VariableElement field : fields) {
                extractImports(field.asType().toString(), imports);
            }

            for (String imp : imports) {
                printWriter.println("import " + imp + ";");
            }
            printWriter.println();

            printWriter.println("public class " + className + " implements GeneratedConfig {");
            printWriter.println();
            printWriter.println("    private File file;");
            printWriter.println("    private YamlConfiguration yaml;");
            printWriter.println();

            // Default Filename Constant
            printWriter.println("    public static final String DEFAULT_FILENAME = \"" + className + extension + "\";");
            // WAIT, annotation.name() was used before. I need configName passed here.
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
            printWriter.println("        this.yaml = new YamlConfiguration();");
            printWriter.println("    }");
            printWriter.println();

            // Factory
            printWriter.println("    public static " + className + " create(File file) {");
            printWriter.println("        " + className + " config = new " + className + "();");
            printWriter.println("        config.init(file);");
            printWriter.println("        return config;");
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

            // Load Method (Sync)
            printWriter.println("    public void load() {");
            printWriter.println("        try {");
            printWriter.println("            if (file.exists()) {");
            printWriter.println("                yaml.load(file);");
            printWriter.println("            }");
            printWriter.println("        } catch (IOException | InvalidConfigurationException e) {");
            printWriter.println("            e.printStackTrace();");
            printWriter.println("        }");
            printWriter.println();
            for (VariableElement field : fields) {
                AnnotationMirror cv = getAnnotationMirror(field, configValueName);
                if (cv == null) continue;

                String path = getAnnotationValue(cv, "fieldName", String.class);

                String fieldName = field.getSimpleName().toString();
                String typeFqn = field.asType().toString();
                String simpleType = simplifyType(typeFqn);

                printWriter.println("        if (yaml.contains(\"" + path + "\")) {");
                if (typeFqn.equals("int") || typeFqn.equals("java.lang.Integer")) {
                     printWriter.println("            this." + fieldName + " = yaml.getInt(\"" + path + "\");");
                } else if (typeFqn.equals("double") || typeFqn.equals("java.lang.Double")) {
                    printWriter.println("            this." + fieldName + " = yaml.getDouble(\"" + path + "\");");
                } else if (typeFqn.equals("boolean") || typeFqn.equals("java.lang.Boolean")) {
                    printWriter.println("            this." + fieldName + " = yaml.getBoolean(\"" + path + "\");");
                } else if (typeFqn.equals("long") || typeFqn.equals("java.lang.Long")) {
                    printWriter.println("            this." + fieldName + " = yaml.getLong(\"" + path + "\");");
                } else if (typeFqn.equals("java.lang.String")) {
                    printWriter.println("            this." + fieldName + " = yaml.getString(\"" + path + "\");");
                } else if (typeFqn.startsWith("java.util.List")) {
                     printWriter.println("            this." + fieldName + " = (List) yaml.getList(\"" + path + "\");");
                } else {
                     printWriter.println("            this." + fieldName + " = (" + simpleType + ") yaml.get(\"" + path + "\");");
                }
                printWriter.println("        }");
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
                AnnotationMirror cv = getAnnotationMirror(field, configValueName);
                if (cv != null) {
                    String path = getAnnotationValue(cv, "fieldName", String.class);
                    printWriter.println("        yaml.set(\"" + path + "\", this." + field.getSimpleName() + ");");
                }
            }
            printWriter.println("        try {");
            printWriter.println("            yaml.save(file);");
            printWriter.println("        } catch (IOException e) {");
            printWriter.println("            e.printStackTrace();");
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

    private <T> T getAnnotationValue(AnnotationMirror mirror, String key, Class<T> type) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().toString().equals(key)) {
                Object value = entry.getValue().getValue();
                if (type.isInstance(value)) {
                    return type.cast(value);
                }
                // Handle Enum likely (VariableElement)
                return (T) value;
            }
        }
        return null;
    }

    private String getEnumName(Object obj) {
        if (obj == null) return "YAML";
        return obj.toString().substring(obj.toString().lastIndexOf('.') + 1);
    }
}

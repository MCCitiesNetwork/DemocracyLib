package net.democracycraft.democracyLib.api.bootstrap.contract.processor;

import net.democracycraft.democracyLib.api.bootstrap.contract.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Build-time generator for a reflection bridge contract.
 * <p>
 * This processor scans bridge APIs/methods annotated with {@link BridgeApi}/{@link BridgeMethod}
 * and generates a deterministic contract class with method identifiers and signatures.
 * <p>
 * Runtime code should use the generated class and avoid inlining method names.
 */
@SupportedAnnotationTypes({
        "net.democracycraft.democracyLib.api.bootstrap.contract.BridgeApi",
        "net.democracycraft.democracyLib.api.bootstrap.contract.BridgeMethod",
        "net.democracycraft.democracyLib.api.bootstrap.contract.BridgeAnchorKey",
        "net.democracycraft.democracyLib.api.bootstrap.contract.BridgeContractVersion",
        "net.democracycraft.democracyLib.api.bootstrap.contract.BridgeOverloadKey"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class BridgeContractProcessor extends AbstractProcessor {

    private static final String GENERATED_PKG = "net.democracycraft.democracyLib.api.bootstrap";
    private static final String GENERATED_NAME = "GeneratedBridgeContract";

    private Types types;
    private Elements elements;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        this.types = processingEnvironment.getTypeUtils();
        this.elements = processingEnvironment.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        if (roundEnvironment.processingOver()) return false;

        Map<String, AnchorKey> anchorKeys = collectAnchorKeys(roundEnvironment);
        List<ApiSurface> apis = collectApis(roundEnvironment);
        Integer protocol = collectProtocolVersion(roundEnvironment);

        if (anchorKeys.isEmpty() && apis.isEmpty() && protocol == null) {
            return false;
        }

        try {
            writeGenerated(anchorKeys, apis, protocol);
        } catch (Exception e) {
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.ERROR, "Failed generating " + GENERATED_NAME + ": " + e.getMessage());
        }

        return false;
    }

    private Map<String, AnchorKey> collectAnchorKeys(RoundEnvironment roundEnvironment) {
        Map<String, AnchorKey> keys = new TreeMap<>();
        for (Element element : roundEnvironment.getElementsAnnotatedWith(BridgeAnchorKey.class)) {
            if (element.getKind() != ElementKind.FIELD) continue;
            VariableElement fieldElement = (VariableElement) element;

            if (!fieldElement.getModifiers().contains(Modifier.STATIC) || !fieldElement.getModifiers().contains(Modifier.FINAL)) {
                error(fieldElement, "@BridgeAnchorKey must be placed on static final fields.");
                continue;
            }

            String value = extractStringConstant(fieldElement);
            if (value == null) {
                error(fieldElement, "@BridgeAnchorKey requires a compile-time String constant initializer.");
                continue;
            }

            String fieldName = fieldElement.getSimpleName().toString();
            keys.put(fieldName, new AnchorKey(fieldName, value));
        }
        return keys;
    }

    private List<ApiSurface> collectApis(RoundEnvironment roundEnvironment) {
        List<ApiSurface> apiSurfaces = new ArrayList<>();
        for (Element element : roundEnvironment.getElementsAnnotatedWith(BridgeApi.class)) {
            if (!(element instanceof TypeElement typeElement)) continue;

            BridgeNamespace namespace = typeElement.getAnnotation(BridgeApi.class).value();
            ApiSurface apiSurface = new ApiSurface(namespace.name());
            apiSurface.typeFqn = elements.getBinaryName(typeElement).toString();

            for (Element enclosedElement : typeElement.getEnclosedElements()) {
                if (enclosedElement.getKind() != ElementKind.METHOD) continue;
                ExecutableElement methodElement = (ExecutableElement) enclosedElement;
                BridgeMethod bridgeMethod = methodElement.getAnnotation(BridgeMethod.class);
                if (bridgeMethod == null) continue;

                apiSurface.methods.add(buildMethodSpec(namespace, methodElement, bridgeMethod));
            }

            apiSurfaces.add(apiSurface);
        }

        // deterministic order
        apiSurfaces.sort(Comparator.comparing(apiSurface -> apiSurface.namespace));
        for (ApiSurface apiSurface : apiSurfaces) {
            apiSurface.methods.sort(Comparator.comparing(methodSpec -> methodSpec.id));
        }

        return apiSurfaces;
    }

    private MethodSpec buildMethodSpec(BridgeNamespace namespace, ExecutableElement methodElement, BridgeMethod bridgeMethod) {
        MethodSpec spec = new MethodSpec();
        spec.source = methodElement;
        spec.namespace = namespace.name();
        spec.javaName = methodElement.getSimpleName().toString();
        spec.side = bridgeMethod.side();
        spec.signaturePolicy = bridgeMethod.signature();
        spec.overloadPolicy = bridgeMethod.overloads();
        spec.sinceProtocol = bridgeMethod.sinceProtocol();

        String overloadKey = null;
        BridgeOverloadKey overloadKeyAnnotation = methodElement.getAnnotation(BridgeOverloadKey.class);
        if (overloadKeyAnnotation != null && overloadKeyAnnotation.value() != null && !overloadKeyAnnotation.value().isBlank()) {
            overloadKey = overloadKeyAnnotation.value().trim();
        }

        spec.paramTypeFqns = methodElement.getParameters().stream()
                .map(parameter -> eraseToFqn(parameter.asType()))
                .collect(Collectors.toList());
        spec.returnTypeFqn = eraseToFqn(methodElement.getReturnType());

        if (bridgeMethod.stability() == BridgeStability.STABLE_ID) {
            if (bridgeMethod.id() == null || bridgeMethod.id().isBlank()) {
                error(methodElement, "BridgeMethod(stability=STABLE_ID) requires a non-empty id.");
                spec.id = namespace.name() + "#" + spec.javaName;
            } else {
                spec.id = bridgeMethod.id().trim();
            }
        } else {
            if (overloadKey != null) {
                spec.id = namespace.name() + "#" + spec.javaName + ":" + overloadKey;
            } else if (spec.signaturePolicy == SignaturePolicy.ARITY_ONLY) {
                spec.id = namespace.name() + "#" + spec.javaName + "/" + spec.paramTypeFqns.size();
            } else {
                String signature = String.join(",", spec.paramTypeFqns);
                spec.id = namespace.name() + "#" + spec.javaName + "(" + signature + ")";
            }
        }

        return spec;
    }

    private Integer collectProtocolVersion(RoundEnvironment roundEnvironment) {
        Integer protocol = null;
        for (Element element : roundEnvironment.getElementsAnnotatedWith(BridgeContractVersion.class)) {
            if (!(element instanceof TypeElement typeElement)) continue;
            int value = typeElement.getAnnotation(BridgeContractVersion.class).value();
            if (protocol == null) {
                protocol = value;
            } else if (!protocol.equals(value)) {
                error(typeElement, "Multiple @BridgeContractVersion values found (" + protocol + " vs " + value + ").");
            }
        }
        return protocol;
    }

    private void writeGenerated(Map<String, AnchorKey> anchorKeys, List<ApiSurface> apis, Integer protocol) throws IOException {
        JavaFileObject file = processingEnv.getFiler().createSourceFile(GENERATED_PKG + "." + GENERATED_NAME);
        try (Writer writer = file.openWriter()) {
            CodeWriter out = new CodeWriter(writer);

            out.line("package " + GENERATED_PKG + ";");
            out.line("");
            out.line("import java.util.*;");
            out.line("");
            out.line("/**");
            out.line(" * GENERATED FILE. DO NOT EDIT.");
            out.line(" * Generated by " + BridgeContractProcessor.class.getName() + ".");
            out.line(" */");
            out.line("public final class " + GENERATED_NAME + " {");
            out.indent();
            out.line("private " + GENERATED_NAME + "() {}");
            out.line("");

            if (protocol != null) {
                out.line("public static final int PROTOCOL_VERSION = " + protocol + ";");
                out.line("");
            }

            writeAnchorKeys(out, anchorKeys);
            out.line("");
            writeMethods(out, apis);
            out.line("");
            writeSpecRecord(out);

            out.unindent();
            out.line("}");
        }
    }

    private void writeAnchorKeys(CodeWriter out, Map<String, AnchorKey> anchorKeys) throws IOException {
        out.line("public static final class AnchorKeys {");
        out.indent();
        out.line("private AnchorKeys() {}");
        for (AnchorKey key : anchorKeys.values()) {
            out.line("public static final String " + key.constName + " = \"" + escapeJava(key.value) + "\";");
        }
        out.unindent();
        out.line("}");
    }

    private void writeMethods(CodeWriter out, List<ApiSurface> apis) throws IOException {
        List<MethodSpec> allMethods = apis.stream().flatMap(api -> api.methods.stream()).collect(Collectors.toList());

        out.line("public static final class Methods {");
        out.indent();
        out.line("private Methods() {}");
        out.line("");

        for (MethodSpec methodSpec : allMethods) {
            String constSeed = methodSpec.namespace + "_" + methodSpec.javaName + "_" + methodSpec.paramTypeFqns.size();
            methodSpec.constName = toConstName(constSeed, methodSpec.id);
            out.line("public static final String " + methodSpec.constName + " = \"" + escapeJava(methodSpec.id) + "\";");
        }

        out.line("");
        out.line("private static final Map<String, Spec> SPECS;");
        out.line("static {");
        out.indent();
        out.line("Map<String, Spec> m = new HashMap<>();");

        for (MethodSpec methodSpec : allMethods) {
            String params = methodSpec.paramTypeFqns.stream()
                    .map(param -> "\"" + escapeJava(param) + "\"")
                    .collect(Collectors.joining(", "));

            out.line(
                    "m.put(" + methodSpec.constName + ", new Spec(\"" + escapeJava(methodSpec.javaName) +
                            "\", List.of(" + params + "), \"" + escapeJava(methodSpec.returnTypeFqn) +
                            "\", \"" + escapeJava(methodSpec.namespace) + "\", " + methodSpec.sinceProtocol + "));"
            );
        }

        out.line("SPECS = Collections.unmodifiableMap(m);");
        out.unindent();
        out.line("}");
        out.line("");
        out.line("public static Spec spec(String id) {");
        out.indent();
        out.line("return SPECS.get(id);");
        out.unindent();
        out.line("}");

        out.unindent();
        out.line("}");
    }

    private void writeSpecRecord(CodeWriter out) throws IOException {
        out.line("public record Spec(String javaName, List<String> paramTypeFqns, String returnTypeFqn, String namespace, int sinceProtocol) {}");
    }

    private String toConstName(String seed, String id) {
        String cleaned = seed.replaceAll("[^A-Za-z0-9]", "_");
        int hash = id.hashCode();
        return "M_" + cleaned.substring(0, Math.min(60, cleaned.length())).toUpperCase(Locale.ROOT) + "_" + Integer.toHexString(hash).toUpperCase(Locale.ROOT);
    }

    private void error(Element element, String msg) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
    }

    private String eraseToFqn(TypeMirror type) {
        try {
            TypeMirror erased = types.erasure(type);
            if (erased.getKind() == TypeKind.DECLARED) {
                DeclaredType declaredType = (DeclaredType) erased;
                Element declaredElement = declaredType.asElement();
                if (declaredElement instanceof TypeElement declaredTypeElement) {
                    return elements.getBinaryName(declaredTypeElement).toString();
                }
            }
        } catch (Throwable ignored) {
        }
        return type.toString();
    }

    private String extractStringConstant(VariableElement fieldElement) {
        Object constant = fieldElement.getConstantValue();
        if (constant instanceof String stringConstant) return stringConstant;
        return null;
    }

    private static String escapeJava(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class CodeWriter implements AutoCloseable {
        private final Writer out;
        private int indent = 0;

        CodeWriter(Writer out) {
            this.out = out;
        }

        void indent() {
            indent++;
        }

        void unindent() {
            indent = Math.max(0, indent - 1);
        }

        void line(String s) throws IOException {
            if (!s.isEmpty()) {
                out.write("    ".repeat(indent));
            }
            out.write(s);
            out.write("\n");
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }

    private record AnchorKey(String constName, String value) {
    }

    private static final class ApiSurface {
        final String namespace;
        String typeFqn;
        final List<MethodSpec> methods = new ArrayList<>();

        ApiSurface(String namespace) {
            this.namespace = namespace;
        }
    }

    private static final class MethodSpec {
        Element source;
        String namespace;
        String javaName;
        List<String> paramTypeFqns;
        String returnTypeFqn;
        BridgeSide side;
        SignaturePolicy signaturePolicy;
        OverloadPolicy overloadPolicy;
        int sinceProtocol;

        String id;
        String constName;
    }
}


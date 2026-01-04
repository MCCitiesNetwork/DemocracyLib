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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Build-time generator for a reflection bridge contract.
 * <p>
 * This processor scans bridge APIs/methods annotated with {@link BridgeApi}/{@link BridgeMethod}
 * and generates a deterministic contract class with method identifiers and signatures.
 * <p>
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
    private static final String GENERATED_IDS_NAME = "GeneratedBridgeIds";

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

        // validate contract determinism and prevent ambiguous/duplicate ids.
        boolean ok = validateNoDuplicateContractIds(apis);
        ok &= validateOverloadPolicies(apis);
        if (!ok) {
            return false;
        }

        try {
            writeGenerated(anchorKeys, apis, protocol);
            writeGeneratedIds(apis);
        } catch (Exception e) {
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.ERROR, "Failed generating " + GENERATED_NAME + ": " + e.getMessage());
        }

        return false;
    }

    private boolean validateNoDuplicateContractIds(List<ApiSurface> apis) {
        boolean ok = true;
        Map<String, MethodSpec> firstById = new HashMap<>();

        for (ApiSurface api : apis) {
            for (MethodSpec m : api.methods) {
                MethodSpec first = firstById.putIfAbsent(m.id, m);
                if (first != null) {
                    ok = false;
                    error(m.source,
                            "Duplicate bridge contract id '" + m.id + "'. " +
                                    "First defined at " + formatElement(first.source) + ". " +
                                    "Fix by using stability=STABLE_ID with an explicit id, " +
                                    "or change SignaturePolicy/BridgeOverloadKey to disambiguate.");
                }
            }
        }

        return ok;
    }

    private boolean validateOverloadPolicies(List<ApiSurface> apis) {
        boolean ok = true;

        for (ApiSurface api : apis) {
            // Group by Java method name.
            Map<String, List<MethodSpec>> byName = api.methods.stream()
                    .collect(Collectors.groupingBy(m -> m.javaName));

            for (Map.Entry<String, List<MethodSpec>> entry : byName.entrySet()) {
                List<MethodSpec> sameName = entry.getValue();
                if (sameName.size() <= 1) continue;

                // If any overload is marked ERROR, any overload at that name is a contract violation.
                boolean anyErrorPolicy = sameName.stream().anyMatch(m -> m.overloadPolicy == OverloadPolicy.ERROR);
                if (anyErrorPolicy) {
                    ok = false;
                    for (MethodSpec m : sameName) {
                        error(m.source,
                                "Overloaded bridge method '" + api.typeFqn + "#" + m.javaName + "' is not allowed (OverloadPolicy.ERROR). " +
                                        "Disambiguate by renaming the method, using BridgeOverloadKey, or change overloads() policy.");
                    }
                    continue;
                }

                // ARITY_ONLY is ambiguous if multiple overloads share the same arity.
                Map<Integer, List<MethodSpec>> byArity = sameName.stream()
                        .collect(Collectors.groupingBy(m -> m.paramTypeFqns == null ? 0 : m.paramTypeFqns.size()));
                for (Map.Entry<Integer, List<MethodSpec>> byArityEntry : byArity.entrySet()) {
                    List<MethodSpec> sameArity = byArityEntry.getValue();
                    if (sameArity.size() <= 1) continue;

                    boolean anyArityOnly = sameArity.stream().anyMatch(m -> m.signaturePolicy == SignaturePolicy.ARITY_ONLY);
                    if (anyArityOnly) {
                        ok = false;
                        for (MethodSpec m : sameArity) {
                            error(m.source,
                                    "Ambiguous overloads for bridge method '" + api.typeFqn + "#" + m.javaName + "' with arity " + byArityEntry.getKey() +
                                            " while SignaturePolicy.ARITY_ONLY is used. " +
                                            "Use SignaturePolicy.FULL_SIGNATURE or add @BridgeOverloadKey to disambiguate.");
                        }
                    }
                }
            }
        }

        return ok;
    }

    private static String formatElement(Element element) {
        if (element == null) return "<unknown>";
        Element enclosing = element.getEnclosingElement();
        String owner = enclosing == null ? "<unknown>" : enclosing.toString();
        return owner + "::" + element.getSimpleName();
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
        MethodSpec methodSpec = new MethodSpec();
        methodSpec.source = methodElement;
        methodSpec.namespace = namespace.name();
        methodSpec.javaName = methodElement.getSimpleName().toString();
        methodSpec.side = bridgeMethod.side();
        methodSpec.signaturePolicy = bridgeMethod.signature();
        methodSpec.overloadPolicy = bridgeMethod.overloads();
        methodSpec.sinceProtocol = bridgeMethod.sinceProtocol();

        String overloadKey = null;
        BridgeOverloadKey overloadKeyAnnotation = methodElement.getAnnotation(BridgeOverloadKey.class);
        if (overloadKeyAnnotation != null && overloadKeyAnnotation.value() != null && !overloadKeyAnnotation.value().isBlank()) {
            overloadKey = overloadKeyAnnotation.value().trim();
        }

        methodSpec.paramTypeFqns = methodElement.getParameters().stream()
                .map(parameter -> eraseToFqn(parameter.asType()))
                .collect(Collectors.toList());
        methodSpec.returnTypeFqn = eraseToFqn(methodElement.getReturnType());

        if (bridgeMethod.stability() == BridgeStability.STABLE_ID) {
            if (bridgeMethod.id() == null || bridgeMethod.id().isBlank()) {
                error(methodElement, "BridgeMethod(stability=STABLE_ID) requires a non-empty id.");
                methodSpec.id = namespace.name() + "#" + methodSpec.javaName;
            } else {
                methodSpec.id = bridgeMethod.id().trim();
            }
        } else {
            if (overloadKey != null) {
                methodSpec.id = namespace.name() + "#" + methodSpec.javaName + ":" + overloadKey;
            } else if (methodSpec.signaturePolicy == SignaturePolicy.ARITY_ONLY) {
                methodSpec.id = namespace.name() + "#" + methodSpec.javaName + "/" + methodSpec.paramTypeFqns.size();
            } else {
                String signature = String.join(",", methodSpec.paramTypeFqns);
                methodSpec.id = namespace.name() + "#" + methodSpec.javaName + "(" + signature + ")";
            }
        }

        return methodSpec;
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
        List<MethodSpec> allMethods = apis.stream().flatMap(api -> api.methods.stream()).toList();

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
        out.line("public static final Map<String, Spec> SPECS;");
        out.line("static {");
        out.indent();
        out.line("Map<String, Spec> methods = new HashMap<>();");

        for (MethodSpec methodSpec : allMethods) {
            String params = methodSpec.paramTypeFqns.stream()
                    .map(param -> "\"" + escapeJava(param) + "\"")
                    .collect(Collectors.joining(", "));

            out.line(
                    "methods.put(" + methodSpec.constName + ", new Spec(\"" + escapeJava(methodSpec.javaName) +
                            "\", List.of(" + params + "), \"" + escapeJava(methodSpec.returnTypeFqn) +
                            "\", \"" + escapeJava(methodSpec.namespace) + "\", " + methodSpec.sinceProtocol + "));"
            );
        }

        out.line("SPECS = Collections.unmodifiableMap(methods);");
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

    private void writeGeneratedIds(List<ApiSurface> apis) throws IOException {
        JavaFileObject file = processingEnv.getFiler().createSourceFile(GENERATED_PKG + "." + GENERATED_IDS_NAME);
        try (Writer writer = file.openWriter()) {
            CodeWriter out = new CodeWriter(writer);

            out.line("package " + GENERATED_PKG + ";");
            out.line("");
            out.line("/**");
            out.line(" * GENERATED FILE. DO NOT EDIT.");
            out.line(" * <p>");
            out.line(" * friendly deterministic aliases for bridge contract ids.");
            out.line(" * Generated by " + BridgeContractProcessor.class.getName() + ".");
            out.line(" */");
            out.line("public final class " + GENERATED_IDS_NAME + " {");
            out.indent();
            out.line("private " + GENERATED_IDS_NAME + "() {}");
            out.line("");

            // Flatten methods, deterministic order.
            List<MethodSpec> allMethods = apis.stream()
                    .flatMap(api -> api.methods.stream())
                    .sorted(Comparator.comparing((MethodSpec m) -> m.namespace)
                            .thenComparing(m -> m.javaName)
                            .thenComparing(m -> String.join(",", m.paramTypeFqns)))
                    .toList();

            String currentNamespace = null;
            for (MethodSpec m : allMethods) {
                if (!Objects.equals(currentNamespace, m.namespace)) {
                    if (currentNamespace != null) {
                        out.unindent();
                        out.line("}");
                        out.line("");
                    }
                    currentNamespace = m.namespace;
                    out.line("public static final class " + namespaceClassName(currentNamespace) + " {");
                    out.indent();
                    out.line("private " + namespaceClassName(currentNamespace) + "() {}");
                    out.line("");
                }

                String fieldName = aliasFieldName(m);
                out.line("public static final String " + fieldName + " = \"" + escapeJava(m.id) + "\";");
            }

            if (currentNamespace != null) {
                out.unindent();
                out.line("}");
            }

            out.unindent();
            out.line("}");
        }
    }

    private static String namespaceClassName(String namespace) {
        // DEMOCRACY_LIB_API -> DemocracyLibApi
        String[] parts = namespace.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String aliasFieldName(MethodSpec methodSpec) {
        // Example: getGitHubGistService__Plugin__GitHubGistConfiguration
        StringBuilder sb = new StringBuilder();
        sb.append(methodSpec.javaName);
        for (String p : methodSpec.paramTypeFqns) {
            sb.append("__").append(simpleTypeToken(p));
        }
        String raw = sb.toString();
        // sanitize to a valid Java identifier
        raw = raw.replaceAll("[^A-Za-z0-9_]", "_");
        // avoid leading digit
        if (!raw.isEmpty() && Character.isDigit(raw.charAt(0))) {
            raw = "_" + raw;
        }
        return raw;
    }

    private static String simpleTypeToken(String fqn) {
        // Use simple name; include outer for nested types.
        if (fqn == null || fqn.isBlank()) return "Unknown";
        String cleaned = fqn.replace('$', '.');
        int lastDot = cleaned.lastIndexOf('.');
        String simple = lastDot >= 0 ? cleaned.substring(lastDot + 1) : cleaned;
        // Squash generics remnants just in case.
        simple = simple.replaceAll("<.*>", "");
        return simple;
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

    private static String escapeJava(String string) {
        if (string == null) return "";
        return string.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String toConstName(String seed, String id) {
        // Keep it deterministic, short, and collision-resistant.
        // The seed provides readability; the hash disambiguates overloads and stable IDs.
        String base = (seed == null ? "M" : seed)
                .replaceAll("[^A-Za-z0-9_]", "_")
                .toUpperCase(Locale.ROOT);

        String hash = shortHashHex(id == null ? "" : id);
        return "M_" + base + "_" + hash;
    }

    private static String shortHashHex(String input) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(input.getBytes(StandardCharsets.UTF_8));
            // 4 bytes => 8 hex chars (good enough for const suffix, contract id still stored as value)
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < 4 && i < digest.length; i++) {
                stringBuilder.append(String.format(Locale.ROOT, "%02X", digest[i]));
            }
            return stringBuilder.toString();
        } catch (Exception e) {
            return "00000000";
        }
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


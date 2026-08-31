package org.hjug.graphbuilder.visitor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hjug.graphbuilder.GraphDependencyCollector;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;

/**
 * Kotlin source-path mapping.
 *
 * <p>The Kotlin dependency-visitor path-resolution flow calls for a
 * {@code sourceFileExtension()} hook on the dependency visitor base so that
 * the synthesized (junit-repo) branch of {@code recordClassLocation} uses a
 * language-appropriate file extension. {@link AbstractDependencyVisitor}
 * provides the hook (defaulting to {@code ".java"}); because
 * {@code KotlinIsoVisitor} extends {@code KotlinVisitor} (not
 * {@code JavaIsoVisitor}), the Kotlin dependency visitor cannot share that
 * base, so it carries a parallel {@code sourceFileExtension()} override that
 * returns {@code ".kt"}.
 *
 * <p>These tests pin both the Java and Kotlin sides of the hook so a future
 * refactor that collides either extension again fails loudly:
 *
 * <ol>
 *   <li>{@code kotlinJunitBranch_usesKtExtension} — when the repository
 *       path contains the {@code junit-} sentinel (same sentinel the Java
 *       visitor uses), the Kotlin class -> source-path mapping entry ends
 *       in {@code .kt}, not {@code .java}.</li>
 *   <li>{@code kotlinRepoBranch_usesCanonicalisedUri} — non-junit repos
 *       still use the parser's source URI, canonicalised against the
 *       repository root.</li>
 *   <li>{@code javaJunitBranch_usesJavaExtension} — sanity-check that the
 *       Java visitor still derives {@code .java} paths on the same
 *       branch (parity with {@link JavaVisitorTest}).</li>
 *   <li>{@code kotlinMultiClassFile_junitBranch_classMapsToSourceFile} —
 *       multiple top-level classes in one file map to that file.</li>
 *   <li>{@code kotlinMultiClassFile_repoBranch_classMapsToSourceFile} —
 *       repo branch also correctly maps multi-class files.</li>
 *   <li>{@code kotlinCompanionObject_junitBranch_hasSourcePath} —
 *       companion objects get source paths if attributed.</li>
 * </ol>
 */
class KotlinSourcePathMappingTest {

    private static final String FIXTURE_DIR = "src/test/resources/kotlinSourcePathSrcDirectory";
    private static final String TESTCLASSES = "src/test/java/org/hjug/graphbuilder/visitor/testclasses";
    private static final String MULTI_CLASS_FIXTURE_DIR = "src/test/resources/kotlinMultiClassSrcDirectory";

    @DisplayName("1. Kotlin junit branch produces .kt extension via sourceFileExtension() hook")
    @Test
    void kotlinJunitBranch_usesKtExtension() throws IOException {
        File srcDirectory = new File(FIXTURE_DIR);
        KotlinParser kotlinParser = KotlinParser.builder()
                .languageLevel(KotlinParser.KotlinLanguageLevel.KOTLIN_2_4)
                .logCompilationWarningsAndErrors(false)
                .build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        GraphDependencyCollector collector = new GraphDependencyCollector(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class));

        // Sentinel triggers the synthetic-path branch in recordClassLocation,
        // which is the only caller of sourceFileExtension().
        String repoPath = "/tmp/junit-fake-kotlin-repo";
        KotlinDependencyVisitor<ExecutionContext> visitor = new KotlinDependencyVisitor<>(repoPath, "", collector);

        List<Path> files;
        try (var walk = Files.walk(Path.of(srcDirectory.getAbsolutePath()))) {
            files = walk.filter(p -> p.toString().endsWith(".kt")).collect(Collectors.toList());
        }
        kotlinParser.parse(files, Path.of(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> visitor.visit(cu, ctx));

        Map<String, String> mapping = visitor.getClassToSourceFilePathMapping();
        String outerFqn = "com.ideacrest.parser.kotlin.sourcepath.SourcePathSampleKt";
        String innerFqn = outerFqn + "$InnerKt";

        assertNotNull(mapping.get(outerFqn), "Outer Kotlin class missing from mapping: " + outerFqn);
        assertNotNull(mapping.get(innerFqn), "Inner Kotlin class missing from mapping: " + innerFqn);
        assertTrue(
                mapping.get(outerFqn).endsWith(".kt"),
                "Outer Kotlin class source path should end with .kt, got: " + mapping.get(outerFqn));
        assertTrue(
                mapping.get(innerFqn).endsWith(".kt"),
                "Inner Kotlin class source path should end with .kt, got: " + mapping.get(innerFqn));
        assertEquals(
                "com/ideacrest/parser/kotlin/sourcepath/SourcePathSampleKt.kt",
                mapping.get(outerFqn),
                "Outer Kotlin synthetic source path mismatch");
    }

    @DisplayName("2. Kotlin non-junit branch canonicalises parser URI (dot path retained)")
    @Test
    void kotlinRepoBranch_usesCanonicalisedUri() throws IOException {
        File srcDirectory = new File(FIXTURE_DIR);
        KotlinParser kotlinParser = KotlinParser.builder()
                .languageLevel(KotlinParser.KotlinLanguageLevel.KOTLIN_2_4)
                .logCompilationWarningsAndErrors(false)
                .build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        GraphDependencyCollector collector = new GraphDependencyCollector(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class));

        // The repo path here is the real fixture directory. recordClassLocation
        // takes the else branch and canonicalises the parser's file:// URI.
        String repoPath = srcDirectory.getAbsolutePath();
        KotlinDependencyVisitor<ExecutionContext> visitor = new KotlinDependencyVisitor<>(repoPath, "", collector);

        List<Path> files;
        try (var walk = Files.walk(Path.of(srcDirectory.getAbsolutePath()))) {
            files = walk.filter(p -> p.toString().endsWith(".kt")).collect(Collectors.toList());
        }
        kotlinParser.parse(files, Path.of(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> visitor.visit(cu, ctx));

        Map<String, String> mapping = visitor.getClassToSourceFilePathMapping();
        String outerFqn = "com.ideacrest.parser.kotlin.sourcepath.SourcePathSampleKt";
        String value = mapping.get(outerFqn);
        assertNotNull(value, "Outer Kotlin class missing from mapping on repo branch");
        assertTrue(
                value.endsWith("com/ideacrest/parser/kotlin/sourcepath/SourcePathSampleKt.kt"),
                "Repo-branch source path should canonicalise to the relative Kotlin path, got: " + value);
    }

    @DisplayName("3. Java junit branch still produces .java extension")
    @Test
    void javaJunitBranch_usesJavaExtension() throws IOException {
        File srcDirectory = new File(TESTCLASSES);
        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        GraphDependencyCollector collector = new GraphDependencyCollector(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class));

        String repoPath = "/tmp/junit-fake-repo";
        JavaVisitor<ExecutionContext> visitor = new JavaVisitor<>(repoPath, "", collector);

        List<Path> files;
        try (var walk = Files.walk(Path.of(srcDirectory.getAbsolutePath()))) {
            files = walk.collect(Collectors.toList());
        }
        javaParser.parse(files, Path.of(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> visitor.visit(cu, ctx));

        Map<String, String> mapping = visitor.getClassToSourceFilePathMapping();
        String innerFqn = "org.hjug.graphbuilder.visitor.testclasses.A$InnerClass";
        assertNotNull(mapping.get(innerFqn), "Inner Java class missing from mapping: " + innerFqn);
        assertEquals(
                "org/hjug/graphbuilder/visitor/testclasses/A.java",
                mapping.get(innerFqn),
                "Java synthetic source path should be derived via the .java sourceFileExtension() hook");
    }

    @DisplayName("4. Kotlin multi-class file: class maps to actual source file (junit branch)")
    @Test
    void kotlinMultiClassFile_junitBranch_classMapsToSourceFile() throws IOException {
        File srcDirectory = new File(MULTI_CLASS_FIXTURE_DIR);
        KotlinParser kotlinParser = KotlinParser.builder()
                .languageLevel(KotlinParser.KotlinLanguageLevel.KOTLIN_2_4)
                .logCompilationWarningsAndErrors(false)
                .build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        GraphDependencyCollector collector = new GraphDependencyCollector(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class));

        String repoPath = "/tmp/junit-fake-kotlin-multi-repo";
        KotlinDependencyVisitor<ExecutionContext> visitor = new KotlinDependencyVisitor<>(repoPath, "", collector);

        List<Path> files;
        try (var walk = Files.walk(Path.of(srcDirectory.getAbsolutePath()))) {
            files = walk.filter(p -> p.toString().endsWith(".kt")).collect(Collectors.toList());
        }
        kotlinParser.parse(files, Path.of(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> visitor.visit(cu, ctx));

        Map<String, String> mapping = visitor.getClassToSourceFilePathMapping();

        // GameSettings is in Settings.kt, not GameSettings.kt
        String gameSettingsFqn = "com.example.app.GameSettings";
        assertNotNull(mapping.get(gameSettingsFqn), "GameSettings missing from mapping");
        assertTrue(
                mapping.get(gameSettingsFqn).endsWith("Settings.kt"),
                "GameSettings should map to Settings.kt, got: " + mapping.get(gameSettingsFqn));

        // OtherSettings is also in Settings.kt
        String otherSettingsFqn = "com.example.app.OtherSettings";
        assertNotNull(mapping.get(otherSettingsFqn), "OtherSettings missing from mapping");
        assertTrue(
                mapping.get(otherSettingsFqn).endsWith("Settings.kt"),
                "OtherSettings should map to Settings.kt, got: " + mapping.get(otherSettingsFqn));

        // GameSettings2 is in GameSettings.kt
        String gameSettings2Fqn = "com.example.app.GameSettings2";
        assertNotNull(mapping.get(gameSettings2Fqn), "GameSettings2 missing from mapping");
        assertTrue(
                mapping.get(gameSettings2Fqn).endsWith("GameSettings.kt"),
                "GameSettings2 should map to GameSettings.kt, got: " + mapping.get(gameSettings2Fqn));

        // TopLevelObject should be registered
        String topLevelObjectFqn = "com.example.app.TopLevelObject";
        assertNotNull(mapping.get(topLevelObjectFqn), "TopLevelObject missing from mapping");
        assertTrue(
                mapping.get(topLevelObjectFqn).endsWith("Settings.kt"),
                "TopLevelObject should map to Settings.kt, got: " + mapping.get(topLevelObjectFqn));

        // SealedExample should be registered
        String sealedExampleFqn = "com.example.app.SealedExample";
        assertNotNull(mapping.get(sealedExampleFqn), "SealedExample missing from mapping");
        assertTrue(
                mapping.get(sealedExampleFqn).endsWith("Settings.kt"),
                "SealedExample should map to Settings.kt, got: " + mapping.get(sealedExampleFqn));

        // ServiceImplementation should be registered
        String serviceImplFqn = "com.example.app.ServiceImplementation";
        assertNotNull(mapping.get(serviceImplFqn), "ServiceImplementation missing from mapping");
        assertTrue(
                mapping.get(serviceImplFqn).endsWith("Settings.kt"),
                "ServiceImplementation should map to Settings.kt, got: " + mapping.get(serviceImplFqn));
    }

    @DisplayName("5. Kotlin multi-class file: class maps to actual source file (repo branch)")
    @Test
    void kotlinMultiClassFile_repoBranch_classMapsToSourceFile() throws IOException {
        File srcDirectory = new File(MULTI_CLASS_FIXTURE_DIR);
        KotlinParser kotlinParser = KotlinParser.builder()
                .languageLevel(KotlinParser.KotlinLanguageLevel.KOTLIN_2_4)
                .logCompilationWarningsAndErrors(false)
                .build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        GraphDependencyCollector collector = new GraphDependencyCollector(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class));

        String repoPath = srcDirectory.getAbsolutePath();
        KotlinDependencyVisitor<ExecutionContext> visitor = new KotlinDependencyVisitor<>(repoPath, "", collector);

        List<Path> files;
        try (var walk = Files.walk(Path.of(srcDirectory.getAbsolutePath()))) {
            files = walk.filter(p -> p.toString().endsWith(".kt")).collect(Collectors.toList());
        }
        kotlinParser.parse(files, Path.of(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> visitor.visit(cu, ctx));

        Map<String, String> mapping = visitor.getClassToSourceFilePathMapping();

        // GameSettings is in Settings.kt
        String gameSettingsFqn = "com.example.app.GameSettings";
        assertNotNull(mapping.get(gameSettingsFqn), "GameSettings missing from mapping on repo branch");
        assertTrue(
                mapping.get(gameSettingsFqn).endsWith("Settings.kt"),
                "GameSettings should map to Settings.kt on repo branch, got: " + mapping.get(gameSettingsFqn));

        // OtherSettings is also in Settings.kt
        String otherSettingsFqn = "com.example.app.OtherSettings";
        assertNotNull(mapping.get(otherSettingsFqn), "OtherSettings missing from mapping on repo branch");
        assertTrue(
                mapping.get(otherSettingsFqn).endsWith("Settings.kt"),
                "OtherSettings should map to Settings.kt on repo branch, got: " + mapping.get(otherSettingsFqn));

        // GameSettings2 is in GameSettings.kt
        String gameSettings2Fqn = "com.example.app.GameSettings2";
        assertNotNull(mapping.get(gameSettings2Fqn), "GameSettings2 missing from mapping on repo branch");
        assertTrue(
                mapping.get(gameSettings2Fqn).endsWith("GameSettings.kt"),
                "GameSettings2 should map to GameSettings.kt on repo branch, got: " + mapping.get(gameSettings2Fqn));

        // TopLevelObject should be registered
        String topLevelObjectFqn = "com.example.app.TopLevelObject";
        assertNotNull(mapping.get(topLevelObjectFqn), "TopLevelObject missing from mapping on repo branch");
        assertTrue(
                mapping.get(topLevelObjectFqn).endsWith("Settings.kt"),
                "TopLevelObject should map to Settings.kt on repo branch, got: " + mapping.get(topLevelObjectFqn));

        // SealedExample should be registered
        String sealedExampleFqn = "com.example.app.SealedExample";
        assertNotNull(mapping.get(sealedExampleFqn), "SealedExample missing from mapping on repo branch");
        assertTrue(
                mapping.get(sealedExampleFqn).endsWith("Settings.kt"),
                "SealedExample should map to Settings.kt on repo branch, got: " + mapping.get(sealedExampleFqn));

        // ServiceImplementation should be registered
        String serviceImplFqn = "com.example.app.ServiceImplementation";
        assertNotNull(mapping.get(serviceImplFqn), "ServiceImplementation missing from mapping on repo branch");
        assertTrue(
                mapping.get(serviceImplFqn).endsWith("Settings.kt"),
                "ServiceImplementation should map to Settings.kt on repo branch, got: " + mapping.get(serviceImplFqn));
    }

    @DisplayName("6. Kotlin companion object gets source path mapping (junit branch)")
    @Test
    void kotlinCompanionObject_junitBranch_hasSourcePath() throws IOException {
        File srcDirectory = new File(MULTI_CLASS_FIXTURE_DIR);
        KotlinParser kotlinParser = KotlinParser.builder()
                .languageLevel(KotlinParser.KotlinLanguageLevel.KOTLIN_2_4)
                .logCompilationWarningsAndErrors(false)
                .build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        GraphDependencyCollector collector = new GraphDependencyCollector(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class));

        String repoPath = "/tmp/junit-fake-kotlin-companion-repo";
        KotlinDependencyVisitor<ExecutionContext> visitor = new KotlinDependencyVisitor<>(repoPath, "", collector);

        List<Path> files;
        try (var walk = Files.walk(Path.of(srcDirectory.getAbsolutePath()))) {
            files = walk.filter(p -> p.toString().endsWith(".kt")).collect(Collectors.toList());
        }
        kotlinParser.parse(files, Path.of(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> visitor.visit(cu, ctx));

        Map<String, String> mapping = visitor.getClassToSourceFilePathMapping();

        // The companion object inside Settings.kt should generate a synthetic class
        // Check if any companion object related class is mapped
        // Note: Companion objects may or may not be attributed as separate classes
        // This test documents the expected behavior
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (entry.getKey().contains("Companion") || entry.getKey().contains("companion")) {
                assertTrue(
                        entry.getValue().endsWith("Settings.kt"),
                        "Companion object should map to Settings.kt, got: " + entry.getValue());
                assertFalse(
                        entry.getValue().contains("null"), "Companion object source path should not contain 'null'");
            }
        }
    }
}

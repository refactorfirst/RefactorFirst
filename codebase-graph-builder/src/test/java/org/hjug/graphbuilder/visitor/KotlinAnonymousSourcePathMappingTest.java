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
import org.openrewrite.kotlin.KotlinParser;

/**
 * Tests for Kotlin anonymous object expressions and lambda expressions
 * source path mapping.
 *
 * <p>Kotlin {@code object : SomeInterface { ... }} anonymous object expressions
 * and lambda expressions {@code { ... }} generate synthetic classes at compile time.
 * The OpenRewrite Kotlin parser attributes these with FQNs like
 * {@code OuterClass$methodName$N} or {@code <anonymous>}.
 *
 * <p>Before the fix, these synthetic classes were not visited by
 * {@code KotlinDependencyVisitor}, so their class vertices were never registered
 * and their source file locations were never recorded, resulting in {@code null}
 * URLs in the DOT graph output.
 *
 * <p>These tests verify that after the fix:
 * <ol>
 *   <li>Anonymous object expressions are visited and their synthetic class FQNs
 *       are registered with proper source paths.</li>
 *   <li>Lambda expressions that generate attributed synthetic classes are
 *       visited and registered.</li>
 * </ol>
 */
class KotlinAnonymousSourcePathMappingTest {

    private static final String FIXTURE_DIR = "src/test/resources/kotlinAnonymousSrcDirectory";

    @DisplayName("Kotlin anonymous object expressions get source path mapping (junit branch)")
    @Test
    void kotlinAnonymousObject_junitBranch_hasSourcePath() throws IOException {
        File srcDirectory = new File(FIXTURE_DIR);
        KotlinParser kotlinParser = KotlinParser.builder()
                .languageLevel(KotlinParser.KotlinLanguageLevel.KOTLIN_2_2)
                .logCompilationWarningsAndErrors(false)
                .build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        GraphDependencyCollector collector = new GraphDependencyCollector(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class));

        // Sentinel triggers the synthetic-path branch in recordClassLocation
        String repoPath = "/tmp/junit-fake-kotlin-anon-repo";
        KotlinDependencyVisitor<ExecutionContext> visitor = new KotlinDependencyVisitor<>(repoPath, "", collector);

        List<Path> files;
        try (var walk = Files.walk(Path.of(srcDirectory.getAbsolutePath()))) {
            files = walk.filter(p -> p.toString().endsWith(".kt")).collect(Collectors.toList());
        }
        kotlinParser.parse(files, Path.of(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> visitor.visit(cu, ctx));

        Map<String, String> mapping = visitor.getClassToSourceFilePathMapping();

        // The outer class should be mapped
        String outerFqn = "com.ideacrest.parser.kotlin.anonymous.AnonymousObjectHolder";
        assertNotNull(mapping.get(outerFqn), "Outer class missing from mapping: " + outerFqn);
        assertTrue(mapping.get(outerFqn).endsWith(".kt"), "Outer class source path should end with .kt");

        // Anonymous object expressions should generate synthetic classes with source paths
        // OpenRewrite attributes Kotlin anonymous objects with the literal "<anonymous>" as
        // the trailing simple-name segment of their FQN (e.g., "pkg.OuterClass.<anonymous>" or
        // just "pkg.<anonymous>" for top-level). This is what HtmlReport.isAnonymousFqn() detects.
        boolean foundAnonymousClass = mapping.keySet().stream()
                .anyMatch(fqn -> fqn.endsWith(".<anonymous>")
                        || (fqn.contains("AnonymousObjectHolder") && fqn.contains("<anonymous>")));

        assertTrue(
                foundAnonymousClass,
                "Expected at least one anonymous object synthetic class in mapping. All mapped FQNs: "
                        + mapping.keySet());

        // The anonymous class should map to the actual source file, not a synthetic path
        String anonPath = mapping.get("com.ideacrest.parser.kotlin.anonymous.<anonymous>");
        assertNotNull(anonPath, "Anonymous class should be in mapping");
        assertTrue(
                anonPath.endsWith("AnonymousObjects.kt"),
                "Anonymous class should map to actual source file AnonymousObjects.kt, got: " + anonPath);

        // Verify the anonymous class has a proper .kt source path
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (entry.getKey().contains("<anonymous>")) {
                assertTrue(
                        entry.getValue().endsWith(".kt"),
                        "Anonymous class source path should end with .kt, got: " + entry.getValue() + " for FQN: "
                                + entry.getKey());
                assertFalse(
                        entry.getValue().contains("null"),
                        "Anonymous class source path should not contain 'null', got: " + entry.getValue());
            }
        }
    }

    @DisplayName("Kotlin lambda expressions get source path mapping (junit branch)")
    @Test
    void kotlinLambda_junitBranch_hasSourcePath() throws IOException {
        File srcDirectory = new File(FIXTURE_DIR);
        KotlinParser kotlinParser = KotlinParser.builder()
                .languageLevel(KotlinParser.KotlinLanguageLevel.KOTLIN_2_2)
                .logCompilationWarningsAndErrors(false)
                .build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        GraphDependencyCollector collector = new GraphDependencyCollector(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class));

        String repoPath = "/tmp/junit-fake-kotlin-lambda-repo";
        KotlinDependencyVisitor<ExecutionContext> visitor = new KotlinDependencyVisitor<>(repoPath, "", collector);

        List<Path> files;
        try (var walk = Files.walk(Path.of(srcDirectory.getAbsolutePath()))) {
            files = walk.filter(p -> p.toString().endsWith(".kt")).collect(Collectors.toList());
        }
        kotlinParser.parse(files, Path.of(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> visitor.visit(cu, ctx));

        Map<String, String> mapping = visitor.getClassToSourceFilePathMapping();

        // Lambda expressions may generate synthetic classes
        // Check if any lambda-related synthetic classes are mapped
        boolean foundLambdaClass = mapping.keySet().stream()
                .anyMatch(fqn -> fqn.contains("AnonymousObjectHolder")
                        && fqn.contains("$")
                        && (fqn.contains("createLambda") || fqn.contains("<anonymous>")));

        // Note: OpenRewrite may or may not attribute synthetic classes to lambdas
        // depending on the Kotlin version and parser configuration.
        // This test documents the expected behavior - if attributed, they should have paths.
        if (foundLambdaClass) {
            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                if (entry.getKey().contains("createLambda") || entry.getKey().contains("<anonymous>")) {
                    assertTrue(
                            entry.getValue().endsWith(".kt"),
                            "Lambda synthetic class source path should end with .kt, got: " + entry.getValue());
                    assertFalse(
                            entry.getValue().contains("null"),
                            "Lambda synthetic class source path should not contain 'null'");
                }
            }
        }
    }

    @DisplayName("Kotlin anonymous object expressions get source path mapping (repo branch)")
    @Test
    void kotlinAnonymousObject_repoBranch_hasCanonicalisedPath() throws IOException {
        File srcDirectory = new File(FIXTURE_DIR);
        KotlinParser kotlinParser = KotlinParser.builder()
                .languageLevel(KotlinParser.KotlinLanguageLevel.KOTLIN_2_2)
                .logCompilationWarningsAndErrors(false)
                .build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        GraphDependencyCollector collector = new GraphDependencyCollector(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class));

        // Real repo path triggers canonicalisation branch
        String repoPath = srcDirectory.getAbsolutePath();
        KotlinDependencyVisitor<ExecutionContext> visitor = new KotlinDependencyVisitor<>(repoPath, "", collector);

        List<Path> files;
        try (var walk = Files.walk(Path.of(srcDirectory.getAbsolutePath()))) {
            files = walk.filter(p -> p.toString().endsWith(".kt")).collect(Collectors.toList());
        }
        kotlinParser.parse(files, Path.of(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> visitor.visit(cu, ctx));

        Map<String, String> mapping = visitor.getClassToSourceFilePathMapping();

        String outerFqn = "com.ideacrest.parser.kotlin.anonymous.AnonymousObjectHolder";
        assertNotNull(mapping.get(outerFqn), "Outer class missing from mapping on repo branch");
        assertTrue(
                mapping.get(outerFqn).endsWith("AnonymousObjects.kt"),
                "Repo-branch source path should canonicalise to the relative Kotlin path");

        // Anonymous classes should have canonicalised paths too
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (entry.getKey().contains("<anonymous>")) {
                assertTrue(
                        entry.getValue().endsWith("AnonymousObjects.kt"),
                        "Anonymous class should have canonicalised path ending with AnonymousObjects.kt, got: "
                                + entry.getValue());
                assertFalse(entry.getValue().contains("null"), "Anonymous class source path should not contain 'null'");
            }
        }
    }
}

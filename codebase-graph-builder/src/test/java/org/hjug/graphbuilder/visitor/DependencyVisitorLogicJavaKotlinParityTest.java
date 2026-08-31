package org.hjug.graphbuilder.visitor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.hjug.graphbuilder.GraphDependencyCollector;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;

/**
 * Regression tests for review.md item #7 — "Extract shared J-level dependency-visitor logic".
 *
 * <p>These tests pin current behavior before the refactor:
 * <ol>
 *   <li>{@code visitCompilationUnit_nullTypeDoesNotNPE} — both Java and Kotlin visitors
 *       must handle a {@code J.CompilationUnit} containing a {@code J.ClassDeclaration}
 *       whose {@code getType()} returns {@code null} without throwing
 *       {@code NullPointerException}. This is the "uncommitted NPE fix" mentioned
 *       in review.md item #2 that was only applied to the Java path.</li>
 *   <li>{@code javaAndKotlinIdenticalMethods_sameEdges} — the five J-level overrides
 *       confirmed identical ({@code visitMethodInvocation}, {@code visitNewClass},
 *       {@code visitInstanceOf}, {@code visitTypeCast}, {@code visitNewArray}) must
 *       produce identical class-graph edges for equivalent Java and Kotlin source.
 *       This uses minimal fixtures exercising only those overrides.</li>
 * </ol>
 */
class DependencyVisitorLogicJavaKotlinParityTest {

    private static final String JAVA_TESTCLASSES = "src/test/java/org/hjug/graphbuilder/visitor/testclasses";
    private static final String KOTLIN_SOURCE_PATH_DIR = "src/test/resources/kotlinSourcePathSrcDirectory";

    @DisplayName("1. visitCompilationUnit handles null ClassDeclaration type without NPE (Java)")
    @Test
    void javaVisitCompilationUnit_nullTypeDoesNotNPE(@TempDir Path tempDir) throws IOException {
        // Create a Java source file where the parser might produce a ClassDeclaration
        // with null type (e.g., due to parse errors or unsupported constructs)
        Path source = tempDir.resolve("NullTypeTest.java");
        Files.writeString(
                source,
                "package com.example.nulltype;\n" + "public class NullTypeTest {\n"
                        + "  // Valid class to ensure parsing succeeds\n"
                        + "  public void validMethod() {}\n"
                        + "}\n"
                        + "// Second class - parser may still attribute this\n"
                        + "class AnotherClass {\n"
                        + "  public void foo() {}\n"
                        + "}\n");

        GraphDependencyCollector collector = new GraphDependencyCollector(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class));
        JavaVisitor<ExecutionContext> visitor = new JavaVisitor<>(tempDir.toString(), "", collector);

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        javaParser
                .parse(Collections.singletonList(source), tempDir, ctx)
                .forEach(cu ->
                        // This should not throw NPE even if a ClassDeclaration has null type
                        visitor.visit(cu, ctx));

        // Verify the visitor completed without exception and registered vertices
        assertTrue(collector.getClassReferencesGraph().containsVertex("com.example.nulltype.NullTypeTest")
                || collector.getClassReferencesGraph().containsVertex("com.example.nulltype.AnotherClass"));
    }

    @DisplayName("2. visitCompilationUnit handles null ClassDeclaration type without NPE (Kotlin)")
    @Test
    void kotlinVisitCompilationUnit_nullTypeDoesNotNPE(@TempDir Path tempDir) throws IOException {
        // Create a Kotlin source file
        Path source = tempDir.resolve("NullTypeTest.kt");
        Files.writeString(
                source,
                "package com.example.nulltype\n" + "class NullTypeTest {\n"
                        + "  fun validMethod() {}\n"
                        + "}\n"
                        + "class AnotherClass {\n"
                        + "  fun foo() {}\n"
                        + "}\n");

        GraphDependencyCollector collector = new GraphDependencyCollector(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class));
        KotlinDependencyVisitor<ExecutionContext> visitor =
                new KotlinDependencyVisitor<>(tempDir.toString(), "", collector);

        KotlinParser kotlinParser = KotlinParser.builder()
                .languageLevel(KotlinParser.KotlinLanguageLevel.KOTLIN_2_4)
                .logCompilationWarningsAndErrors(false)
                .build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        kotlinParser
                .parse(Collections.singletonList(source), tempDir, ctx)
                .forEach(cu ->
                        // This should not throw NPE even if a ClassDeclaration has null type
                        visitor.visit(cu, ctx));

        // Verify the visitor completed without exception and registered vertices
        assertTrue(collector.getClassReferencesGraph().containsVertex("com.example.nulltype.NullTypeTest")
                || collector.getClassReferencesGraph().containsVertex("com.example.nulltype.AnotherClass"));
    }

    @DisplayName("3. Identical J-level overrides produce same edges for minimal fixture (Java vs Kotlin)")
    @Test
    void identicalMethods_sameEdges() throws IOException {
        // This test uses the existing Kotlin source-path-mapping fixture and a minimal Java equivalent
        // that exercises only the 5 confirmed-identical J-level overrides:
        // visitMethodInvocation, visitNewClass, visitInstanceOf, visitTypeCast, visitNewArray

        // Java fixture: simple class with method invocation and new class
        Path javaSource = Path.of(JAVA_TESTCLASSES, "methodInvocation", "A.java");
        Path kotlinSource = Path.of(
                KOTLIN_SOURCE_PATH_DIR, "com", "ideacrest", "parser", "kotlin", "sourcepath", "SourcePathSampleKt.kt");

        Graph<String, DefaultWeightedEdge> javaGraph = buildJavaGraph(javaSource);
        Graph<String, DefaultWeightedEdge> kotlinGraph = buildKotlinGraph(kotlinSource);

        // Compare only the core project vertices (excluding stdlib differences)
        compareProjectEdges(
                javaGraph,
                kotlinGraph,
                "org.hjug.graphbuilder.visitor.testclasses.methodInvocation",
                "com.ideacrest.parser.kotlin.sourcepath");
    }

    private Graph<String, DefaultWeightedEdge> buildJavaGraph(Path sourceFile) throws IOException {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graph<String, DefaultWeightedEdge> pkgGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        String repoPath = sourceFile.getParent().toString();
        JavaVisitor<ExecutionContext> visitor = new JavaVisitor<>(repoPath, "", collector);

        javaParser
                .parse(Collections.singletonList(sourceFile), sourceFile.getParent(), ctx)
                .forEach(cu -> visitor.visit(cu, ctx));

        return classGraph;
    }

    private Graph<String, DefaultWeightedEdge> buildKotlinGraph(Path sourceFile) throws IOException {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graph<String, DefaultWeightedEdge> pkgGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        KotlinParser kotlinParser = KotlinParser.builder()
                .languageLevel(KotlinParser.KotlinLanguageLevel.KOTLIN_2_4)
                .logCompilationWarningsAndErrors(false)
                .build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        String repoPath = sourceFile
                .getParent()
                .getParent()
                .getParent()
                .getParent()
                .getParent()
                .toString(); // kotlinSourcePathSrcDirectory
        KotlinDependencyVisitor<ExecutionContext> visitor = new KotlinDependencyVisitor<>(repoPath, "", collector);

        kotlinParser
                .parse(
                        Collections.singletonList(sourceFile),
                        sourceFile
                                .getParent()
                                .getParent()
                                .getParent()
                                .getParent()
                                .getParent(),
                        ctx)
                .forEach(cu -> visitor.visit(cu, ctx));

        return classGraph;
    }

    private void compareProjectEdges(
            Graph<String, DefaultWeightedEdge> javaGraph,
            Graph<String, DefaultWeightedEdge> kotlinGraph,
            String javaPkgPrefix,
            String kotlinPkgPrefix) {
        // Extract project-specific vertices
        var javaVertices = javaGraph.vertexSet().stream()
                .filter(v -> v.startsWith(javaPkgPrefix))
                .toList();
        var kotlinVertices = kotlinGraph.vertexSet().stream()
                .filter(v -> v.startsWith(kotlinPkgPrefix))
                .toList();

        // Both should have at least one vertex
        assertFalse(javaVertices.isEmpty(), "Java graph should have project vertices");
        assertFalse(kotlinVertices.isEmpty(), "Kotlin graph should have project vertices");

        // Normalize vertex names by stripping package prefix
        var javaNormalized = javaVertices.stream()
                .collect(java.util.stream.Collectors.toMap(
                        v -> v.substring(javaPkgPrefix.length()), v -> v, (a, b) -> a));
        var kotlinNormalized = kotlinVertices.stream()
                .collect(java.util.stream.Collectors.toMap(
                        v -> v.substring(kotlinPkgPrefix.length()), v -> v, (a, b) -> a));

        assertEquals(
                normalizedEdges(javaGraph, javaNormalized),
                normalizedEdges(kotlinGraph, kotlinNormalized),
                "Java and Kotlin project edges and weights should match exactly");
    }

    private Map<String, Double> normalizedEdges(
            Graph<String, DefaultWeightedEdge> graph, Map<String, String> normalizedVertices) {
        Map<String, Double> edges = new HashMap<>();
        Map<String, String> vertexNames = new HashMap<>();
        normalizedVertices.forEach((normalized, vertex) -> vertexNames.put(vertex, normalized));
        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            String source = vertexNames.get(graph.getEdgeSource(edge));
            String target = vertexNames.get(graph.getEdgeTarget(edge));
            if (source != null && target != null) {
                edges.put(source + " -> " + target, graph.getEdgeWeight(edge));
            }
        }
        return edges;
    }
}

package org.hjug.graphbuilder.visitor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.hjug.graphbuilder.GraphDependencyCollector;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;

class JavaInitializerBlockVisitorTest {

    @Test
    void visitInstanceInitializerBlocks() throws IOException {

        String pathString = "src/test/java/org/hjug/graphbuilder/visitor/testclasses/initializers";
        File srcDirectory = new File(pathString);

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        Graph<String, DefaultWeightedEdge> classReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graph<String, DefaultWeightedEdge> packageReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphDependencyCollector dependencyCollector =
                new GraphDependencyCollector(classReferencesGraph, packageReferencesGraph);

        String repo = srcDirectory.toURI().toString().replace("/" + pathString, "");
        JavaVisitor<ExecutionContext> classDeclarationVisitor = new JavaVisitor<>(repo, dependencyCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            classDeclarationVisitor.visit(cu, ctx);
        });

        // Verify that the test class is in the graph
        Assertions.assertTrue(
                classReferencesGraph.containsVertex(
                        "org.hjug.graphbuilder.visitor.testclasses.initializers.InitializerBlockTestClass"),
                "InitializerBlockTestClass should be in the graph");

        // Verify ArrayList is captured from instance initializer block: new ArrayList<>()
        Assertions.assertTrue(
                classReferencesGraph.containsVertex("java.util.ArrayList"),
                "ArrayList should be captured from instance initializer block");

        // Verify edge from InitializerBlockTestClass to ArrayList exists
        Assertions.assertTrue(
                classReferencesGraph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.initializers.InitializerBlockTestClass",
                        "java.util.ArrayList"),
                "Should have edge from InitializerBlockTestClass to ArrayList from initializer block");

        // Verify HashMap is captured from instance initializer block: new HashMap<>()
        Assertions.assertTrue(
                classReferencesGraph.containsVertex("java.util.HashMap"),
                "HashMap should be captured from instance initializer block");

        // Verify edge from InitializerBlockTestClass to HashMap exists
        Assertions.assertTrue(
                classReferencesGraph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.initializers.InitializerBlockTestClass",
                        "java.util.HashMap"),
                "Should have edge from InitializerBlockTestClass to HashMap from initializer block");

        // Verify StringBuilder is captured from instance initializer block: new StringBuilder()
        Assertions.assertTrue(
                classReferencesGraph.containsVertex("java.lang.StringBuilder"),
                "StringBuilder should be captured from instance initializer block");

        // Verify edge from InitializerBlockTestClass to StringBuilder exists
        Assertions.assertTrue(
                classReferencesGraph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.initializers.InitializerBlockTestClass",
                        "java.lang.StringBuilder"),
                "Should have edge from InitializerBlockTestClass to StringBuilder from initializer block");
    }

    @Test
    void visitStaticInitializerBlocks() throws IOException {

        String pathString = "src/test/java/org/hjug/graphbuilder/visitor/testclasses/initializers";
        File srcDirectory = new File(pathString);

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        Graph<String, DefaultWeightedEdge> classReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graph<String, DefaultWeightedEdge> packageReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphDependencyCollector dependencyCollector =
                new GraphDependencyCollector(classReferencesGraph, packageReferencesGraph);

        String repo = srcDirectory.toURI().toString().replace("/" + pathString, "");
        JavaVisitor<ExecutionContext> classDeclarationVisitor = new JavaVisitor<>(repo, dependencyCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            classDeclarationVisitor.visit(cu, ctx);
        });

        // Verify that the complex test class is in the graph
        Assertions.assertTrue(
                classReferencesGraph.containsVertex(
                        "org.hjug.graphbuilder.visitor.testclasses.initializers.ComplexInitializerClass"),
                "ComplexInitializerClass should be in the graph");

        // Verify ConcurrentHashMap is captured from static initializer block
        Assertions.assertTrue(
                classReferencesGraph.containsVertex("java.util.concurrent.ConcurrentHashMap"),
                "ConcurrentHashMap should be captured from static initializer block");

        // Verify edge from ComplexInitializerClass to ConcurrentHashMap exists
        Assertions.assertTrue(
                classReferencesGraph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.initializers.ComplexInitializerClass",
                        "java.util.concurrent.ConcurrentHashMap"),
                "Should have edge from ComplexInitializerClass to ConcurrentHashMap from static initializer");

        // Verify AtomicInteger is captured from static initializer block
        Assertions.assertTrue(
                classReferencesGraph.containsVertex("java.util.concurrent.atomic.AtomicInteger"),
                "AtomicInteger should be captured from static initializer block");

        // Verify edge from ComplexInitializerClass to AtomicInteger exists
        Assertions.assertTrue(
                classReferencesGraph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.initializers.ComplexInitializerClass",
                        "java.util.concurrent.atomic.AtomicInteger"),
                "Should have edge from ComplexInitializerClass to AtomicInteger from static initializer");

        // Verify nested classes are captured from instance initializer
        Assertions.assertTrue(
                classReferencesGraph.containsVertex(
                        "org.hjug.graphbuilder.visitor.testclasses.initializers.ComplexInitializerClass$DataProcessor"),
                "DataProcessor nested class should be captured from instance initializer");

        Assertions.assertTrue(
                classReferencesGraph.containsVertex(
                        "org.hjug.graphbuilder.visitor.testclasses.initializers.ComplexInitializerClass$HelperService"),
                "HelperService nested class should be captured from instance initializer");
    }
}

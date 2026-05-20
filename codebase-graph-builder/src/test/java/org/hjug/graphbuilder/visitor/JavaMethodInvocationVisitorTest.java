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
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;

class JavaMethodInvocationVisitorTest {

    @Test
    void visitMethodInvocations() throws IOException {

        String pathString = "src/test/java/org/hjug/graphbuilder/visitor/testclasses/methodInvocation";
        File srcDirectory = new File(pathString);

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        Graph<String, DefaultWeightedEdge> classReferencesGraph =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graph<String, DefaultWeightedEdge> packageReferencesGraph =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphDependencyCollector dependencyCollector =
                new GraphDependencyCollector(classReferencesGraph, packageReferencesGraph);

        String repo = srcDirectory.toURI().toString().replace("/" + pathString, "");
        JavaVisitor<ExecutionContext> classDeclarationVisitor = new JavaVisitor<>(repo, dependencyCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            classDeclarationVisitor.visit(cu, ctx);
        });

        Graph<String, DefaultWeightedEdge> graph = classReferencesGraph;
        Assertions.assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.methodInvocation.A"));
        Assertions.assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.methodInvocation.B"));
        Assertions.assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.methodInvocation.C"));

        Assertions.assertEquals(
                3,
                graph.getEdgeWeight(graph.getEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.methodInvocation.A",
                        "org.hjug.graphbuilder.visitor.testclasses.methodInvocation.B")));
        Assertions.assertEquals(
                3,
                graph.getEdgeWeight(graph.getEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.methodInvocation.A",
                        "org.hjug.graphbuilder.visitor.testclasses.methodInvocation.C")));
    }
}

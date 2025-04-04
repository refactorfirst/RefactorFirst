package org.hjug.graphbuilder.visitor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
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

        File srcDirectory = new File("src/test/java/org/hjug/graphbuilder/visitor/testclasses/methodInvocation");

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        Graph<String, DefaultWeightedEdge> classReferencesGraph =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        Graph<String, DefaultWeightedEdge> packageReferencesGraph =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        JavaClassDeclarationVisitor<ExecutionContext> classDeclarationVisitor =
                new JavaClassDeclarationVisitor<>(classReferencesGraph);
        JavaVariableTypeVisitor<ExecutionContext> variableTypeVisitor =
                new JavaVariableTypeVisitor<>(classReferencesGraph, packageReferencesGraph);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            classDeclarationVisitor.visit(cu, ctx);
            variableTypeVisitor.visit(cu, ctx);
        });

        Graph<String, DefaultWeightedEdge> graph = classDeclarationVisitor.getClassReferencesGraph();
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

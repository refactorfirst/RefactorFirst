package org.hjug.parser.visitor;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

class JavaMethodInvocationVisitorTest {

    @Test
    void visitMethodInvocations() throws IOException {

        File srcDirectory = new File("src/test/java/org/hjug/parser/visitor/testclasses/methodInvocation");

        JavaParser javaParser =
                JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        JavaClassDeclarationVisitor<ExecutionContext> classDeclarationVisitor = new JavaClassDeclarationVisitor<>();

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            classDeclarationVisitor.visit(cu, ctx);
        });

        Graph<String, DefaultWeightedEdge> graph = classDeclarationVisitor.getClassReferencesGraph();
        Assertions.assertTrue(graph.containsVertex("org.hjug.parser.visitor.testclasses.methodInvocation.A"));
        Assertions.assertTrue(graph.containsVertex("org.hjug.parser.visitor.testclasses.methodInvocation.B"));
        Assertions.assertTrue(graph.containsVertex("org.hjug.parser.visitor.testclasses.methodInvocation.H"));

        Assertions.assertEquals(1,
                graph.getEdgeWeight(graph.getEdge("org.hjug.parser.visitor.testclasses.methodInvocation.A", "org.hjug.parser.visitor.testclasses.methodInvocation.B")));
        Assertions.assertEquals(2,
                graph.getEdgeWeight(graph.getEdge("org.hjug.parser.visitor.testclasses.methodInvocation.A", "org.hjug.parser.visitor.testclasses.methodInvocation.H")));
        Assertions.assertEquals(1,
                graph.getEdgeWeight(graph.getEdge("org.hjug.parser.visitor.testclasses.methodInvocation.H", "org.hjug.parser.visitor.testclasses.methodInvocation.B")));

    }
}
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

class JavaMethodDeclarationVisitorTest {

    @Test
    void visitMethodDeclarations() throws IOException {

        String pathString = "src/test/java/org/hjug/graphbuilder/visitor/testclasses";
        File srcDirectory = new File(pathString);

        org.openrewrite.java.JavaParser javaParser =
                JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        Graph<String, DefaultWeightedEdge> classReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graph<String, DefaultWeightedEdge> packageReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        GraphDependencyCollector dependencyCollector =
                new GraphDependencyCollector(classReferencesGraph, packageReferencesGraph);

        String repo = srcDirectory.toURI().toString().replace("/" + pathString, "");
        JavaVisitor<ExecutionContext> methodDeclarationVisitor = new JavaVisitor<>(repo, dependencyCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            methodDeclarationVisitor.visit(cu, ctx);
        });

        Assertions.assertTrue(classReferencesGraph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.A"));

        // TODO: Assert stuff
        /*   Assertions.assertTrue(methodDeclarationVisitor.getClassReferencesGraph().containsVertex("org.hjug.javaVariableVisitorTestClasses.A"));
        Assertions.assertTrue(methodDeclarationVisitor.getClassReferencesGraph().containsVertex("org.hjug.javaVariableVisitorTestClasses.B"));
        Assertions.assertTrue(methodDeclarationVisitor.getClassReferencesGraph().containsVertex("org.hjug.javaVariableVisitorTestClasses.C"));
        Assertions.assertFalse(methodDeclarationVisitor.getClassReferencesGraph().containsVertex("org.hjug.javaVariableVisitorTestClasses.D"));
        Assertions.assertTrue(methodDeclarationVisitor.getClassReferencesGraph().containsVertex("org.hjug.javaVariableVisitorTestClasses.MyAnnotation"));
        Assertions.assertFalse(methodDeclarationVisitor.getClassReferencesGraph().containsVertex("org.hjug.javaVariableVisitorTestClasses.E"));
        Assertions.assertTrue(methodDeclarationVisitor.getClassReferencesGraph().containsVertex("org.hjug.javaVariableVisitorTestClasses.F"));
        Assertions.assertTrue(methodDeclarationVisitor.getClassReferencesGraph().containsVertex("org.hjug.javaVariableVisitorTestClasses.G"));*/
    }
}

package org.hjug.graphbuilder.visitor;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;

class JavaVisitorTest {

    @Test
    void visitClasses() throws IOException {

        String pathString = "src/test/java/org/hjug/graphbuilder/visitor/testclasses";
        File srcDirectory = new File(pathString);

        org.openrewrite.java.JavaParser javaParser =
                JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        final Graph<String, DefaultWeightedEdge> classReferencesGraph =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        final Graph<String, DefaultWeightedEdge> packageReferencesGraph =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        final GraphDependencyCollector dependencyCollector =
                new GraphDependencyCollector(classReferencesGraph, packageReferencesGraph);

        String repo = srcDirectory.toURI().toString().replace("/" + pathString, "");
        final JavaVisitor<ExecutionContext> javaVisitor = new JavaVisitor<>(repo, dependencyCollector);

        //        final JavaVisitor<ExecutionContext> javaVisitor = new JavaVisitor<>(dependencyCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            System.out.println(cu.getSourcePath());
            javaVisitor.visit(cu, ctx);
        });

        assertEquals(5, dependencyCollector.getPackagesInCodebase().size());
    }
}

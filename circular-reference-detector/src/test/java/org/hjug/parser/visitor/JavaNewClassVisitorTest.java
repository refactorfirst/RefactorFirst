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

public class JavaNewClassVisitorTest {

    @Test
    void visitNewClass() throws IOException {

        File srcDirectory = new File("src/test/java/org/hjug/parser/visitor/testclasses/newClass");

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        JavaClassDeclarationVisitor classDeclarationVisitor = new JavaClassDeclarationVisitor();
        JavaVariableTypeVisitor variableTypeVisitor = new JavaVariableTypeVisitor();

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            classDeclarationVisitor.visit(cu, ctx);
            variableTypeVisitor.visit(cu, ctx);
        });

        Graph<String, DefaultWeightedEdge> assignmentGraph = variableTypeVisitor.getClassReferencesGraph();
        Assertions.assertTrue(assignmentGraph.containsVertex("org.hjug.parser.visitor.testclasses.newClass.A"));
        Assertions.assertTrue(assignmentGraph.containsVertex("org.hjug.parser.visitor.testclasses.newClass.B"));
        Assertions.assertTrue(assignmentGraph.containsVertex("org.hjug.parser.visitor.testclasses.newClass.C"));
        Assertions.assertTrue(assignmentGraph.containsVertex("org.hjug.parser.visitor.testclasses.newClass.D"));

        Graph<String, DefaultWeightedEdge> newInstanceGraph = classDeclarationVisitor.getClassReferencesGraph();
        Assertions.assertTrue(newInstanceGraph.containsVertex("org.hjug.parser.visitor.testclasses.newClass.A"));
        Assertions.assertTrue(newInstanceGraph.containsVertex("org.hjug.parser.visitor.testclasses.newClass.D"));

    }

}

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

    //@Test
    void visitNewClass() throws IOException {

        File srcDirectory = new File("src/test/java/org/hjug/parser/visitor/testclasses/newClass");

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        JavaNewClassVisitor classDeclarationVisitor = new JavaNewClassVisitor();

//        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
//        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
//            classDeclarationVisitor.visit(cu, ctx);
//        });
//
//        Graph<String, DefaultWeightedEdge> graph = classDeclarationVisitor.getClassReferencesGraph();
//        Assertions.assertTrue(graph.containsVertex("org.hjug.parser.visitor.testclasses.newClass.A"));

    }

}

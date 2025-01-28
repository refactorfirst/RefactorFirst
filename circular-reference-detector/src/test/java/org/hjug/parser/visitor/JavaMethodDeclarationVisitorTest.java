package org.hjug.parser.visitor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;

class JavaMethodDeclarationVisitorTest {

    @Test
    void visitMethodDeclarations() throws IOException {

        File srcDirectory = new File("src/test/java/org/hjug/parser/visitor/testclasses");

        org.openrewrite.java.JavaParser javaParser =
                JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        JavaMethodDeclarationVisitor<ExecutionContext> methodDeclarationVisitor = new JavaMethodDeclarationVisitor<>();

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            methodDeclarationVisitor.visit(cu, ctx);
        });

        methodDeclarationVisitor.getClassReferencesGraph();

        Assertions.assertTrue(methodDeclarationVisitor
                .getClassReferencesGraph()
                .containsVertex("org.hjug.parser.visitor.testclasses.A"));

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

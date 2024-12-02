package org.hjug.parser;

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

import static org.junit.jupiter.api.Assertions.*;

class JavaClassDeclarationVisitorTest {

    @Test
    void visitClasses() throws IOException {

        File srcDirectory = new File("src/test/resources/visitorSrcDirectory");

        org.openrewrite.java.JavaParser javaParser =
                JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        JavaClassDeclarationVisitor<ExecutionContext> javaVariableCapturingVisitor = new JavaClassDeclarationVisitor<>();

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            javaVariableCapturingVisitor.visit(cu, ctx);
        });

        Assertions.assertTrue(javaVariableCapturingVisitor.getClassReferencesGraph().containsVertex("org.hjug.javaVariableVisitorTestClasses.A"));
        Assertions.assertTrue(javaVariableCapturingVisitor.getClassReferencesGraph().containsVertex("org.hjug.javaVariableVisitorTestClasses.B"));
        Assertions.assertTrue(javaVariableCapturingVisitor.getClassReferencesGraph().containsVertex("org.hjug.javaVariableVisitorTestClasses.C"));
        Assertions.assertFalse(javaVariableCapturingVisitor.getClassReferencesGraph().containsVertex("org.hjug.javaVariableVisitorTestClasses.D"));
        Assertions.assertTrue(javaVariableCapturingVisitor.getClassReferencesGraph().containsVertex("org.hjug.javaVariableVisitorTestClasses.MyAnnotation"));
        Assertions.assertFalse(javaVariableCapturingVisitor.getClassReferencesGraph().containsVertex("org.hjug.javaVariableVisitorTestClasses.E"));
        Assertions.assertTrue(javaVariableCapturingVisitor.getClassReferencesGraph().containsVertex("org.hjug.javaVariableVisitorTestClasses.F"));
        Assertions.assertTrue(javaVariableCapturingVisitor.getClassReferencesGraph().containsVertex("org.hjug.javaVariableVisitorTestClasses.G"));
    }

}
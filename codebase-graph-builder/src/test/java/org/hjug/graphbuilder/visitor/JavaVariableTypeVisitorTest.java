package org.hjug.graphbuilder.visitor;

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

class JavaVariableTypeVisitorTest {

    @Test
    void visitClasses() throws IOException {

        File srcDirectory = new File("src/test/java/org/hjug/graphbuilder/visitor/testclasses");

        org.openrewrite.java.JavaParser javaParser =
                JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        JavaVariableTypeVisitor<ExecutionContext> javaVariableCapturingVisitor = new JavaVariableTypeVisitor<>();

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            javaVariableCapturingVisitor.visit(cu, ctx);
        });

        Assertions.assertTrue(javaVariableCapturingVisitor
                .getClassReferencesGraph()
                .containsVertex("org.hjug.graphbuilder.visitor.testclasses.A"));
        Assertions.assertTrue(javaVariableCapturingVisitor
                .getClassReferencesGraph()
                .containsVertex("org.hjug.graphbuilder.visitor.testclasses.B"));
        Assertions.assertTrue(javaVariableCapturingVisitor
                .getClassReferencesGraph()
                .containsVertex("org.hjug.graphbuilder.visitor.testclasses.C"));
        Assertions.assertTrue(javaVariableCapturingVisitor
                .getClassReferencesGraph()
                .containsVertex("org.hjug.graphbuilder.visitor.testclasses.D"));
        Assertions.assertTrue(javaVariableCapturingVisitor
                .getClassReferencesGraph()
                .containsVertex("org.hjug.graphbuilder.visitor.testclasses.E"));
        Assertions.assertTrue(javaVariableCapturingVisitor
                .getClassReferencesGraph()
                .containsVertex("org.hjug.graphbuilder.visitor.testclasses.MyAnnotation"));
        Assertions.assertFalse(javaVariableCapturingVisitor
                .getClassReferencesGraph()
                .containsVertex("org.hjug.graphbuilder.visitor.testclasses.F"));
        Assertions.assertFalse(javaVariableCapturingVisitor
                .getClassReferencesGraph()
                .containsVertex("org.hjug.graphbuilder.visitor.testclasses.G"));
    }
}

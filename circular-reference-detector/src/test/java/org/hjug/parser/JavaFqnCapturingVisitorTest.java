package org.hjug.parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;

class JavaFqnCapturingVisitorTest {

    @Test
    void visitClasses() throws IOException {

        File srcDirectory = new File("src/test/resources/visitorSrcDirectory");

        org.openrewrite.java.JavaParser javaParser =
                JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        JavaFqnCapturingVisitor<ExecutionContext> javaFqnCapturingVisitor = new JavaFqnCapturingVisitor();

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            javaFqnCapturingVisitor.visit(cu, ctx);
        });

        Map<String, Map<String, String>> fqns = javaFqnCapturingVisitor.getFqns();
        Map<String, String> processed = fqns.get("org.hjug.testclasses");
        Assertions.assertEquals("org.hjug.testclasses.A", processed.get("A"));
        Assertions.assertEquals("org.hjug.testclasses.A.InnerClass", processed.get("A.InnerClass"));
        Assertions.assertEquals("org.hjug.testclasses.A.InnerClass", processed.get("InnerClass"));
        Assertions.assertEquals(
                "org.hjug.testclasses.A.InnerClass.InnerInner", processed.get("A.InnerClass.InnerInner"));
        Assertions.assertEquals("org.hjug.testclasses.A.InnerClass.InnerInner", processed.get("InnerClass.InnerInner"));
        Assertions.assertEquals("org.hjug.testclasses.A.InnerClass.InnerInner", processed.get("InnerInner"));
        Assertions.assertEquals(
                "org.hjug.testclasses.A.InnerClass.InnerInner.MegaInner",
                processed.get("A.InnerClass.InnerInner.MegaInner"));
        Assertions.assertEquals(
                "org.hjug.testclasses.A.InnerClass.InnerInner.MegaInner",
                processed.get("InnerClass.InnerInner.MegaInner"));
        Assertions.assertEquals(
                "org.hjug.testclasses.A.InnerClass.InnerInner.MegaInner", processed.get("InnerInner.MegaInner"));
        Assertions.assertEquals("org.hjug.testclasses.A.InnerClass.InnerInner.MegaInner", processed.get("MegaInner"));
        Assertions.assertEquals("org.hjug.testclasses.A.StaticInnerClass", processed.get("A.StaticInnerClass"));
        Assertions.assertEquals("org.hjug.testclasses.A.StaticInnerClass", processed.get("StaticInnerClass"));
        Assertions.assertEquals("org.hjug.testclasses.NonPublic", processed.get("NonPublic"));
    }
}

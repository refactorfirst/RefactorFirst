package org.hjug.parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;

class JavaVisitorTest {

    @Test
    void visitClasses() throws IOException {

        File srcDirectory = new File("src/test/resources/visitorSrcDirectory");

        org.openrewrite.java.JavaParser javaParser =
                JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        JavaVisitor javaVisitor = new JavaVisitor();

        List<String> fqdns = new ArrayList<>();

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach((cu) -> {
            System.out.println(cu.getSourcePath());
            javaVisitor.visit(cu, ctx);
            //                fqdns.addAll(customVisitor.getFqdns());
        });

        //        return fqdns;
    }

    class Inner {}
}

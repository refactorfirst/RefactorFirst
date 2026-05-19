package org.hjug.graphbuilder.visitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.hjug.graphbuilder.DependencyCollector;
import org.hjug.graphbuilder.GraphDependencyCollector;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;

class JavaVisitorInnerClassTest {

    private static final String PATH_STRING = "src/test/java/org/hjug/graphbuilder/visitor/testclasses";

    private String repoFromTestclasses() {
        File srcDirectory = new File(PATH_STRING);
        return srcDirectory.toURI().toString().replace("/" + PATH_STRING, "");
    }

    private void visitAll(JavaVisitor<ExecutionContext> visitor) throws IOException {
        File srcDirectory = new File(PATH_STRING);
        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);
        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> visitor.visit(cu, ctx));
    }

    private JavaVisitor<ExecutionContext> buildVisitor(String repo) {
        GraphDependencyCollector collector = new GraphDependencyCollector(
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class));
        return new JavaVisitor<>(repo, collector);
    }

    @Test
    void instanceInnerClassFqnIsTrackedInMapping() throws IOException {
        JavaVisitor<ExecutionContext> visitor = buildVisitor(repoFromTestclasses());
        visitAll(visitor);

        assertTrue(visitor.getClassToSourceFilePathMapping()
                .containsKey("org.hjug.graphbuilder.visitor.testclasses.A$InnerClass"));
    }

    @Test
    void staticNestedClassFqnIsTrackedInMapping() throws IOException {
        JavaVisitor<ExecutionContext> visitor = buildVisitor(repoFromTestclasses());
        visitAll(visitor);

        assertTrue(visitor.getClassToSourceFilePathMapping()
                .containsKey("org.hjug.graphbuilder.visitor.testclasses.A$StaticInnerClass"));
    }

    @Test
    void deeplyNestedInnerClassFqnIsTrackedInMapping() throws IOException {
        JavaVisitor<ExecutionContext> visitor = buildVisitor(repoFromTestclasses());
        visitAll(visitor);

        assertTrue(visitor.getClassToSourceFilePathMapping()
                .containsKey("org.hjug.graphbuilder.visitor.testclasses.A$InnerClass$InnerInner$MegaInner"));
    }

    @Test
    void innerClassPathIsSimplifiedFromFqnWhenRepoContainsJunitDash() throws IOException {
        JavaVisitor<ExecutionContext> visitor = buildVisitor("/tmp/junit-fake-repo");
        visitAll(visitor);

        assertEquals(
                "org/hjug/graphbuilder/visitor/testclasses/A.java",
                visitor.getClassToSourceFilePathMapping()
                        .get("org.hjug.graphbuilder.visitor.testclasses.A$InnerClass"));
    }

    @Test
    void recordClassLocationIsCalledForEachInnerClass() throws IOException {
        DependencyCollector mockCollector = mock(DependencyCollector.class);
        File srcDirectory = new File(PATH_STRING);
        String repo = repoFromTestclasses();
        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);
        JavaVisitor<ExecutionContext> visitor = new JavaVisitor<>(repo, mockCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> visitor.visit(cu, ctx));

        verify(mockCollector)
                .recordClassLocation(eq("org.hjug.graphbuilder.visitor.testclasses.A$InnerClass"), anyString());
        verify(mockCollector)
                .recordClassLocation(eq("org.hjug.graphbuilder.visitor.testclasses.A$StaticInnerClass"), anyString());
    }
}

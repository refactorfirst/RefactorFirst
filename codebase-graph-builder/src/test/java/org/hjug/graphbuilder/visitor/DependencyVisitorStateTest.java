package org.hjug.graphbuilder.visitor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hjug.graphbuilder.GraphDependencyCollector;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaParser;

/**
 * Tests for {@link DependencyVisitorState} repositoryRoot field.
 */
class DependencyVisitorStateTest {

    @DisplayName("repositoryRoot field exists and can be set/get")
    @Test
    void repositoryRoot_canBeSetAndGet() {
        GraphDependencyCollector collector = new GraphDependencyCollector(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class));
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        JavaParser javaParser = JavaParser.fromJavaVersion().build();

        // Create a visitor to access its state
        JavaVisitor<ExecutionContext> visitor = new JavaVisitor<>("/tmp/test-repo", "", collector);

        // Access state via getter (AbstractDependencyVisitor exposes it)
        DependencyVisitorState state = visitor.getState();
        assertNotNull(state);

        // Default repositoryRoot should be empty
        assertEquals("", state.getRepositoryRoot());

        // Set repositoryRoot
        state.setRepositoryRoot("/path/to/repo/root");
        assertEquals("/path/to/repo/root", state.getRepositoryRoot());
    }

    @DisplayName("repositoryRoot is used by recordClassLocation for canonicalization")
    @Test
    void repositoryRoot_usedForCanonicalization() throws IOException {
        GraphDependencyCollector collector = new GraphDependencyCollector(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class));
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        JavaParser javaParser = JavaParser.fromJavaVersion().build();

        // Create a simple Java file in a package structure matching repo-root/module1/...
        // Use a temp directory that works on Windows
        Path tempDir = Files.createTempDirectory("repo-root-test");
        Path module1Src = tempDir.resolve("module1/src/main/java/com/example");
        Files.createDirectories(module1Src);
        String code = "package com.example;\n\npublic class ClassInModule1 { }";
        Path sourceFile = module1Src.resolve("ClassInModule1.java");
        Files.writeString(sourceFile, code);

        // Create visitor with repositoryRoot set (junit branch)
        JavaVisitor<ExecutionContext> visitor = new JavaVisitor<>("/tmp/junit-fake-repo", "", collector);

        DependencyVisitorState state = visitor.getState();
        state.setRepositoryRoot(tempDir.toString());

        // Parse all Java files under the source root (tempDir)
        List<Path> files;
        try (var walk = Files.walk(tempDir)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        javaParser.parse(files, tempDir, ctx).forEach(cu -> {
            // Make source path absolute (resolve against source root tempDir)
            Path absoluteSourcePath = tempDir.resolve(cu.getSourcePath()).normalize();
            SourceFile cuWithAbsPath = cu.withSourcePath(absoluteSourcePath);
            visitor.visit(cuWithAbsPath, ctx);
        });

        // The class should map to repo-root relative path: module1/src/main/java/com/example/ClassInModule1.java
        Map<String, String> mapping = visitor.getClassToSourceFilePathMapping();
        String classFqn = "com.example.ClassInModule1";
        assertNotNull(mapping.get(classFqn), "ClassInModule1 should be in mapping");

        // In junit branch with repositoryRoot, path should be relative to repositoryRoot
        String expectedPath = "module1/src/main/java/com/example/ClassInModule1.java";
        assertEquals(expectedPath, mapping.get(classFqn), "Should map to repo-root relative path");
    }
}

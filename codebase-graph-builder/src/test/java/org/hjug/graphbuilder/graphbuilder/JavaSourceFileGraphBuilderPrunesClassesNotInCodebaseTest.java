package org.hjug.graphbuilder.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.CompositeGraphBuilder;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * White-box test for the static class/prune helpers that used to live on the
 * (removed) {@code JavaGraphBuilder} and now live on
 * {@link JavaSourceFileGraphBuilder}. Exercises pruning directly against an
 * end-to-end DTO produced from the Java-only fixture via
 * {@link CompositeGraphBuilder}.
 */
class JavaSourceFileGraphBuilderPrunesClassesNotInCodebaseTest {

    @Test
    void removeClassesNotInCodebase() throws IOException {
        File srcDirectory = new File("src/test/resources/javaSrcDirectory");
        CompositeGraphBuilder compositeGraphBuilder = new CompositeGraphBuilder();
        CodebaseGraphDTO dto = compositeGraphBuilder.getCodebaseGraphDTO(srcDirectory.getAbsolutePath(), false, "");
        Graph<String, DefaultWeightedEdge> classReferencesGraph = dto.getClassReferencesGraph();
        classReferencesGraph.addVertex("org.favioriteoss.FunClass");
        classReferencesGraph.addVertex("org.favioriteoss.AnotherFunClass");

        DefaultWeightedEdge edge1 =
                classReferencesGraph.addEdge("com.ideacrest.parser.testclasses.A", "org.favioriteoss.FunClass");
        DefaultWeightedEdge edge2 =
                classReferencesGraph.addEdge("com.ideacrest.parser.testclasses.A", "org.favioriteoss.AnotherFunClass");

        assertTrue(classReferencesGraph.containsVertex("org.favioriteoss.FunClass"));
        assertTrue(classReferencesGraph.containsVertex("org.favioriteoss.AnotherFunClass"));

        Set<String> packagesInCodebase = new HashSet<>();
        packagesInCodebase.add("com.ideacrest.parser.testclasses");

        JavaSourceFileGraphBuilder.removeClassesNotInCodebase(packagesInCodebase, classReferencesGraph);

        assertFalse(classReferencesGraph.containsVertex("org.favioriteoss.FunClass"));
        assertFalse(classReferencesGraph.containsVertex("org.favioriteoss.AnotherFunClass"));
        assertFalse(classReferencesGraph.containsEdge(edge1));
        assertFalse(classReferencesGraph.containsEdge(edge2));
    }

    @Test
    @DisplayName("removePackagesNotInCodebase drops packages outside the codebase package set")
    void removePackagesNotInCodebase() throws IOException {
        File srcDirectory = new File("src/test/resources/javaSrcDirectory");
        CompositeGraphBuilder compositeGraphBuilder = new CompositeGraphBuilder();
        CodebaseGraphDTO dto = compositeGraphBuilder.getCodebaseGraphDTO(srcDirectory.getAbsolutePath(), false, "");
        Graph<String, DefaultWeightedEdge> packageReferencesGraph = dto.getPackageReferencesGraph();
        packageReferencesGraph.addVertex("org.favioriteoss");

        assertTrue(packageReferencesGraph.containsVertex("org.favioriteoss"));

        Set<String> packagesInCodebase =
                new HashSet<>(dto.getPackageReferencesGraph().vertexSet());
        // remove the rogue package from the "in codebase" set
        packagesInCodebase.remove("org.favioriteoss");

        JavaSourceFileGraphBuilder.removePackagesNotInCodebase(packagesInCodebase, packageReferencesGraph);

        assertFalse(packageReferencesGraph.containsVertex("org.favioriteoss"));
    }

    @Test
    @DisplayName("External JavaFX classes referenced in source are pruned from class graph")
    void externalJavaFXClassesArePruned(@TempDir File tempDir) throws IOException {
        // Create a temporary Java file that references JavaFX classes
        File srcFile = new File(tempDir, "MyApp.java");
        Files.writeString(
                srcFile.toPath(),
                """
            package com.myapp;
            import javafx.scene.control.Button;
            import javafx.scene.layout.Pane;
            public class MyApp {
                Button btn = new Button();
                Pane pane = new Pane();
            }
            """);

        CompositeGraphBuilder builder = new CompositeGraphBuilder();
        CodebaseGraphDTO dto = builder.getCodebaseGraphDTO(tempDir.getAbsolutePath(), false, "");

        // JavaFX classes should NOT be in the class graph
        assertFalse(dto.getClassReferencesGraph().containsVertex("javafx.scene.control.Button"));
        assertFalse(dto.getClassReferencesGraph().containsVertex("javafx.scene.layout.Pane"));
        // Fabricated versions (attributed to caller's package) should also be removed
        assertFalse(dto.getClassReferencesGraph().containsVertex("com.myapp.Button"));
        assertFalse(dto.getClassReferencesGraph().containsVertex("com.myapp.Pane"));

        // Only the actual codebase class should remain
        assertTrue(dto.getClassReferencesGraph().containsVertex("com.myapp.MyApp"));
    }
}

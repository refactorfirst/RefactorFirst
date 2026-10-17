package org.hjug.graphbuilder.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.CompositeGraphBuilder;
import org.hjug.graphbuilder.VertexInfo;
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

    /** Verifies that pruning external classes also removes their incident dependency edges. */
    @Test
    void removeClassesNotInCodebase() throws IOException {
        File srcDirectory = new File("src/test/resources/javaSrcDirectory");
        CompositeGraphBuilder compositeGraphBuilder = new CompositeGraphBuilder();
        CodebaseGraphDTO dto = compositeGraphBuilder.getCodebaseGraphDTO(srcDirectory.getAbsolutePath(), false, "");
        Graph<VertexInfo, DefaultWeightedEdge> classReferencesGraph = dto.getClassReferencesGraph();
        classReferencesGraph.addVertex(VertexInfo.forName("org.favioriteoss.FunClass"));
        classReferencesGraph.addVertex(VertexInfo.forName("org.favioriteoss.AnotherFunClass"));

        DefaultWeightedEdge edge1 = classReferencesGraph.addEdge(
                VertexInfo.forName("com.ideacrest.parser.testclasses.A"),
                VertexInfo.forName("org.favioriteoss.FunClass"));
        DefaultWeightedEdge edge2 = classReferencesGraph.addEdge(
                VertexInfo.forName("com.ideacrest.parser.testclasses.A"),
                VertexInfo.forName("org.favioriteoss.AnotherFunClass"));

        assertTrue(classReferencesGraph.containsVertex(VertexInfo.forName("org.favioriteoss.FunClass")));
        assertTrue(classReferencesGraph.containsVertex(VertexInfo.forName("org.favioriteoss.AnotherFunClass")));

        Set<String> packagesInCodebase = new HashSet<>();
        packagesInCodebase.add("com.ideacrest.parser.testclasses");

        JavaSourceFileGraphBuilder.removeClassesNotInCodebase(packagesInCodebase, classReferencesGraph);

        assertFalse(classReferencesGraph.containsVertex(VertexInfo.forName("org.favioriteoss.FunClass")));
        assertFalse(classReferencesGraph.containsVertex(VertexInfo.forName("org.favioriteoss.AnotherFunClass")));
        assertFalse(classReferencesGraph.containsEdge(edge1));
        assertFalse(classReferencesGraph.containsEdge(edge2));
    }

    /** Verifies that package pruning removes vertices outside the set of codebase packages. */
    @Test
    @DisplayName("removePackagesNotInCodebase drops packages outside the codebase package set")
    void removePackagesNotInCodebase() throws IOException {
        File srcDirectory = new File("src/test/resources/javaSrcDirectory");
        CompositeGraphBuilder compositeGraphBuilder = new CompositeGraphBuilder();
        CodebaseGraphDTO dto = compositeGraphBuilder.getCodebaseGraphDTO(srcDirectory.getAbsolutePath(), false, "");
        Graph<VertexInfo, DefaultWeightedEdge> packageReferencesGraph = dto.getPackageReferencesGraph();
        packageReferencesGraph.addVertex(VertexInfo.forName("org.favioriteoss"));

        assertTrue(packageReferencesGraph.containsVertex(VertexInfo.forName("org.favioriteoss")));

        Set<String> packagesInCodebase = dto.getPackageReferencesGraph().vertexSet().stream()
                .map(VertexInfo::getName)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        // remove the rogue package from the "in codebase" set
        packagesInCodebase.remove("org.favioriteoss");

        JavaSourceFileGraphBuilder.removePackagesNotInCodebase(packagesInCodebase, packageReferencesGraph);

        assertFalse(packageReferencesGraph.containsVertex(VertexInfo.forName("org.favioriteoss")));
    }

    /** Verifies that JavaFX library references are excluded from the final codebase graph. */
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
        assertFalse(dto.getClassReferencesGraph().containsVertex(VertexInfo.forName("javafx.scene.control.Button")));
        assertFalse(dto.getClassReferencesGraph().containsVertex(VertexInfo.forName("javafx.scene.layout.Pane")));
        // Fabricated versions (attributed to caller's package) should also be removed
        assertFalse(dto.getClassReferencesGraph().containsVertex(VertexInfo.forName("com.myapp.Button")));
        assertFalse(dto.getClassReferencesGraph().containsVertex(VertexInfo.forName("com.myapp.Pane")));

        // Only the actual codebase class should remain
        assertTrue(dto.getClassReferencesGraph().containsVertex(VertexInfo.forName("com.myapp.MyApp")));
    }
}

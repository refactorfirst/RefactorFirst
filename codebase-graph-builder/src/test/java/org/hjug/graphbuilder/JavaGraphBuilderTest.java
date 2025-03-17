package org.hjug.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JavaGraphBuilderTest {

    JavaGraphBuilder javaGraphBuilder = new JavaGraphBuilder();

    @DisplayName("When source directory input param is empty or null throw IllegalArgumentException.")
    @Test
    void parseSourceDirectoryEmptyTest() {
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> javaGraphBuilder.getClassReferences("", false, ""));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> javaGraphBuilder.getClassReferences(null, false, ""));
    }

    @DisplayName("Given a valid source directory input parameter return a valid graph.")
    @Test
    void parseSourceDirectoryTest() throws IOException {
        File srcDirectory = new File("src/test/resources/javaSrcDirectory");
        Graph<String, DefaultWeightedEdge> classReferencesGraph =
                javaGraphBuilder.getClassReferences(srcDirectory.getAbsolutePath(), false, "");
        assertNotNull(classReferencesGraph);
        assertEquals(5, classReferencesGraph.vertexSet().size());
        assertEquals(7, classReferencesGraph.edgeSet().size());
        assertTrue(classReferencesGraph.containsVertex("com.ideacrest.parser.testclasses.A"));
        assertTrue(classReferencesGraph.containsVertex("com.ideacrest.parser.testclasses.B"));
        assertTrue(classReferencesGraph.containsVertex("com.ideacrest.parser.testclasses.C"));
        assertTrue(classReferencesGraph.containsVertex("com.ideacrest.parser.testclasses.D"));
        assertTrue(classReferencesGraph.containsVertex("com.ideacrest.parser.testclasses.E"));
        assertTrue(classReferencesGraph.containsEdge(
                "com.ideacrest.parser.testclasses.A", "com.ideacrest.parser.testclasses.B"));
        assertTrue(classReferencesGraph.containsEdge(
                "com.ideacrest.parser.testclasses.B", "com.ideacrest.parser.testclasses.C"));
        assertTrue(classReferencesGraph.containsEdge(
                "com.ideacrest.parser.testclasses.C", "com.ideacrest.parser.testclasses.A"));
        assertTrue(classReferencesGraph.containsEdge(
                "com.ideacrest.parser.testclasses.C", "com.ideacrest.parser.testclasses.E"));
        assertTrue(classReferencesGraph.containsEdge(
                "com.ideacrest.parser.testclasses.D", "com.ideacrest.parser.testclasses.A"));
        assertTrue(classReferencesGraph.containsEdge(
                "com.ideacrest.parser.testclasses.D", "com.ideacrest.parser.testclasses.C"));
        assertTrue(classReferencesGraph.containsEdge(
                "com.ideacrest.parser.testclasses.E", "com.ideacrest.parser.testclasses.D"));

        // confirm edge weight calculations
        assertEquals(
                1,
                getEdgeWeight(
                        classReferencesGraph,
                        "com.ideacrest.parser.testclasses.A",
                        "com.ideacrest.parser.testclasses.B"));
        assertEquals(
                2,
                getEdgeWeight(
                        classReferencesGraph,
                        "com.ideacrest.parser.testclasses.E",
                        "com.ideacrest.parser.testclasses.D"));
    }

    private static double getEdgeWeight(
            Graph<String, DefaultWeightedEdge> classReferencesGraph, String sourceVertex, String targetVertex) {
        return classReferencesGraph.getEdgeWeight(classReferencesGraph.getEdge(sourceVertex, targetVertex));
    }

    @Test
    void removeClassesNotInCodebase() throws IOException {
        File srcDirectory = new File("src/test/resources/javaSrcDirectory");
        Graph<String, DefaultWeightedEdge> classReferencesGraph =
                javaGraphBuilder.getClassReferences(srcDirectory.getAbsolutePath(), false, "");

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

        javaGraphBuilder.removeClassesNotInCodebase(packagesInCodebase, classReferencesGraph);

        assertFalse(classReferencesGraph.containsVertex("org.favioriteoss.FunClass"));
        assertFalse(classReferencesGraph.containsVertex("org.favioriteoss.AnotherFunClass"));
        assertFalse(classReferencesGraph.containsEdge(edge1));
        assertFalse(classReferencesGraph.containsEdge(edge2));
    }
}

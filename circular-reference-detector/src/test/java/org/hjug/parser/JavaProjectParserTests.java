package org.hjug.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JavaProjectParserTests {

    JavaProjectParser sutJavaProjectParser = new JavaProjectParser();

    @DisplayName("When source directory input param is empty or null throw IllegalArgumentException.")
    @Test
    void parseSourceDirectoryEmptyTest() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> sutJavaProjectParser.getClassReferences(""));
        Assertions.assertThrows(IllegalArgumentException.class, () -> sutJavaProjectParser.getClassReferences(null));
    }

    @DisplayName("Given a valid source directory input parameter return a valid graph.")
    @Test
    void parseSourceDirectoryTest() throws IOException {
        File srcDirectory = new File("src/test/resources/javaSrcDirectory");
        Graph<String, DefaultWeightedEdge> classReferencesGraph =
                sutJavaProjectParser.getClassReferences(srcDirectory.getAbsolutePath());
        assertNotNull(classReferencesGraph);
        assertEquals(5, classReferencesGraph.vertexSet().size());
        assertEquals(7, classReferencesGraph.edgeSet().size());
        assertTrue(classReferencesGraph.containsVertex("A"));
        assertTrue(classReferencesGraph.containsVertex("B"));
        assertTrue(classReferencesGraph.containsVertex("C"));
        assertTrue(classReferencesGraph.containsVertex("D"));
        assertTrue(classReferencesGraph.containsVertex("E"));
        assertTrue(classReferencesGraph.containsEdge("A", "B"));
        assertTrue(classReferencesGraph.containsEdge("B", "C"));
        assertTrue(classReferencesGraph.containsEdge("C", "A"));
        assertTrue(classReferencesGraph.containsEdge("C", "E"));
        assertTrue(classReferencesGraph.containsEdge("D", "A"));
        assertTrue(classReferencesGraph.containsEdge("D", "C"));
        assertTrue(classReferencesGraph.containsEdge("E", "D"));

        // confirm edge weight calculations
        assertEquals(1, getEdgeWeight(classReferencesGraph, "A", "B"));
        assertEquals(2, getEdgeWeight(classReferencesGraph, "E", "D"));
    }

    private static double getEdgeWeight(
            Graph<String, DefaultWeightedEdge> classReferencesGraph, String sourceVertex, String targetVertex) {
        return classReferencesGraph.getEdgeWeight(classReferencesGraph.getEdge(sourceVertex, targetVertex));
    }
}

package com.ideacrest.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JavaProjectParserTests {

    JavaProjectParser sutJavaProjectParser = new JavaProjectParser();

    @DisplayName("When source directory input param is empty or null throw IllegalArgumentException.")
    @Test
    public void parseSourceDirectoryEmptyTest() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> sutJavaProjectParser.getClassReferences(""));
        Assertions.assertThrows(IllegalArgumentException.class, () -> sutJavaProjectParser.getClassReferences(null));
    }

    @DisplayName("Given a valid source directory input parameter return a valid graph.")
    @Test
    public void parseSourceDirectoryTest() throws IOException {
        File srcDirectory = new File("src/test/resources/javaSrcDirectory");
        Graph<String, DefaultEdge> classReferencesGraph =
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
    }
}

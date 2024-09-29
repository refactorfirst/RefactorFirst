package org.hjug.cycledetector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Map;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CircularReferenceCheckerTests {

    CircularReferenceChecker sutCircularReferenceChecker = new CircularReferenceChecker();

    @DisplayName("Detect 3 cycles from given graph.")
    @Test
    public void detectCyclesTest() {
        Graph<String, DefaultWeightedEdge> classReferencesGraph = new DefaultDirectedGraph<>(DefaultWeightedEdge.class);
        classReferencesGraph.addVertex("A");
        classReferencesGraph.addVertex("B");
        classReferencesGraph.addVertex("C");
        classReferencesGraph.addEdge("A", "B");
        classReferencesGraph.addEdge("B", "C");
        classReferencesGraph.addEdge("C", "A");
        Map<String, AsSubgraph<String, DefaultWeightedEdge>> cyclesForEveryVertexMap =
                sutCircularReferenceChecker.detectCycles(classReferencesGraph);
        assertEquals(3, cyclesForEveryVertexMap.size());
    }

    @DisplayName("Create graph image in given outputDirectory")
    @Test
    public void createImageTest() {
        Graph<String, DefaultWeightedEdge> classReferencesGraph = new DefaultDirectedGraph<>(DefaultWeightedEdge.class);
        classReferencesGraph.addVertex("A");
        classReferencesGraph.addVertex("B");
        classReferencesGraph.addVertex("C");
        classReferencesGraph.addEdge("A", "B");
        classReferencesGraph.addEdge("B", "C");
        classReferencesGraph.addEdge("C", "A");
        File newGraphImage = new File("src/test/resources/testOutputDirectory/graphtestGraph.png");
        assertTrue(newGraphImage.exists() && !newGraphImage.isDirectory());
    }
}

package com.ideacrest.cycledetector;


import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularReferenceCheckerTests {
	
	CircularReferenceChecker sutCircularReferenceChecker = new CircularReferenceChecker();
	
	@DisplayName("Detect 3 cycles from given graph.")
	@Test
	public void detectCyclesTest() {
		Graph<String, DefaultEdge> classReferencesGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
		classReferencesGraph.addVertex("A");
		classReferencesGraph.addVertex("B");
		classReferencesGraph.addVertex("C");
		classReferencesGraph.addEdge("A", "B");
		classReferencesGraph.addEdge("B", "C");
		classReferencesGraph.addEdge("C", "A");
		Map<String, AsSubgraph<String, DefaultEdge>> cyclesForEveryVertexMap = sutCircularReferenceChecker.detectCyles(classReferencesGraph);
		assertEquals(3, cyclesForEveryVertexMap.size());
	}
	
	@DisplayName("Create graph image in given outputDirectory")
	@Test
	public void createImageTest() throws IOException {
		Graph<String, DefaultEdge> classReferencesGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
		classReferencesGraph.addVertex("A");
		classReferencesGraph.addVertex("B");
		classReferencesGraph.addVertex("C");
		classReferencesGraph.addEdge("A", "B");
		classReferencesGraph.addEdge("B", "C");
		classReferencesGraph.addEdge("C", "A");
		sutCircularReferenceChecker.createImage("src/test/resources/testOutputDirectory",classReferencesGraph,"testGraph");
		File newGraphImage = new File("src/test/resources/testOutputDirectory/graphtestGraph.png");
		assertTrue(newGraphImage.exists() && !newGraphImage.isDirectory());
	}
}

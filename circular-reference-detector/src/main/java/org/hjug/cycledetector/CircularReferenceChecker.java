package org.hjug.cycledetector;

import java.util.HashMap;
import java.util.Map;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultWeightedEdge;

public class CircularReferenceChecker {

    /**
     * Detects cycles in the classReferencesGraph parameter
     * and stores the cycles of a class as a subgraph in a Map
     *
     * @param classReferencesGraph
     * @return a Map of Class and its Cycle Graph
     */
    public Map<String, AsSubgraph<String, DefaultWeightedEdge>> detectCycles(
            Graph<String, DefaultWeightedEdge> classReferencesGraph) {
        Map<String, AsSubgraph<String, DefaultWeightedEdge>> cyclesForEveryVertexMap = new HashMap<>();
        CycleDetector<String, DefaultWeightedEdge> cycleDetector = new CycleDetector<>(classReferencesGraph);
        cycleDetector.findCycles().forEach(v -> {
            AsSubgraph<String, DefaultWeightedEdge> subGraph =
                    new AsSubgraph<>(classReferencesGraph, cycleDetector.findCyclesContainingVertex(v));
            cyclesForEveryVertexMap.put(v, subGraph);
        });
        return cyclesForEveryVertexMap;
    }
}

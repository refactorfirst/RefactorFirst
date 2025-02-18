package org.hjug.cycledetector;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultWeightedEdge;

@Slf4j
public class CircularReferenceChecker {

    private final Map<String, AsSubgraph<String, DefaultWeightedEdge>> uniqueSubGraphs = new HashMap<>();

    public Map<String, AsSubgraph<String, DefaultWeightedEdge>> getCycles(Graph<String, DefaultWeightedEdge> graph) {

        Map<String, AsSubgraph<String, DefaultWeightedEdge>> cycles = detectCycles(graph);

        cycles.forEach((vertex, subGraph) -> {
            int vertexCount = subGraph.vertexSet().size();
            int edgeCount = subGraph.edgeSet().size();

            if (vertexCount > 1 && edgeCount > 1 && !isDuplicateSubGraph(subGraph, vertex)) {
                uniqueSubGraphs.put(vertex, subGraph);
                log.info("Vertex: " + vertex + " vertex count: " + vertexCount + " edge count: " + edgeCount);
            }
        });

        return uniqueSubGraphs;
    }

    private boolean isDuplicateSubGraph(AsSubgraph<String, DefaultWeightedEdge> subGraph, String vertex) {
        if (!uniqueSubGraphs.isEmpty()) {
            for (AsSubgraph<String, DefaultWeightedEdge> renderedSubGraph : uniqueSubGraphs.values()) {
                if (renderedSubGraph.vertexSet().size() == subGraph.vertexSet().size()
                        && renderedSubGraph.edgeSet().size()
                                == subGraph.edgeSet().size()
                        && renderedSubGraph.vertexSet().contains(vertex)) {
                    return true;
                }
            }
        }

        return false;
    }

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

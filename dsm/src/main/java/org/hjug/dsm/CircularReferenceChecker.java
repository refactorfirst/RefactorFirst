package org.hjug.dsm;

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

    /**
     * Detects cycles in the graph that is passed in
     * and returns the unique cycles in the graph as a map of subgraphs
     *
     * @param graph
     * @return a Map of unique cycles in the graph
     */

    public Map<String, AsSubgraph<String, DefaultWeightedEdge>> getCycles(Graph<String, DefaultWeightedEdge> graph) {

        // use CycleDetector.findCycles()?
        Map<String, AsSubgraph<String, DefaultWeightedEdge>> cycles = detectCycles(graph);

        cycles.forEach((vertex, subGraph) -> {
            int vertexCount = subGraph.vertexSet().size();
            int edgeCount = subGraph.edgeSet().size();

            if (vertexCount > 1 && edgeCount > 1 && !isDuplicateSubGraph(subGraph, vertex)) {
                uniqueSubGraphs.put(vertex, subGraph);
                log.debug("Vertex: {} vertex count: {} edge count: {}", vertex, vertexCount, edgeCount);
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

    private Map<String, AsSubgraph<String, DefaultWeightedEdge>> detectCycles(
            Graph<String, DefaultWeightedEdge> graph) {
        Map<String, AsSubgraph<String, DefaultWeightedEdge>> cyclesForEveryVertexMap = new HashMap<>();
        CycleDetector<String, DefaultWeightedEdge> cycleDetector = new CycleDetector<>(graph);
        cycleDetector.findCycles().forEach(v -> {
            AsSubgraph<String, DefaultWeightedEdge> subGraph =
                    new AsSubgraph<>(graph, cycleDetector.findCyclesContainingVertex(v));
            cyclesForEveryVertexMap.put(v, subGraph);
        });
        return cyclesForEveryVertexMap;
    }
}

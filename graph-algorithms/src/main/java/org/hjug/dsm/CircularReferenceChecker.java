package org.hjug.dsm;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.AsSubgraph;

@Slf4j
public class CircularReferenceChecker<V, E> {

    private final Map<V, AsSubgraph<V, E>> uniqueSubGraphs = new HashMap<>();

    /**
     * Detects cycles in the graph that is passed in
     * and returns the unique cycles in the graph as a map of subgraphs
     *
     * @param graph
     * @return a Map of unique cycles in the graph
     */
    public Map<V, AsSubgraph<V, E>> getCycles(Graph<V, E> graph) {

        if (!uniqueSubGraphs.isEmpty()) {
            return uniqueSubGraphs;
        }

        // use CycleDetector.findCycles()?
        Map<V, AsSubgraph<V, E>> cycles = detectCycles(graph);

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

    private boolean isDuplicateSubGraph(AsSubgraph<V, E> subGraph, V vertex) {
        if (!uniqueSubGraphs.isEmpty()) {
            for (AsSubgraph<V, E> renderedSubGraph : uniqueSubGraphs.values()) {
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

    private Map<V, AsSubgraph<V, E>> detectCycles(Graph<V, E> graph) {
        Map<V, AsSubgraph<V, E>> cyclesForEveryVertexMap = new HashMap<>();
        CycleDetector<V, E> cycleDetector = new CycleDetector<>(graph);
        cycleDetector.findCycles().forEach(v -> {
            AsSubgraph<V, E> subGraph = new AsSubgraph<>(graph, cycleDetector.findCyclesContainingVertex(v));
            cyclesForEveryVertexMap.put(v, subGraph);
        });
        return cyclesForEveryVertexMap;
    }
}

package org.hjug.dsm;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.opt.graph.sparse.SparseIntDirectedWeightedGraph;

@Slf4j
public class SparseGraphCircularReferenceChecker {

    private final Map<Integer, AsSubgraph<Integer, Integer>> uniqueSubGraphs = new HashMap<>();

    /**
     * Detects cycles in the graph that is passed in
     * and returns the unique cycles in the graph as a map of subgraphs
     *
     * @param graph
     * @return a Map of unique cycles in the graph
     */
    public Map<Integer, AsSubgraph<Integer, Integer>> getCycles(SparseIntDirectedWeightedGraph graph) {

        if (!uniqueSubGraphs.isEmpty()) {
            return uniqueSubGraphs;
        }

        // use CycleDetector.findCycles()?
        Map<Integer, AsSubgraph<Integer, Integer>> cycles = detectCycles(graph);

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

    private boolean isDuplicateSubGraph(AsSubgraph<Integer, Integer> subGraph, Integer vertex) {
        if (!uniqueSubGraphs.isEmpty()) {
            for (AsSubgraph<Integer, Integer> renderedSubGraph : uniqueSubGraphs.values()) {
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

    private Map<Integer, AsSubgraph<Integer, Integer>> detectCycles(SparseIntDirectedWeightedGraph graph) {
        Map<Integer, AsSubgraph<Integer, Integer>> cyclesForEveryVertexMap = new HashMap<>();
        CycleDetector<Integer, Integer> cycleDetector = new CycleDetector<>(graph);
        cycleDetector.findCycles().forEach(v -> {
            AsSubgraph<Integer, Integer> subGraph =
                    new AsSubgraph<>(graph, cycleDetector.findCyclesContainingVertex(v));
            cyclesForEveryVertexMap.put(v, subGraph);
        });
        return cyclesForEveryVertexMap;
    }
}

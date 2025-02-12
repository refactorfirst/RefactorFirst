package org.hjug.cycledetector;

import java.util.*;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.TarjanSimpleCycles;
import org.jgrapht.graph.DefaultWeightedEdge;

/*
Prompt:
provide a java implementation of an algorithm that will identify all backward edges in a cycle
identified by JGraphT that is part of a graph with weighted edges that have integer weights.
If there are multiple backward edges, order the backward edges in the order they should be removed.

Modified slightly to track the number of cycles a backward edge participates in
 */
public class BackwardEdgesFinder<V> {

    private final Graph<V, DefaultWeightedEdge> graph;

    public BackwardEdgesFinder(Graph<V, DefaultWeightedEdge> graph) {
        this.graph = graph;
    }

    public Map<DefaultWeightedEdge, Integer> findBackwardEdges() {
        TarjanSimpleCycles<V, DefaultWeightedEdge> tarjan = new TarjanSimpleCycles<>(graph);
        List<List<V>> cycles = tarjan.findSimpleCycles();
        Map<DefaultWeightedEdge, Integer> backwardEdgesWithCounts = new TreeMap<>(((Comparator<DefaultWeightedEdge>)
                        // compare based on weight
                        (e1, e2) -> Integer.compare((int) graph.getEdgeWeight(e1), (int) graph.getEdgeWeight(e2)))
                // then compare based on name (this will result in edges being in alpha order - not exactly ideal)
                .thenComparing(DefaultWeightedEdge::toString));

        for (List<V> cycle : cycles) {
            Set<DefaultWeightedEdge> cycleEdges = new HashSet<>();
            for (int i = 0; i < cycle.size(); i++) {
                V source = cycle.get(i);
                // getting (i + 1) % cycle size produces the next node added to the cycle
                V target = cycle.get((i + 1) % cycle.size());
                DefaultWeightedEdge edge = graph.getEdge(source, target);
                // if there is an edge between the nodes, add it to cycleEdges
                if (edge != null) {
                    cycleEdges.add(edge);
                }
            }

            for (DefaultWeightedEdge edge : cycleEdges) {
                if (isBackwardEdge(edge, cycle)) {
                    backwardEdgesWithCounts.put(edge, backwardEdgesWithCounts.getOrDefault(edge, 0) + 1);
                }
            }
        }

        // The ordering is naive, but it's a start
        return backwardEdgesWithCounts;
    }

    private boolean isBackwardEdge(DefaultWeightedEdge edge, List<V> cycle) {
        V source = graph.getEdgeSource(edge);
        V target = graph.getEdgeTarget(edge);
        int sourceIndex = cycle.indexOf(source);
        int targetIndex = cycle.indexOf(target);
        return sourceIndex > targetIndex;
    }
}

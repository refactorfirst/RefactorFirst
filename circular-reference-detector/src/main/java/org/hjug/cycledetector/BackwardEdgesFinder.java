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
 */
public class BackwardEdgesFinder<V> {

    private final Graph<V, DefaultWeightedEdge> graph;

    public BackwardEdgesFinder(Graph<V, DefaultWeightedEdge> graph) {
        this.graph = graph;
    }

    public List<DefaultWeightedEdge> findBackwardEdges() {
        TarjanSimpleCycles<V, DefaultWeightedEdge> tarjan = new TarjanSimpleCycles<>(graph);
        List<List<V>> cycles = tarjan.findSimpleCycles();
        List<DefaultWeightedEdge> backwardEdges = new ArrayList<>();

        for (List<V> cycle : cycles) {
            Set<DefaultWeightedEdge> cycleEdges = new HashSet<>();
            for (int i = 0; i < cycle.size(); i++) {
                V source = cycle.get(i);
                V target = cycle.get((i + 1) % cycle.size());
                DefaultWeightedEdge edge = graph.getEdge(source, target);
                if (edge != null) {
                    cycleEdges.add(edge);
                }
            }

            for (DefaultWeightedEdge edge : cycleEdges) {
                if (isBackwardEdge(edge, cycle)) {
                    backwardEdges.add(edge);
                }
            }
        }

        backwardEdges.sort(Comparator.comparingDouble(graph::getEdgeWeight));
        return backwardEdges;
    }

    private boolean isBackwardEdge(DefaultWeightedEdge edge, List<V> cycle) {
        V source = graph.getEdgeSource(edge);
        V target = graph.getEdgeTarget(edge);
        int sourceIndex = cycle.indexOf(source);
        int targetIndex = cycle.indexOf(target);
        return sourceIndex > targetIndex;
    }
}

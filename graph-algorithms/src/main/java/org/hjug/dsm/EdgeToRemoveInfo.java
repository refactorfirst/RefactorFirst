package org.hjug.dsm;

import lombok.Data;
import org.jgrapht.graph.DefaultWeightedEdge;

@Data
public class EdgeToRemoveInfo {
    private final DefaultWeightedEdge edge;
    private final int removedEdgeWeight;
    private final int newCycleCount;
    private final double payoff; // impact / effort
}

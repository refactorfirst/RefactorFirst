package org.hjug.dsm;

import lombok.Data;
import org.jgrapht.graph.DefaultWeightedEdge;

@Data
public class EdgeToRemoveInfo {
    private final DefaultWeightedEdge edge;
    private final double edgeWeight;
    private final int edgeInCycleCount;
    private final int newCycleCount;
    private final double averageCycleNodeCount;
    private final double payoff; // impact / effort
}

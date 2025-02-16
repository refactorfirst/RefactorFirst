package org.hjug.dsm;

import lombok.Data;
import org.jgrapht.graph.DefaultWeightedEdge;

@Data
public class EdgeToRemoveInfo {
    private final DefaultWeightedEdge edge;
    private final double edgeWeight;
    private final int newEdgeCount;
    private final int newStronglyConnectedComponentCount;
    private final double newAverageStronglyConnectedComponentSize;
}
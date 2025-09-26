package org.hjug.feedback.arc;

import lombok.Data;
import org.jgrapht.graph.DefaultWeightedEdge;

@Data
public class EdgeInfo {

    private final DefaultWeightedEdge edge;
    private final int presentInCycleCount;
    private final boolean removeSource;
    private final boolean removeTarget;
    private final int weight;
}

package org.hjug.feedback;

import java.util.Map;
import java.util.Set;
import lombok.Data;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultWeightedEdge;

@Data
public class CycleRemovalResult<V> {
    private final Map<V, AsSubgraph<V, DefaultWeightedEdge>> cycles;
    private final Set<DefaultWeightedEdge> edgesToRemove;
    private final Set<V> vertexesToRemove;
    private final Map<DefaultWeightedEdge, Integer> edgeCycleCounts;
}

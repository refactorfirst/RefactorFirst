package org.hjug.cbc;

import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultWeightedEdge;

@Data
@Slf4j
public class RankedCycle {

    private final String cycleName;

    private final Set<String> vertexSet;
    private final Set<DefaultWeightedEdge> edgeSet;
    private final List<CycleNode> cycleNodes;
    private float rawPriority;
    private Integer priority = 0;

    public RankedCycle(
            String cycleName, Set<String> vertexSet, Set<DefaultWeightedEdge> edgeSet, List<CycleNode> cycleNodes) {
        this.cycleNodes = cycleNodes;
        this.cycleName = cycleName;
        this.vertexSet = vertexSet;
        this.edgeSet = edgeSet;
        this.rawPriority = (float) (vertexSet.size()); // go away?
    }
}

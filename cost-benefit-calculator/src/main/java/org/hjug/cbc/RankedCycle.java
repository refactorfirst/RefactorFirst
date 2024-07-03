package org.hjug.cbc;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultEdge;

@Data
@Slf4j
public class RankedCycle {

    private final String cycleName;
    private final Integer changePronenessRankSum;

    private final Set<String> vertexSet;
    private final Set<DefaultEdge> edgeSet;
    private final double minCutCount;
    private final Set<DefaultEdge> minCutEdges;
    private final List<CycleNode> cycleNodes;

    private float rawPriority;
    private Integer priority = 0;
    private float averageChangeProneness;
    private Integer changePronenessRank;
    private float impact;

    public RankedCycle(
            String cycleName,
            Integer changePronenessRankSum,
            Set<String> vertexSet,
            Set<DefaultEdge> edgeSet,
            double minCutCount,
            Set<DefaultEdge> minCutEdges,
            List<CycleNode> cycleNodes) {
        this.cycleNodes = cycleNodes;
        this.cycleName = cycleName;
        this.changePronenessRankSum = changePronenessRankSum;
        this.vertexSet = vertexSet;
        this.edgeSet = edgeSet;
        this.minCutCount = minCutCount;

        if (null == minCutEdges) {
            this.minCutEdges = new HashSet<>();
        } else {
            this.minCutEdges = minCutEdges;
        }

        if (minCutCount == 0.0) {
            this.impact = (float) (vertexSet.size());
        } else {
            this.impact = (float) (vertexSet.size() / minCutCount);
        }

        this.averageChangeProneness = (float) changePronenessRankSum / vertexSet.size();
        this.rawPriority = this.impact + averageChangeProneness;
    }
}

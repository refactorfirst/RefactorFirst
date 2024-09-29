package org.hjug.cbc;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultWeightedEdge;

@Data
@Slf4j
public class RankedCycle {

    private final String cycleName;
    private Integer changePronenessRankSum = 0;

    private final Set<String> vertexSet;
    private final Set<DefaultWeightedEdge> edgeSet;
    private final double minCutCount;
    private final Set<DefaultWeightedEdge> minCutEdges;
    private final List<CycleNode> cycleNodes;

    private float rawPriority;
    private Integer priority = 0;
    private float averageChangeProneness;
    private Integer changePronenessRank = 0;
    private float impact;

    public RankedCycle(
            String cycleName,
            Set<String> vertexSet,
            Set<DefaultWeightedEdge> edgeSet,
            double minCutCount,
            Set<DefaultWeightedEdge> minCutEdges,
            List<CycleNode> cycleNodes) {
        this.cycleNodes = cycleNodes;
        this.cycleName = cycleName;
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

        this.rawPriority = this.impact;
    }

    public RankedCycle(
            String cycleName,
            Integer changePronenessRankSum,
            Set<String> vertexSet,
            Set<DefaultWeightedEdge> edgeSet,
            double minCutCount,
            Set<DefaultWeightedEdge> minCutEdges,
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

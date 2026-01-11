package org.hjug.feedback.arc;

import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultWeightedEdge;

@RequiredArgsConstructor
public class EdgeInfoCalculator {

    private final Graph<String, DefaultWeightedEdge> graph;
    private final Collection<DefaultWeightedEdge> edgesToRemove;
    private final Set<String> vertexesToRemove;
    private final Map<String, AsSubgraph<String, DefaultWeightedEdge>> cycles;

    public Collection<EdgeInfo> calculateEdgeInformation() {
        List<EdgeInfo> edgeInfos = new ArrayList<>();

        for (DefaultWeightedEdge edge : edgesToRemove) {
            int presentInCycleCount = (int) cycles.values().stream()
                    .filter(cycle -> cycle.containsEdge(edge))
                    .count();

            EdgeInfo edgeInfo = new EdgeInfo(
                    edge,
                    presentInCycleCount,
                    vertexesToRemove.contains(graph.getEdgeSource(edge)),
                    vertexesToRemove.contains(graph.getEdgeTarget(edge)),
                    (int) graph.getEdgeWeight(edge));
            edgeInfos.add(edgeInfo);
        }

        return edgeInfos.stream()
                .sorted(Comparator.comparing(EdgeInfo::getPresentInCycleCount)
                        .reversed()
                        .thenComparing(edgeInfo -> edgeInfo.isRemoveSource() ? 0 : 1)
                        .thenComparing(edgeInfo -> edgeInfo.isRemoveTarget() ? 0 : 1)
                        .thenComparing(EdgeInfo::getWeight))
                .collect(Collectors.toList());
    }
}

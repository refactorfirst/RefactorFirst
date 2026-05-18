package org.hjug.cbc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hjug.dsm.CircularReferenceChecker;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.JavaGraphBuilder;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultWeightedEdge;

@RequiredArgsConstructor
@Slf4j
public class CycleRanker {

    private final String repositoryPath;

    @Getter
    private Graph<String, DefaultWeightedEdge> classReferencesGraph;

    @Getter
    private CodebaseGraphDTO codebaseGraphDTO;

    public void generateClassReferencesGraph(boolean excludeTests, String testSourceDirectory) {
        try {
            JavaGraphBuilder javaGraphBuilder = new JavaGraphBuilder();

            codebaseGraphDTO = javaGraphBuilder.getCodebaseGraphDTO(repositoryPath, excludeTests, testSourceDirectory);

            classReferencesGraph = codebaseGraphDTO.getClassReferencesGraph();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<RankedCycle> performCycleAnalysis(boolean excludeTests, String testSourceDirectory) {
        List<RankedCycle> rankedCycles = new ArrayList<>();
        try {
            boolean calculateCycleChurn = false;
            generateClassReferencesGraph(excludeTests, testSourceDirectory);
            identifyRankedCycles(rankedCycles);
            sortRankedCycles(rankedCycles, calculateCycleChurn);
            setPriorities(rankedCycles);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return rankedCycles;
    }

    private void identifyRankedCycles(List<RankedCycle> rankedCycles) throws IOException {
        CircularReferenceChecker circularReferenceChecker = new CircularReferenceChecker();
        Map<String, AsSubgraph<String, DefaultWeightedEdge>> cycles =
                circularReferenceChecker.getCycles(classReferencesGraph);
        cycles.forEach((vertex, subGraph) -> {
            List<CycleNode> cycleNodes = subGraph.vertexSet().stream()
                    .map(classInCycle -> new CycleNode(
                            classInCycle,
                            codebaseGraphDTO.getClassToSourceFilePathMapping().get(classInCycle)))
                    //                        .peek(cycleNode -> log.info(cycleNode.toString()))
                    .collect(Collectors.toList());

            rankedCycles.add(createRankedCycle(vertex, subGraph, cycleNodes, 0.0, new HashSet<>()));
        });
    }

    public CycleNode classToCycleNode(String fqnClass) {
        return new CycleNode(
                fqnClass, codebaseGraphDTO.getClassToSourceFilePathMapping().get(fqnClass));
    }

    private RankedCycle createRankedCycle(
            String vertex,
            AsSubgraph<String, DefaultWeightedEdge> subGraph,
            List<CycleNode> cycleNodes,
            double minCut,
            Set<DefaultWeightedEdge> minCutEdges) {

        return new RankedCycle(vertex, subGraph.vertexSet(), subGraph.edgeSet(), minCut, minCutEdges, cycleNodes);
    }

    private static void sortRankedCycles(List<RankedCycle> rankedCycles, boolean calculateChurnForCycles) {
        if (calculateChurnForCycles) {
            rankedCycles.sort(Comparator.comparing(RankedCycle::getAverageChangeProneness));

            int cpr = 1;
            for (RankedCycle rankedCycle : rankedCycles) {
                rankedCycle.setChangePronenessRank(cpr++);
            }
        } else {
            rankedCycles.sort(Comparator.comparing(RankedCycle::getRawPriority).reversed());
        }
    }

    private static void setPriorities(List<RankedCycle> rankedCycles) {
        int priority = 1;
        for (RankedCycle rankedCycle : rankedCycles) {
            rankedCycle.setPriority(priority++);
        }
    }
}

package org.hjug.cbc;

import java.io.IOException;
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
    private CodebaseGraphDTO codebaseGraphDTO;

    public CodebaseGraphDTO generateClassReferencesGraph(boolean excludeTests, String testSourceDirectory) {
        try {
            JavaGraphBuilder javaGraphBuilder = new JavaGraphBuilder();
            codebaseGraphDTO = javaGraphBuilder.getCodebaseGraphDTO(repositoryPath, excludeTests, testSourceDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return codebaseGraphDTO;
    }

    public List<RankedCycle> performCycleAnalysis(Graph<String, DefaultWeightedEdge> graph) {
        List<RankedCycle> rankedCycles;
        try {
            boolean calculateCycleChurn = false;
            rankedCycles = new ArrayList<>(identifyRankedCycles(graph));
            sortRankedCycles(rankedCycles, calculateCycleChurn);
            setPriorities(rankedCycles);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return rankedCycles;
    }

    private List<RankedCycle> identifyRankedCycles(Graph<String, DefaultWeightedEdge> classReferencesGraph)
            throws IOException {
        List<RankedCycle> rankedCycles = new ArrayList<>();
        CircularReferenceChecker<String, DefaultWeightedEdge> circularReferenceChecker =
                new CircularReferenceChecker<>();
        Map<String, AsSubgraph<String, DefaultWeightedEdge>> cycles =
                circularReferenceChecker.getCycles(classReferencesGraph);
        cycles.forEach((vertex, subGraph) -> {
            List<CycleNode> cycleNodes = subGraph.vertexSet().stream()
                    .map(classInCycle -> new CycleNode(classInCycle, getClassRepoPath(classInCycle)))
                    //                        .peek(cycleNode -> log.info(cycleNode.toString()))
                    .collect(Collectors.toList());

            rankedCycles.add(createRankedCycle(vertex, subGraph, cycleNodes, 0.0, new HashSet<>()));
        });

        return rankedCycles;
    }

    public CycleNode classToCycleNode(String fqnClass) {
        return new CycleNode(fqnClass, getClassRepoPath(fqnClass));
    }

    private String getClassRepoPath(String classInCycle) {
        String fileRepoPath;
        Map<String, String> classToSourceFilePathMapping = codebaseGraphDTO.getClassToSourceFilePathMapping();
        if (classInCycle.contains("$") && !classToSourceFilePathMapping.containsKey(classInCycle)) {
            fileRepoPath = classToSourceFilePathMapping.get(classInCycle.substring(0, classInCycle.indexOf("$")));
        } else {
            fileRepoPath = classToSourceFilePathMapping.get(classInCycle);
        }
        return fileRepoPath;
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

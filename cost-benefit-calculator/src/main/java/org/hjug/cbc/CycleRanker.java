package org.hjug.cbc;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hjug.dsm.CircularReferenceChecker;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.CompositeGraphBuilder;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultWeightedEdge;

@RequiredArgsConstructor
@Slf4j
public class CycleRanker {

    private final String repositoryPath;
    private final String repositoryRoot;

    @Getter
    private CodebaseGraphDTO codebaseGraphDTO;

    // TODO: should this method belong in this class?
    public CodebaseGraphDTO generateClassReferencesGraph(boolean excludeTests, String testSourceDirectory) {
        try {
            // Route through CompositeGraphBuilder so Kotlin source files are
            // also walked and contribute edges/vertices. Kotlin analysis runs
            // unconditionally; a Kotlin parse/build failure falls back to the
            // Java-only DTO.
            CompositeGraphBuilder compositeGraphBuilder = new CompositeGraphBuilder();
            codebaseGraphDTO = compositeGraphBuilder.getCodebaseGraphDTO(
                    repositoryPath, repositoryRoot, excludeTests, testSourceDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return codebaseGraphDTO;
    }

    /**
     * Build a unified {@link CodebaseGraphDTO} from a directory that may contain
     * both Java and Kotlin source files.
     *
     * @param repositoryPath     path to the source directory
     * @param repositoryRoot     path to the Git repository root for URL canonicalization;
     *                           may be empty or equal to repositoryPath for single-module projects
     * @param excludeTests      whether to exclude test files
     * @param testSourceDirectory test source directory pattern
     * @return a merged CodebaseGraphDTO
     * @throws IOException if parsing fails
     */
    public CodebaseGraphDTO getCodebaseGraphDTO(
            String repositoryPath, String repositoryRoot, boolean excludeTests, String testSourceDirectory)
            throws IOException {
        if (repositoryPath == null || repositoryPath.isEmpty()) {
            throw new IllegalArgumentException("Source directory cannot be null or empty");
        }

        CompositeGraphBuilder compositeGraphBuilder = new CompositeGraphBuilder();
        return compositeGraphBuilder.getCodebaseGraphDTO(
                repositoryPath, repositoryRoot, excludeTests, testSourceDirectory);
    }

    public List<RankedCycle> rankCycles(Graph<String, DefaultWeightedEdge> graph) {
        List<RankedCycle> rankedCycles;
        try {
            rankedCycles = new ArrayList<>(identifyRankedCycles(graph));
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

            rankedCycles.add(new RankedCycle(vertex, subGraph.vertexSet(), subGraph.edgeSet(), cycleNodes));
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

    private static void setPriorities(List<RankedCycle> rankedCycles) {
        rankedCycles.sort(Comparator.comparing(RankedCycle::getRawPriority).reversed());
        int priority = 1;
        for (RankedCycle rankedCycle : rankedCycles) {
            rankedCycle.setPriority(priority++);
        }
    }
}

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
import org.hjug.graphbuilder.GraphBuilderConfig;
import org.hjug.graphbuilder.VertexInfo;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultWeightedEdge;

@RequiredArgsConstructor
@Slf4j
public class CycleRanker {

    // path to the source directory
    private final String repositoryPath;

    // path to the Git repository root for URL canonicalization;
    // may be empty or equal to repositoryPath for single-module projects
    private final String repositoryRoot;

    @Getter
    private CodebaseGraphDTO codebaseGraphDTO;

    public CycleRanker(String repositoryPath) {
        this(repositoryPath, repositoryPath);
    }

    /**
     * Build a unified {@link CodebaseGraphDTO} from a directory that may contain
     * both Java and Kotlin source files.
     *
     * @param excludeTests      whether to exclude test files
     * @param testSourceDirectory test source directory pattern
     * @return a merged CodebaseGraphDTO
     */
    // TODO: should this method belong in this class?
    public CodebaseGraphDTO generateClassReferencesGraph(boolean excludeTests, String testSourceDirectory) {
        if (repositoryPath == null || repositoryPath.isEmpty()) {
            throw new IllegalArgumentException("Source directory cannot be null or empty");
        }
        try {
            // Route through CompositeGraphBuilder so Kotlin source files are
            // also walked and contribute edges/vertices. Kotlin analysis runs
            // unconditionally; a Kotlin parse/build failure falls back to the
            // Java-only DTO.
            CompositeGraphBuilder compositeGraphBuilder = new CompositeGraphBuilder();
            GraphBuilderConfig config = GraphBuilderConfig.builder()
                    .excludeTests(excludeTests)
                    .testSourceDirectory(testSourceDirectory)
                    .build();
            codebaseGraphDTO = compositeGraphBuilder.getCodebaseGraphDTO(repositoryPath, repositoryRoot, config);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return codebaseGraphDTO;
    }

    /**
     * Finds cyclic subgraphs and assigns priorities in descending order of vertex count.
     *
     * @param graph the dependency graph to analyze
     * @return the detected cycles with priorities assigned
     */
    public List<RankedCycle> rankCycles(Graph<VertexInfo, DefaultWeightedEdge> graph) {
        List<RankedCycle> rankedCycles;
        try {
            rankedCycles = new ArrayList<>(identifyRankedCycles(graph));
            setPriorities(rankedCycles);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return rankedCycles;
    }

    /** Builds cycle records from cyclic subgraphs, retaining vertex names and repository source paths. */
    private List<RankedCycle> identifyRankedCycles(Graph<VertexInfo, DefaultWeightedEdge> classReferencesGraph)
            throws IOException {
        List<RankedCycle> rankedCycles = new ArrayList<>();
        CircularReferenceChecker<VertexInfo, DefaultWeightedEdge> circularReferenceChecker =
                new CircularReferenceChecker<>();
        Map<VertexInfo, AsSubgraph<VertexInfo, DefaultWeightedEdge>> cycles =
                circularReferenceChecker.getCycles(classReferencesGraph);
        cycles.forEach((vertex, subGraph) -> {
            List<CycleNode> cycleNodes = subGraph.vertexSet().stream()
                    .map(classInCycle ->
                            new CycleNode(classInCycle.getName(), getClassRepoPath(classInCycle.getName())))
                    //                        .peek(cycleNode -> log.info(cycleNode.toString()))
                    .collect(Collectors.toList());

            Set<String> vertexNames =
                    subGraph.vertexSet().stream().map(VertexInfo::getName).collect(Collectors.toSet());

            rankedCycles.add(new RankedCycle(vertex.getName(), vertexNames, subGraph.edgeSet(), cycleNodes));
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

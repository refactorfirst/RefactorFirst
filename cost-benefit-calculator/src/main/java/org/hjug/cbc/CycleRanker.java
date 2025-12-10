package org.hjug.cbc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private final JavaGraphBuilder javaGraphBuilder = new JavaGraphBuilder();

    @Getter
    private Graph<String, DefaultWeightedEdge> classReferencesGraph;

    @Getter
    private CodebaseGraphDTO codebaseGraphDTO;

    @Getter
    private Map<String, String> classNamesAndPaths = new HashMap<>();

    @Getter
    private Map<String, String> fqnsAndPaths = new HashMap<>();

    public void generateClassReferencesGraph(boolean excludeTests, String testSourceDirectory) {
        try {
            codebaseGraphDTO = javaGraphBuilder.getCodebaseGraphDTO(repositoryPath, excludeTests, testSourceDirectory);

            classReferencesGraph = codebaseGraphDTO.getClassReferencesGraph();

            loadClassNamesAndPaths();

            /*for (Map.Entry<String, String> stringStringEntry : fqnsAndPaths.entrySet()) {
                log.info(stringStringEntry.getKey() + " : " + stringStringEntry.getValue());
            }*/

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
            // TODO: Calculate min cuts for smaller graphs - has a runtime of O(V^4) for a graph
            /*Set<DefaultWeightedEdge> minCutEdges;
            GusfieldGomoryHuCutTree<String, DefaultWeightedEdge> gusfieldGomoryHuCutTree =
                    new GusfieldGomoryHuCutTree<>(new AsUndirectedGraph<>(subGraph));
            double minCut = gusfieldGomoryHuCutTree.calculateMinCut();
            minCutEdges = gusfieldGomoryHuCutTree.getCutEdges();*/

            List<CycleNode> cycleNodes = subGraph.vertexSet().stream()
                    .map(classInCycle -> new CycleNode(classInCycle, classNamesAndPaths.get(classInCycle)))
                    //                        .peek(cycleNode -> log.info(cycleNode.toString()))
                    .collect(Collectors.toList());

            rankedCycles.add(createRankedCycle(vertex, subGraph, cycleNodes, 0.0, new HashSet<>()));
        });
    }

    public CycleNode classToCycleNode(String fqnClass) {
        return new CycleNode(fqnClass, fqnsAndPaths.get(fqnClass));
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

    void loadClassNamesAndPaths() throws IOException {
        try (Stream<Path> walk = Files.walk(Paths.get(repositoryPath))) {
            walk.forEach(path -> {
                String filename = path.getFileName().toString();
                if (filename.endsWith(".java")) {
                    // extract package and class name
                    String packageName = getPackageName(path);
                    String uriString = path.toUri().toString();
                    String className = getClassName(filename);
                    String canonicalUri = canonicaliseURIStringForRepoLookup(uriString);
                    fqnsAndPaths.put(packageName + "." + className, canonicalUri);
                    classNamesAndPaths.put(className, canonicalUri);
                }
            });
        }
    }

    private static String getPackageName(Path path) {
        try {
            return Files.readAllLines(path).stream()
                    .filter(line -> line.startsWith("package"))
                    .map(line -> line.replace("package", "").replace(";", "").trim())
                    .findFirst()
                    .orElse("");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String canonicaliseURIStringForRepoLookup(String uriString) {
        if (repositoryPath.startsWith("/") || repositoryPath.startsWith("\\")) {
            return uriString.replace("file://" + repositoryPath.replace("\\", "/") + "/", "");
        }
        return uriString.replace("file:///" + repositoryPath.replace("\\", "/") + "/", "");
    }

    /**
     * Extract class name from java file name
     * Example : MyJavaClass.java becomes MyJavaClass
     *
     * @param javaFileName
     * @return
     */
    private String getClassName(String javaFileName) {
        return javaFileName.substring(0, javaFileName.indexOf('.'));
    }
}

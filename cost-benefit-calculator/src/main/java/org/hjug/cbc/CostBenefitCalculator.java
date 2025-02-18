package org.hjug.cbc;

import static net.sourceforge.pmd.RuleViolation.CLASS_NAME;
import static net.sourceforge.pmd.RuleViolation.PACKAGE_NAME;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.pmd.*;
import net.sourceforge.pmd.lang.LanguageRegistry;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.hjug.cycledetector.CircularReferenceChecker;
import org.hjug.git.ChangePronenessRanker;
import org.hjug.git.GitLogReader;
import org.hjug.git.ScmLogInfo;
import org.hjug.graphbuilder.JavaGraphBuilder;
import org.hjug.metrics.*;
import org.hjug.metrics.rules.CBORule;
import org.jgrapht.Graph;
import org.jgrapht.alg.flow.GusfieldGomoryHuCutTree;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.AsUndirectedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

@Slf4j
public class CostBenefitCalculator implements AutoCloseable {

    private Report report;
    private String repositoryPath;
    private GitLogReader gitLogReader;

    private final ChangePronenessRanker changePronenessRanker;
    private final JavaGraphBuilder javaGraphBuilder = new JavaGraphBuilder();

    @Getter
    private Graph<String, DefaultWeightedEdge> classReferencesGraph;

    public CostBenefitCalculator(String repositoryPath) {
        this.repositoryPath = repositoryPath;

        log.info("Initiating Cost Benefit calculation");
        try {
            gitLogReader = new GitLogReader(new File(repositoryPath));
        } catch (IOException e) {
            log.error("Failure to access Git repository", e);
        }

        changePronenessRanker = new ChangePronenessRanker(gitLogReader);
    }

    @Override
    public void close() throws Exception {
        gitLogReader.close();
    }

    public List<RankedCycle> runCycleAnalysis() {
        List<RankedCycle> rankedCycles = new ArrayList<>();
        try {
            boolean calculateCycleChurn = false;
            identifyRankedCycles(rankedCycles);
            sortRankedCycles(rankedCycles, calculateCycleChurn);
            setPriorities(rankedCycles);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return rankedCycles;
    }

    public List<RankedCycle> runCycleAnalysisAndCalculateCycleChurn() {
        List<RankedCycle> rankedCycles = new ArrayList<>();
        try {
            boolean calculateCycleChurn = true;
            identifyRankedCycles(rankedCycles);
            sortRankedCycles(rankedCycles, calculateCycleChurn);
            setPriorities(rankedCycles);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return rankedCycles;
    }

    private void identifyRankedCycles(List<RankedCycle> rankedCycles) throws IOException {
        classReferencesGraph = javaGraphBuilder.getClassReferences(repositoryPath);
        CircularReferenceChecker circularReferenceChecker = new CircularReferenceChecker();
        Map<String, AsSubgraph<String, DefaultWeightedEdge>> cycles =
                circularReferenceChecker.getCycles(classReferencesGraph);
        Map<String, String> classNamesAndPaths = getClassNamesAndPaths();
        cycles.forEach((vertex, subGraph) -> {
            Set<DefaultWeightedEdge> minCutEdges;
            GusfieldGomoryHuCutTree<String, DefaultWeightedEdge> gusfieldGomoryHuCutTree =
                    new GusfieldGomoryHuCutTree<>(new AsUndirectedGraph<>(subGraph));
            double minCut = gusfieldGomoryHuCutTree.calculateMinCut();
            minCutEdges = gusfieldGomoryHuCutTree.getCutEdges();

            List<CycleNode> cycleNodes = subGraph.vertexSet().stream()
                    .map(classInCycle -> new CycleNode(classInCycle, classNamesAndPaths.get(classInCycle)))
                    //                        .peek(cycleNode -> log.info(cycleNode.toString()))
                    .collect(Collectors.toList());

            rankedCycles.add(createRankedCycle(vertex, subGraph, cycleNodes, minCut, minCutEdges));
        });
    }

    private static void setPriorities(List<RankedCycle> rankedCycles) {
        int priority = 1;
        for (RankedCycle rankedCycle : rankedCycles) {
            rankedCycle.setPriority(priority++);
        }
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

    private RankedCycle createRankedCycle(
            String vertex,
            AsSubgraph<String, DefaultWeightedEdge> subGraph,
            List<CycleNode> cycleNodes,
            double minCut,
            Set<DefaultWeightedEdge> minCutEdges) {

        return new RankedCycle(vertex, subGraph.vertexSet(), subGraph.edgeSet(), minCut, minCutEdges, cycleNodes);
    }

    private RankedCycle createRankedCycleWithChurn(
            boolean calculateChurnForCycles,
            String vertex,
            AsSubgraph<String, DefaultWeightedEdge> subGraph,
            List<CycleNode> cycleNodes,
            double minCut,
            Set<DefaultWeightedEdge> minCutEdges) {
        RankedCycle rankedCycle;
        if (calculateChurnForCycles) {
            List<ScmLogInfo> changeRanks = getRankedChangeProneness(cycleNodes);

            Map<String, CycleNode> cycleNodeMap = new HashMap<>();

            for (CycleNode cycleNode : cycleNodes) {
                cycleNodeMap.put(cycleNode.getFileName(), cycleNode);
            }

            for (ScmLogInfo changeRank : changeRanks) {
                CycleNode cycleNode = cycleNodeMap.get(changeRank.getPath());
                cycleNode.setScmLogInfo(changeRank);
            }

            // sum change proneness ranks
            int changePronenessRankSum = changeRanks.stream()
                    .mapToInt(ScmLogInfo::getChangePronenessRank)
                    .sum();
            rankedCycle = new RankedCycle(
                    vertex,
                    changePronenessRankSum,
                    subGraph.vertexSet(),
                    subGraph.edgeSet(),
                    minCut,
                    minCutEdges,
                    cycleNodes);
        } else {
            rankedCycle =
                    new RankedCycle(vertex, subGraph.vertexSet(), subGraph.edgeSet(), minCut, minCutEdges, cycleNodes);
        }
        return rankedCycle;
    }

    // copied from PMD's PmdTaskImpl.java and modified
    public void runPmdAnalysis() throws IOException {
        PMDConfiguration configuration = new PMDConfiguration();

        try (PmdAnalysis pmd = PmdAnalysis.create(configuration)) {
            RuleSetLoader rulesetLoader = pmd.newRuleSetLoader();
            pmd.addRuleSets(rulesetLoader.loadRuleSetsWithoutException(List.of("category/java/design.xml")));

            Rule cboClassRule = new CBORule();
            cboClassRule.setLanguage(LanguageRegistry.PMD.getLanguageByFullName("Java"));
            pmd.addRuleSet(RuleSet.forSingleRule(cboClassRule));

            log.info("files to be scanned: " + Paths.get(repositoryPath));

            try (Stream<Path> files = Files.walk(Paths.get(repositoryPath))) {
                files.filter(Files::isRegularFile).forEach(file -> pmd.files().addFile(file));
            }

            report = pmd.performAnalysisAndCollectReport();
        }
    }

    public List<RankedDisharmony> calculateGodClassCostBenefitValues() {
        List<GodClass> godClasses = getGodClasses();

        List<ScmLogInfo> scmLogInfos = getRankedChangeProneness(godClasses);

        Map<String, ScmLogInfo> rankedLogInfosByPath =
                scmLogInfos.stream().collect(Collectors.toMap(ScmLogInfo::getPath, logInfo -> logInfo, (a, b) -> b));

        List<RankedDisharmony> rankedDisharmonies = new ArrayList<>();
        for (GodClass godClass : godClasses) {
            if (rankedLogInfosByPath.containsKey(godClass.getFileName())) {
                rankedDisharmonies.add(
                        new RankedDisharmony(godClass, rankedLogInfosByPath.get(godClass.getFileName())));
            }
        }

        rankedDisharmonies.sort(
                Comparator.comparing(RankedDisharmony::getRawPriority).reversed());

        int godClassPriority = 1;
        for (RankedDisharmony rankedGodClassDisharmony : rankedDisharmonies) {
            rankedGodClassDisharmony.setPriority(godClassPriority++);
        }

        return rankedDisharmonies;
    }

    private List<GodClass> getGodClasses() {
        List<GodClass> godClasses = new ArrayList<>();
        for (RuleViolation violation : report.getViolations()) {
            if (violation.getRule().getName().contains("GodClass")) {
                GodClass godClass = new GodClass(
                        violation.getAdditionalInfo().get(CLASS_NAME),
                        getFileName(violation),
                        violation.getAdditionalInfo().get(PACKAGE_NAME),
                        violation.getDescription());
                log.info("God Class identified: {}", godClass.getFileName());
                godClasses.add(godClass);
            }
        }

        GodClassRanker godClassRanker = new GodClassRanker();
        godClassRanker.rankGodClasses(godClasses);

        return godClasses;
    }

    <T extends Disharmony> List<ScmLogInfo> getRankedChangeProneness(List<T> disharmonies) {
        log.info("Calculating Change Proneness");

        List<ScmLogInfo> scmLogInfos = disharmonies.parallelStream()
                .map(disharmony -> {
                    String path = disharmony.getFileName();
                    ScmLogInfo scmLogInfo = null;
                    try {
                        scmLogInfo = gitLogReader.fileLog(path);
                        log.info("Successfully fetched scmLogInfo for {}", scmLogInfo.getPath());
                    } catch (GitAPIException | IOException e) {
                        log.error("Error reading Git repository contents.", e);
                    } catch (NullPointerException e) {
                        log.info("Encountered nested class in a class containing a violation.  Class: {}", path);
                        scmLogInfo = new ScmLogInfo(path, 0, 0, 0);
                    }
                    return scmLogInfo;
                })
                .collect(Collectors.toList());

        changePronenessRanker.rankChangeProneness(scmLogInfos);
        return scmLogInfos;
    }

    public List<RankedDisharmony> calculateCBOCostBenefitValues() {
        List<CBOClass> cboClasses = getCBOClasses();

        List<ScmLogInfo> scmLogInfos = getRankedChangeProneness(cboClasses);

        Map<String, ScmLogInfo> rankedLogInfosByPath =
                scmLogInfos.stream().collect(Collectors.toMap(ScmLogInfo::getPath, logInfo -> logInfo, (a, b) -> b));

        List<RankedDisharmony> rankedDisharmonies = new ArrayList<>();
        for (CBOClass cboClass : cboClasses) {
            rankedDisharmonies.add(new RankedDisharmony(cboClass, rankedLogInfosByPath.get(cboClass.getFileName())));
        }

        rankedDisharmonies.sort(
                Comparator.comparing(RankedDisharmony::getRawPriority).reversed());

        int cboPriority = 1;
        for (RankedDisharmony rankedCBODisharmony : rankedDisharmonies) {
            rankedCBODisharmony.setPriority(cboPriority++);
        }

        return rankedDisharmonies;
    }

    private List<CBOClass> getCBOClasses() {
        List<CBOClass> cboClasses = new ArrayList<>();
        for (RuleViolation violation : report.getViolations()) {
            if (violation.getRule().getName().contains("CBORule")) {
                log.info(violation.getDescription());
                CBOClass godClass = new CBOClass(
                        violation.getAdditionalInfo().get(CLASS_NAME),
                        getFileName(violation),
                        violation.getAdditionalInfo().get(PACKAGE_NAME),
                        violation.getDescription());
                log.info("Highly Coupled class identified: {}", godClass.getFileName());
                cboClasses.add(godClass);
            }
        }
        return cboClasses;
    }

    private String getFileName(RuleViolation violation) {
        String uriString = violation.getFileId().getUriString();
        return canonicaliseURIStringForRepoLookup(uriString);
    }

    private String canonicaliseURIStringForRepoLookup(String uriString) {
        if (repositoryPath.startsWith("/") || repositoryPath.startsWith("\\")) {
            return uriString.replace("file://" + repositoryPath.replace("\\", "/") + "/", "");
        }
        return uriString.replace("file:///" + repositoryPath.replace("\\", "/") + "/", "");
    }

    public Map<String, String> getClassNamesAndPaths() throws IOException {

        Map<String, String> fileNamePaths = new HashMap<>();

        try (Stream<Path> walk = Files.walk(Paths.get(repositoryPath))) {
            walk.forEach(path -> {
                String filename = path.getFileName().toString();
                if (filename.endsWith(".java")) {
                    String uriString = path.toUri().toString();
                    fileNamePaths.put(getClassName(filename), canonicaliseURIStringForRepoLookup(uriString));
                }
            });
        }

        return fileNamePaths;
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

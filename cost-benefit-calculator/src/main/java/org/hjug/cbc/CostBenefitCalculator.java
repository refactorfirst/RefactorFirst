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
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.pmd.*;
import net.sourceforge.pmd.lang.LanguageRegistry;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.hjug.cycledetector.CircularReferenceChecker;
import org.hjug.git.ChangePronenessRanker;
import org.hjug.git.GitLogReader;
import org.hjug.git.ScmLogInfo;
import org.hjug.metrics.*;
import org.hjug.metrics.rules.CBORule;
import org.hjug.parser.JavaProjectParser;
import org.jgrapht.Graph;
import org.jgrapht.alg.flow.GusfieldGomoryHuCutTree;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.AsUndirectedGraph;
import org.jgrapht.graph.DefaultEdge;

@Slf4j
public class CostBenefitCalculator {

    private final Map<String, AsSubgraph> renderedSubGraphs = new HashMap<>();

    private Report report;
    private String repositoryPath;
    private final GitLogReader gitLogReader = new GitLogReader();
    private Repository repository = null;
    private final ChangePronenessRanker changePronenessRanker;
    private final JavaProjectParser javaProjectParser = new JavaProjectParser();

    public CostBenefitCalculator(String repositoryPath) {
        this.repositoryPath = repositoryPath;

        log.info("Initiating Cost Benefit calculation");
        try {
            repository = gitLogReader.gitRepository(new File(repositoryPath));
            for (String file :
                    gitLogReader.listRepositoryContentsAtHEAD(repository).keySet()) {
                log.info("Files at HEAD: {}", file);
            }
        } catch (IOException e) {
            log.error("Failure to access Git repository", e);
        }

        changePronenessRanker = new ChangePronenessRanker(repository, gitLogReader);
    }

    public List<RankedCycle> runCycleAnalysis(String outputDirectoryPath, boolean renderImages) {
        List<RankedCycle> rankedCycles = new ArrayList<>();
        try {
            Map<String, String> classNamesAndPaths = getClassNamesAndPaths();
            Graph<String, DefaultEdge> classReferencesGraph = javaProjectParser.getClassReferences(repositoryPath);
            CircularReferenceChecker circularReferenceChecker = new CircularReferenceChecker();
            Map<String, AsSubgraph<String, DefaultEdge>> cyclesForEveryVertexMap =
                    circularReferenceChecker.detectCycles(classReferencesGraph);
            cyclesForEveryVertexMap.forEach((vertex, subGraph) -> {
                int vertexCount = subGraph.vertexSet().size();
                int edgeCount = subGraph.edgeSet().size();
                double minCut = 0;
                Set<DefaultEdge> minCutEdges = null;
                if (vertexCount > 1 && edgeCount > 1 && !isDuplicateSubGraph(subGraph, vertex)) {
                    if (renderImages) {
                        try {
                            circularReferenceChecker.createImage(
                                    outputDirectoryPath + "/refactorFirst/cycles", subGraph, vertex);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    renderedSubGraphs.put(vertex, subGraph);
                    log.info("Vertex: " + vertex + " vertex count: " + vertexCount + " edge count: " + edgeCount);
                    GusfieldGomoryHuCutTree<String, DefaultEdge> gusfieldGomoryHuCutTree =
                            new GusfieldGomoryHuCutTree<>(new AsUndirectedGraph<>(subGraph));
                    minCut = gusfieldGomoryHuCutTree.calculateMinCut();
                    log.info("Min cut weight: " + minCut);
                    minCutEdges = gusfieldGomoryHuCutTree.getCutEdges();

                    log.info("Minimum Cut Edges:");
                    for (DefaultEdge minCutEdge : minCutEdges) {
                        log.info(minCutEdge.toString());
                    }

                    List<CycleNode> cycleNodes = subGraph.vertexSet().stream()
                            .map(classInCycle -> new CycleNode(classInCycle, classNamesAndPaths.get(classInCycle)))
                            .collect(Collectors.toList());
                    List<ScmLogInfo> changeRanks = getRankedChangeProneness(cycleNodes);

                    Map<String, CycleNode> cycleNodeMap = new HashMap<>();

                    for (CycleNode cycleNode : cycleNodes) {
                        cycleNodeMap.put(cycleNode.getFileName(), cycleNode);
                    }

                    for (ScmLogInfo changeRank : changeRanks) {
                        CycleNode cn = cycleNodeMap.get(changeRank.getPath());
                        cn.setScmLogInfo(changeRank);
                    }

                    // sum change proneness ranks
                    int changePronenessRankSum = changeRanks.stream()
                            .mapToInt(ScmLogInfo::getChangePronenessRank)
                            .sum();
                    rankedCycles.add(new RankedCycle(
                            vertex,
                            changePronenessRankSum,
                            subGraph.vertexSet(),
                            subGraph.edgeSet(),
                            minCut,
                            minCutEdges,
                            cycleNodes));
                }
            });

            rankedCycles.sort(Comparator.comparing(RankedCycle::getAverageChangeProneness));
            int cpr = 1;
            for (RankedCycle rankedCycle : rankedCycles) {
                rankedCycle.setChangePronenessRank(cpr++);
            }

            rankedCycles.sort(Comparator.comparing(RankedCycle::getRawPriority).reversed());

            int priority = 1;
            for (RankedCycle rankedCycle : rankedCycles) {
                rankedCycle.setPriority(priority++);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return rankedCycles;
    }

    private boolean isDuplicateSubGraph(AsSubgraph<String, DefaultEdge> subGraph, String vertex) {
        if (!renderedSubGraphs.isEmpty()) {
            for (AsSubgraph renderedSubGraph : renderedSubGraphs.values()) {
                if (renderedSubGraph.vertexSet().size() == subGraph.vertexSet().size()
                        && renderedSubGraph.edgeSet().size()
                                == subGraph.edgeSet().size()
                        && renderedSubGraph.vertexSet().contains(vertex)) {
                    return true;
                }
            }
        }

        return false;
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
                files.forEach(file -> pmd.files().addFile(file));
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
        List<ScmLogInfo> scmLogInfos = new ArrayList<>();
        log.info("Calculating Change Proneness");
        for (Disharmony disharmony : disharmonies) {
            String path = disharmony.getFileName();
            ScmLogInfo scmLogInfo = null;
            try {
                scmLogInfo = gitLogReader.fileLog(repository, path);
                log.info("Successfully fetched scmLogInfo for {}", scmLogInfo.getPath());
            } catch (GitAPIException | IOException e) {
                log.error("Error reading Git repository contents.", e);
            } catch (NullPointerException e) {
                log.error("Encountered nested class in a class containing a violation.  Class: {}", path);
            }

            if (null != scmLogInfo) {
                log.info("adding {}", scmLogInfo.getPath());
                scmLogInfos.add(scmLogInfo);
            }
        }

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
        return violation.getFileId().getUriString().replace("file:///" + repositoryPath.replace("\\", "/") + "/", "");
    }

    public Map<String, String> getClassNamesAndPaths() throws IOException {

        Map<String, String> fileNamePaths = new HashMap<>();

        try (Stream<Path> walk = Files.walk(Paths.get(repositoryPath))) {
            walk.forEach(path -> {
                String filename = path.getFileName().toString();
                if (filename.endsWith(".java")) {
                    fileNamePaths.put(
                            getClassName(filename),
                            path.toUri().toString().replace("file:///" + repositoryPath.replace("\\", "/") + "/", ""));
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

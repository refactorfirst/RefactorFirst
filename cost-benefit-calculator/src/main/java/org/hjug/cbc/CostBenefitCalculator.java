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
import org.hjug.git.ChangePronenessRanker;
import org.hjug.git.GitLogReader;
import org.hjug.git.ScmLogInfo;
import org.hjug.metrics.*;
import org.hjug.metrics.rules.CBORule;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

@Slf4j
public class CostBenefitCalculator implements AutoCloseable {

    private Report report;
    private final String repositoryPath;
    private GitLogReader gitLogReader;

    private final ChangePronenessRanker changePronenessRanker;

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

    // copied from PMD's PmdTaskImpl.java and modified
    public void runPmdAnalysis() throws IOException {
        PMDConfiguration configuration = new PMDConfiguration();

        try (PmdAnalysis pmd = PmdAnalysis.create(configuration)) {
            loadRules(pmd);

            try (Stream<Path> files = Files.walk(Paths.get(repositoryPath))) {
                files.filter(Files::isRegularFile).forEach(file -> pmd.files().addFile(file));
            }

            report = pmd.performAnalysisAndCollectReport();
        }
    }

    public void runPmdAnalysis(boolean excludeTests, String testSourceDirectory) throws IOException {
        PMDConfiguration configuration = new PMDConfiguration();

        try (PmdAnalysis pmd = PmdAnalysis.create(configuration)) {
            loadRules(pmd);

            try (Stream<Path> files = Files.walk(Paths.get(repositoryPath))) {
                Stream<Path> pathStream;
                if (excludeTests) {
                    pathStream = files.filter(Files::isRegularFile)
                            .filter(file -> !file.toString().contains(testSourceDirectory));
                } else {
                    pathStream = files.filter(Files::isRegularFile);
                }

                pathStream.forEach(file -> pmd.files().addFile(file));
            }

            report = pmd.performAnalysisAndCollectReport();
        }
    }

    private void loadRules(PmdAnalysis pmd) {
        RuleSetLoader rulesetLoader = pmd.newRuleSetLoader();
        pmd.addRuleSets(rulesetLoader.loadRuleSetsWithoutException(List.of("category/java/design.xml")));

        Rule cboClassRule = new CBORule();
        cboClassRule.setLanguage(LanguageRegistry.PMD.getLanguageByFullName("Java"));
        pmd.addRuleSet(RuleSet.forSingleRule(cboClassRule));

        log.info("files to be scanned: " + Paths.get(repositoryPath));
    }

    public List<RankedDisharmony> calculateGodClassCostBenefitValues() {
        List<GodClass> godClasses = getGodClasses();

        List<ScmLogInfo> scmLogInfos = getRankedChangeProneness(godClasses);

        Map<String, ScmLogInfo> rankedLogInfosByPath = getRankedLogInfosByPath(scmLogInfos);

        List<RankedDisharmony> rankedDisharmonies = godClasses.stream()
                .filter(godClass -> rankedLogInfosByPath.containsKey(godClass.getFileName()))
                .map(godClass -> new RankedDisharmony(godClass, rankedLogInfosByPath.get(godClass.getFileName())))
                .sorted(Comparator.comparing(RankedDisharmony::getRawPriority).reversed())
                .collect(Collectors.toList());

        int godClassPriority = 1;
        for (RankedDisharmony rankedGodClassDisharmony : rankedDisharmonies) {
            rankedGodClassDisharmony.setPriority(godClassPriority++);
        }

        return rankedDisharmonies;
    }

    private static Map<String, ScmLogInfo> getRankedLogInfosByPath(List<ScmLogInfo> scmLogInfos) {
        return scmLogInfos.stream().collect(Collectors.toMap(ScmLogInfo::getPath, logInfo -> logInfo, (a, b) -> b));
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

    public <T extends Disharmony> List<ScmLogInfo> getRankedChangeProneness(List<T> disharmonies) {
        log.info("Calculating Change Proneness");

        List<ScmLogInfo> scmLogInfos = disharmonies.parallelStream()
                .map(disharmony -> {
                    String path = disharmony.getFileName();
                    ScmLogInfo scmLogInfo = null;
                    try {
                        scmLogInfo = gitLogReader.fileLog(path);
                        log.debug("Successfully fetched scmLogInfo for {}", scmLogInfo.getPath());
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

        Map<String, ScmLogInfo> rankedLogInfosByPath = getRankedLogInfosByPath(scmLogInfos);

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

    public List<RankedDisharmony> calculateSourceNodeCostBenefitValues(
            Graph<String, DefaultWeightedEdge> classGraph,
            Map<DefaultWeightedEdge, CycleNode> edgeSourceNodeInfos,
            Map<DefaultWeightedEdge, Integer> edgeToRemoveCycleCounts,
            Set<String> vertexesToRemove) {
        List<ScmLogInfo> scmLogInfos = getRankedChangeProneness(new ArrayList<>(edgeSourceNodeInfos.values()));

        Map<String, ScmLogInfo> rankedLogInfosByPath = getRankedLogInfosByPath(scmLogInfos);

        List<RankedDisharmony> edgesThatNeedToBeRemoved = new ArrayList<>();

        for (Map.Entry<DefaultWeightedEdge, CycleNode> entry : edgeSourceNodeInfos.entrySet()) {
            if (rankedLogInfosByPath.containsKey(entry.getValue().getFileName())) {
                boolean sourceNodeShouldBeRemoved = vertexesToRemove.contains(classGraph.getEdgeSource(entry.getKey()));
                String edgeTarget = classGraph.getEdgeTarget(entry.getKey());
                boolean targetNodeShouldBeRemoved = vertexesToRemove.contains(edgeTarget);

                /*
                [INFO] Edge source: org.apache.myfaces.tobago.internal.component.AbstractUIPage
                [INFO] Filename: null
                [INFO] Path: null
                */

                String fileName = entry.getValue().getFileName();
                if (null == fileName) continue;
                log.info("Edge source: {}", classGraph.getEdgeSource(entry.getKey()));
                log.info("Filename: {}", fileName);
                String path = rankedLogInfosByPath.get(fileName).getPath();
                log.info("Path: {}", path);
                Paths.get(path).getFileName().toString();

                RankedDisharmony edgeThatNeedsToBeRemoved = new RankedDisharmony(
                        entry.getValue().getClassName(),
                        entry.getKey(),
                        edgeToRemoveCycleCounts.get(entry.getKey()),
                        (int) classGraph.getEdgeWeight(entry.getKey()),
                        sourceNodeShouldBeRemoved,
                        targetNodeShouldBeRemoved,
                        rankedLogInfosByPath.get(entry.getValue().getFileName()));
                edgesThatNeedToBeRemoved.add(edgeThatNeedsToBeRemoved);
            }
        }

        sortEdgesThatNeedToBeRemoved(edgesThatNeedToBeRemoved);

        // Then subtract edge weight
        int rawPriority = 1;
        for (RankedDisharmony rankedDisharmony : edgesThatNeedToBeRemoved) {
            rankedDisharmony.setRawPriority(rawPriority++ - rankedDisharmony.getEffortRank());
        }

        edgesThatNeedToBeRemoved.sort(
                Comparator.comparing(RankedDisharmony::getRawPriority).reversed());

        // Then set priority
        int sourceNodePriority = 1;
        for (RankedDisharmony rankedSourceNodeDisharmony : edgesThatNeedToBeRemoved) {
            rankedSourceNodeDisharmony.setPriority(sourceNodePriority++);
        }

        return edgesThatNeedToBeRemoved;
    }

    static void sortEdgesThatNeedToBeRemoved(List<RankedDisharmony> rankedDisharmonies) {
        // Sort by impact value
        // Order by cycle count reversed (highest count bubbles to the top)
        rankedDisharmonies.sort(Comparator.comparingInt(RankedDisharmony::getCycleCount)
                .reversed()
                // then if the source node is in the list of nodes to be removed
                // multiplying by -1 reverses the sort order (reverse doesn't work in chained comparators)
                .thenComparingInt(rankedDisharmony -> -1 * rankedDisharmony.getSourceNodeShouldBeRemoved())
                // then if the target node is in the list of nodes to be removed
                .thenComparingInt(rankedDisharmUpdaony -> -1 * rankedDisharmony.getTargetNodeShouldBeRemoved())
                // then by change proneness
                .thenComparingInt(rankedDisharmony -> -1 * rankedDisharmony.getChangePronenessRank()));
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
}

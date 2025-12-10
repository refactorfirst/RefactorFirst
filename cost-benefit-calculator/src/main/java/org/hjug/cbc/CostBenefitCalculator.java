package org.hjug.cbc;

import static net.sourceforge.pmd.RuleViolation.CLASS_NAME;
import static net.sourceforge.pmd.RuleViolation.PACKAGE_NAME;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<String, String> classToSourceFilePathMapping;

    public CostBenefitCalculator(String repositoryPath, Map<String, String> classToSourceFilePathMapping) {
        this.repositoryPath = repositoryPath;

        log.info("Initiating Cost Benefit calculation");
        try {
            gitLogReader = new GitLogReader(new File(repositoryPath));
        } catch (IOException e) {
            log.error("Failure to access Git repository", e);
        }

        changePronenessRanker = new ChangePronenessRanker(gitLogReader);
        this.classToSourceFilePathMapping = classToSourceFilePathMapping;
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

    private static Map<String, ScmLogInfo> getRankedLogInfosByClass(List<ScmLogInfo> scmLogInfos) {
        return scmLogInfos.stream()
                .collect(Collectors.toMap(ScmLogInfo::getClassName, logInfo -> logInfo, (a, b) -> b));
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

        Map<String, String> innerClassPaths = new ConcurrentHashMap<>();
        Map<String, ScmLogInfo> scmLogInfosByPath = new ConcurrentHashMap<>();
        Map<String, ScmLogInfo> scmLogInfosByClass = new ConcurrentHashMap<>();

        disharmonies.parallelStream().forEach(disharmony -> {
            String className = disharmony.getClassName();
            String path;
            ScmLogInfo scmLogInfo = null;
            if (className.contains("$")) {
                path = classToSourceFilePathMapping.get(className.substring(0, className.indexOf("$")));
                log.debug("Found source file {} for nested class: {}", path, className);
                innerClassPaths.put(className, path);
            } else {
                path = disharmony.getFileName();
                try {
                    log.debug("Reading scmLogInfo for {}", path);
                    scmLogInfo = gitLogReader.fileLog(path);
                    scmLogInfo.setClassName(className);
                    log.debug("Successfully fetched scmLogInfo for {}", scmLogInfo.getPath());
                    scmLogInfosByPath.put(path, scmLogInfo);
                    scmLogInfosByClass.put(className, scmLogInfo);
                } catch (GitAPIException | IOException e) {
                    log.error("Error reading Git repository contents.", e);
                } catch (NullPointerException e) {
                    // Should not be reached
                    log.error(
                            "Encountered nested class in a class containing a violation.  Class: {}, Path: {}",
                            className,
                            path);
                }
            }
        });

        innerClassPaths.entrySet().parallelStream().forEach(innerClassPathEntry -> {
            ScmLogInfo scmLogInfo = scmLogInfosByPath.get(innerClassPathEntry.getValue());

            ScmLogInfo innerClassScmLogInfo = null;
            if (scmLogInfo == null) {
                try {
                    String className = innerClassPathEntry.getKey();
                    String path = classToSourceFilePathMapping.get(className.substring(0, className.indexOf("$")));
                    log.debug("Reading scmLogInfo for inner class {}", canonicaliseURIStringForRepoLookup(path));
                    innerClassScmLogInfo = gitLogReader.fileLog(canonicaliseURIStringForRepoLookup(path));
                    innerClassScmLogInfo.setClassName(className);
                    log.debug(
                            "Successfully fetched scmLogInfo for inner class {} at {}",
                            innerClassScmLogInfo.getClassName(),
                            innerClassScmLogInfo.getPath());
                    scmLogInfosByPath.put(path, innerClassScmLogInfo);
                    scmLogInfosByClass.put(className, innerClassScmLogInfo);
                } catch (GitAPIException | IOException e) {
                    log.error("Error reading Git repository contents.", e);
                }
            } else {
                innerClassScmLogInfo = new ScmLogInfo(
                        innerClassPathEntry.getValue(),
                        innerClassPathEntry.getKey(),
                        scmLogInfo.getEarliestCommit(),
                        scmLogInfo.getMostRecentCommit(),
                        scmLogInfo.getCommitCount());

                String className = innerClassPathEntry.getKey();
                innerClassScmLogInfo.setClassName(className);
                String path = classToSourceFilePathMapping.get(className.substring(0, className.indexOf("$")));
                scmLogInfosByPath.put(path, innerClassScmLogInfo);
                scmLogInfosByClass.put(className, innerClassScmLogInfo);
            }

            scmLogInfosByClass.put(innerClassPathEntry.getKey(), innerClassScmLogInfo);
        });

        ArrayList<ScmLogInfo> sortedScmInfos = new ArrayList<>(scmLogInfosByClass.values());
        changePronenessRanker.rankChangeProneness(sortedScmInfos);
        return sortedScmInfos;
    }

    public List<RankedDisharmony> calculateCBOCostBenefitValues() {
        List<CBOClass> cboClasses = getCBOClasses();

        List<ScmLogInfo> scmLogInfos = getRankedChangeProneness(cboClasses);

        Map<String, ScmLogInfo> rankedLogInfosByPath = getRankedLogInfosByPath(scmLogInfos);
        for (Map.Entry<String, ScmLogInfo> stringScmLogInfoEntry : rankedLogInfosByPath.entrySet()) {
            log.debug(
                    "ScmLogInfo entry: {} path: {}",
                    stringScmLogInfoEntry.getKey(),
                    stringScmLogInfoEntry.getValue().getPath());
        }

        List<RankedDisharmony> rankedDisharmonies = new ArrayList<>();
        for (CBOClass cboClass : cboClasses) {
            log.debug("CBO Class identified: {}", cboClass.getFileName());
            log.debug(
                    "ScmLogInfo: {}",
                    rankedLogInfosByPath.get(cboClass.getFileName()).getPath());
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
                log.debug("Highly Coupled class identified: {}", godClass.getFileName());
                cboClasses.add(godClass);
            }
        }
        return cboClasses;
    }

    public List<RankedDisharmony> calculateSourceNodeCostBenefitValues(
            Graph<String, DefaultWeightedEdge> classGraph,
            Map<DefaultWeightedEdge, CycleNode> edgeSourceNodeInfos,
            Map<DefaultWeightedEdge, CycleNode> edgeTargetNodeInfos,
            Map<DefaultWeightedEdge, Integer> edgeToRemoveCycleCounts,
            Set<String> vertexesToRemove) {
        List<ScmLogInfo> sourceLogInfos = getRankedChangeProneness(new ArrayList<>(edgeSourceNodeInfos.values()));
        List<ScmLogInfo> targetLogInfos = getRankedChangeProneness(new ArrayList<>(edgeTargetNodeInfos.values()));
        List<ScmLogInfo> scmLogInfos = new ArrayList<>(sourceLogInfos.size() + targetLogInfos.size());
        scmLogInfos.addAll(sourceLogInfos);
        scmLogInfos.addAll(targetLogInfos);

        Map<String, ScmLogInfo> sourceRankedLogInfosByPath = getRankedLogInfosByPath(scmLogInfos);
        List<RankedDisharmony> edgesThatNeedToBeRemoved = new ArrayList<>();

        for (Map.Entry<DefaultWeightedEdge, CycleNode> entry : edgeSourceNodeInfos.entrySet()) {
            String edgeSource = classGraph.getEdgeSource(entry.getKey());

            String edgeSourcePath;
            if (edgeSource.contains("$")) {
                edgeSourcePath = classToSourceFilePathMapping.get(edgeSource.substring(0, edgeSource.indexOf("$")));
            } else {
                edgeSourcePath = classToSourceFilePathMapping.get(edgeSource);
            }

            String edgeTarget = classGraph.getEdgeTarget(entry.getKey());
            String edgeTargetPath;
            if (edgeTarget.contains("$")) {
                edgeTargetPath = classToSourceFilePathMapping.get(edgeTarget.substring(0, edgeTarget.indexOf("$")));
            } else {
                edgeTargetPath = classToSourceFilePathMapping.get(edgeTarget);
            }

            String sourceNodeFileName = canonicaliseURIStringForRepoLookup(edgeSourcePath);
            String targetNodeFileName = canonicaliseURIStringForRepoLookup(edgeTargetPath);

            if (sourceRankedLogInfosByPath.containsKey(sourceNodeFileName)) {
                boolean sourceNodeShouldBeRemoved = vertexesToRemove.contains(edgeSource);
                boolean targetNodeShouldBeRemoved = vertexesToRemove.contains(edgeTarget);

                ScmLogInfo sourceScmLogInfo = sourceRankedLogInfosByPath.get(sourceNodeFileName);
                ScmLogInfo targetScmLogInfo = sourceRankedLogInfosByPath.get(targetNodeFileName);

                RankedDisharmony edgeThatNeedsToBeRemoved = new RankedDisharmony(
                        edgeSource,
                        edgeTarget,
                        entry.getKey(),
                        edgeToRemoveCycleCounts.get(entry.getKey()),
                        (int) classGraph.getEdgeWeight(entry.getKey()),
                        sourceNodeShouldBeRemoved,
                        targetNodeShouldBeRemoved,
                        sourceScmLogInfo,
                        targetScmLogInfo);
                edgesThatNeedToBeRemoved.add(edgeThatNeedsToBeRemoved);
            }
        }

        sortEdgesThatNeedToBeRemoved(edgesThatNeedToBeRemoved);

        // Then subtract edge weight
        int rawPriority = 1;
        for (RankedDisharmony rankedDisharmony : edgesThatNeedToBeRemoved) {
            rankedDisharmony.setRawPriority(rawPriority++);
        }

        // Push edges with higher weights down in the priority list
        edgesThatNeedToBeRemoved.sort(Comparator.comparing(RankedDisharmony::getRawPriority));

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
                // then by weight, with lowest weight edges bubbling to the top
                .thenComparingInt(RankedDisharmony::getEffortRank)
                // then by change proneness
                .thenComparingInt(rankedDisharmony -> -1 * rankedDisharmony.getChangePronenessRank())
                .thenComparingInt(rankedDisharmony -> -1 * rankedDisharmony.getEdgeTargetChangePronenessRank())
                // then if the source node is in the list of nodes to be removed
                // multiplying by -1 reverses the sort order (reverse doesn't work in chained comparators)
                .thenComparingInt(rankedDisharmony -> -1 * rankedDisharmony.getSourceNodeShouldBeRemoved())
                // then if the target node is in the list of nodes to be removed
                .thenComparingInt(rankedDisharmony -> -1 * rankedDisharmony.getTargetNodeShouldBeRemoved()));
    }

    private String getFileName(RuleViolation violation) {
        String uriString = violation.getFileId().getUriString();
        return canonicaliseURIStringForRepoLookup(uriString);
    }

    String canonicaliseURIStringForRepoLookup(String uriString) {
        if (repositoryPath.startsWith("/") || repositoryPath.startsWith("\\")) {
            return uriString.replace("file://" + repositoryPath.replace("\\", "/") + "/", "");
        }
        return uriString.replace("file:///" + repositoryPath.replace("\\", "/") + "/", "");
    }
}

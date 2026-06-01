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
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.ClassDisharmony;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.MethodDisharmony;
import org.hjug.graphbuilder.metrics.DisharmonyTypes;
import org.hjug.metrics.*;
import org.hjug.metrics.DisharmonyInstance;
import org.hjug.metrics.DisharmonyRanker;
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

    private void loadRules(PmdAnalysis pmd) {
        RuleSetLoader rulesetLoader = pmd.newRuleSetLoader();
        pmd.addRuleSets(rulesetLoader.loadRuleSetsWithoutException(List.of("category/java/design.xml")));

        Rule cboClassRule = new CBORule();
        cboClassRule.setLanguage(LanguageRegistry.PMD.getLanguageByFullName("Java"));
        pmd.addRuleSet(RuleSet.forSingleRule(cboClassRule));

        log.info("files to be scanned: " + Paths.get(repositoryPath));
    }

    public List<RankedDisharmony> calculateGodClassCostBenefitValues(List<GodClass> godClasses) {
        List<ScmLogInfo> scmLogInfos = getRankedChangeProneness(godClasses);

        Map<String, ScmLogInfo> rankedLogInfosByPath = getRankedLogInfosByPath(scmLogInfos);

        List<RankedDisharmony> rankedDisharmonies = godClasses.stream()
                .filter(godClass -> rankedLogInfosByPath.containsKey(godClass.getFileRepoPath()))
                .map(godClass -> new RankedDisharmony(godClass, rankedLogInfosByPath.get(godClass.getFileRepoPath())))
                .sorted(Comparator.comparing(RankedDisharmony::getRawPriority).reversed())
                .collect(Collectors.toList());

        int godClassPriority = 1;
        for (RankedDisharmony rankedGodClassDisharmony : rankedDisharmonies) {
            rankedGodClassDisharmony.setPriority(godClassPriority++);
        }

        return rankedDisharmonies;
    }

    /**
     *  Returns a map of ScmLogInfo objects keyed by the path of the file.
     *  If there are multiple ScmLogInfo objects for a single path, the last one is returned.
     *  TODO: this method should be revisited to make it more robust to allow it to handle nested classes
     *
     * @param scmLogInfos
     * @return A map of ScmLogInfo objects keyed by the path of the file.  If there are multiple ScmLogInfo objects for a single path, the last one is returned.
     */
    private static Map<String, ScmLogInfo> getRankedLogInfosByPath(List<ScmLogInfo> scmLogInfos) {
        return scmLogInfos.stream().collect(Collectors.toMap(ScmLogInfo::getPath, logInfo -> logInfo, (a, b) -> b));
    }

    public List<GodClass> getGodClasses(CodebaseGraphDTO codebaseGraphDTO) {
        List<ClassDisharmony> raw = codebaseGraphDTO.getClassDisharmoniesOfType(DisharmonyTypes.GOD_CLASS);

        List<GodClass> godClasses = raw.stream()
                .map(classDisharmony -> new GodClass(
                        classDisharmony.getMetrics().getClassName(),
                        canonicaliseURIStringForRepoLookup(
                                classDisharmony.getMetrics().getSourceFilePath()),
                        classDisharmony.getMetrics().getPackageName(),
                        classDisharmony.getDescription()))
                .collect(Collectors.toList());

        GodClassRanker godClassRanker = new GodClassRanker();
        godClassRanker.rankGodClasses(godClasses);

        return godClasses;
    }

    public List<DisharmonyInstance> getClassDisharmonies(CodebaseGraphDTO codebaseGraphDTO, String disharmonyType) {
        List<ClassDisharmony> raw = codebaseGraphDTO.getClassDisharmoniesOfType(disharmonyType);

        List<DisharmonyInstance> instances = raw.stream()
                .map(d -> {
                    DisharmonyInstance instance = new DisharmonyInstance(
                            disharmonyType,
                            d.getClassName(),
                            canonicaliseURIStringForRepoLookup(
                                    d.getMetrics().getSourceFilePath().replace("\\", "/")),
                            d.getMetrics().getPackageName(),
                            null,
                            new java.util.ArrayList<>(d.getMetricValues()));
                    instance.setDescription(d.getDescription());
                    return instance;
                })
                .collect(Collectors.toList());

        new DisharmonyRanker().rank(instances);
        return instances;
    }

    public List<DisharmonyInstance> getMethodDisharmonies(CodebaseGraphDTO codebaseGraphDTO, String disharmonyType) {
        List<MethodDisharmony> raw = codebaseGraphDTO.getMethodDisharmoniesOfType(disharmonyType);

        List<DisharmonyInstance> instances = raw.stream()
                .map(d -> {
                    String filePath = classToSourceFilePathMapping.get(d.getClassName());
                    // Try outer class for inner/anonymous classes (e.g., "Outer$Inner" → "Outer")
                    if (filePath == null && d.getClassName().contains("$")) {
                        filePath = classToSourceFilePathMapping.get(
                                d.getClassName().substring(0, d.getClassName().indexOf("$")));
                    }
                    if (filePath == null) {
                        log.warn("No source file mapping found for method disharmony in class: {}", d.getClassName());
                    }
                    return new DisharmonyInstance(
                            disharmonyType,
                            d.getClassName(),
                            filePath,
                            "",
                            d.getMethodSignature(),
                            new java.util.ArrayList<>(d.getMetricValues()));
                })
                .collect(Collectors.toList());

        new DisharmonyRanker().rank(instances);
        return instances;
    }

    public List<RankedDisharmony> calculateDisharmonyCostBenefitValues(List<DisharmonyInstance> instances) {
        List<ScmLogInfo> scmLogInfos = getRankedChangeProneness(instances);
        Map<String, ScmLogInfo> rankedLogInfosByPath = getRankedLogInfosByPath(scmLogInfos);

        List<RankedDisharmony> rankedDisharmonies = instances.stream()
                .filter(i -> rankedLogInfosByPath.containsKey(i.getFileRepoPath()))
                .map(i -> new RankedDisharmony(i, rankedLogInfosByPath.get(i.getFileRepoPath())))
                .sorted(Comparator.comparing(RankedDisharmony::getRawPriority).reversed())
                .collect(Collectors.toList());

        int priority = 1;
        for (RankedDisharmony rd : rankedDisharmonies) {
            rd.setPriority(priority++);
        }
        return rankedDisharmonies;
    }

    // TODO: Go away
    public List<GodClass> getGodClasses() {
        List<GodClass> godClasses = new ArrayList<>();
        for (RuleViolation violation : report.getViolations()) {
            if (violation.getRule().getName().contains("GodClass")) {
                GodClass godClass = new GodClass(
                        violation.getAdditionalInfo().get(CLASS_NAME),
                        getFileName(violation),
                        violation.getAdditionalInfo().get(PACKAGE_NAME),
                        violation.getDescription());
                log.info("God Class identified: {}", godClass.getFileRepoPath());
                godClasses.add(godClass);
            }
        }

        GodClassRanker godClassRanker = new GodClassRanker();
        godClassRanker.rankGodClasses(godClasses);

        return godClasses;
    }

    public <T extends Disharmony> List<ScmLogInfo> getRankedChangeProneness(List<T> disharmonies) {
        log.info("Calculating Change Proneness");

        List<Optional<ScmLogInfo>> scmLogInfos = disharmonies.parallelStream()
                .map(disharmony -> {
                    String className = disharmony.getClassName();
                    String path = null;
                    ScmLogInfo scmLogInfo = null;
                    try {
                        path = disharmony.getFileRepoPath();
                        try {
                            if (className.contains("$") && !classToSourceFilePathMapping.containsKey(className)) {
                                path = classToSourceFilePathMapping.get(className.substring(0, className.indexOf("$")));
                                log.debug("Found source file {} for lambda: {}", path, className);
                            }

                            log.debug("Reading scmLogInfo for {}", path);
                            scmLogInfo = gitLogReader.fileLog(path);
                            scmLogInfo.setClassName(className);
                            log.debug("Successfully fetched scmLogInfo for {}", scmLogInfo.getPath());

                        } catch (GitAPIException | IOException e) {
                            log.error("Error reading Git repository contents.", e);
                        }
                    } catch (NullPointerException e) {
                        // Should not be reached
                        log.error(
                                "Error looking up class SCM info.  If this error is encountered, "
                                        + "please log a bug on the RefactorFirst project and describe if the class is a nested class, lambda, etc. \nClass: {}, Path: {}",
                                className,
                                path,
                                e);
                    }

                    Optional<ScmLogInfo> scmLogInfoOptional = Optional.ofNullable(scmLogInfo);
                    if (scmLogInfoOptional.isEmpty()) {
                        log.warn("No scmLogInfo found for class: {} at path: {}", className, path);
                    }
                    return scmLogInfoOptional;
                })
                .collect(Collectors.toList());

        List<ScmLogInfo> sortedScmInfos = scmLogInfos.stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

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
            log.debug("CBO Class identified: {}", cboClass.getFileRepoPath());
            log.debug(
                    "ScmLogInfo: {}",
                    rankedLogInfosByPath.get(cboClass.getFileRepoPath()).getPath());
            rankedDisharmonies.add(
                    new RankedDisharmony(cboClass, rankedLogInfosByPath.get(cboClass.getFileRepoPath())));
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
                log.debug("Highly Coupled class identified: {}", godClass.getFileRepoPath());
                cboClasses.add(godClass);
            }
        }
        return cboClasses;
    }

    public List<RankedDisharmony> calculateSourceNodeCostBenefitValues(
            Graph<String, DefaultWeightedEdge> classGraph,
            Map<DefaultWeightedEdge, Integer> edgeToRemoveCycleCounts,
            CodebaseGraphDTO dto,
            Set<String> vertexesToRemove) {
        List<RankedDisharmony> edgesThatNeedToBeRemoved = new ArrayList<>();

        for (DefaultWeightedEdge edge : classGraph.edgeSet()) {
            // shouldn't have to check for null edges & counts :-(
            if (null == edge || null == edgeToRemoveCycleCounts.get(edge)) continue;

            String edgeSource = classGraph.getEdgeSource(edge);
            String edgeTarget = classGraph.getEdgeTarget(edge);

            boolean sourceNodeShouldBeRemoved = vertexesToRemove.contains(edgeSource);
            boolean targetNodeShouldBeRemoved = vertexesToRemove.contains(edgeTarget);

            RankedDisharmony edgeThatNeedsToBeRemoved = new RankedDisharmony(
                    edgeSource,
                    edge,
                    edgeToRemoveCycleCounts.get(edge),
                    (int) classGraph.getEdgeWeight(edge),
                    sourceNodeShouldBeRemoved,
                    targetNodeShouldBeRemoved,
                    dto.getClassDisharmonyCountForClass(edgeSource),
                    dto.getClassDisharmonyCountForClass(edgeTarget));
            edgesThatNeedToBeRemoved.add(edgeThatNeedsToBeRemoved);
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
                // then by disharmony count
                .thenComparingInt(RankedDisharmony::getChangePronenessRank)
                .thenComparingInt(RankedDisharmony::getEdgeTargetChangePronenessRank)
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

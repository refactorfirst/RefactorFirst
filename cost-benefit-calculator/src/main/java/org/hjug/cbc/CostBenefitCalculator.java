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
}

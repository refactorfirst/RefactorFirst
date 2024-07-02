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
import org.hjug.git.ChangePronenessRanker;
import org.hjug.git.GitLogReader;
import org.hjug.git.ScmLogInfo;
import org.hjug.metrics.*;
import org.hjug.metrics.rules.CBORule;

@Slf4j
public class CostBenefitCalculator {

    private Report report;
    private String projBaseDir = null;

    // copied from PMD's PmdTaskImpl.java and modified
    public void runPmdAnalysis(String projectBaseDir) throws IOException {
        projBaseDir = projectBaseDir;
        PMDConfiguration configuration = new PMDConfiguration();

        try (PmdAnalysis pmd = PmdAnalysis.create(configuration)) {
            RuleSetLoader rulesetLoader = pmd.newRuleSetLoader();
            pmd.addRuleSets(rulesetLoader.loadRuleSetsWithoutException(List.of("category/java/design.xml")));

            Rule cboClassRule = new CBORule();
            cboClassRule.setLanguage(LanguageRegistry.PMD.getLanguageByFullName("Java"));
            pmd.addRuleSet(RuleSet.forSingleRule(cboClassRule));

            log.info("files to be scanned: " + Paths.get(projectBaseDir));

            try (Stream<Path> files = Files.walk(Paths.get(projectBaseDir))) {
                files.forEach(file -> pmd.files().addFile(file));
            }

            report = pmd.performAnalysisAndCollectReport();
        }
    }

    public List<RankedDisharmony> calculateGodClassCostBenefitValues(String repositoryPath) {

        GitLogReader repositoryLogReader = new GitLogReader();
        Repository repository = null;
        log.info("Initiating Cost Benefit calculation");
        try {
            repository = repositoryLogReader.gitRepository(new File(repositoryPath));
            for (String file :
                    repositoryLogReader.listRepositoryContentsAtHEAD(repository).keySet()) {
                log.info("Files at HEAD: {}", file);
            }
        } catch (IOException e) {
            log.error("Failure to access Git repository", e);
        }

        // pass repo path here, not ByteArrayOutputStream
        List<GodClass> godClasses = getGodClasses();

        List<ScmLogInfo> scmLogInfos = getRankedChangeProneness(repositoryLogReader, repository, godClasses);

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

    <T extends Disharmony> List<ScmLogInfo> getRankedChangeProneness(
            GitLogReader repositoryLogReader, Repository repository, List<T> disharmonies) {
        List<ScmLogInfo> scmLogInfos = new ArrayList<>();
        log.info("Calculating Change Proneness");
        for (Disharmony disharmony : disharmonies) {
            String path = disharmony.getFileName();
            ScmLogInfo scmLogInfo = null;
            try {
                scmLogInfo = repositoryLogReader.fileLog(repository, path);
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

        ChangePronenessRanker changePronenessRanker = new ChangePronenessRanker(repository, repositoryLogReader);
        changePronenessRanker.rankChangeProneness(scmLogInfos);
        return scmLogInfos;
    }

    public List<RankedDisharmony> calculateCBOCostBenefitValues(String repositoryPath) {

        GitLogReader repositoryLogReader = new GitLogReader();
        Repository repository = null;
        log.info("Initiating Cost Benefit calculation");
        try {
            repository = repositoryLogReader.gitRepository(new File(repositoryPath));
        } catch (IOException e) {
            log.error("Failure to access Git repository", e);
        }

        List<CBOClass> cboClasses = getCBOClasses();

        List<ScmLogInfo> scmLogInfos = getRankedChangeProneness(repositoryLogReader, repository, cboClasses);

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
        return violation.getFileId().getUriString().replace("file:///" + projBaseDir.replace("\\", "/") + "/", "");
    }
}

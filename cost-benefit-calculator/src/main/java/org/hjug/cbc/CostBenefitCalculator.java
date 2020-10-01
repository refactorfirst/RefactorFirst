package org.hjug.cbc;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.hjug.git.ChangePronenessRanker;
import org.hjug.git.GitLogReader;
import org.hjug.git.ScmLogInfo;
import org.hjug.git.RepositoryLogReader;
import org.hjug.metrics.GodClass;
import org.hjug.metrics.GodClassRanker;
import org.hjug.metrics.PMDGodClassRuleRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class CostBenefitCalculator {

    public static void main(String[] args) {
        CostBenefitCalculator costBenefitCalculator = new CostBenefitCalculator();
        String path = "C:\\Code\\myfaces-tobago";
        List<RankedDisharmony> rankedDisharmonies = costBenefitCalculator.calculateCostBenefitValues(path);
        rankedDisharmonies.sort(Comparator.comparing(RankedDisharmony::getPriority).reversed());
        for (RankedDisharmony disharmony : rankedDisharmonies) {
            System.out.println("Priority: " + disharmony.getPriority() + "\t path: " + disharmony.getPath());
        }
    }


    public List<RankedDisharmony> calculateCostBenefitValues(String repositoryPath) {

        RepositoryLogReader repositoryLogReader = new GitLogReader();
        Repository repository = null;
        try {
            repository = repositoryLogReader.gitRepository(new File(repositoryPath));
        } catch (IOException e) {
            log.error("Failure to access Git repository", e);
        }

        List<GodClass> godClasses = getGodClasses(repositoryLogReader, repository);

        List<ScmLogInfo> scmLogInfos = getRankedChangeProneness(repositoryLogReader, repository, godClasses);

        Map<String, ScmLogInfo> rankedLogInfosByPath =
                scmLogInfos.stream()
                        .collect(Collectors.toMap(ScmLogInfo::getPath, logInfo -> logInfo, (a, b) -> b));

        List<RankedDisharmony> rankedDisharmonies = new ArrayList<>();
        for (GodClass godClass : godClasses) {
            rankedDisharmonies.add(new RankedDisharmony(godClass, rankedLogInfosByPath.get(godClass.getFileName())));
        }

        return rankedDisharmonies;
    }

    List<ScmLogInfo> getRankedChangeProneness(RepositoryLogReader repositoryLogReader, Repository repository, List<GodClass> godClasses) {
        List<ScmLogInfo> scmLogInfos = new ArrayList<>();
        for (GodClass godClass : godClasses) {
            String path = godClass.getFileName();
            ScmLogInfo scmLogInfo = null;
            try {
                scmLogInfo = repositoryLogReader.fileLog(repository, path);
            } catch (GitAPIException | IOException e) {
                log.error("Error reading Git repository contents", e);
            }

            scmLogInfos.add(scmLogInfo);
        }

        ChangePronenessRanker changePronenessRanker
                = new ChangePronenessRanker(repository, repositoryLogReader);
        changePronenessRanker.rankChangeProneness(scmLogInfos);
        return scmLogInfos;
    }

    private List<GodClass> getGodClasses(RepositoryLogReader repositoryLogReader, Repository repository) {
        Map<String, ByteArrayOutputStream> filesToScan = null;
        try {
            filesToScan = repositoryLogReader.listRepositoryContentsAtHEAD(repository);
        } catch (IOException e) {
            log.error("Error reading Git repository contents", e);
        }


        PMDGodClassRuleRunner ruleRunner = new PMDGodClassRuleRunner();

        List<GodClass> godClasses = new ArrayList<>();
        for (String filePath : filesToScan.keySet()) {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(filesToScan.get(filePath).toByteArray());
            Optional<GodClass> godClassOptional = ruleRunner.runGodClassRule(filePath, inputStream);
            godClassOptional.ifPresent(godClasses::add);
        }

        GodClassRanker godClassRanker = new GodClassRanker();
        godClassRanker.rankGodClasses(godClasses);
        return godClasses;
    }


}

package org.hjug.cbc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.hjug.git.ChangePronenessRanker;
import org.hjug.git.GitLogReader;
import org.hjug.git.RepositoryLogReader;
import org.hjug.git.ScmLogInfo;
import org.hjug.metrics.GodClass;
import org.hjug.metrics.GodClassRanker;
import org.hjug.metrics.PMDGodClassRuleRunner;

@Slf4j
public class CostBenefitCalculator {

    public List<RankedDisharmony> calculateCostBenefitValues(String repositoryPath) {

        RepositoryLogReader repositoryLogReader = new GitLogReader();
        Repository repository = null;
        log.info("Initiating Cost Benefit calculation");
        try {
            repository = repositoryLogReader.gitRepository(new File(repositoryPath));
        } catch (IOException e) {
            log.error("Failure to access Git repository", e);
        }

        List<GodClass> godClasses = getGodClasses(repositoryLogReader, repository);

        List<ScmLogInfo> scmLogInfos = getRankedChangeProneness(repositoryLogReader, repository, godClasses);

        Map<String, ScmLogInfo> rankedLogInfosByPath =
                scmLogInfos.stream().collect(Collectors.toMap(ScmLogInfo::getPath, logInfo -> logInfo, (a, b) -> b));

        List<RankedDisharmony> rankedDisharmonies = new ArrayList<>();
        for (GodClass godClass : godClasses) {
            rankedDisharmonies.add(new RankedDisharmony(godClass, rankedLogInfosByPath.get(godClass.getFileName())));
        }

        return rankedDisharmonies;
    }

    List<ScmLogInfo> getRankedChangeProneness(
            RepositoryLogReader repositoryLogReader, Repository repository, List<GodClass> godClasses) {
        List<ScmLogInfo> scmLogInfos = new ArrayList<>();
        log.info("Calculating Change Proneness for each God Class");
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

        ChangePronenessRanker changePronenessRanker = new ChangePronenessRanker(repository, repositoryLogReader);
        changePronenessRanker.rankChangeProneness(scmLogInfos);
        return scmLogInfos;
    }

    private List<GodClass> getGodClasses(RepositoryLogReader repositoryLogReader, Repository repository) {
        Map<String, ByteArrayOutputStream> filesToScan = new HashMap<>();
        log.info("Identifying God Classes from files in repository");
        try {
            filesToScan = repositoryLogReader.listRepositoryContentsAtHEAD(repository);
        } catch (IOException e) {
            log.error("Error reading Git repository contents", e);
        }

        PMDGodClassRuleRunner ruleRunner = new PMDGodClassRuleRunner();

        List<GodClass> godClasses = new ArrayList<>();
        for (Map.Entry<String, ByteArrayOutputStream> entry : filesToScan.entrySet()) {
            String filePath = entry.getKey();
            ByteArrayOutputStream value = entry.getValue();

            ByteArrayInputStream inputStream = new ByteArrayInputStream(value.toByteArray());
            Optional<GodClass> godClassOptional = ruleRunner.runGodClassRule(filePath, inputStream);
            godClassOptional.ifPresent(godClasses::add);
        }

        GodClassRanker godClassRanker = new GodClassRanker();
        godClassRanker.rankGodClasses(godClasses);
        return godClasses;
    }
}

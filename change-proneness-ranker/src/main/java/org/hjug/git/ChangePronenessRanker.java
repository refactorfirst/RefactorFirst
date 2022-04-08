package org.hjug.git;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

@Slf4j
public class ChangePronenessRanker {

    private Repository repository;
    private RepositoryLogReader repositoryLogReader;

    public ChangePronenessRanker(Repository repository, RepositoryLogReader repositoryLogReader) {
        this.repositoryLogReader = repositoryLogReader;
        this.repository = repository;
    }

    public void rankChangeProneness(List<ScmLogInfo> scmLogInfos) {
        TreeMap<Integer, Integer> changeCountsByTimeStamps = new TreeMap<>();

        try {
            log.info("Capturing change count based on commit timestamps");
            changeCountsByTimeStamps.putAll(repositoryLogReader.captureChangeCountByCommitTimestamp(repository));
        } catch (IOException | GitAPIException e) {
            log.error("Error reading from repository: {}", e.getMessage());
        }

        for (ScmLogInfo scmLogInfo : scmLogInfos) {
            int commitsInRepositorySinceCreation =
                    changeCountsByTimeStamps.tailMap(scmLogInfo.getEarliestCommit()).values().stream()
                            .mapToInt(i -> i)
                            .sum();

            scmLogInfo.setChangeProneness((float) scmLogInfo.getCommitCount() / commitsInRepositorySinceCreation);
        }

        scmLogInfos.sort(Comparator.comparing(ScmLogInfo::getChangeProneness));

        int rank = 0;
        for (ScmLogInfo scmLogInfo : scmLogInfos) {
            scmLogInfo.setChangePronenessRank(++rank);
        }
    }
}

package org.hjug.git;

import java.io.IOException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;

@Slf4j
public class ChangePronenessRanker {

    private final TreeMap<Integer, Integer> changeCountsByTimeStamps = new TreeMap<>();
    private final Map<String, ScmLogInfo> cachedScmLogInfos = new HashMap<>();

    public ChangePronenessRanker(GitLogReader repositoryLogReader) {
        try {
            log.info("Capturing change count based on commit timestamps");
            changeCountsByTimeStamps.putAll(repositoryLogReader.captureChangeCountByCommitTimestamp());
        } catch (IOException | GitAPIException e) {
            log.error("Error reading from repository: {}", e.getMessage());
        }
    }

    public void rankChangeProneness(List<ScmLogInfo> scmLogInfos) {
        for (ScmLogInfo scmLogInfo : scmLogInfos) {
            if (!cachedScmLogInfos.containsKey(scmLogInfo.getPath())) {
                int commitsInRepositorySinceCreation =
                        changeCountsByTimeStamps.tailMap(scmLogInfo.getEarliestCommit()).values().stream()
                                .mapToInt(i -> i)
                                .sum();

                scmLogInfo.setChangeProneness((float) scmLogInfo.getCommitCount() / commitsInRepositorySinceCreation);
                cachedScmLogInfos.put(scmLogInfo.getPath(), scmLogInfo);
            } else {
                scmLogInfo.setChangeProneness(
                        cachedScmLogInfos.get(scmLogInfo.getPath()).getChangeProneness());
            }
        }

        scmLogInfos.sort(Comparator.comparing(ScmLogInfo::getChangeProneness));

        int rank = 0;
        for (ScmLogInfo scmLogInfo : scmLogInfos) {
            scmLogInfo.setChangePronenessRank(++rank);
        }
    }
}

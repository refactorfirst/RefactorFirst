package org.hjug.git;

import java.io.IOException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;

@Slf4j
public class ChangePronenessRanker {

    private final Map<Integer, Integer> changeCountsByTimeStamps = new HashMap<>();
    private final Map<String, ScmLogInfo> cachedScmLogInfos = new HashMap<>();

    public ChangePronenessRanker(GitLogReader repositoryLogReader) {
        try {
            log.info("Capturing change count based on commit timestamps");
            changeCountsByTimeStamps.putAll(
                    computeChangeCountsByTimeStamps(repositoryLogReader.captureChangeCountByCommitTimestamp()));
        } catch (IOException | GitAPIException e) {
            log.error("Error reading from repository: {}", e.getMessage());
        }
    }

    private Map<Integer, Integer> computeChangeCountsByTimeStamps(TreeMap<Integer, Integer> commitsWithChangeCounts) {
        HashMap<Integer, Integer> changeCountsByTimeStamps = new HashMap<>();
        int runningTotal = 0;
        for (Map.Entry<Integer, Integer> commitChangeCountEntry :
                commitsWithChangeCounts.descendingMap().entrySet()) {
            runningTotal += commitChangeCountEntry.getValue();
            changeCountsByTimeStamps.put(commitChangeCountEntry.getKey(), runningTotal);
        }

        return changeCountsByTimeStamps;
    }

    public void rankChangeProneness(List<ScmLogInfo> scmLogInfos) {
        for (ScmLogInfo scmLogInfo : scmLogInfos) {
            if (!cachedScmLogInfos.containsKey(scmLogInfo.getPath())) {
                if (scmLogInfo.getEarliestCommit() == 0) {
                    log.warn("No commits found for {}", scmLogInfo.getPath());
                    continue;
                }

                int commitsInRepositorySinceCreation = changeCountsByTimeStamps.get(scmLogInfo.getEarliestCommit());

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

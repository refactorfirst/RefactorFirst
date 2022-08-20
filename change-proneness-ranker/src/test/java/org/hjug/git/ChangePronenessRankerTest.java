package org.hjug.git;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ChangePronenessRankerTest {

    private ChangePronenessRanker changePronenessRanker;
    private RepositoryLogReader repositoryLogReader;

    @BeforeEach
    public void setUp() {
        repositoryLogReader = mock(RepositoryLogReader.class);
        changePronenessRanker = new ChangePronenessRanker(null, repositoryLogReader);
    }

    // TODO: this should probably be a cucumber test
    @Test
    void testChangePronenessCalculation() throws IOException, GitAPIException {
        ScmLogInfo scmLogInfo = new ScmLogInfo("path", 1595275997, 0, 1);

        TreeMap<Integer, Integer> commitsWithChangeCounts = new TreeMap<>();
        commitsWithChangeCounts.put(scmLogInfo.getEarliestCommit(), scmLogInfo.getCommitCount());
        commitsWithChangeCounts.put(scmLogInfo.getEarliestCommit() + 5 * 60, 3);
        commitsWithChangeCounts.put(scmLogInfo.getEarliestCommit() + 10 * 60, 3);

        when(repositoryLogReader.captureChangeCountByCommitTimestamp(any())).thenReturn(commitsWithChangeCounts);

        List<ScmLogInfo> scmLogInfos = new ArrayList<>();
        scmLogInfos.add(scmLogInfo);
        changePronenessRanker.rankChangeProneness(scmLogInfos);

        // 1 commit of a class we're interested in, 6 commits of other files after it
        Assertions.assertEquals((float) 1 / 7, scmLogInfo.getChangeProneness(), 0.1);
    }

    @Test
    void testRankChangeProneness() throws IOException, GitAPIException {
        ScmLogInfo scmLogInfo = new ScmLogInfo("file1", 1595275997, 0, 1);

        TreeMap<Integer, Integer> commitsWithChangeCounts = new TreeMap<>();
        commitsWithChangeCounts.put(scmLogInfo.getEarliestCommit(), scmLogInfo.getCommitCount());
        commitsWithChangeCounts.put(scmLogInfo.getEarliestCommit() + 5 * 60, 3);
        commitsWithChangeCounts.put(scmLogInfo.getEarliestCommit() + 10 * 60, 3);

        ScmLogInfo scmLogInfo2 = new ScmLogInfo("file2", 1595175997, 0, 1);

        commitsWithChangeCounts.put(scmLogInfo2.getEarliestCommit(), scmLogInfo2.getCommitCount());
        commitsWithChangeCounts.put(scmLogInfo2.getEarliestCommit() + 5 * 60, 5);
        commitsWithChangeCounts.put(scmLogInfo2.getEarliestCommit() + 10 * 60, 5);

        when(repositoryLogReader.captureChangeCountByCommitTimestamp(any())).thenReturn(commitsWithChangeCounts);

        List<ScmLogInfo> scmLogInfos = new ArrayList<>();
        scmLogInfos.add(scmLogInfo);
        scmLogInfos.add(scmLogInfo2);
        changePronenessRanker.rankChangeProneness(scmLogInfos);

        // ranks higher since fewer commits since initial commit
        Assertions.assertEquals(2, scmLogInfo.getChangePronenessRank());
        // ranks lower since there have been more commits since initial commit
        Assertions.assertEquals(1, scmLogInfo2.getChangePronenessRank());
    }
}

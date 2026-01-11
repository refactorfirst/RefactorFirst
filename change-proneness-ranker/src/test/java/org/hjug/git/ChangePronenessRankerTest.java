package org.hjug.git;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChangePronenessRankerTest {

    private ChangePronenessRanker changePronenessRanker;
    private GitLogReader repositoryLogReader;

    @BeforeEach
    public void setUp() {
        repositoryLogReader = mock(GitLogReader.class);
    }

    // TODO: this should probably be a cucumber test
    @Test
    void testChangePronenessCalculation() throws IOException, GitAPIException {
        ScmLogInfo scmLogInfo = new ScmLogInfo("path", null, 1595275997, 0, 1);

        TreeMap<Integer, Integer> commitsWithChangeCounts = new TreeMap<>();
        commitsWithChangeCounts.put(scmLogInfo.getEarliestCommit(), scmLogInfo.getCommitCount());
        commitsWithChangeCounts.put(scmLogInfo.getEarliestCommit() + 5 * 60, 3);
        commitsWithChangeCounts.put(scmLogInfo.getEarliestCommit() + 10 * 60, 3);

        when(repositoryLogReader.captureChangeCountByCommitTimestamp()).thenReturn(commitsWithChangeCounts);

        changePronenessRanker = new ChangePronenessRanker(repositoryLogReader);
        List<ScmLogInfo> scmLogInfos = new ArrayList<>();
        scmLogInfos.add(scmLogInfo);
        changePronenessRanker.rankChangeProneness(scmLogInfos);

        // 1 commit of a class we're interested in, 6 commits of other files after it
        Assertions.assertEquals((float) 1 / 7, scmLogInfo.getChangeProneness(), 0.1);
    }

    @Test
    void testRankChangeProneness() throws IOException, GitAPIException {
        // more recent commit
        ScmLogInfo newerCommit = new ScmLogInfo("file1", null, 1595275997, 0, 1);

        TreeMap<Integer, Integer> commitsWithChangeCounts = new TreeMap<>();
        commitsWithChangeCounts.put(newerCommit.getEarliestCommit(), newerCommit.getCommitCount());
        commitsWithChangeCounts.put(newerCommit.getEarliestCommit() + 5 * 60, 3);
        commitsWithChangeCounts.put(newerCommit.getEarliestCommit() + 10 * 60, 3);

        // older commit
        ScmLogInfo olderCommit = new ScmLogInfo("file2", null, 1595175997, 0, 1);

        commitsWithChangeCounts.put(olderCommit.getEarliestCommit(), olderCommit.getCommitCount());
        commitsWithChangeCounts.put(olderCommit.getEarliestCommit() + 5 * 60, 5);
        commitsWithChangeCounts.put(olderCommit.getEarliestCommit() + 10 * 60, 5);

        when(repositoryLogReader.captureChangeCountByCommitTimestamp()).thenReturn(commitsWithChangeCounts);
        changePronenessRanker = new ChangePronenessRanker(repositoryLogReader);

        List<ScmLogInfo> scmLogInfos = new ArrayList<>();
        scmLogInfos.add(newerCommit);
        scmLogInfos.add(olderCommit);
        changePronenessRanker.rankChangeProneness(scmLogInfos);

        // ranks higher since fewer commits since initial commit
        Assertions.assertEquals(2, newerCommit.getChangePronenessRank());
        // ranks lower since there have been more commits since initial commit
        Assertions.assertEquals(1, olderCommit.getChangePronenessRank());
    }
}

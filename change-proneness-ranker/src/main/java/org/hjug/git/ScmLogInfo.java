package org.hjug.git;

import lombok.Data;

@Data
public class ScmLogInfo {

    private String path;
    private int earliestCommit;
    private int mostRecentCommit;
    private int commitCount;
    private float changeProneness;
    private int changePronenessRank;

    public ScmLogInfo(String path, int earliestCommit, int mostRecentCommit, int commitCount) {
        this.path = path;
        this.earliestCommit = earliestCommit;
        this.mostRecentCommit = mostRecentCommit;
        this.commitCount = commitCount;
    }
}

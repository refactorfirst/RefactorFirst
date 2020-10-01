package org.hjug.git;

import lombok.Data;

@Data
public class ScmLogInfo {

    private String path;
    private int earliestCommit;
    private int commitCount;
    private float changeProneness;
    private int changePronenessRank;

    public ScmLogInfo(String path, int earliestCommit, int commitCount) {
        this.path = path;
        this.earliestCommit = earliestCommit;
        this.commitCount = commitCount;
    }
}

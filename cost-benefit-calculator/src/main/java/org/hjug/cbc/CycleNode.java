package org.hjug.cbc;

import java.time.Instant;
import lombok.Data;
import org.hjug.git.ScmLogInfo;
import org.hjug.metrics.Disharmony;

@Data
public class CycleNode implements Disharmony {

    private final String className;
    private String fileName;
    private Integer changePronenessRank;

    private Instant firstCommitTime;
    private Instant mostRecentCommitTime;
    private Integer commitCount;

    public CycleNode(String className, String fileName) {
        this.className = className;
        this.fileName = fileName;
    }

    public String getPackageName() {
        return className.substring(0, className.lastIndexOf('.'));
    }

    public void setScmLogInfo(ScmLogInfo scmLogInfo) {
        firstCommitTime = Instant.ofEpochSecond(scmLogInfo.getEarliestCommit());
        mostRecentCommitTime = Instant.ofEpochSecond(scmLogInfo.getMostRecentCommit());
        commitCount = scmLogInfo.getCommitCount();
    }
}

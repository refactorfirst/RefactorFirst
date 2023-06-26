package org.hjug.cbc;

import java.nio.file.Paths;
import java.time.Instant;
import lombok.Data;
import org.hjug.git.ScmLogInfo;
import org.hjug.metrics.CBOClass;
import org.hjug.metrics.GodClass;

@Data
public class RankedDisharmony {

    private final String path;
    private final String fileName;
    private final String className;
    private final Integer effortRank;
    private final Integer changePronenessRank;
    private final Integer rawPriority;
    private Integer priority;

    private Integer wmc;
    private Integer wmcRank;
    private Integer atfd;
    private Integer atfdRank;
    private Float tcc;
    private Integer tccRank;

    private final Instant firstCommitTime;
    private final Instant mostRecentCommitTime;
    private final Integer commitCount;

    public RankedDisharmony(GodClass godClass, ScmLogInfo scmLogInfo) {
        path = scmLogInfo.getPath();
        // from https://stackoverflow.com/questions/1011287/get-file-name-from-a-file-location-in-java
        fileName = Paths.get(path).getFileName().toString();
        className = godClass.getClassName();
        changePronenessRank = scmLogInfo.getChangePronenessRank();
        effortRank = godClass.getOverallRank();
        rawPriority = changePronenessRank - effortRank;

        wmc = godClass.getWmc();
        wmcRank = godClass.getWmcRank();
        atfd = godClass.getAtfd();
        atfdRank = godClass.getAtfdRank();
        tcc = godClass.getTcc();
        tccRank = godClass.getTccRank();

        firstCommitTime = Instant.ofEpochSecond(scmLogInfo.getEarliestCommit());
        mostRecentCommitTime = Instant.ofEpochSecond(scmLogInfo.getMostRecentCommit());
        commitCount = scmLogInfo.getCommitCount();
    }

    public RankedDisharmony(CBOClass cboClass, ScmLogInfo scmLogInfo) {
        path = scmLogInfo.getPath();
        // from https://stackoverflow.com/questions/1011287/get-file-name-from-a-file-location-in-java
        fileName = Paths.get(path).getFileName().toString();
        className = cboClass.getClassName();
        changePronenessRank = scmLogInfo.getChangePronenessRank();
        effortRank = cboClass.getCouplingCount();
        rawPriority = changePronenessRank - effortRank;

        firstCommitTime = Instant.ofEpochSecond(scmLogInfo.getEarliestCommit());
        mostRecentCommitTime = Instant.ofEpochSecond(scmLogInfo.getMostRecentCommit());
        commitCount = scmLogInfo.getCommitCount();
    }
}

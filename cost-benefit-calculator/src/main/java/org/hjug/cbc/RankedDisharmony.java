package org.hjug.cbc;

import java.nio.file.Paths;
import java.time.Instant;
import lombok.Data;
import org.hjug.git.ScmLogInfo;
import org.hjug.metrics.GodClass;

@Data
public class RankedDisharmony {

    private final String path;
    private final String fileName;
    private final String className;
    private final Integer effortRank;
    private final Integer changePronenessRank;
    private final Integer priority;

    private final Integer wmc;
    private final Integer wmcRank;
    private final Integer atfd;
    private final Integer atfdRank;
    private final Float tcc;
    private final Integer tccRank;

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
        priority = changePronenessRank - effortRank;

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
}

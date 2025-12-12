package org.hjug.cbc;

import java.nio.file.Paths;
import java.time.Instant;
import lombok.Data;
import org.hjug.git.ScmLogInfo;
import org.hjug.metrics.CBOClass;
import org.hjug.metrics.GodClass;
import org.jgrapht.graph.DefaultWeightedEdge;

@Data
public class RankedDisharmony {

    private Instant firstCommitTime;
    private Instant mostRecentCommitTime;
    private Integer commitCount;

    private String path;
    private String fileName;
    private final String className;
    private final Integer effortRank;
    private final Integer changePronenessRank;
    private Integer rawPriority;
    private Integer priority = 0;

    private Integer wmc;
    private Integer wmcRank;
    private Integer atfd;
    private Integer atfdRank;
    private Float tcc;
    private Integer tccRank;

    private DefaultWeightedEdge edge;
    private Integer cycleCount;
    private int sourceNodeShouldBeRemoved;
    private int targetNodeShouldBeRemoved;
    private String edgeTargetClass;
    private Integer edgeTargetChangePronenessRank;

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

    public RankedDisharmony(
            String edgeSource,
            DefaultWeightedEdge edge,
            int cycleCount,
            int weight,
            boolean sourceNodeShouldBeRemoved,
            boolean targetNodeShouldBeRemoved,
            ScmLogInfo sourceScmLogInfo,
            ScmLogInfo targetScmLogInfo) {

        if (null != sourceScmLogInfo) {
            path = sourceScmLogInfo.getPath();
            // from https://stackoverflow.com/questions/1011287/get-file-name-from-a-file-location-in-java
            fileName = Paths.get(path).getFileName().toString();
            firstCommitTime = Instant.ofEpochSecond(sourceScmLogInfo.getEarliestCommit());
            mostRecentCommitTime = Instant.ofEpochSecond(sourceScmLogInfo.getMostRecentCommit());
            commitCount = sourceScmLogInfo.getCommitCount();
        }

        className = edgeSource;
        this.edge = edge;
        this.cycleCount = cycleCount;
        changePronenessRank = null == sourceScmLogInfo ? 0 : sourceScmLogInfo.getChangePronenessRank();
        edgeTargetChangePronenessRank = null == targetScmLogInfo ? 0 : targetScmLogInfo.getChangePronenessRank();
        effortRank = weight;
        this.sourceNodeShouldBeRemoved = sourceNodeShouldBeRemoved ? 1 : 0;
        this.targetNodeShouldBeRemoved = targetNodeShouldBeRemoved ? 1 : 0;
    }
}

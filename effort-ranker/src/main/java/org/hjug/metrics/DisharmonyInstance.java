package org.hjug.metrics;

import java.util.List;
import lombok.Data;
import org.hjug.graphbuilder.metrics.DisharmonyMetric;

@Data
public class DisharmonyInstance implements Disharmony {

    private final String disharmonyType;
    private final String className;
    private final String fileRepoPath;
    private final String packageName;
    /** Null for class-level disharmonies. */
    private final String methodSignature;

    private final List<DisharmonyMetric> metrics;

    private Integer sumOfRanks;
    private Integer overallRank;
    private String description;
}

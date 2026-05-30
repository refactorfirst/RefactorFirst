package org.hjug.graphbuilder.metrics;

import lombok.Data;

@Data
public class DisharmonyMetric {

    public enum Direction {
        ASCENDING,
        DESCENDING
    }

    private final String name;
    private final double value;
    private final Direction direction;
    private Integer rank;
}

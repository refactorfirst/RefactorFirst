package org.hjug.refactorfirst.report.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphVisualDTO {
    private String graphId;
    private int classCount;
    private int relationshipCount;
    private int dotThreshold;
    private boolean dotThresholdExceeded;
    private String dot;
    private boolean hasEdges;
}

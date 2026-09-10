package org.hjug.refactorfirst.report.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CycleSummaryDTO {
    private String cycleName;
    private int priority;
    private int classCount;
    private int relationshipCount;
}

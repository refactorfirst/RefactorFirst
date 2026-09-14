package org.hjug.refactorfirst.report.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassRelationshipDTO {
    private String sourceClass;
    private String targetClass;
    private String sourceUrl;
    private String targetUrl;
    private boolean sourceMarked;
    private boolean targetMarked;
    private int weight;
    private String renderedLabel;
    private int priority;
    private int cycleCount;
    private int effortRank;
    private boolean alsoRemovesPackageRelationship;
    private int packageCycleCount;
}

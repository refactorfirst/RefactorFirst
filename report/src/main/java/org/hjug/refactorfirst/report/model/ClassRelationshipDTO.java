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
    private String sourceClassPath;
    private String targetClassPath;
    private String simpleSourceClassName;
    private String simpleTargetClassName;
    private boolean sourceMarked;
    private boolean targetMarked;
    private int weight;
    private int priority;
    private int cycleCount;
    private int effortRank;
    private boolean alsoRemovesPackageRelationship;
    private int packageCycleCount;
}

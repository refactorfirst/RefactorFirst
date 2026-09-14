package org.hjug.refactorfirst.report.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageRelationshipDTO {
    private String sourcePackage;
    private String targetPackage;
    private boolean sourceMarked;
    private boolean targetMarked;
    private int weight;
    private String renderedLabel;
    private int priority;
    private int cycleCount;
    private int effortRank;

    @Builder.Default
    private List<String> classRelationshipsToBreakPackage = new ArrayList<>();
}

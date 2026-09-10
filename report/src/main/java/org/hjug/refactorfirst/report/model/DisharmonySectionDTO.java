package org.hjug.refactorfirst.report.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisharmonySectionDTO {
    private String type;
    private String anchorId;
    private String title;
    private boolean methodLevel;
    private String problem;
    private String solution;
    private int maxPriority;
    private DisharmonyChartDTO chart;
    private DisharmonyTableDTO table;
}

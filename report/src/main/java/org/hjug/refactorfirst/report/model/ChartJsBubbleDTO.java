package org.hjug.refactorfirst.report.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartJsBubbleDTO {
    private String id;
    private String label;
    private int x;
    private int y;
    private int r;
    private int priority;
    private int effortRank;
    private int changePronenessRank;
    private String color;
    private String borderColor;
}

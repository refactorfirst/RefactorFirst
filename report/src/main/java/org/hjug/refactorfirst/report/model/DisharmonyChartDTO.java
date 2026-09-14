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
public class DisharmonyChartDTO {
    private String canvasId;
    private String xAxisLabel;
    private String yAxisLabel;

    @Builder.Default
    private List<ChartJsBubbleDTO> bubbles = new ArrayList<>();
}

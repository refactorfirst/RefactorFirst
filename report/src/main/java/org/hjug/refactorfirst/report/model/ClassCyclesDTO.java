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
public class ClassCyclesDTO {
    private boolean hasCycles;

    @Builder.Default
    private List<CycleSummaryDTO> summary = new ArrayList<>();

    private LargestCycleDTO largestCycle;
}

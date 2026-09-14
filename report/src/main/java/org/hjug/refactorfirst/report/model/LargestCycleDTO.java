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
public class LargestCycleDTO {
    private boolean hasCycleMap;
    private String cycleName;
    private String cycleIdentifier;
    private int classCount;
    private int relationshipCount;
    private String dot;
    private boolean dotThresholdExceeded;

    @Builder.Default
    private List<CycleBreakdownRowDTO> breakdown = new ArrayList<>();
}

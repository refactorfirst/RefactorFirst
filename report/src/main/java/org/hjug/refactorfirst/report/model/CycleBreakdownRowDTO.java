package org.hjug.refactorfirst.report.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CycleBreakdownRowDTO {
    private String className;
    private String classPath;
    private boolean marked;
    private String edgesHtml;
}

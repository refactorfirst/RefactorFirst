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
public class RefactorFirstReportDTO {
    private ProjectMetadataDTO project;
    private GraphVisualDTO classMap;
    private ClassRelationshipsToRemoveDTO classRelationshipsToRemove;
    private GraphVisualDTO packageMap;
    private PackageRelationshipsToRemoveDTO packageRelationshipsToRemove;
    private boolean hasDisharmonies;

    @Builder.Default
    private List<DisharmonySectionDTO> disharmonies = new ArrayList<>();

    private ClassCyclesDTO classCycles;
}

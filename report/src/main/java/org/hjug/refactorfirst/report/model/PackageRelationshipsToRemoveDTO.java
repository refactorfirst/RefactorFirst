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
public class PackageRelationshipsToRemoveDTO {
    private int cycleCount;
    private int relationshipsToRemoveCount;
    private boolean hasRelationships;

    @Builder.Default
    private List<PackageRelationshipDTO> relationships = new ArrayList<>();
}

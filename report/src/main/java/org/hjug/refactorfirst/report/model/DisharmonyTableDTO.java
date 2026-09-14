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
public class DisharmonyTableDTO {
    @Builder.Default
    private List<String> headers = new ArrayList<>();

    @Builder.Default
    private List<DisharmonyTableRowDTO> rows = new ArrayList<>();
}

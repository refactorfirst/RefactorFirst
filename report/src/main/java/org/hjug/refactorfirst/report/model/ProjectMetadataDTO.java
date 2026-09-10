package org.hjug.refactorfirst.report.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMetadataDTO {
    private String name;
    private String version;
    private String repoUrl;
    private String baseDir;
    private String scanTimestamp;
    private boolean hasAnyDisharmony;
}

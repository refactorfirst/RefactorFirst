package org.hjug.mavenreport.RefactorFirstJsonReport;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
class JsonReport {
    private List<JsonReportDisharmonyEntry> rankedDisharmonies;

    private List<String> errors;
}

package org.hjug.mavenreport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

public class RefactorFirstMavenReportTest {

    private RefactorFirstMavenReport mavenReport = new RefactorFirstMavenReport();

    @Test
    void testGetOutputName() {
        // This report will generate simple-report.html when invoked in a project with `mvn site`
        assertEquals("refactor-first-report", mavenReport.getOutputName());
    }

    @Test
    void getName() {
        // Name of the report when listed in the project-reports.html page of a project
        assertEquals("Refactor First Report", mavenReport.getName(Locale.getDefault()));
    }

    @Test
    void getDescription() {
        // Description of the report when listed in the project-reports.html page of a project
        assertEquals(
                "Ranks the disharmonies in a codebase.  The classes that should be refactored first "
                        + " have the highest priority values.",
                mavenReport.getDescription(Locale.getDefault()));
    }
}

package org.hjug.mavenreport;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RefactorFirstMavenReportTest {
    @Test
    void completeDocumentUsesExternalReportContract() {
        // Given: the site report that renders a complete HTML document.
        var report = new RefactorFirstMavenReport();
        // When / Then: Maven must use the external report path without site wrapping.
        assertTrue(report.isExternalReport());
    }
}

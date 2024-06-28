package org.hjug.refactorfirst.report;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SimpleHtmlReportTest {

    @Test
    void isDateTime() {
        HtmlReport htmlReport = new HtmlReport();
        String commitDateTime = "7/22/23, 5:00 AM";
        Assertions.assertTrue(htmlReport.isDateTime(commitDateTime));
    }
}

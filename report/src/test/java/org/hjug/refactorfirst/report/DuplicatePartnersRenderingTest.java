package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.refactorfirst.report.model.DisharmonySectionDTO;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DuplicatePartnersRenderingTest {

    @Test
    void significantDuplicationPartnersStayOnSingleLinesInBothReports() throws Exception {
        // Given: a significant-duplication finding with a partner name containing angle brackets
        RankedDisharmony finding = Mockito.mock(RankedDisharmony.class);
        Mockito.when(finding.getFileName()).thenReturn("Example.java");
        Mockito.when(finding.getPath()).thenReturn("src/main/java/Example.java");
        Mockito.when(finding.getPriority()).thenReturn(1);
        Mockito.when(finding.getEffortRank()).thenReturn(2);
        Mockito.when(finding.getChangePronenessRank()).thenReturn(1);
        Mockito.when(finding.getMostRecentCommitTime()).thenReturn(Instant.parse("2026-01-15T12:00:00Z"));
        Mockito.when(finding.getCommitCount()).thenReturn(2);
        Mockito.when(finding.getDuplicationPartners())
                .thenReturn("apply(Unknown,Unknown) ↔ <unknown>.apply(Unknown,Unknown); second() ↔ Other.second()");

        SimpleHtmlReport.DisharmonySpec duplicationSpec = SimpleHtmlReport.DISHARMONY_SPECS.stream()
                .filter(spec -> "SIG_DUP".equals(spec.anchorId()))
                .findFirst()
                .orElseThrow();
        DisharmonySectionDTO duplicationSection =
                new ReportModelBuilder().buildDisharmonySection(duplicationSpec, false, List.of(finding));
        RefactorFirstReportDTO report = InteractiveTemplateFixture.reportDto();
        report.setDisharmonies(List.of(duplicationSection));
        Map<String, Object> view = new ViewModelFactory().createViewModel(report);

        // When: both report templates render the finding
        TemplateEngine engine = new TemplateEngine();
        String simpleHtml = engine.render("templates/refactor-first-simple-report.mustache", view);
        String interactiveHtml = engine.renderWithAssets("templates/refactor-first-report.mustache", view);

        // Then: each partner entry is non-wrapping, while semicolon-separated instances keep their break
        String partnerMarkup =
                "<span class=\"rf-duplicate-partner\">apply(Unknown,Unknown) ↔ &lt;unknown&gt;.apply(Unknown,Unknown)<br>second() ↔ Other.second()</span>";
        Pattern nowrapRule = Pattern.compile("\\.rf-duplicate-partner\\s*\\{\\s*white-space:\\s*nowrap;");
        assertTrue(simpleHtml.contains(partnerMarkup), simpleHtml);
        assertTrue(interactiveHtml.contains(partnerMarkup), interactiveHtml);
        assertTrue(nowrapRule.matcher(simpleHtml).find(), simpleHtml);
        assertTrue(nowrapRule.matcher(interactiveHtml).find(), interactiveHtml);
    }
}

package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.List;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.refactorfirst.report.model.DisharmonySectionDTO;
import org.hjug.refactorfirst.report.model.DisharmonyTableCellDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Pins the JSON contract of the disharmony table cells: the Class cell carries the
 * plain file name and the source path relative to the project root, and no
 * server-rendered link — viewers combine the path with project.repoUrl.
 */
class JsonGeneratorDisharmonySectionTest {

    /** Creates a ranked God Class finding for the file at the given path. */
    private RankedDisharmony mockRankedDisharmony(String fileName, String path) {
        RankedDisharmony rd = Mockito.mock(RankedDisharmony.class);
        Mockito.when(rd.getFileName()).thenReturn(fileName);
        Mockito.when(rd.getPath()).thenReturn(path);
        Mockito.when(rd.getPriority()).thenReturn(1);
        Mockito.when(rd.getEffortRank()).thenReturn(5);
        Mockito.when(rd.getChangePronenessRank()).thenReturn(10);
        Mockito.when(rd.getMostRecentCommitTime()).thenReturn(Instant.now());
        Mockito.when(rd.getCommitCount()).thenReturn(1);
        Mockito.when(rd.getDuplicationPartners()).thenReturn(null);
        return rd;
    }

    @DisplayName("the disharmony Class cell carries the plain file name and the source path, not a rendered link")
    @Test
    void buildDisharmonySection_classCellCarriesPathAndFileName() {
        // Given: one ranked God Class finding
        JsonGenerator generator = new JsonGenerator();
        RankedDisharmony rd = mockRankedDisharmony("TestClass.java", "src/main/java/com/example/TestClass.java");

        // When: the disharmony section is built without detail columns
        DisharmonySectionDTO section =
                generator.buildDisharmonySection(SimpleHtmlReport.DISHARMONY_SPECS.get(0), false, List.of(rd));

        // Then: the Class cell lists the file name and the path relative to the
        // project root; the URL is the viewer's to construct
        DisharmonyTableCellDTO classCell =
                section.getTable().getRows().get(0).getCells().get(0);
        assertEquals("TestClass.java", classCell.getContent());
        assertEquals("src/main/java/com/example/TestClass.java", classCell.getPath());
        assertFalse(classCell.getContent().contains("<a"), "The Class cell must not embed a server-built link");
        assertFalse(
                classCell.getContent().contains("https://github.com/example/repo"),
                "The Class cell must not embed the repository URL");
    }
}

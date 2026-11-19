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

    /** A ranked finding for a class with the given fully qualified name in the file at the given path. */
    private RankedDisharmony mockRankedDisharmony(String fileName, String path, String className) {
        RankedDisharmony rd = mockRankedDisharmony(fileName, path);
        Mockito.when(rd.getClassName()).thenReturn(className);
        return rd;
    }

    private DisharmonySectionDTO sectionFor(RankedDisharmony rd) {
        return new JsonGenerator().buildDisharmonySection(SimpleHtmlReport.DISHARMONY_SPECS.get(0), false, List.of(rd));
    }

    @DisplayName("a C# row is named after its class, because one .cs file often holds several classes")
    @Test
    void buildDisharmonySection_cSharpRowShowsTheClassName() {
        // Given: a finding in class Clerk, which is declared in Order.cs beside class Order
        RankedDisharmony rd = mockRankedDisharmony("Order.cs", "src/Orders/Order.cs", "Shop.Orders.Clerk");

        // When: the disharmony section is built
        DisharmonySectionDTO section = sectionFor(rd);

        // Then: the Class cell and the chart bubble show the class; the link still goes to the file
        DisharmonyTableCellDTO classCell =
                section.getTable().getRows().get(0).getCells().get(0);
        assertEquals("Clerk", classCell.getContent());
        assertEquals("src/Orders/Order.cs", classCell.getPath());
        assertEquals("Clerk", section.getChart().getBubbles().get(0).getLabel());
        assertEquals("Clerk", section.getChart().getBubbles().get(0).getId());
        assertEquals(
                "src/Orders/Order.cs", section.getChart().getBubbles().get(0).getPath());
    }

    @DisplayName("a Java row keeps its file name even when the class name is known")
    @Test
    void buildDisharmonySection_javaRowStillShowsTheFileName() {
        RankedDisharmony rd = mockRankedDisharmony(
                "TestClass.java", "src/main/java/com/example/TestClass.java", "com.example.TestClass");

        DisharmonySectionDTO section = sectionFor(rd);

        assertEquals(
                "TestClass.java",
                section.getTable().getRows().get(0).getCells().get(0).getContent());
        assertEquals("TestClass.java", section.getChart().getBubbles().get(0).getLabel());
        assertEquals("TestClass.java", section.getChart().getBubbles().get(0).getId());
    }

    @DisplayName("a C# row with no class name falls back to its file name")
    @Test
    void buildDisharmonySection_cSharpRowWithoutAClassNameShowsTheFileName() {
        RankedDisharmony rd = mockRankedDisharmony("Order.cs", "src/Orders/Order.cs", null);

        DisharmonySectionDTO section = sectionFor(rd);

        assertEquals(
                "Order.cs",
                section.getTable().getRows().get(0).getCells().get(0).getContent());
        assertEquals("Order.cs", section.getChart().getBubbles().get(0).getLabel());
    }
}

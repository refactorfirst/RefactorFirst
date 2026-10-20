package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.hjug.refactorfirst.report.model.ClassNodeDTO;
import org.hjug.refactorfirst.report.model.EdgeDTO;
import org.hjug.refactorfirst.report.model.GraphVisualDTO;
import org.hjug.refactorfirst.report.model.HierarchyDTO;
import org.hjug.refactorfirst.report.model.PackageNodeDTO;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;
import org.junit.jupiter.api.Test;

/**
 * refactor-first.json carries the nested hierarchy instead of raw DOT; the browser viewers
 * synthesize DOT client-side from its fields. These tests pin the JSON contract: every field
 * that feeds DOT synthesis — package names, class ids and labels, source paths, and edge
 * weights/flags — must survive serialization round-trips with the special characters DOT
 * rendering cares about ($-separators, quotes, backslashes, angle brackets) intact.
 */
class HierarchyJsonEscapingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Verifies the hierarchy round-trips nested clusters with breakup and removal flags. */
    @Test
    void hierarchy_roundTripsNestedClustersAndFlags() throws Exception {
        HierarchyDTO hierarchy = HierarchyDTO.builder()
                .packages(List.of(
                        PackageNodeDTO.builder()
                                .name("org.hjug")
                                .id("cluster_abc")
                                .needsBreakUp(true)
                                .children(List.of(PackageNodeDTO.builder()
                                        .name("org.hjug.cbc")
                                        .id("cluster_def")
                                        .nodeId("package_def")
                                        .renderAsNode(true)
                                        .needsBreakUp(true)
                                        .build()))
                                .classes(List.of(ClassNodeDTO.builder()
                                        .fqn("org.hjug.cbc.CostBenefitCalculator")
                                        .id("CostBenefitCalculator")
                                        .label("CostBenefitCalculator")
                                        .path(
                                                "cost-benefit-calculator/src/main/java/org/hjug/cbc/CostBenefitCalculator.java")
                                        .markedForRemoval(true)
                                        .build()))
                                .build(),
                        PackageNodeDTO.builder()
                                .name(HierarchyBuilder.DEFAULT_PACKAGE)
                                .id("cluster_default")
                                .build()))
                .edges(List.of(EdgeDTO.builder()
                        .source("package_def")
                        .target("CostBenefitCalculator")
                        .weight(3)
                        .markedForRemoval(true)
                        .build()))
                .build();

        RefactorFirstReportDTO report = RefactorFirstReportDTO.builder()
                .classMap(GraphVisualDTO.builder()
                        .graphId("classGraph")
                        .hierarchy(hierarchy)
                        .build())
                .build();

        String json = objectMapper.writeValueAsString(report);
        RefactorFirstReportDTO deserialized = objectMapper.readValue(json, RefactorFirstReportDTO.class);

        assertEquals(hierarchy, deserialized.getClassMap().getHierarchy());
    }

    /** Verifies labels keep $-separators, quotes, backslashes and angle brackets through JSON. */
    @Test
    void hierarchy_roundTripsDotRelevantSpecialCharacters() throws Exception {
        ClassNodeDTO anon = ClassNodeDTO.builder()
                .fqn("dev.DeveloperWASDControl.<anonymous>")
                .id("DeveloperWASDControl_anonymous")
                .label("DeveloperWASDControl$anonymous")
                .path("src/main/kotlin/dev/DeveloperWASDControl.kt")
                .build();
        ClassNodeDTO quoted = ClassNodeDTO.builder()
                .fqn("Weird")
                .id("Weird")
                .label("say \"hi\" \\ back<slash>")
                .path("")
                .build();
        HierarchyDTO hierarchy = HierarchyDTO.builder()
                .packages(List.of(PackageNodeDTO.builder()
                        .name("com.\"weird\"\\name")
                        .id("cluster_deadbeef")
                        .classes(List.of(anon, quoted))
                        .build()))
                .edges(List.of())
                .build();

        RefactorFirstReportDTO report = RefactorFirstReportDTO.builder()
                .classMap(GraphVisualDTO.builder()
                        .graphId("classGraph")
                        .hierarchy(hierarchy)
                        .build())
                .build();

        String json = objectMapper.writeValueAsString(report);
        // the JSON must carry the anonymous marker and the $-separator verbatim
        assertTrue(json.contains("<anonymous>"));
        assertTrue(json.contains("DeveloperWASDControl$anonymous"));

        RefactorFirstReportDTO deserialized = objectMapper.readValue(json, RefactorFirstReportDTO.class);
        HierarchyDTO restored = deserialized.getClassMap().getHierarchy();
        assertEquals(hierarchy, restored);
        assertEquals(
                "say \"hi\" \\ back<slash>",
                restored.getPackages().get(0).getClasses().get(1).getLabel());
    }
}

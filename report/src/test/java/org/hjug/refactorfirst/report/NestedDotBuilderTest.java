package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.hjug.refactorfirst.report.model.ClassNodeDTO;
import org.hjug.refactorfirst.report.model.EdgeDTO;
import org.hjug.refactorfirst.report.model.HierarchyDTO;
import org.hjug.refactorfirst.report.model.PackageNodeDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link NestedDotBuilder} contract: rendering a {@link HierarchyDTO} as nested DOT
 * where packages become labelled clusters (rectangles), package-map participants become
 * rectangle nodes, and classes become ellipse nodes inside their owning package cluster.
 *
 * <p>Visual contract under test:
 *
 * <ul>
 *   <li>Clusters are declared before edges so every node keeps its cluster scope; edges
 *       reference the pre-resolved node ids and keep the exact attribute format of the
 *       previous emitters.</li>
 *   <li>Classes render as {@code shape=ellipse}; package nodes as {@code shape=rectangle}.</li>
 *   <li>Classes marked for removal keep {@code color=red style=filled}; packages that need to
 *       be broken up render pink — clusters via {@code style=filled fillcolor=pink}, package
 *       nodes via {@code color=pink style=filled}.</li>
 *   <li>Every cluster declares an explicit fill — pink when flagged, white otherwise — because
 *       vizdom propagates a styled cluster's fill to its nested cluster rects; the explicit
 *       white keeps child packages of a pink cluster on their normal fill.</li>
 *   <li>Labels are omitted when equal to the node id and escaped with the DOT-quoted rules;
 *       hyperlinks are only emitted when requested and only for classes with a source path.</li>
 * </ul>
 */
class NestedDotBuilderTest {

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private static ClassNodeDTO classNode(String fqn, String id, String label, String path, boolean marked) {
        return ClassNodeDTO.builder()
                .fqn(fqn)
                .id(id)
                .label(label)
                .path(path)
                .markedForRemoval(marked)
                .build();
    }

    private static PackageNodeDTO packageNode(String name, boolean needsBreakUp) {
        return PackageNodeDTO.builder()
                .name(name)
                .id(HierarchyBuilder.clusterId(name))
                .needsBreakUp(needsBreakUp)
                .build();
    }

    // ------------------------------------------------------------------
    // class map nesting
    // ------------------------------------------------------------------

    @DisplayName("class map nests ellipse class nodes inside labelled package clusters")
    @Test
    void buildNestedDot_nestsClassesInPackageClusters() {
        // Given: a branching package tree with two packages, one class whose label equals its
        // id and one whose label differs
        PackageNodeDTO orgA = PackageNodeDTO.builder()
                .name("org.a")
                .id(HierarchyBuilder.clusterId("org.a"))
                .classes(List.of(classNode("org.a.Alpha", "Alpha", "Alpha", "", false)))
                .build();
        PackageNodeDTO orgB = PackageNodeDTO.builder()
                .name("org.b")
                .id(HierarchyBuilder.clusterId("org.b"))
                .classes(List.of(
                        classNode("org.b.Beta", "Beta", "Beta", "", false),
                        classNode("org.b.Gamma", "Gamma", "Gamma Label", "", false)))
                .build();
        PackageNodeDTO org = PackageNodeDTO.builder()
                .name("org")
                .id(HierarchyBuilder.clusterId("org"))
                .children(List.of(orgA, orgB))
                .build();
        HierarchyDTO hierarchy = HierarchyDTO.builder()
                .packages(List.of(org))
                .edges(List.of(EdgeDTO.builder()
                        .source("Alpha")
                        .target("Beta")
                        .weight(3)
                        .markedForRemoval(false)
                        .build()))
                .build();

        // When: the hierarchy is rendered as nested DOT
        String dot = NestedDotBuilder.buildNestedDot(hierarchy, "https://github.com/x/y/blob", true);

        // Then: clusters come first, classes are ellipses with labels omitted when equal to
        // the id, and edges follow the clusters with the previous emitters' attribute format
        String expected = "strict digraph G {\n"
                + "subgraph " + HierarchyBuilder.clusterId("org") + " {\n"
                + "label=\"org\";\n"
                + "style=filled fillcolor=white;\n"
                + "subgraph " + HierarchyBuilder.clusterId("org.a") + " {\n"
                + "label=\"org.a\";\n"
                + "style=filled fillcolor=white;\n"
                + "Alpha [ shape=ellipse];\n"
                + "}\n"
                + "subgraph " + HierarchyBuilder.clusterId("org.b") + " {\n"
                + "label=\"org.b\";\n"
                + "style=filled fillcolor=white;\n"
                + "Beta [ shape=ellipse];\n"
                + "Gamma [ label=\"Gamma Label\" shape=ellipse];\n"
                + "}\n"
                + "}\n"
                + "Alpha -> Beta [ label = \"3\" weight = \"3\" ];\n"
                + "}";
        assertEquals(expected, dot);
    }

    @DisplayName("marked classes stay red and break-up package clusters fill pink")
    @Test
    void buildNestedDot_flagsRemovalTargets() {
        // Given: a package flagged for breakup containing a marked and an unmarked class
        PackageNodeDTO pA = PackageNodeDTO.builder()
                .name("p.a")
                .id(HierarchyBuilder.clusterId("p.a"))
                .needsBreakUp(true)
                .classes(List.of(classNode("p.a.A", "A", "A", "", true), classNode("p.a.B", "B", "B", "", false)))
                .build();
        PackageNodeDTO root = PackageNodeDTO.builder()
                .name("p")
                .id(HierarchyBuilder.clusterId("p"))
                .children(List.of(pA, packageNode("p.b", false)))
                .build();

        // When: the hierarchy is rendered as nested DOT
        String dot = NestedDotBuilder.buildNestedDot(
                HierarchyDTO.builder().packages(List.of(root)).edges(List.of()).build(), "", true);

        // Then: the cluster fills pink while the class removal keeps the red fill
        String expected = "strict digraph G {\n"
                + "subgraph " + HierarchyBuilder.clusterId("p") + " {\n"
                + "label=\"p\";\n"
                + "style=filled fillcolor=white;\n"
                + "subgraph " + HierarchyBuilder.clusterId("p.a") + " {\n"
                + "label=\"p.a\";\n"
                + "style=filled fillcolor=pink;\n"
                + "A [ shape=ellipse color=red style=filled];\n"
                + "B [ shape=ellipse];\n"
                + "}\n"
                + "subgraph " + HierarchyBuilder.clusterId("p.b") + " {\n"
                + "label=\"p.b\";\n"
                + "style=filled fillcolor=white;\n"
                + "}\n"
                + "}\n"
                + "}";
        assertEquals(expected, dot);
    }

    // ------------------------------------------------------------------
    // package graph (flat ellipse nodes + relationship edges, vizdom-rendered)
    // ------------------------------------------------------------------

    @DisplayName("package graph renders one ellipse per package with only the relationship edges")
    @Test
    void buildPackageDot_rendersFlatEllipseNodesAndEdges() {
        // Given: a parent package participating in a relationship with two child
        // packages, one flagged for breakup, and two relationships
        String hjugNodeId = HtmlReport.renderSafePackageNodeId("org.hjug");
        String cbcNodeId = HtmlReport.renderSafePackageNodeId("org.hjug.cbc");
        String dsmNodeId = HtmlReport.renderSafePackageNodeId("org.hjug.dsm");
        PackageNodeDTO hjug = PackageNodeDTO.builder()
                .name("org.hjug")
                .id(HierarchyBuilder.clusterId("org.hjug"))
                .nodeId(hjugNodeId)
                .path("src/main/java/org/hjug/")
                .renderAsNode(false)
                .children(List.of(
                        PackageNodeDTO.builder()
                                .name("org.hjug.cbc")
                                .id(HierarchyBuilder.clusterId("org.hjug.cbc"))
                                .nodeId(cbcNodeId)
                                .path("src/main/java/org/hjug/cbc/")
                                .needsBreakUp(true)
                                .build(),
                        PackageNodeDTO.builder()
                                .name("org.hjug.dsm")
                                .id(HierarchyBuilder.clusterId("org.hjug.dsm"))
                                .nodeId(dsmNodeId)
                                .path("src/main/java/org/hjug/dsm/")
                                .build()))
                .build();
        HierarchyDTO hierarchy = HierarchyDTO.builder()
                .packages(List.of(hjug))
                .edges(List.of(
                        EdgeDTO.builder()
                                .source(hjugNodeId)
                                .target(cbcNodeId)
                                .weight(1)
                                .markedForRemoval(false)
                                .build(),
                        EdgeDTO.builder()
                                .source(cbcNodeId)
                                .target(dsmNodeId)
                                .weight(2)
                                .markedForRemoval(true)
                                .build()))
                .build();

        // When: the package graph DOT is built
        String dot = NestedDotBuilder.buildPackageDot(hierarchy, "");

        // Then: the graph is flat — every participating package renders once as an
        // ellipse (no subgraphs, clusters, or anchors), break-up packages fill red,
        // and the edges carry only the relationship weights with removals in red
        String expected = "strict digraph G {\n"
                + hjugNodeId
                + " [label=\"org.hjug\" shape=ellipse];\n"
                + cbcNodeId
                + " [label=\"org.hjug.cbc\" shape=ellipse color=red style=filled];\n"
                + dsmNodeId
                + " [label=\"org.hjug.dsm\" shape=ellipse];\n"
                + hjugNodeId
                + " -> " + cbcNodeId + " [ label = \"1\" weight = \"1\" ];\n"
                + cbcNodeId
                + " -> " + dsmNodeId + " [ label = \"2\" weight = \"2\" color = \"red\" ];\n"
                + "}";
        assertEquals(expected, dot);
    }

    @DisplayName("package graph hyperlinks nodes with a source path when the repository URL resolves to http(s)")
    @Test
    void buildPackageDot_hyperlinksPackagesWithPaths() {
        // Given: two participating packages, one with a source path and one without
        String cbcNodeId = HtmlReport.renderSafePackageNodeId("org.hjug.cbc");
        String dsmNodeId = HtmlReport.renderSafePackageNodeId("org.hjug.dsm");
        PackageNodeDTO hjug = PackageNodeDTO.builder()
                .name("org.hjug")
                .id(HierarchyBuilder.clusterId("org.hjug"))
                .renderAsNode(false)
                .children(List.of(
                        PackageNodeDTO.builder()
                                .name("org.hjug.cbc")
                                .id(HierarchyBuilder.clusterId("org.hjug.cbc"))
                                .nodeId(cbcNodeId)
                                .path("src/main/java/org/hjug/cbc/")
                                .build(),
                        PackageNodeDTO.builder()
                                .name("org.hjug.dsm")
                                .id(HierarchyBuilder.clusterId("org.hjug.dsm"))
                                .nodeId(dsmNodeId)
                                .path("")
                                .build()))
                .build();
        HierarchyDTO hierarchy = HierarchyDTO.builder()
                .packages(List.of(hjug))
                .edges(List.of(EdgeDTO.builder()
                        .source(cbcNodeId)
                        .target(dsmNodeId)
                        .weight(1)
                        .markedForRemoval(false)
                        .build()))
                .build();

        // When: rendered with a valid repository URL
        String withRepoUrl = NestedDotBuilder.buildPackageDot(hierarchy, "https://github.com/x/y/blob/main/");

        // Then: the package with a path is hyperlinked in a new tab; the pathless one is not
        assertTrue(withRepoUrl.contains(
                "URL=\"https://github.com/x/y/blob/main/src/main/java/org/hjug/cbc/\" target=\"_blank\""));
        assertFalse(
                withRepoUrl.contains("URL=\"https://github.com/x/y/blob/main/\""), "pathless packages carry no link");

        // And When: rendered without a repository URL or with a non-http(s) scheme
        String noRepoUrl = NestedDotBuilder.buildPackageDot(hierarchy, "");
        assertFalse(noRepoUrl.contains("URL="), "no hyperlink without a repository URL");
        String javascriptUrl = NestedDotBuilder.buildPackageDot(hierarchy, "javascript:alert(1)/");
        assertFalse(javascriptUrl.contains("URL="), "non-http(s) repository URLs must not become links");
    }

    @DisplayName("package graph renders packages without edges and empty/null hierarchies as the bare skeleton")
    @Test
    void buildPackageDot_rendersEmptyHierarchyAsBareSkeleton() {
        // Given: an empty and a null hierarchy
        String bare = "strict digraph G {\n}";
        assertEquals(
                bare,
                NestedDotBuilder.buildPackageDot(
                        HierarchyDTO.builder()
                                .packages(List.of())
                                .edges(List.of())
                                .build(),
                        ""));
        assertEquals(bare, NestedDotBuilder.buildPackageDot(null, ""));
    }

    // hyperlinks
    // ------------------------------------------------------------------

    @DisplayName("hyperlinks are emitted only when requested and only for classes with a path")
    @Test
    void buildNestedDot_emitsHyperlinksOnlyWhenRequestedAndPathPresent() {
        // Given: two classes in the default package, one with a source path and one without
        PackageNodeDTO defaultPkg = PackageNodeDTO.builder()
                .name(HierarchyBuilder.DEFAULT_PACKAGE)
                .id(HierarchyBuilder.clusterId(HierarchyBuilder.DEFAULT_PACKAGE))
                .classes(List.of(
                        classNode("A", "A", "A", "/src/main/java/A.java", false), classNode("B", "B", "B", "", false)))
                .build();
        HierarchyDTO hierarchy = HierarchyDTO.builder()
                .packages(List.of(defaultPkg))
                .edges(List.of())
                .build();

        // When: rendered with hyperlinks enabled
        String withLinks = NestedDotBuilder.buildNestedDot(hierarchy, "https://github.com/x/y/blob", true);

        // Then: only the class with a path carries the URL attribute
        String expectedWithLinks = "strict digraph G {\n"
                + "subgraph " + HierarchyBuilder.clusterId(HierarchyBuilder.DEFAULT_PACKAGE) + " {\n"
                + "label=\"(default package)\";\n"
                + "style=filled fillcolor=white;\n"
                + "A [URL=\"https://github.com/x/y/blob/src/main/java/A.java\" target=\"_blank\" shape=ellipse];\n"
                + "B [ shape=ellipse];\n"
                + "}\n"
                + "}";
        assertEquals(expectedWithLinks, withLinks);

        // And When: rendered with hyperlinks disabled
        String withoutLinks = NestedDotBuilder.buildNestedDot(hierarchy, "https://github.com/x/y/blob", false);

        // Then: no class carries a URL attribute
        String expectedWithoutLinks = "strict digraph G {\n"
                + "subgraph " + HierarchyBuilder.clusterId(HierarchyBuilder.DEFAULT_PACKAGE) + " {\n"
                + "label=\"(default package)\";\n"
                + "style=filled fillcolor=white;\n"
                + "A [ shape=ellipse];\n"
                + "B [ shape=ellipse];\n"
                + "}\n"
                + "}";
        assertEquals(expectedWithoutLinks, withoutLinks);
    }

    // ------------------------------------------------------------------
    // escaping and edge format
    // ------------------------------------------------------------------

    @DisplayName("package names and class labels are DOT-quoted; $ stays literal")
    @Test
    void buildNestedDot_escapesLabelsAndPackageNames() {
        // Given: a package name with quotes and a backslash plus labels carrying a $-separator
        // and a real line break
        PackageNodeDTO pkg = PackageNodeDTO.builder()
                .name("com.\"weird\"\\name")
                .id(HierarchyBuilder.clusterId("com.\"weird\"\\name"))
                .classes(List.of(
                        classNode("Widget.<anonymous>", "Widget_anonymous", "Widget$anonymous", "", false),
                        classNode("Anon", "Anon", "line1\nline2", "", false)))
                .build();

        // When: the hierarchy is rendered as nested DOT
        String dot = NestedDotBuilder.buildNestedDot(
                HierarchyDTO.builder().packages(List.of(pkg)).edges(List.of()).build(), "", true);

        // Then: quotes and backslashes are escaped, line breaks become \n, and $ stays literal
        String expected = "strict digraph G {\n"
                + "subgraph " + HierarchyBuilder.clusterId("com.\"weird\"\\name") + " {\n"
                + "label=\"com.\\\"weird\\\"\\\\name\";\n"
                + "style=filled fillcolor=white;\n"
                + "Widget_anonymous [ label=\"Widget$anonymous\" shape=ellipse];\n"
                + "Anon [ label=\"line1\\nline2\" shape=ellipse];\n"
                + "}\n"
                + "}";
        assertEquals(expected, dot);
    }

    @DisplayName("edges keep the emitters' exact format, red only when marked for removal")
    @Test
    void buildNestedDot_rendersEdgesWithWeightsAndRedForRemovals() {
        // Given: a marked and an unmarked edge between two default-package classes
        PackageNodeDTO defaultPkg = PackageNodeDTO.builder()
                .name(HierarchyBuilder.DEFAULT_PACKAGE)
                .id(HierarchyBuilder.clusterId(HierarchyBuilder.DEFAULT_PACKAGE))
                .classes(List.of(classNode("A", "A", "A", "", false), classNode("B", "B", "B", "", false)))
                .build();
        HierarchyDTO hierarchy = HierarchyDTO.builder()
                .packages(List.of(defaultPkg))
                .edges(List.of(
                        EdgeDTO.builder()
                                .source("A")
                                .target("B")
                                .weight(2)
                                .markedForRemoval(true)
                                .build(),
                        EdgeDTO.builder()
                                .source("B")
                                .target("A")
                                .weight(1)
                                .markedForRemoval(false)
                                .build()))
                .build();

        // When: the hierarchy is rendered as nested DOT
        String dot = NestedDotBuilder.buildNestedDot(hierarchy, "", true);

        // Then: the marked edge is red and the unmarked edge is not
        String expected = "strict digraph G {\n"
                + "subgraph " + HierarchyBuilder.clusterId(HierarchyBuilder.DEFAULT_PACKAGE) + " {\n"
                + "label=\"(default package)\";\n"
                + "style=filled fillcolor=white;\n"
                + "A [ shape=ellipse];\n"
                + "B [ shape=ellipse];\n"
                + "}\n"
                + "A -> B [ label = \"2\" weight = \"2\" color = \"red\" ];\n"
                + "B -> A [ label = \"1\" weight = \"1\" ];\n"
                + "}";
        assertEquals(expected, dot);
    }

    @DisplayName("child clusters declare an explicit white fill inside a pink parent cluster")
    @Test
    void buildNestedDot_fillsChildClustersWhiteInsidePinkParents() {
        // Given: vizdom propagates a styled cluster's fill to its nested cluster rects, so a
        // break-up package's children would otherwise inherit the pink; the hierarchy mirrors
        // the reported shape — org.hjug.graphbuilder flagged with three child packages
        PackageNodeDTO metrics = PackageNodeDTO.builder()
                .name("org.hjug.graphbuilder.metrics")
                .id(HierarchyBuilder.clusterId("org.hjug.graphbuilder.metrics"))
                .classes(List.of(classNode(
                        "org.hjug.graphbuilder.metrics.DisharmonyDetector",
                        "DisharmonyDetector",
                        "DisharmonyDetector",
                        "",
                        false)))
                .build();
        PackageNodeDTO visitor = PackageNodeDTO.builder()
                .name("org.hjug.graphbuilder.visitor")
                .id(HierarchyBuilder.clusterId("org.hjug.graphbuilder.visitor"))
                .classes(List.of(classNode(
                        "org.hjug.graphbuilder.visitor.GraphVisitor", "GraphVisitor", "GraphVisitor", "", false)))
                .build();
        PackageNodeDTO graphbuilder = PackageNodeDTO.builder()
                .name("org.hjug.graphbuilder")
                .id(HierarchyBuilder.clusterId("org.hjug.graphbuilder"))
                .needsBreakUp(true)
                .children(List.of(metrics, visitor))
                .build();

        // When: the hierarchy is rendered as nested DOT
        String dot = NestedDotBuilder.buildNestedDot(
                HierarchyDTO.builder()
                        .packages(List.of(PackageNodeDTO.builder()
                                .name("org.hjug")
                                .id(HierarchyBuilder.clusterId("org.hjug"))
                                .children(List.of(graphbuilder))
                                .build()))
                        .edges(List.of(EdgeDTO.builder()
                                .source("DisharmonyDetector")
                                .target("GraphVisitor")
                                .weight(1)
                                .markedForRemoval(false)
                                .build()))
                        .build(),
                "",
                true);

        // Then: the flagged cluster fills pink while its child clusters (and the neutral
        // ancestors) declare an explicit white fill that overrides the inheritance
        List<String> lines = List.of(dot.split("\n"));
        int pink = 0;
        int white = 0;
        for (String line : lines) {
            if (line.equals("style=filled fillcolor=pink;")) pink++;
            if (line.equals("style=filled fillcolor=white;")) white++;
        }
        assertEquals(1, pink, "only the flagged package cluster fills pink");
        assertEquals(3, white, "the two children and the neutral ancestor fill white");
        assertEquals("style=filled fillcolor=white;", lines.get(lines.indexOf("label=\"org.hjug\";") + 1));
        assertEquals("style=filled fillcolor=pink;", lines.get(lines.indexOf("label=\"org.hjug.graphbuilder\";") + 1));
        assertEquals(
                "style=filled fillcolor=white;",
                lines.get(lines.indexOf("label=\"org.hjug.graphbuilder.metrics\";") + 1));
        assertEquals(
                "style=filled fillcolor=white;",
                lines.get(lines.indexOf("label=\"org.hjug.graphbuilder.visitor\";") + 1));
    }

    // ------------------------------------------------------------------
    // degenerate hierarchies
    // ------------------------------------------------------------------

    @DisplayName("empty and null hierarchies render a bare digraph header and footer")
    @Test
    void buildNestedDot_rendersEmptyHierarchyAsBareDigraph() {
        // Given: an empty hierarchy and a null hierarchy
        HierarchyDTO empty =
                HierarchyDTO.builder().packages(List.of()).edges(List.of()).build();

        // When: both are rendered
        String emptyDot = NestedDotBuilder.buildNestedDot(empty, "", true);
        String nullDot = NestedDotBuilder.buildNestedDot(null, "", true);

        // Then: both produce the bare digraph skeleton
        assertEquals("strict digraph G {\n}", emptyDot);
        assertEquals("strict digraph G {\n}", nullDot);
    }
}

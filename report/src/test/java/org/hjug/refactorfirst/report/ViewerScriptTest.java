package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the bundled viewer's graph contract: refactor-first.json carries the nested hierarchy
 * instead of raw DOT, so the viewer must synthesize the DOT client-side — mirroring
 * {@link NestedDotBuilder} on the Java side and lib/hierarchy-dot.js on the report page — and
 * keep exposing the synthesized graphs through the {@code window.*_dot} globals the Mustache
 * template's popup buttons reference.
 *
 * <p>The synthesis itself is pinned byte-for-byte by {@code NestedDotBuilderTest} (Java) and
 * {@code tests/unit/hierarchy-dot.test.js} (page repo); these assertions pin that the viewer
 * script wires the hierarchy payloads into that logic and no longer reads the removed
 * {@code dot} fields.
 */
class ViewerScriptTest {

    /** Loads the bundled viewer script from the report resources. */
    private String loadViewer() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/viewer/index.html")) {
            assertNotNull(is, "viewer/index.html must be bundled in the report resources");
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @DisplayName("viewer reads the hierarchy payloads, never the removed dot fields")
    @Test
    void viewer_readsHierarchyPayloadsNotRawDot() throws Exception {
        // Given: the bundled viewer script
        String viewer = loadViewer();

        // Then: every graph consumes its hierarchy payload...
        assertTrue(viewer.contains("data.classMap.hierarchy"), "class map must read .hierarchy");
        assertTrue(viewer.contains("data.packageMap.hierarchy"), "package map must read .hierarchy");
        assertTrue(viewer.contains("largestCycle.hierarchy"), "cycle map must read .hierarchy");

        // ...and no residual raw-dot field reads remain (the threshold flags keep their
        // dotThreshold* names, so the assertions match the field access shapes, not substrings)
        assertFalse(viewer.contains(".dot)"), "no payload field may end in .dot");
        assertFalse(viewer.contains(".dot;"), "no payload field may be read as .dot");
    }

    @DisplayName("viewer carries the nested-dot synthesis mirroring NestedDotBuilder")
    @Test
    void viewer_definesHierarchySynthesis() throws Exception {
        // Given: the bundled viewer script
        String viewer = loadViewer();

        // Then: it inlines the synthesis with the shared builder's visual contract:
        // package clusters, ellipse classes, rectangle package nodes, pink breakup
        // packages, and the red removal markers
        assertTrue(viewer.contains("function hierarchyToDot"), "must define hierarchyToDot");
        assertTrue(viewer.contains("function packageHierarchyToDot"), "must define the package-mode synthesis");
        assertTrue(viewer.contains("subgraph "), "packages must nest as clusters");
        assertTrue(viewer.contains("shape=ellipse"), "classes must render as ellipses");
        assertTrue(viewer.contains("shape=rectangle"), "leaf packages must render as rectangles");
        assertTrue(viewer.contains("fillcolor=pink"), "break-up clusters must fill pink");
        assertTrue(
                viewer.contains("fillcolor=white"),
                "unflagged clusters must fill white explicitly so children of a pink cluster never inherit the fill");
        assertTrue(viewer.contains("color=red style=filled"), "marked classes must stay red");
        assertTrue(viewer.contains("color = \"red\""), "marked relationships must stay red");
        assertTrue(viewer.contains("strict digraph G {"), "synthesis must emit the digraph header");
        // package-mode contract: the flat relationship graph — one ellipse per
        // package, edges only, red break-up fills, no clusters or anchors
        assertTrue(viewer.contains("shape=ellipse"), "package nodes must render as ellipses");
        assertTrue(
                viewer.contains("if (pkg.needsBreakUp) line += ' color=red style=filled'"),
                "break-up packages must fill red");
        assertFalse(viewer.contains("compound=true"), "the flat package map has no compound cluster edges");
        assertFalse(viewer.contains("shape=point style=invis"), "the flat package map has no invisible anchors");
        assertFalse(viewer.contains("@hpcc-js/wasm-graphviz"), "the package graph renders through vizdom");
    }

    @DisplayName("viewer escapes names and labels with the DOT-quoted rules")
    @Test
    void viewer_escapesDotQuotedValues() throws Exception {
        // Given: the bundled viewer script
        String viewer = loadViewer();

        // Then: the escaping chain mirrors the Java escapeDotQuoted
        assertTrue(viewer.contains("function escapeDotQuoted"), "must define escapeDotQuoted");
        assertTrue(viewer.contains("replace(/\\\\/g, '\\\\\\\\')"), "backslashes must be escaped for DOT strings");
        assertTrue(viewer.contains("replace(/\"/g, '\\\\\"')"), "quotes must be escaped for DOT strings");
    }

    @DisplayName("viewer renders the package graph through vizdom like the class and cycle maps")
    @Test
    void viewer_rendersPackageGraphThroughVizdom() throws Exception {
        // Given: the bundled viewer script
        String viewer = loadViewer();

        // Then: all three graphs render through the single vizdom DotParser path,
        // the package graph using the flat package-mode synthesis
        assertTrue(
                viewer.contains("renderGraph('packageGraph', data.packageMap.hierarchy, packageHierarchyToDot)"),
                "the package graph must render via the shared vizdom renderGraph path");
        assertTrue(
                viewer.contains("renderGraph('classGraph', data.classMap.hierarchy, hierarchyToDot)"),
                "the class graph must render via the shared vizdom renderGraph path");
        assertFalse(viewer.contains("wasm-graphviz"), "the retired hpcc-js engine must be fully removed");
    }

    @DisplayName("popup graph builders drop package cluster nodes but keep the classes inside them")
    @Test
    void popupBuilders_dropClusterNodes() throws Exception {
        // Given: the bundled viewer script
        String viewer = loadViewer();

        // Then: both popup builders (the 2D sugiyama/sigma renderGraph and the 3D
        // createForceGraph) drop the cluster nodes graphlib-dot creates per named
        // subgraph — packages are noise in the popups — while keeping every class
        // node the clusters contained, including class ids like cluster_Helper,
        // and skip edges touching dropped clusters
        int nodeFilters = viewer.split("graphlibGraph\\.children\\(node\\)\\.length > 0", -1).length - 1;
        assertEquals(2, nodeFilters, "both the 2D and 3D popup builders must filter subgraph nodes by structure");
        assertFalse(
                viewer.contains("startsWith('cluster_')"),
                "real class nodes such as cluster_Helper must keep their incident edges");
        int edgeGuards = viewer.split("droppedClusters.has", -1).length - 1;
        assertEquals(4, edgeGuards, "both popup builders must also guard edges against dropped clusters");
    }

    @DisplayName("viewer hyperlinks every graph's nodes through the project repository URL")
    @Test
    void viewer_hyperlinksAllGraphNodes() throws Exception {
        // Given: the bundled viewer script
        String viewer = loadViewer();

        // Then: both synthesis modes accept a repository URL and emit http(s)-gated
        // new-tab hyperlinks on nodes with a source path, wired from project.repoUrl
        // for all three graphs and the popup globals
        assertTrue(viewer.contains("function httpUrl"), "the viewer must gate hyperlinks to http(s)");
        assertTrue(
                viewer.contains("repoUrl: data.project && data.project.repoUrl"),
                "the viewer must wire project.repoUrl");
        assertTrue(viewer.contains("URL=\""), "the synthesis must emit URL attributes");
        assertTrue(viewer.contains("target=\"_blank\""), "hyperlinks must open a new browser tab");
        // the inline vizdom render restores the anchor target vizdom drops on every anchor
        // (a graph hyperlinks many nodes, so the replacement must be global)
        assertTrue(
                viewer.contains("svg = svg.replace(/<a href=/g, '<a target=\"_blank\" href=')"),
                "the viewer's inline render must restore the new-tab anchor target on every anchor");
        assertFalse(
                viewer.contains("svg = svg.replace('<a href='"),
                "a non-global anchor replacement would only restore the first anchor's target");
    }

    @DisplayName("viewer keeps exposing synthesized DOT through the window globals")
    @Test
    void viewer_exposesSynthesizedDotsAsWindowGlobals() throws Exception {
        // Given: the bundled viewer script
        String viewer = loadViewer();

        // Then: the template's popup buttons keep finding their graphs in the same
        // window.*_dot globals, now holding the synthesized nested DOT
        assertTrue(viewer.contains("window.classGraph_dot ="), "class map global must be assigned");
        assertTrue(viewer.contains("window.packageGraph_dot ="), "package map global must be assigned");
        assertTrue(viewer.contains("_dot']"), "cycle global must be assigned by identifier");
        assertTrue(
                viewer.contains("hierarchyToDot(data.classMap.hierarchy, synthesisOptions)"),
                "globals must hold synthesized DOT");
    }
}

package org.hjug.refactorfirst.report;

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
        // package-mode contract: compound cluster edges via invisible anchors
        assertTrue(viewer.contains("compound=true"), "package graph must enable compound cluster edges");
        assertTrue(
                viewer.contains("shape=point style=invis"), "edge-referenced parent packages need invisible anchors");
        assertTrue(viewer.contains("ltail = \""), "edges leaving cluster packages must clip via ltail");
        assertTrue(viewer.contains("lhead = \""), "edges entering cluster packages must clip via lhead");
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

    @DisplayName("viewer renders the package graph with the pinned hpcc-js wasm-graphviz module")
    @Test
    void viewer_rendersPackageGraphWithGraphviz() throws Exception {
        // Given: the bundled viewer script
        String viewer = loadViewer();

        // Then: the package graph renders via the pinned @hpcc-js/wasm-graphviz module —
        // the only engine that honors the compound ltail/lhead cluster edges — while
        // the class and cycle graphs stay on vizdom
        assertTrue(
                viewer.contains("https://cdn.jsdelivr.net/npm/@hpcc-js/wasm-graphviz@1.7.0/dist/index.js"),
                "the viewer must import Graphviz from the pinned wasm-graphviz URL");
        assertTrue(viewer.contains("Graphviz.load()"), "the Graphviz module must be initialized before rendering");
        assertTrue(viewer.contains("graphviz.dot("), "the package graph must render through graphviz.dot");
        assertTrue(
                viewer.contains("packageHierarchyToDot(data.packageMap.hierarchy)"),
                "the package graph must use the package-mode synthesis");
        assertTrue(viewer.contains("vizdom_ts.min.js"), "the class and cycle graphs must keep rendering via vizdom");
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
        assertTrue(viewer.contains("hierarchyToDot(data.classMap.hierarchy)"), "globals must hold synthesized DOT");
    }
}

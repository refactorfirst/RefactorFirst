package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import org.hjug.cbc.CycleNode;
import org.hjug.cbc.RankedCycle;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.VertexInfo;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Test;

/**
 * Pins the interactive report's DOT/id statics (the parity bridge shared with
 * {@link HierarchyBuilder}) after the P4 pipeline migration. Deletions vs the
 * pre-P4 suite are mapped in the migration PR: printTitle/generateDotImage/popup
 * script tests moved to the template + rf-graphs.js assets (Node tests); the
 * template-literal DOT wrappers were replaced by the raw DOT builders the rf-data
 * payload embeds (synthesis goldens stay in {@link NestedDotBuilderTest}).
 */
class HtmlReportTest {

    private final HtmlReport mavenReport = new HtmlReport();

    @Test
    void testGetOutputName() {
        assertEquals("refactor-first-report", mavenReport.getOutputName());
    }

    @Test
    void getName() {
        assertEquals("Refactor First Report", mavenReport.getName(Locale.getDefault()));
    }

    @Test
    void getDescription() {
        assertEquals(
                "Ranks the disharmonies in a codebase.  The classes that should be refactored first "
                        + " have the highest priority values.",
                mavenReport.getDescription(Locale.getDefault()));
    }

    /** Verifies cycle DOT nests classes in package clusters, keeping source links and edge weights. */
    @Test
    void buildClassCycleDot() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex(VertexInfo.forName("A"));
        classGraph.addVertex(VertexInfo.forName("B"));
        classGraph.addVertex(VertexInfo.forName("C"));
        classGraph.addEdge(VertexInfo.forName("A"), VertexInfo.forName("B"));
        classGraph.addEdge(VertexInfo.forName("B"), VertexInfo.forName("C"));
        classGraph.addEdge(VertexInfo.forName("C"), VertexInfo.forName("A"));
        classGraph.setEdgeWeight(VertexInfo.forName("A"), VertexInfo.forName("B"), 2);

        String cycleName = "Test";
        List<CycleNode> cycleNodes = new ArrayList<>();
        RankedCycle rankedCycle = new RankedCycle(
                cycleName,
                classGraph.vertexSet().stream()
                        .map(VertexInfo::getName)
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)),
                classGraph.edgeSet(),
                cycleNodes);

        HtmlReport htmlReport = new HtmlReport();
        CodebaseGraphDTO dto = mock(CodebaseGraphDTO.class);
        HashMap<String, String> map = new HashMap<>();
        map.put("A", "/src/main/java/org/hjug/refactorfirst/A.java");
        map.put("B", "/src/main/java/org/hjug/refactorfirst/B.java");
        map.put("C", "/src/main/java/org/hjug/refactorfirst/C.java");
        when(dto.getClassToSourceFilePathMapping()).thenReturn(map);
        String repoUrl = "https://github.com/refactorfirst/RefactorFirst/blob";
        String dot = htmlReport.buildRawClassCycleDot(classGraph, rankedCycle, repoUrl, dto);
        // The packageless classes nest inside the synthetic default-package cluster; edges are
        // declared after the clusters so the nodes keep their cluster scope. The raw DOT the
        // rf-data payload embeds carries no template-literal wrapping.
        String expectedDot = "strict digraph G {\n"
                + "subgraph " + HierarchyBuilder.clusterId(HierarchyBuilder.DEFAULT_PACKAGE) + " {\n"
                + "label=\"(default package)\";\n"
                + "style=filled fillcolor=white;\n"
                + "class_A [ label=\"A\" shape=ellipse URL=\"https://github.com/refactorfirst/RefactorFirst/blob/src/main/java/org/hjug/refactorfirst/A.java\" target=\"_blank\"];\n"
                + "class_B [ label=\"B\" shape=ellipse URL=\"https://github.com/refactorfirst/RefactorFirst/blob/src/main/java/org/hjug/refactorfirst/B.java\" target=\"_blank\"];\n"
                + "class_C [ label=\"C\" shape=ellipse URL=\"https://github.com/refactorfirst/RefactorFirst/blob/src/main/java/org/hjug/refactorfirst/C.java\" target=\"_blank\"];\n"
                + "}\n"
                + "class_A -> class_B [ label = \"2\" weight = \"2\" ];\n"
                + "class_B -> class_C [ label = \"1\" weight = \"1\" ];\n"
                + "class_C -> class_A [ label = \"1\" weight = \"1\" ];\n"
                + "}";

        assertEquals(expectedDot, dot);
    }

    /**
     * {@code isSinkAnonymousOrSyntheticVertex} is the render-time noise filter: it suppresses only
     * the <em>sink</em> subset of anonymous/synthetic vertices (those with no outgoing edges), to
     * keep the Class/Cycle Map DOT graph readable. Active vertices (with outgoing edges) render.
     */
    @Test
    void isSinkAnonymousOrSyntheticVertex_truthTable() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex(VertexInfo.forName("com.foo.Outer$1"));
        classGraph.addVertex(VertexInfo.forName("com.foo.Outer$"));
        classGraph.addVertex(VertexInfo.forName("com.foo.Outer$2"));
        classGraph.addVertex(VertexInfo.forName("com.foo.Outer$Inner"));
        classGraph.addVertex(VertexInfo.forName("<anonymous>"));
        classGraph.addVertex(VertexInfo.forName("<anon2>"));
        classGraph.addVertex(VertexInfo.forName("com.foo.Named"));
        // give the "active" vertices an outgoing edge
        classGraph.addVertex(VertexInfo.forName("com.foo.Other"));
        classGraph.addEdge(VertexInfo.forName("com.foo.Outer$1"), VertexInfo.forName("com.foo.Other"));
        classGraph.addEdge(VertexInfo.forName("<anonymous>"), VertexInfo.forName("com.foo.Other"));
        classGraph.addEdge(VertexInfo.forName("com.foo.Outer$Inner"), VertexInfo.forName("com.foo.Other"));
        classGraph.addEdge(VertexInfo.forName("com.foo.Named"), VertexInfo.forName("com.foo.Other"));

        // sinks (outDegreeOf == 0) and anonymous/synthetic -> suppressed
        assertTrue(HtmlReport.isSinkAnonymousOrSyntheticVertex(classGraph, "com.foo.Outer$2"), "Outer$2 numeric sink");
        assertTrue(
                HtmlReport.isSinkAnonymousOrSyntheticVertex(classGraph, "com.foo.Outer$"),
                "Outer$ trailing-dollar sink");
        assertTrue(HtmlReport.isSinkAnonymousOrSyntheticVertex(classGraph, "<anon2>"), "Kotlin <anonymous> sink");

        // active (has outgoing edges) anonymous/synthetic -> NOT suppressed
        assertFalse(
                HtmlReport.isSinkAnonymousOrSyntheticVertex(classGraph, "com.foo.Outer$1"),
                "Outer$1 with outgoing edge");
        assertFalse(
                HtmlReport.isSinkAnonymousOrSyntheticVertex(classGraph, "<anonymous>"),
                "<anonymous> with outgoing edge");

        // named inner class never suppressed regardless of edges
        assertFalse(
                HtmlReport.isSinkAnonymousOrSyntheticVertex(classGraph, "com.foo.Outer$Inner"), "Outer$Inner never");
        assertFalse(
                HtmlReport.isSinkAnonymousOrSyntheticVertex(classGraph, "com.foo.Named"), "plain named class never");
    }

    /**
     * Renders a class-cycle DOT graph that contains Java {@code Outer$1}/{@code Outer$2} and
     * Kotlin {@code "<anonymous>"} vertices. Active anonymous/synthetic vertices render (with
     * reversible ASCII DOT ids and the original {@code $} visible in the label; Kotlin
     * anonymous labels use the enclosing source file's base name). Sink-only anonymous/synthetic
     * vertices are suppressed. The DOT body must not contain a raw {@code <} from an anonymous vertex.
     */
    @Test
    void buildClassCycleDot_rendersAnonymousAndSyntheticVertices() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex(VertexInfo.forName("com.example.Outer"));
        classGraph.addVertex(VertexInfo.forName("com.example.Outer$1")); // active (outgoing edge)
        classGraph.addVertex(VertexInfo.forName("com.example.Outer$2")); // sink (no outgoing edge)
        classGraph.addVertex(VertexInfo.forName("com.example.Other"));
        classGraph.addVertex(VertexInfo.forName("<anonymous>")); // active (outgoing edge)
        classGraph.addVertex(VertexInfo.forName("<anonSink>")); // sink
        // wire a cycle: Outer -> Outer$1 -> Other -> Outer; anonymous -> Outer; Outer -> anonymous
        classGraph.addEdge(VertexInfo.forName("com.example.Outer"), VertexInfo.forName("com.example.Outer$1"));
        classGraph.addEdge(VertexInfo.forName("com.example.Outer$1"), VertexInfo.forName("com.example.Other"));
        classGraph.addEdge(VertexInfo.forName("com.example.Other"), VertexInfo.forName("com.example.Outer"));
        classGraph.addEdge(VertexInfo.forName("com.example.Outer"), VertexInfo.forName("<anonymous>"));
        classGraph.addEdge(VertexInfo.forName("<anonymous>"), VertexInfo.forName("com.example.Outer"));

        List<CycleNode> cycleNodes = new ArrayList<>();
        RankedCycle rankedCycle = new RankedCycle(
                "Cycle",
                classGraph.vertexSet().stream()
                        .map(VertexInfo::getName)
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)),
                classGraph.edgeSet(),
                cycleNodes);

        HtmlReport htmlReport = new HtmlReport();
        CodebaseGraphDTO dto = mock(CodebaseGraphDTO.class);
        HashMap<String, String> map = new HashMap<>();
        map.put("com.example.Outer", "/src/main/java/com/example/Outer.java");
        map.put("com.example.Outer$1", "/src/main/java/com/example/Outer.java");
        map.put("com.example.Outer$2", "/src/main/java/com/example/Outer.java");
        map.put("com.example.Other", "/src/main/java/com/example/Other.java");
        map.put("<anonymous>", "/src/main/kotlin/com/example/Foo.kt");
        map.put("<anonSink>", "/src/main/kotlin/com/example/Bar.kt");
        when(dto.getClassToSourceFilePathMapping()).thenReturn(map);
        String repoUrl = "https://example.com/repo/blob";

        String dot = htmlReport.buildRawClassCycleDot(classGraph, rankedCycle, repoUrl, dto);

        // active Java anonymous Outer$1 renders with DOT id class_com_002eexample_002eOuter_00241 and a $-visible label
        assertTrue(
                dot.contains("class_com_002eexample_002eOuter_00241 ["),
                "Outer$1 must render with the _$-safe DOT id class_com_002eexample_002eOuter_00241");
        assertTrue(dot.contains("label=\"Outer$1\""), "Outer$1 label keeps its $ in the raw DOT");

        // sink Java anonymous Outer$2 is omitted
        assertFalse(dot.contains("class_com_002eexample_002eOuter_00242 ["), "sink Outer$2 must be suppressed");
        assertFalse(dot.contains("Outer$2"), "Outer$2 must not appear anywhere in the DOT");

        assertTrue(
                dot.contains("class__003canonymous_003e ["),
                "<anonymous> must render with the DOT id class__003canonymous_003e encoded from its original class name");
        // ...and the human-readable label uses $ as the enclosing separator
        assertTrue(dot.contains("label=\"Foo$anonymous\""), "<anonymous> label must be Foo$anonymous");

        // sink anonymous is omitted entirely (both as id and label)
        assertFalse(dot.contains("class__003canonSink_003e"), "sink <anonSink> DOT id must be suppressed");
        assertFalse(dot.contains("label=\"Bar\\$anonymous\""), "sink <anonSink> label must be suppressed");

        // DOT body must not contain a raw '<' char from an anonymous vertex. With the
        // enclosing-source-file-name rendering the anonymous vertex no longer contributes the
        // literal "<anonymous>" to the DOT. (Edges use "->" which contains '>'; we only assert
        // that no anonymous vertex line carries a raw '<' or a '<anon' encoding.)
        assertFalse(dot.contains("<anonymous"), "DOT must not contain the literal <anonymous after rendering");
        assertFalse(dot.contains("<anonSink>"), "DOT must not contain the suppressed <anonSink> literal");
        // no edge should reference a suppressed vertex id
        assertFalse(
                dot.contains("class_com_002eexample_002eOuter_00242 ->"), "no edge may start from suppressed Outer$2");
        assertFalse(dot.contains("-> class_com_002eexample_002eOuter_00242"), "no edge may end at suppressed Outer$2");
        assertFalse(
                dot.contains("-> " + HtmlReport.renderSafeNodeId("<anonSink>")),
                "no edge may end at suppressed <anonSink>");
    }

    /**
     * Renders an active Kotlin {@code "<anonymous>"} vertex whose source mapping points at a
     * Kotlin file. The DOT node id must encode the original anonymous FQN and the
     * source-derived label must read {@code DeveloperWASDControl$anonymous} (with {@code $}
     * escaped as {@code \$}). No literal {@code <}/{@code >} may appear in the DOT node id.
     */
    @Test
    void buildClassCycleDot_rendersKotlinAnonymousWithEnclosingSourceFileName() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex(VertexInfo.forName("dev.DeveloperWASDControl"));
        // OpenRewrite attributes Kotlin anonymous objects with an FQN whose trailing segment is
        // the literal "<anonymous>" but whose prefix carries the enclosing class, e.g.
        // "dev.DeveloperWASDControl.<anonymous>". The source-mapping key equals this full FQN.
        classGraph.addVertex(VertexInfo.forName("dev.DeveloperWASDControl.<anonymous>"));
        classGraph.addVertex(VertexInfo.forName("com.almasb.fxgl.app.GameApplication"));
        // anonymous depends on GameApplication; DeveloperWASDControl depends on anonymous -> cycle
        classGraph.addEdge(
                VertexInfo.forName("dev.DeveloperWASDControl.<anonymous>"),
                VertexInfo.forName("com.almasb.fxgl.app.GameApplication"));
        classGraph.addEdge(
                VertexInfo.forName("dev.DeveloperWASDControl"),
                VertexInfo.forName("dev.DeveloperWASDControl.<anonymous>"));
        classGraph.addEdge(
                VertexInfo.forName("com.almasb.fxgl.app.GameApplication"),
                VertexInfo.forName("dev.DeveloperWASDControl"));

        List<CycleNode> cycleNodes = new ArrayList<>();
        RankedCycle rankedCycle = new RankedCycle(
                "Cycle",
                classGraph.vertexSet().stream()
                        .map(VertexInfo::getName)
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)),
                classGraph.edgeSet(),
                cycleNodes);

        HtmlReport htmlReport = new HtmlReport();
        CodebaseGraphDTO dto = mock(CodebaseGraphDTO.class);
        HashMap<String, String> map = new HashMap<>();
        map.put("dev.DeveloperWASDControl", "/fxgl-samples/src/main/kotlin/dev/DeveloperWASDControl.kt");
        map.put("dev.DeveloperWASDControl.<anonymous>", "/fxgl-samples/src/main/kotlin/dev/DeveloperWASDControl.kt");
        map.put(
                "com.almasb.fxgl.app.GameApplication",
                "/fxgl-core/src/main/kotlin/com/almasb/fxgl/app/GameApplication.kt");
        when(dto.getClassToSourceFilePathMapping()).thenReturn(map);
        String repoUrl = "https://github.com/AlmasB/FXGL/blob/ca465c07fd109b9b59b9f7226478676ac068a0ba";

        String dot = htmlReport.buildRawClassCycleDot(classGraph, rankedCycle, repoUrl, dto);

        // DOT body must not contain the old lt_anonymous_gt encoding for the active anonymous vertex
        assertFalse(dot.contains("lt_anonymous_gt"), "must not use the lt_anonymous_gt encoding");

        // node id encodes the anonymous vertex identity
        assertTrue(
                dot.contains("class_dev_002eDeveloperWASDControl_002e_003canonymous_003e ["),
                "anonymous vertex must render with the DOT id class_dev_002eDeveloperWASDControl_002e_003canonymous_003e");

        // human-readable label uses $ as the enclosing-class separator
        assertTrue(
                dot.contains("label=\"DeveloperWASDControl$anonymous\""),
                "label must be DeveloperWASDControl$anonymous, was: " + dot);

        // no literal < or > in the DOT node id (the label is fine on its own line); verify the
        // node declaration line (not an edge line, which uses "->") carries no raw < or >.
        String anonLine = Arrays.stream(dot.split("\n"))
                .filter(l -> l.startsWith("class_dev_002eDeveloperWASDControl_002e_003canonymous_003e ["))
                .findFirst()
                .orElse("");
        assertFalse(anonLine.isEmpty(), "anonymous node declaration line must be present");
        assertFalse(anonLine.contains("<"), "anonymous DOT id line must not contain a raw '<'");
        assertFalse(anonLine.contains(">"), "anonymous DOT id line must not contain a raw '>'");
    }

    /** Classes in a package named cluster keep their distinct ids and incident edges. */
    @Test
    void buildClassCycleDot_keepsClassesInPackageNamedCluster() {
        // Given: two Helper classes in different packages, one in a package literally named
        // cluster, with distinct full-FQN identities
        Graph<VertexInfo, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex(VertexInfo.forName("cluster.Helper"));
        classGraph.addVertex(VertexInfo.forName("other.Helper"));
        classGraph.addEdge(VertexInfo.forName("cluster.Helper"), VertexInfo.forName("other.Helper"));
        classGraph.addEdge(VertexInfo.forName("other.Helper"), VertexInfo.forName("cluster.Helper"));

        List<CycleNode> cycleNodes = new ArrayList<>();
        RankedCycle rankedCycle = new RankedCycle(
                "HelperCycle",
                classGraph.vertexSet().stream()
                        .map(VertexInfo::getName)
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)),
                classGraph.edgeSet(),
                cycleNodes);

        HtmlReport htmlReport = new HtmlReport();
        CodebaseGraphDTO dto = mock(CodebaseGraphDTO.class);
        HashMap<String, String> map = new HashMap<>();
        map.put("cluster.Helper", "/src/main/java/cluster/Helper.java");
        map.put("other.Helper", "/src/main/java/other/Helper.java");
        when(dto.getClassToSourceFilePathMapping()).thenReturn(map);

        // When: the cycle DOT is built
        String dot = htmlReport.buildRawClassCycleDot(classGraph, rankedCycle, "https://github.com/x/y/blob", dto);

        // Then: both colliding Helpers render as distinct class nodes
        assertTrue(
                dot.contains("class_cluster_002eHelper ["),
                "the cluster.Helper class must render as the class node class_cluster_002eHelper: " + dot);
        assertTrue(
                dot.contains("class_other_002eHelper ["),
                "the other.Helper class must render as the class node class_other_002eHelper: " + dot);
    }

    /** Cycle names slugify into safe graph identifiers (rf-data keys / popup button data). */
    @Test
    void graphIdentifier_slugifiesUnsafeCycleNames() {
        // A leading digit or markup in the cycle name must never yield an identifier that
        // starts with a digit or carries the raw markup.
        String identifier = HtmlReport.graphIdentifier("1<cycle>");
        assertTrue(identifier.startsWith("graph_1_cycle_"), identifier);
        assertFalse(identifier.contains("<"), "markup must not survive the slug: " + identifier);

        // Empty names fall back to a non-empty, valid identifier
        assertEquals("graph_cycle_0", HtmlReport.graphIdentifier(""));
    }

    @Test
    void renderInteractivePage_rendersNoGitMessageInsteadOfAnalysisFailed() throws Exception {
        // Given: a base directory with no Git repository
        File baseDir = File.createTempFile("rf-no-git", "");
        baseDir.delete();
        baseDir.mkdirs();

        // When: the interactive page renders for a missing Git repository
        String html = new HtmlReport()
                .renderInteractivePage(
                        false, // showDetails
                        50, // edgeAnalysisCount
                        true, // analyzeCycles
                        true, // excludeTests
                        "src/test",
                        "NoGitProject",
                        "1.0.0",
                        baseDir);

        // Then: the no-Git message page reaches the caller instead of the analysis-failed throw
        assertTrue(html.contains("No Git repository found in project"), html);
        assertTrue(html.contains("NoGitProject"), html);
    }

    /** Verifies distinct package node IDs in the raw package-map DOT the rf-data payload embeds. */
    @Test
    void buildPackageGraphDot_usesDistinctIdsAndKeepsScriptContextForTheJsonLayer() {
        Graph<VertexInfo, DefaultWeightedEdge> graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        String dotted = "1.a.b";
        String underscored = "1.a_b\\name</script>";
        graph.addVertex(VertexInfo.forName(dotted));
        graph.addVertex(VertexInfo.forName(underscored));
        graph.addEdge(VertexInfo.forName(dotted), VertexInfo.forName(underscored));

        String dot = new HtmlReport().buildRawPackageGraphDot(graph, "", null);

        assertNotEquals(HtmlReport.renderSafePackageNodeId(dotted), HtmlReport.renderSafePackageNodeId(underscored));
        assertTrue(dot.contains(HtmlReport.renderSafePackageNodeId(dotted)));
        assertTrue(dot.contains(HtmlReport.renderSafePackageNodeId(underscored)));
        // The raw DOT keeps the label text; the rf-data JSON block escapes the script
        // terminator for the HTML context (pinned by DataEmbedderTest).
        assertTrue(dot.contains("</script>"));
        assertFalse(dot.contains("`"), "raw DOT carries no template-literal wrapping");
    }

    @Test
    void escapeDotQuoted_escapesQuotesBackslashesAndLineBreaks() {
        assertEquals("a\\\\b\\\"c\\nd", HtmlReport.escapeDotQuoted("a\\b\"c\nd"));
    }

    /**
     * Test that renderSafeNodeId retains the FQN even when the simple name is unique.
     */
    @Test
    void renderSafeNodeId_withGraphContext_encodesFqnWhenUnique() {
        HtmlReport htmlReport = new HtmlReport();

        // Single occurrence of "Pixel" still retains full identity
        String pixel = "com.almasb.fxgl.texture.Pixel";

        // Create a graph with only one Pixel class
        Graph<VertexInfo, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex(VertexInfo.forName("com.almasb.fxgl.texture.Pixel"));
        classGraph.addVertex(VertexInfo.forName("com.almasb.fxgl.texture.Texture"));

        String nodeId = htmlReport.renderSafeNodeId(pixel, classGraph);

        assertEquals(
                "class_com_002ealmasb_002efxgl_002etexture_002ePixel", nodeId, "Ids retain the full class identity");
    }

    /**
     * Test that renderSafeNodeId with graph context uses FQN when collision exists.
     */
    @Test
    void renderSafeNodeId_withGraphContext_usesFqnWhenCollision() {
        HtmlReport htmlReport = new HtmlReport();

        // Two Pixel classes - collision!
        String pixel1 = "com.almasb.fxgl.app.scene.Pixel";
        String pixel2 = "com.almasb.fxgl.texture.Pixel";

        // Create a graph with both Pixel classes
        Graph<VertexInfo, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex(VertexInfo.forName("com.almasb.fxgl.app.scene.Pixel"));
        classGraph.addVertex(VertexInfo.forName("com.almasb.fxgl.texture.Pixel"));
        classGraph.addVertex(VertexInfo.forName("com.almasb.fxgl.texture.Texture"));

        String nodeId1 = htmlReport.renderSafeNodeId("com.almasb.fxgl.app.scene.Pixel", classGraph);
        String nodeId2 = htmlReport.renderSafeNodeId("com.almasb.fxgl.texture.Pixel", classGraph);

        // Both retain their full class identities
        assertEquals("class_com_002ealmasb_002efxgl_002eapp_002escene_002ePixel", nodeId1);
        assertEquals("class_com_002ealmasb_002efxgl_002etexture_002ePixel", nodeId2);
        assertNotEquals(nodeId1, nodeId2);
    }

    /**
     * Test that renderSafeNodeId with graph context handles inner classes correctly.
     * Inner classes should use their full FQN since they contain $.
     */
    @Test
    void renderSafeNodeId_withGraphContext_innerClassUsesFqn() {
        HtmlReport htmlReport = new HtmlReport();

        Graph<VertexInfo, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex(VertexInfo.forName("com.example.Outer$Inner"));
        classGraph.addVertex(VertexInfo.forName("com.example.Outer"));

        String nodeId = htmlReport.renderSafeNodeId("com.example.Outer$Inner", classGraph);

        // Inner classes always use FQN-based ID (contains $)
        assertEquals("class_com_002eexample_002eOuter_0024Inner", nodeId);
    }

    /**
     * Test that renderSafeNodeId with graph context handles anonymous classes.
     * Anonymous classes should use their special encoding.
     */
    @Test
    void renderSafeNodeId_withGraphContext_anonymousUsesSpecialEncoding() {
        HtmlReport htmlReport = new HtmlReport();

        Graph<VertexInfo, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex(VertexInfo.forName("<anonymous>"));

        String nodeId = htmlReport.renderSafeNodeId("<anonymous>", classGraph);

        // Anonymous classes use reversible ASCII encoding
        assertEquals("class__003canonymous_003e", nodeId);
    }

    /**
     * Test that renderSafeNodeId with graph context handles enclosing class prefix for anonymous.
     */
    @Test
    void renderSafeNodeId_withGraphContext_anonymousWithPrefixUsesFqn() {
        HtmlReport htmlReport = new HtmlReport();

        Graph<VertexInfo, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex(VertexInfo.forName("dev.DeveloperWASDControl.<anonymous>"));

        String nodeId = htmlReport.renderSafeNodeId("dev.DeveloperWASDControl.<anonymous>", classGraph);

        // Anonymous with enclosing class prefix uses reversible full-FQN encoding
        assertEquals("class_dev_002eDeveloperWASDControl_002e_003canonymous_003e", nodeId);
    }

    /**
     * Test that renderSafeNodeId produces unique node IDs for classes with the same simple name
     * but in different packages (the FXGL Pixel collision case). Full names use reversible ASCII escaping.
     */
    @Test
    void renderSafeNodeId_uniqueNodeIdsForSameSimpleNameDifferentPackages() {
        HtmlReport htmlReport = new HtmlReport();

        // Two Pixel classes from different packages (FXGL case)
        String pixel1 = "com.almasb.fxgl.app.scene.Pixel";
        String pixel2 = "com.almasb.fxgl.texture.Pixel";

        String nodeId1 = htmlReport.renderSafeNodeId(pixel1);
        String nodeId2 = htmlReport.renderSafeNodeId(pixel2);

        // Node IDs should be unique (full FQN with reversible ASCII escaping)
        assertEquals("class_com_002ealmasb_002efxgl_002eapp_002escene_002ePixel", nodeId1);
        assertEquals("class_com_002ealmasb_002efxgl_002etexture_002ePixel", nodeId2);
        assertNotEquals(nodeId1, nodeId2, "Node IDs must be unique for different packages");

        // Labels should still be human-readable (simple name)
        assertEquals("Pixel", htmlReport.getClassName(pixel1));
        assertEquals("Pixel", htmlReport.getClassName(pixel2));
    }

    /**
     * Test that renderSafeNodeId with codebaseGraphDTO also produces unique node IDs
     * for non-anonymous classes with same simple name.
     */
    @Test
    void renderSafeNodeId_withDto_uniqueNodeIdsForSameSimpleNameDifferentPackages() {
        HtmlReport htmlReport = new HtmlReport();
        CodebaseGraphDTO dto = mock(CodebaseGraphDTO.class);
        HashMap<String, String> map = new HashMap<>();
        map.put("com.almasb.fxgl.app.scene.Pixel", "/src/main/kotlin/com/almasb/fxgl/app/scene/IntroScene.kt");
        map.put("com.almasb.fxgl.texture.Pixel", "/src/main/kotlin/com/almasb/fxgl/texture/Images.kt");
        when(dto.getClassToSourceFilePathMapping()).thenReturn(map);

        String pixel1 = "com.almasb.fxgl.app.scene.Pixel";
        String pixel2 = "com.almasb.fxgl.texture.Pixel";

        String nodeId1 = htmlReport.renderSafeNodeId(pixel1, dto);
        String nodeId2 = htmlReport.renderSafeNodeId(pixel2, dto);

        // Node IDs should be unique (full FQN with reversible ASCII escaping)
        assertEquals("class_com_002ealmasb_002efxgl_002eapp_002escene_002ePixel", nodeId1);
        assertEquals("class_com_002ealmasb_002efxgl_002etexture_002ePixel", nodeId2);
        assertNotEquals(nodeId1, nodeId2, "Node IDs must be unique for different packages");
    }

    /**
     * Test that renderSafeNodeId still handles inner classes correctly (dollar sign encoded reversibly).
     */
    @Test
    void renderSafeNodeId_innerClassProducesUniqueNodeId() {
        HtmlReport htmlReport = new HtmlReport();

        String innerClass = "com.example.Outer$Inner";
        String nodeId = htmlReport.renderSafeNodeId(innerClass);

        // Dollar sign is encoded without losing identity
        assertEquals("class_com_002eexample_002eOuter_0024Inner", nodeId);
    }

    /**
     * Test that renderSafeNodeId still handles Kotlin anonymous classes correctly
     * (the same identity with or without a source DTO).
     */
    @Test
    void renderSafeNodeId_kotlinAnonymousProducesValidNodeId() {
        HtmlReport htmlReport = new HtmlReport();

        // Without DTO - encode the full class identity
        String anon1 = "<anonymous>";
        String nodeId1 = htmlReport.renderSafeNodeId(anon1);
        assertEquals("class__003canonymous_003e", nodeId1);

        // With enclosing class prefix
        String anon2 = "dev.DeveloperWASDControl.<anonymous>";
        String nodeId2 = htmlReport.renderSafeNodeId(anon2);
        assertEquals("class_dev_002eDeveloperWASDControl_002e_003canonymous_003e", nodeId2);

        // Source mappings preserve identity and only affect the visible label
        CodebaseGraphDTO dto = mock(CodebaseGraphDTO.class);
        HashMap<String, String> map = new HashMap<>();
        map.put("dev.DeveloperWASDControl.<anonymous>", "/fxgl-samples/src/main/kotlin/dev/DeveloperWASDControl.kt");
        when(dto.getClassToSourceFilePathMapping()).thenReturn(map);

        String nodeId3 = htmlReport.renderSafeNodeId(anon2, dto);
        assertEquals("class_dev_002eDeveloperWASDControl_002e_003canonymous_003e", nodeId3);
    }
}

package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.*;
import org.hjug.cbc.CycleNode;
import org.hjug.cbc.RankedCycle;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Test;

class HtmlReportTest {

    private final HtmlReport mavenReport = new HtmlReport();

    @Test
    void testGetOutputName() {
        // This report will generate simple-report.html when invoked in a project with `mvn site`
        assertEquals("refactor-first-report", mavenReport.getOutputName());
    }

    @Test
    void getName() {
        // Name of the report when listed in the project-reports.html page of a project
        assertEquals("Refactor First Report", mavenReport.getName(Locale.getDefault()));
    }

    @Test
    void getDescription() {
        // Description of the report when listed in the project-reports.html page of a project
        assertEquals(
                "Ranks the disharmonies in a codebase.  The classes that should be refactored first "
                        + " have the highest priority values.",
                mavenReport.getDescription(Locale.getDefault()));
    }

    @Test
    void buildClassCycleDot() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("A");
        classGraph.addVertex("B");
        classGraph.addVertex("C");
        classGraph.addEdge("A", "B");
        classGraph.addEdge("B", "C");
        classGraph.addEdge("C", "A");
        classGraph.setEdgeWeight("A", "B", 2);

        String cycleName = "Test";
        List<CycleNode> cycleNodes = new ArrayList<>();
        RankedCycle rankedCycle = new RankedCycle(cycleName, classGraph.vertexSet(), classGraph.edgeSet(), cycleNodes);

        HtmlReport htmlReport = new HtmlReport();
        CodebaseGraphDTO dto = mock(CodebaseGraphDTO.class);
        HashMap<String, String> map = new HashMap<>();
        map.put("A", "/src/main/java/org/hjug/refactorfirst/A.java");
        map.put("B", "/src/main/java/org/hjug/refactorfirst/B.java");
        map.put("C", "/src/main/java/org/hjug/refactorfirst/C.java");
        when(dto.getClassToSourceFilePathMapping()).thenReturn(map);
        String repoUrl = "https://github.com/refactorfirst/RefactorFirst/blob";
        String dot = htmlReport.buildClassCycleDot(classGraph, rankedCycle, repoUrl, dto);
        String expectedDot = "`strict digraph G {\n"
                + "A -\\u003E B [ label = \"2\" weight = \"2\" ];\n"
                + "B -\\u003E C [ label = \"1\" weight = \"1\" ];\n"
                + "C -\\u003E A [ label = \"1\" weight = \"1\" ];\n"
                + "A [URL=\"https://github.com/refactorfirst/RefactorFirst/blob/src/main/java/org/hjug/refactorfirst/A.java\" target=\"_blank\"];\n"
                + "B [URL=\"https://github.com/refactorfirst/RefactorFirst/blob/src/main/java/org/hjug/refactorfirst/B.java\" target=\"_blank\"];\n"
                + "C [URL=\"https://github.com/refactorfirst/RefactorFirst/blob/src/main/java/org/hjug/refactorfirst/C.java\" target=\"_blank\"];\n"
                + "}`;";

        assertEquals(expectedDot, dot);
    }

    /**
     * {@code isSinkAnonymousOrSyntheticVertex} is the render-time noise filter: it suppresses only
     * the <em>sink</em> subset of anonymous/synthetic vertices (those with no outgoing edges), to
     * keep the Class/Cycle Map DOT graph readable. Active vertices (with outgoing edges) render.
     */
    @Test
    void isSinkAnonymousOrSyntheticVertex_truthTable() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("com.foo.Outer$1");
        classGraph.addVertex("com.foo.Outer$");
        classGraph.addVertex("com.foo.Outer$2");
        classGraph.addVertex("com.foo.Outer$Inner");
        classGraph.addVertex("<anonymous>");
        classGraph.addVertex("<anon2>");
        classGraph.addVertex("com.foo.Named");
        // give the "active" vertices an outgoing edge
        classGraph.addVertex("com.foo.Other");
        classGraph.addEdge("com.foo.Outer$1", "com.foo.Other");
        classGraph.addEdge("<anonymous>", "com.foo.Other");
        classGraph.addEdge("com.foo.Outer$Inner", "com.foo.Other");
        classGraph.addEdge("com.foo.Named", "com.foo.Other");

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
     * {@code $}→{@code _} in the DOT id and {@code $} visible in the label; {@code <anonymous>}
     * renders with a {@code <}/{@code >}-free DOT id and a human-readable {@code <anonymous>}
     * label). Sink-only anonymous/synthetic vertices are suppressed. The DOT body must not contain
     * a raw {@code <} other than inside HTML-escaped labels.
     */
    @Test
    void buildClassCycleDot_rendersAnonymousAndSyntheticVertices() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("com.example.Outer");
        classGraph.addVertex("com.example.Outer$1"); // active (outgoing edge)
        classGraph.addVertex("com.example.Outer$2"); // sink (no outgoing edge)
        classGraph.addVertex("com.example.Other");
        classGraph.addVertex("<anonymous>"); // active (outgoing edge)
        classGraph.addVertex("<anonSink>"); // sink
        // wire a cycle: Outer -> Outer$1 -> Other -> Outer; anonymous -> Outer; Outer -> anonymous
        classGraph.addEdge("com.example.Outer", "com.example.Outer$1");
        classGraph.addEdge("com.example.Outer$1", "com.example.Other");
        classGraph.addEdge("com.example.Other", "com.example.Outer");
        classGraph.addEdge("com.example.Outer", "<anonymous>");
        classGraph.addEdge("<anonymous>", "com.example.Outer");

        List<CycleNode> cycleNodes = new ArrayList<>();
        RankedCycle rankedCycle = new RankedCycle("Cycle", classGraph.vertexSet(), classGraph.edgeSet(), cycleNodes);

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

        String dot = htmlReport.buildClassCycleDot(classGraph, rankedCycle, repoUrl, dto);

        // active Java anonymous Outer$1 renders with DOT id Outer_1 and a $-visible label
        assertTrue(dot.contains("Outer_1 ["), "Outer$1 must render with the _$-safe DOT id Outer_1");
        assertTrue(dot.contains("label=\"Outer\\$1\""), "Outer$1 label must keep an escaped $");

        // sink Java anonymous Outer$2 is omitted
        assertFalse(dot.contains("Outer_2 ["), "sink Outer$2 must be suppressed");
        assertFalse(dot.contains("Outer$2"), "Outer$2 must not appear anywhere in the DOT");

        assertTrue(
                dot.contains("Foo_anonymous ["),
                "<anonymous> must render with the DOT id Foo_anonymous derived from the enclosing source file");
        // ...and the human-readable label uses $ as the enclosing separator
        assertTrue(dot.contains("label=\"Foo\\$anonymous\""), "<anonymous> label must be Foo\\$anonymous");

        // sink anonymous is omitted entirely (both as id and label)
        assertFalse(dot.contains("Bar_anonymous"), "sink <anonSink> DOT id must be suppressed");
        assertFalse(dot.contains("label=\"Bar\\$anonymous\""), "sink <anonSink> label must be suppressed");

        // DOT body must not contain a raw '<' char from an anonymous vertex. With the
        // enclosing-source-file-name rendering the anonymous vertex no longer contributes the
        // literal "<anonymous>" to the DOT. (Edges use "->" which contains '>'; we only assert
        // that no anonymous vertex line carries a raw '<' or a '<anon' encoding.)
        assertFalse(dot.contains("<anonymous"), "DOT must not contain the literal <anonymous after rendering");
        assertFalse(dot.contains("<anonSink>"), "DOT must not contain the suppressed <anonSink> literal");
        // no edge should reference a suppressed vertex id
        assertFalse(dot.contains("Outer_2 ->"), "no edge may start from suppressed Outer$2");
        assertFalse(dot.contains("-> Outer_2"), "no edge may end at suppressed Outer$2");
        assertFalse(
                dot.contains("-> " + htmlReport.renderSafeNodeId("<anonSink>")),
                "no edge may end at suppressed <anonSink>");
    }

    /**
     * Renders an active Kotlin {@code "<anonymous>"} vertex whose source mapping points at a
     * Kotlin file. The DOT node id must be derived from the enclosing source file's base name
     * (e.g. {@code DeveloperWASDControl.kt} -> {@code DeveloperWASDControl_anonymous}) and the
     * label must read {@code DeveloperWASDControl$anonymous} (with {@code $} escaped as
     * {@code \$}). No literal {@code <}/{@code >} may appear in the DOT node id.
     */
    @Test
    void buildClassCycleDot_rendersKotlinAnonymousWithEnclosingSourceFileName() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("dev.DeveloperWASDControl");
        // OpenRewrite attributes Kotlin anonymous objects with an FQN whose trailing segment is
        // the literal "<anonymous>" but whose prefix carries the enclosing class, e.g.
        // "dev.DeveloperWASDControl.<anonymous>". The source-mapping key equals this full FQN.
        classGraph.addVertex("dev.DeveloperWASDControl.<anonymous>");
        classGraph.addVertex("com.almasb.fxgl.app.GameApplication");
        // anonymous depends on GameApplication; DeveloperWASDControl depends on anonymous -> cycle
        classGraph.addEdge("dev.DeveloperWASDControl.<anonymous>", "com.almasb.fxgl.app.GameApplication");
        classGraph.addEdge("dev.DeveloperWASDControl", "dev.DeveloperWASDControl.<anonymous>");
        classGraph.addEdge("com.almasb.fxgl.app.GameApplication", "dev.DeveloperWASDControl");

        List<CycleNode> cycleNodes = new ArrayList<>();
        RankedCycle rankedCycle = new RankedCycle("Cycle", classGraph.vertexSet(), classGraph.edgeSet(), cycleNodes);

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

        String dot = htmlReport.buildClassCycleDot(classGraph, rankedCycle, repoUrl, dto);

        // DOT body must not contain the old lt_anonymous_gt encoding for the active anonymous vertex
        assertFalse(dot.contains("lt_anonymous_gt"), "must not use the lt_anonymous_gt encoding");

        // node id derived from the enclosing source file name
        assertTrue(
                dot.contains("DeveloperWASDControl_anonymous ["),
                "anonymous vertex must render with the DOT id DeveloperWASDControl_anonymous");

        // human-readable label uses $ as the enclosing-class separator, escaped for DOT
        assertTrue(
                dot.contains("label=\"DeveloperWASDControl\\$anonymous\""),
                "label must be DeveloperWASDControl\\$anonymous, was: " + dot);

        // no literal < or > in the DOT node id (the label is fine on its own line); verify the
        // node declaration line (not an edge line, which uses "->") carries no raw < or >.
        String anonLine = Arrays.stream(dot.split("\n"))
                .filter(l -> l.startsWith("DeveloperWASDControl_anonymous [URL"))
                .findFirst()
                .orElse("");
        assertFalse(anonLine.isEmpty(), "anonymous node declaration line must be present");
        assertFalse(anonLine.contains("<"), "anonymous DOT id line must not contain a raw '<'");
        assertFalse(anonLine.contains(">"), "anonymous DOT id line must not contain a raw '>'");
    }

    /**
     * A vertex absent from {@code classToSourceFilePathMapping} must render without any {@code URL=}
     * attribute (no broken link) and must never contain the literal substring {@code "null"}.
     * This protects both genuinely external classes (JavaFX, JDK) and any vertex that Step 2
     * reconciliation could not resolve.
     */
    @Test
    void hyperlinkClassForDot_missingPath_rendersNoUrlAttribute() {
        HtmlReport htmlReport = new HtmlReport();
        CodebaseGraphDTO dto = mock(CodebaseGraphDTO.class);
        HashMap<String, String> map = new HashMap<>();
        // Mapping exists for some classes but NOT for the one we'll query
        map.put("com.example.Known", "/src/main/java/com/example/Known.java");
        when(dto.getClassToSourceFilePathMapping()).thenReturn(map);

        String repoUrl = "https://github.com/example/repo/blob/";
        String result = htmlReport.hyperlinkClassForDot("com.example.Unknown", repoUrl, dto);

        // No URL attribute at all
        assertFalse(result.contains("URL="), "missing-path vertex must not render URL=");
        // Never the literal string "null"
        assertFalse(result.contains("null"), "result must not contain the literal 'null'");
        // Should return empty string
        assertEquals("", result);
    }

    @Test
    void renderClassCycleVisuals_usesSafeIdentifierAndEscapedDisplayName() {
        HtmlReport htmlReport = new HtmlReport();
        htmlReport.classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        RankedCycle leadingDigit = new RankedCycle("1<cycle>", Set.of(), Set.of(), List.of());

        String result = htmlReport.renderClassCycleVisuals(leadingDigit, "", null);

        assertTrue(result.contains("const graph_1_cycle_"));
        assertFalse(result.contains("const 1"));
        assertTrue(result.contains("Show 1&lt;cycle&gt; 2D Popup"));
        assertFalse(result.contains("Show 1<cycle>"));
    }

    @Test
    void renderClassCycleVisuals_usesNonEmptyIdentifierForEmptyName() {
        HtmlReport htmlReport = new HtmlReport();
        htmlReport.classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        RankedCycle emptyName = new RankedCycle("", Set.of(), Set.of(), List.of());

        String result = htmlReport.renderClassCycleVisuals(emptyName, "", null);

        assertTrue(result.contains("const graph_cycle_0_dot"));
        assertFalse(result.contains("const _dot"));
    }

    @Test
    void buildPackageGraphDot_usesDistinctIdsAndEscapesRawScriptContext() {
        Graph<String, DefaultWeightedEdge> graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        String dotted = "1.a.b";
        String underscored = "1.a_b\\name</script>";
        graph.addVertex(dotted);
        graph.addVertex(underscored);
        graph.addEdge(dotted, underscored);

        String dot = new HtmlReport().buildPackageGraphDot(graph, "", null);

        assertNotEquals(HtmlReport.renderSafePackageNodeId(dotted), HtmlReport.renderSafePackageNodeId(underscored));
        assertTrue(dot.contains(HtmlReport.renderSafePackageNodeId(dotted)));
        assertTrue(dot.contains(HtmlReport.renderSafePackageNodeId(underscored)));
        assertFalse(dot.contains("</script>"));
        assertTrue(dot.contains("\\u003C/script\\u003E"));
        assertTrue(dot.contains("\\\\\\\\name"), "DOT and template-literal escaping must both preserve backslashes");
    }

    @Test
    void escapeDotQuoted_escapesQuotesBackslashesAndLineBreaks() {
        assertEquals("a\\\\b\\\"c\\nd", HtmlReport.escapeDotQuoted("a\\b\"c\nd"));
    }

    /**
     * Test that renderSafeNodeId with graph context uses simple name when unique.
     */
    @Test
    void renderSafeNodeId_withGraphContext_usesSimpleNameWhenUnique() {
        HtmlReport htmlReport = new HtmlReport();

        // Single occurrence of "Pixel" - should use simple name
        String pixel = "com.almasb.fxgl.texture.Pixel";

        // Create a graph with only one Pixel class
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("com.almasb.fxgl.texture.Pixel");
        classGraph.addVertex("com.almasb.fxgl.texture.Texture");

        String nodeId = htmlReport.renderSafeNodeId(pixel, classGraph);

        assertEquals("Pixel", nodeId, "Should use simple name when unique");
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
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("com.almasb.fxgl.app.scene.Pixel");
        classGraph.addVertex("com.almasb.fxgl.texture.Pixel");
        classGraph.addVertex("com.almasb.fxgl.texture.Texture");

        String nodeId1 = htmlReport.renderSafeNodeId("com.almasb.fxgl.app.scene.Pixel", classGraph);
        String nodeId2 = htmlReport.renderSafeNodeId("com.almasb.fxgl.texture.Pixel", classGraph);

        // Both should use FQN-based IDs due to collision
        assertEquals("com_almasb_fxgl_app_scene_Pixel", nodeId1);
        assertEquals("com_almasb_fxgl_texture_Pixel", nodeId2);
        assertNotEquals(nodeId1, nodeId2);
    }

    /**
     * Test that renderSafeNodeId with graph context handles inner classes correctly.
     * Inner classes should use their full FQN since they contain $.
     */
    @Test
    void renderSafeNodeId_withGraphContext_innerClassUsesFqn() {
        HtmlReport htmlReport = new HtmlReport();

        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("com.example.Outer$Inner");
        classGraph.addVertex("com.example.Outer");

        String nodeId = htmlReport.renderSafeNodeId("com.example.Outer$Inner", classGraph);

        // Inner classes always use FQN-based ID (contains $)
        assertEquals("com_example_Outer_Inner", nodeId);
    }

    /**
     * Test that renderSafeNodeId with graph context handles anonymous classes.
     * Anonymous classes should use their special encoding.
     */
    @Test
    void renderSafeNodeId_withGraphContext_anonymousUsesSpecialEncoding() {
        HtmlReport htmlReport = new HtmlReport();

        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("<anonymous>");

        String nodeId = htmlReport.renderSafeNodeId("<anonymous>", classGraph);

        // Anonymous classes use lt_anonymous_gt encoding
        assertEquals("lt_anonymous_gt", nodeId);
    }

    /**
     * Test that renderSafeNodeId with graph context handles enclosing class prefix for anonymous.
     */
    @Test
    void renderSafeNodeId_withGraphContext_anonymousWithPrefixUsesFqn() {
        HtmlReport htmlReport = new HtmlReport();

        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("dev.DeveloperWASDControl.<anonymous>");

        String nodeId = htmlReport.renderSafeNodeId("dev.DeveloperWASDControl.<anonymous>", classGraph);

        // Anonymous with enclosing class prefix uses full FQN with lt_anonymous_gt
        assertEquals("dev_DeveloperWASDControl_lt_anonymous_gt", nodeId);
    }

    /**
     * Test that renderSafeNodeId produces unique node IDs for classes with the same simple name
     * but in different packages (the FXGL Pixel collision case). Option A: full FQN with dots→underscores.
     */
    @Test
    void renderSafeNodeId_uniqueNodeIdsForSameSimpleNameDifferentPackages() {
        HtmlReport htmlReport = new HtmlReport();

        // Two Pixel classes from different packages (FXGL case)
        String pixel1 = "com.almasb.fxgl.app.scene.Pixel";
        String pixel2 = "com.almasb.fxgl.texture.Pixel";

        String nodeId1 = htmlReport.renderSafeNodeId(pixel1);
        String nodeId2 = htmlReport.renderSafeNodeId(pixel2);

        // Node IDs should be unique (full FQN with dots→underscores)
        assertEquals("com_almasb_fxgl_app_scene_Pixel", nodeId1);
        assertEquals("com_almasb_fxgl_texture_Pixel", nodeId2);
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

        // Node IDs should be unique (full FQN with dots→underscores)
        assertEquals("com_almasb_fxgl_app_scene_Pixel", nodeId1);
        assertEquals("com_almasb_fxgl_texture_Pixel", nodeId2);
        assertNotEquals(nodeId1, nodeId2, "Node IDs must be unique for different packages");
    }

    /**
     * Test that renderSafeNodeId still handles inner classes correctly (dollar sign → underscore).
     */
    @Test
    void renderSafeNodeId_innerClassProducesUniqueNodeId() {
        HtmlReport htmlReport = new HtmlReport();

        String innerClass = "com.example.Outer$Inner";
        String nodeId = htmlReport.renderSafeNodeId(innerClass);

        // Dollar sign should become underscore
        assertEquals("com_example_Outer_Inner", nodeId);
    }

    /**
     * Test that hyperlinkClassForDot handles null CodebaseGraphDTO gracefully,
     * returning empty string instead of throwing NullPointerException.
     * This matches the null-handling behavior of enclosingSourceFileBaseName.
     */
    @Test
    void hyperlinkClassForDot_nullDto_returnsEmptyString() {
        HtmlReport htmlReport = new HtmlReport();
        String repoUrl = "https://github.com/example/repo/blob/";

        String result = htmlReport.hyperlinkClassForDot("com.example.Test", repoUrl, null);

        assertEquals("", result, "null DTO must return empty string");
    }

    /**
     * Test that hyperlinkClassForDot handles null classToSourceFilePathMapping gracefully,
     * returning empty string instead of throwing NullPointerException.
     * This matches the null-handling behavior of enclosingSourceFileBaseName.
     */
    @Test
    void hyperlinkClassForDot_nullMapping_returnsEmptyString() {
        HtmlReport htmlReport = new HtmlReport();
        CodebaseGraphDTO dto = mock(CodebaseGraphDTO.class);
        when(dto.getClassToSourceFilePathMapping()).thenReturn(null);
        String repoUrl = "https://github.com/example/repo/blob/";

        String result = htmlReport.hyperlinkClassForDot("com.example.Test", repoUrl, dto);

        assertEquals("", result, "null mapping must return empty string");
    }

    /**
     * Test that renderSafeNodeId still handles Kotlin anonymous classes correctly
     * (source-aware when DTO provided, lt_anonymous_gt fallback when not).
     */
    @Test
    void renderSafeNodeId_kotlinAnonymousProducesValidNodeId() {
        HtmlReport htmlReport = new HtmlReport();

        // Without DTO - fallback to lt_anonymous_gt encoding
        String anon1 = "<anonymous>";
        String nodeId1 = htmlReport.renderSafeNodeId(anon1);
        assertEquals("lt_anonymous_gt", nodeId1);

        // With enclosing class prefix
        String anon2 = "dev.DeveloperWASDControl.<anonymous>";
        String nodeId2 = htmlReport.renderSafeNodeId(anon2);
        assertEquals("dev_DeveloperWASDControl_lt_anonymous_gt", nodeId2);

        // With DTO and source mapping - should use enclosing source file name
        CodebaseGraphDTO dto = mock(CodebaseGraphDTO.class);
        HashMap<String, String> map = new HashMap<>();
        map.put("dev.DeveloperWASDControl.<anonymous>", "/fxgl-samples/src/main/kotlin/dev/DeveloperWASDControl.kt");
        when(dto.getClassToSourceFilePathMapping()).thenReturn(map);

        String nodeId3 = htmlReport.renderSafeNodeId(anon2, dto);
        assertEquals("DeveloperWASDControl_anonymous_302290128", nodeId3);
    }

    @Test
    void printTitle_escapesProjectNameAndVersionInTitleTag() {
        HtmlReport htmlReport = new HtmlReport();
        String projectName = "Test<script>Project";
        String projectVersion = "1.0\"onload=alert(1)";

        String result = htmlReport.printTitle(projectName, projectVersion);

        // The project name and version should be escaped in the title tag
        assertTrue(result.contains("Test<script>Project"), "Project name should be escaped in title: " + result);
        assertTrue(result.contains("1.0\"onload=alert(1)"), "Project version should be escaped in title: " + result);
    }
}

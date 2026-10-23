package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hjug.cbc.RankedCycle;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.VertexInfo;
import org.hjug.refactorfirst.report.model.ClassNodeDTO;
import org.hjug.refactorfirst.report.model.EdgeDTO;
import org.hjug.refactorfirst.report.model.HierarchyDTO;
import org.hjug.refactorfirst.report.model.PackageNodeDTO;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link HierarchyBuilder} contract: the nested package/class hierarchy built from
 * VertexInfo graph data that replaces raw DOT in refactor-first.json and drives every nested
 * package + class DOT graph.
 *
 * <p>The hierarchy rules under test:
 *
 * <ul>
 *   <li>Packages nest as ancestor clusters and single-child chains collapse into their deepest
 *       package (D1/D2).</li>
 *   <li>Classes are resolved to their owning package, including {@code $}-nested classes and
 *       Kotlin {@code <anonymous>} classes (via the enclosing class's package).</li>
 *   <li>Packageless classes land in the synthetic {@code (default package)} bucket.</li>
 *   <li>Render-time noise filtering matches the current DOT emitters: sink-only anonymous or
 *       synthetic classes and their edges are suppressed while neighbouring classes survive.</li>
 *   <li>Node ids match today's emitters exactly (source-aware anonymous ids, FQN fallback on
 *       simple-name collision), so the DOT builders can consume them verbatim.</li>
 *   <li>Removal flags map to the visual contract: classes/edges marked for removal stay red,
 *       packages that need to be broken up are flagged for pink fill.</li>
 * </ul>
 */
class HierarchyBuilderTest {

    private static final String DEFAULT_PACKAGE = HierarchyBuilder.DEFAULT_PACKAGE;

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    /** Creates an empty weighted directed graph matching the report's graph type. */
    private static Graph<VertexInfo, DefaultWeightedEdge> newGraph() {
        return new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
    }

    /** Adds a weighted edge, creating both endpoint vertices. */
    private static void addEdge(Graph<VertexInfo, DefaultWeightedEdge> graph, String from, String to, int weight) {
        graph.addVertex(VertexInfo.forName(from));
        graph.addVertex(VertexInfo.forName(to));
        DefaultWeightedEdge edge = graph.addEdge(VertexInfo.forName(from), VertexInfo.forName(to));
        graph.setEdgeWeight(edge, weight);
    }

    /** Creates a DTO whose graphs are unused by the builder; only the source mapping matters. */
    private static CodebaseGraphDTO dto(Map<String, String> sourcePaths) {
        return new CodebaseGraphDTO(null, null, Map.of(), sourcePaths, List.of(), List.of());
    }

    /** Finds a child package by name, failing with a readable message when absent. */
    private static PackageNodeDTO childByName(PackageNodeDTO parent, String name) {
        return parent.getChildren().stream()
                .filter(child -> child.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected child package " + name));
    }

    /** Finds a class node by FQN inside a package, failing with a readable message when absent. */
    private static ClassNodeDTO classByFqn(PackageNodeDTO pkg, String fqn) {
        return pkg.getClasses().stream()
                .filter(cls -> cls.getFqn().equals(fqn))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected class " + fqn + " in package " + pkg.getName()));
    }

    /** Finds an edge by its node ids, failing with a readable message when absent. */
    private static EdgeDTO edgeBetween(HierarchyDTO hierarchy, String source, String target) {
        return hierarchy.getEdges().stream()
                .filter(edge ->
                        edge.getSource().equals(source) && edge.getTarget().equals(target))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected edge " + source + " -> " + target));
    }

    /** Reports whether the hierarchy contains an edge with the given node ids. */
    private static boolean hasEdge(HierarchyDTO hierarchy, String source, String target) {
        return hierarchy.getEdges().stream()
                .anyMatch(edge ->
                        edge.getSource().equals(source) && edge.getTarget().equals(target));
    }

    // ------------------------------------------------------------------
    // class hierarchy
    // ------------------------------------------------------------------

    @DisplayName("class hierarchy nests classes in package clusters under branching ancestors")
    @Test
    void buildClassHierarchy_nestsClassesUnderBranchingAncestors() {
        // Given: org.a.Alpha -> org.b.Beta, so org branches into org.a and org.b
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        addEdge(classGraph, "org.a.Alpha", "org.b.Beta", 3);

        // When: the class hierarchy is built
        HierarchyDTO hierarchy =
                HierarchyBuilder.buildClassHierarchy(classGraph, dto(new HashMap<>()), Set.of(), Set.of(), Set.of());

        // Then: the single root is the branching package "org" holding org.a and org.b
        assertEquals(1, hierarchy.getPackages().size(), "a branching package must not collapse");
        PackageNodeDTO org = hierarchy.getPackages().get(0);
        assertEquals("org", org.getName());
        assertFalse(org.isRenderAsNode());
        assertFalse(org.isNeedsBreakUp());
        assertTrue(org.getId().startsWith("cluster_"));

        PackageNodeDTO orgA = childByName(org, "org.a");
        assertEquals(
                List.of("org.a.Alpha"),
                orgA.getClasses().stream().map(ClassNodeDTO::getFqn).toList());
        PackageNodeDTO orgB = childByName(org, "org.b");
        assertEquals(
                List.of("org.b.Beta"),
                orgB.getClasses().stream().map(ClassNodeDTO::getFqn).toList());

        // And: the class carries the resolved id/label/path and the edge carries its weight
        ClassNodeDTO alpha = classByFqn(orgA, "org.a.Alpha");
        assertEquals("Alpha", alpha.getId());
        assertEquals("Alpha", alpha.getLabel());
        assertEquals("", alpha.getPath());
        assertFalse(alpha.isMarkedForRemoval());
        assertEquals(3, edgeBetween(hierarchy, "Alpha", "Beta").getWeight());
        assertFalse(edgeBetween(hierarchy, "Alpha", "Beta").isMarkedForRemoval());
    }

    @DisplayName("class hierarchy collapses single-child package chains into the deepest package")
    @Test
    void buildClassHierarchy_collapsesSingleChildChains() {
        // Given: classes only in com.foo.bar and org.other
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        addEdge(classGraph, "com.foo.bar.Baz", "org.other.Zed", 1);

        // When: the class hierarchy is built
        HierarchyDTO hierarchy =
                HierarchyBuilder.buildClassHierarchy(classGraph, dto(new HashMap<>()), Set.of(), Set.of(), Set.of());

        // Then: the roots are the collapsed deepest packages, in sorted order
        assertEquals(
                List.of("com.foo.bar", "org.other"),
                hierarchy.getPackages().stream().map(PackageNodeDTO::getName).toList());
        assertTrue(hierarchy.getPackages().get(0).getChildren().isEmpty());
    }

    @DisplayName("packageless classes land in the synthetic default package")
    @Test
    void buildClassHierarchy_defaultsPackagelessClasses() {
        // Given: a default-package class and a packaged class
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        addEdge(classGraph, "Standalone", "org.a.Alpha", 1);

        // When: the class hierarchy is built
        HierarchyDTO hierarchy =
                HierarchyBuilder.buildClassHierarchy(classGraph, dto(new HashMap<>()), Set.of(), Set.of(), Set.of());

        // Then: two roots — the default package bucket and org.a (org collapsed into its only child)
        assertEquals(
                List.of(DEFAULT_PACKAGE, "org.a"),
                hierarchy.getPackages().stream().map(PackageNodeDTO::getName).toList());
        PackageNodeDTO defaultPkg = hierarchy.getPackages().get(0);
        assertEquals("Standalone", classByFqn(defaultPkg, "Standalone").getId());
    }

    @DisplayName("nested $ classes and Kotlin anonymous classes belong to the owning package")
    @Test
    void buildClassHierarchy_placesNestedAndAnonymousClassesInOwnerPackage() {
        // Given: a Java nested class and an active Kotlin anonymous class in org.pkg
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        addEdge(classGraph, "org.pkg.Outer$Inner", "org.other.Q", 1);
        addEdge(classGraph, "org.pkg.Widget.<anonymous>", "org.other.Q", 2);
        Map<String, String> sourcePaths = new HashMap<>();
        sourcePaths.put("org.pkg.Widget.<anonymous>", "src/main/kotlin/org/pkg/Widget.kt");

        // When: the class hierarchy is built
        HierarchyDTO hierarchy =
                HierarchyBuilder.buildClassHierarchy(classGraph, dto(sourcePaths), Set.of(), Set.of(), Set.of());

        // Then: both classes live in org.pkg; the nested class uses its FQN-derived id and the
        // anonymous class gets the source-aware owner id, owner label, and source path
        assertEquals(1, hierarchy.getPackages().size());
        PackageNodeDTO org = hierarchy.getPackages().get(0);
        assertEquals("org", org.getName());
        PackageNodeDTO orgPkg = childByName(org, "org.pkg");
        assertEquals(2, orgPkg.getClasses().size());

        ClassNodeDTO inner = classByFqn(orgPkg, "org.pkg.Outer$Inner");
        assertEquals("org_pkg_Outer_Inner", inner.getId());
        assertEquals("Outer$Inner", inner.getLabel());

        ClassNodeDTO anon = classByFqn(orgPkg, "org.pkg.Widget.<anonymous>");
        assertEquals("Widget_anonymous", anon.getId());
        assertEquals("Widget$anonymous", anon.getLabel());
        assertEquals("src/main/kotlin/org/pkg/Widget.kt", anon.getPath());
    }

    @DisplayName("sink-only anonymous classes are suppressed but their neighbours still render")
    @Test
    void buildClassHierarchy_suppressesSinkOnlyAnonymousClasses() {
        // Given: Alpha's only edge targets a synthetic sink Sink$1 (no outgoing edges)
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        addEdge(classGraph, "org.a.Alpha", "org.p.Sink$1", 1);

        // When: the class hierarchy is built
        HierarchyDTO hierarchy =
                HierarchyBuilder.buildClassHierarchy(classGraph, dto(new HashMap<>()), Set.of(), Set.of(), Set.of());

        // Then: Sink$1 and its edge are gone, org.p never appears, but Alpha still renders
        assertEquals(1, hierarchy.getPackages().size());
        PackageNodeDTO orgA = hierarchy.getPackages().get(0);
        assertEquals("org.a", orgA.getName());
        ClassNodeDTO alpha = classByFqn(orgA, "org.a.Alpha");
        assertNotNull(alpha);
        assertTrue(hierarchy.getEdges().isEmpty());
    }

    @DisplayName("removal targets are flagged: classes red, packages pink, edges red")
    @Test
    void buildClassHierarchy_flagsRemovalTargets() {
        // Given: a p.a.A <-> p.b.B cycle with A, package p.a, and the A -> B edge marked for removal
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        addEdge(classGraph, "p.a.A", "p.b.B", 5);
        addEdge(classGraph, "p.b.B", "p.a.A", 2);
        DefaultWeightedEdge aToB = classGraph.getEdge(VertexInfo.forName("p.a.A"), VertexInfo.forName("p.b.B"));

        // When: the class hierarchy is built with those removal sets
        HierarchyDTO hierarchy = HierarchyBuilder.buildClassHierarchy(
                classGraph,
                dto(new HashMap<>()),
                Set.of(VertexInfo.forName("p.a.A")),
                Set.of(VertexInfo.forName("p.a")),
                Set.of(aToB));

        // Then: the class and edge carry removal flags and package p.a needs to be broken up
        PackageNodeDTO p = hierarchy.getPackages().get(0);
        assertEquals("p", p.getName());
        assertTrue(childByName(p, "p.a").isNeedsBreakUp());
        assertFalse(childByName(p, "p.b").isNeedsBreakUp());
        assertTrue(classByFqn(childByName(p, "p.a"), "p.a.A").isMarkedForRemoval());
        assertFalse(classByFqn(childByName(p, "p.b"), "p.b.B").isMarkedForRemoval());
        assertTrue(edgeBetween(hierarchy, "A", "B").isMarkedForRemoval());
        assertFalse(edgeBetween(hierarchy, "B", "A").isMarkedForRemoval());
    }

    @DisplayName("simple-name collisions resolve to FQN-derived ids; unique names to simple ids")
    @Test
    void buildClassHierarchy_resolvesIdCollisions() {
        // Given: two Foo classes in different packages plus a unique Solo class
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        addEdge(classGraph, "p.a.Foo", "p.b.Foo", 1);
        addEdge(classGraph, "p.b.Foo", "p.c.Solo", 1);

        // When: the class hierarchy is built
        HierarchyDTO hierarchy =
                HierarchyBuilder.buildClassHierarchy(classGraph, dto(new HashMap<>()), Set.of(), Set.of(), Set.of());

        // Then: colliding classes use FQN-derived ids, the unique class uses its simple name,
        // and the edges reference those resolved ids
        PackageNodeDTO p = hierarchy.getPackages().get(0);
        assertEquals("p", p.getName());
        assertEquals("p_a_Foo", classByFqn(childByName(p, "p.a"), "p.a.Foo").getId());
        assertEquals("p_b_Foo", classByFqn(childByName(p, "p.b"), "p.b.Foo").getId());
        assertEquals("Solo", classByFqn(childByName(p, "p.c"), "p.c.Solo").getId());
        assertEquals(2, hierarchy.getEdges().size());
        assertTrue(hasEdge(hierarchy, "p_a_Foo", "p_b_Foo"));
        assertTrue(hasEdge(hierarchy, "p_b_Foo", "Solo"));
    }

    @DisplayName("package hierarchy derives each package's source directory from the class source mapping")
    @Test
    void buildPackageHierarchy_derivesPackagePathsFromSourceMapping() {
        // Given: an org.a -> org.b dependency and a source mapping that places
        // org.a's classes under a module root and org.b's under another root
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = newGraph();
        addEdge(packageGraph, "org.a", "org.b", 1);
        Map<String, String> sourcePaths = new HashMap<>();
        sourcePaths.put("org.a.A", "cost-benefit-calculator/src/main/java/org/a/A.java");
        sourcePaths.put("org.b.B", "graph-algorithms/src/main/java/org/b/B.java");

        // When: the package hierarchy is built
        HierarchyDTO hierarchy =
                HierarchyBuilder.buildPackageHierarchy(packageGraph, dto(sourcePaths), Set.of(), Set.of());

        // Then: each package carries its repo-relative source directory (the
        // hyperlinked location in the package graph); a package without a mapped
        // class carries an empty path and renders without a hyperlink
        PackageNodeDTO org = hierarchy.getPackages().get(0);
        assertEquals(
                "cost-benefit-calculator/src/main/java/org/a/",
                childByName(org, "org.a").getPath());
        assertEquals(
                "graph-algorithms/src/main/java/org/b/",
                childByName(org, "org.b").getPath());

        // And When: the same hierarchy is built without a source mapping
        HierarchyDTO withoutPaths =
                HierarchyBuilder.buildPackageHierarchy(packageGraph, dto(new HashMap<>()), Set.of(), Set.of());

        // Then: the packages carry empty paths
        assertEquals("", childByName(withoutPaths.getPackages().get(0), "org.a").getPath());
    }

    // ------------------------------------------------------------------
    // cycle hierarchy
    // ------------------------------------------------------------------

    @DisplayName("cycle hierarchy keeps only cycle members with packages pruned to the cycle")
    @Test
    void buildCycleHierarchy_restrictsToCycleMembers() {
        // Given: a cycle p.a.A <-> p.b.B and an outsider p.c.Solo -> p.b.B outside the cycle
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        addEdge(classGraph, "p.a.A", "p.b.B", 5);
        addEdge(classGraph, "p.b.B", "p.a.A", 2);
        addEdge(classGraph, "p.c.Solo", "p.b.B", 1);
        DefaultWeightedEdge aToB = classGraph.getEdge(VertexInfo.forName("p.a.A"), VertexInfo.forName("p.b.B"));
        DefaultWeightedEdge bToA = classGraph.getEdge(VertexInfo.forName("p.b.B"), VertexInfo.forName("p.a.A"));
        RankedCycle cycle =
                new RankedCycle("p.a.A", new LinkedHashSet<>(List.of("p.a.A", "p.b.B")), Set.of(aToB, bToA), List.of());

        // When: the cycle hierarchy is built
        HierarchyDTO hierarchy = HierarchyBuilder.buildCycleHierarchy(
                classGraph, dto(new HashMap<>()), Set.of(), Set.of(), Set.of(), cycle);

        // Then: only the cycle classes appear, under p with p.a and p.b; the outsider is gone
        PackageNodeDTO p = hierarchy.getPackages().get(0);
        assertEquals("p", p.getName());
        assertEquals(2, p.getChildren().size());
        assertNotNull(classByFqn(childByName(p, "p.a"), "p.a.A"));
        assertNotNull(classByFqn(childByName(p, "p.b"), "p.b.B"));
        assertEquals(2, hierarchy.getEdges().size());
        assertTrue(hasEdge(hierarchy, "A", "B"));
        assertTrue(hasEdge(hierarchy, "B", "A"));
        assertFalse(hasEdge(hierarchy, "Solo", "B"));
    }

    @DisplayName("cycle hierarchy applies graph-wide id collision rules")
    @Test
    void buildCycleHierarchy_appliesGraphWideIdCollisions() {
        // Given: p.a.Foo <-> p.b.Foo in a cycle plus p.c.Foo outside it, three Foo simple names
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        addEdge(classGraph, "p.a.Foo", "p.b.Foo", 1);
        addEdge(classGraph, "p.b.Foo", "p.a.Foo", 1);
        addEdge(classGraph, "p.c.Foo", "p.a.Foo", 1);
        DefaultWeightedEdge aToB = classGraph.getEdge(VertexInfo.forName("p.a.Foo"), VertexInfo.forName("p.b.Foo"));
        DefaultWeightedEdge bToA = classGraph.getEdge(VertexInfo.forName("p.b.Foo"), VertexInfo.forName("p.a.Foo"));
        RankedCycle cycle = new RankedCycle(
                "p.a.Foo", new LinkedHashSet<>(List.of("p.a.Foo", "p.b.Foo")), Set.of(aToB, bToA), List.of());

        // When: the cycle hierarchy is built
        HierarchyDTO hierarchy = HierarchyBuilder.buildCycleHierarchy(
                classGraph, dto(new HashMap<>()), Set.of(), Set.of(), Set.of(), cycle);

        // Then: cycle members use FQN-derived ids because the full graph was consulted for
        // collisions, keeping ids identical to the class map's
        PackageNodeDTO p = hierarchy.getPackages().get(0);
        assertEquals("p_a_Foo", classByFqn(childByName(p, "p.a"), "p.a.Foo").getId());
        assertEquals("p_b_Foo", classByFqn(childByName(p, "p.b"), "p.b.Foo").getId());
    }

    // ------------------------------------------------------------------
    // package hierarchy
    // ------------------------------------------------------------------

    @DisplayName("package hierarchy nests rectangle nodes under collapsed ancestor clusters")
    @Test
    void buildPackageHierarchy_nestsNodesUnderAncestors() {
        // Given: package edges org.hjug.cbc -> org.hjug.dsm and org.hjug.report -> org.hjug.cbc
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = newGraph();
        addEdge(packageGraph, "org.hjug.cbc", "org.hjug.dsm", 4);
        addEdge(packageGraph, "org.hjug.report", "org.hjug.cbc", 2);

        // When: the package hierarchy is built
        HierarchyDTO hierarchy =
                HierarchyBuilder.buildPackageHierarchy(packageGraph, dto(new HashMap<>()), Set.of(), Set.of());

        // Then: org collapsed into org.hjug, which contains the three rectangle node packages
        assertEquals(1, hierarchy.getPackages().size());
        PackageNodeDTO hjug = hierarchy.getPackages().get(0);
        assertEquals("org.hjug", hjug.getName());
        assertFalse(hjug.isRenderAsNode());
        assertNull(hjug.getNodeId());
        assertEquals(
                List.of("org.hjug.cbc", "org.hjug.dsm", "org.hjug.report"),
                hjug.getChildren().stream().map(PackageNodeDTO::getName).toList());
        for (PackageNodeDTO child : hjug.getChildren()) {
            assertTrue(child.isRenderAsNode());
            assertEquals(HtmlReport.renderSafePackageNodeId(child.getName()), child.getNodeId());
            assertTrue(child.getClasses().isEmpty());
        }

        // And: edges reference the package node ids with their weights
        String cbcId = HtmlReport.renderSafePackageNodeId("org.hjug.cbc");
        String dsmId = HtmlReport.renderSafePackageNodeId("org.hjug.dsm");
        assertEquals(4, edgeBetween(hierarchy, cbcId, dsmId).getWeight());
        assertEquals(2, hierarchy.getEdges().size());
    }

    @DisplayName("a parent package in a relationship renders as a cluster with an invisible-anchor id")
    @Test
    void buildPackageHierarchy_rendersParentAsClusterWithAnchorId() {
        // Given: org.hjug itself participates in a dependency and contains child packages
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = newGraph();
        addEdge(packageGraph, "org.hjug", "org.hjug.cbc", 1);
        addEdge(packageGraph, "org.hjug.cbc", "org.hjug.dsm", 1);

        // When: the package hierarchy is built
        HierarchyDTO hierarchy =
                HierarchyBuilder.buildPackageHierarchy(packageGraph, dto(new HashMap<>()), Set.of(), Set.of());

        // Then: org.hjug renders as a cluster carrying the anchor id its edges
        // reference — no duplicate rectangle node inside its own box — and only
        // its childless children render as rectangle nodes
        PackageNodeDTO hjug = hierarchy.getPackages().get(0);
        assertEquals("org.hjug", hjug.getName());
        assertFalse(hjug.isRenderAsNode(), "a parent package must render as a cluster, not a node");
        assertEquals(HtmlReport.renderSafePackageNodeId("org.hjug"), hjug.getNodeId());
        assertEquals(
                List.of("org.hjug.cbc", "org.hjug.dsm"),
                hjug.getChildren().stream().map(PackageNodeDTO::getName).toList());
        for (PackageNodeDTO child : hjug.getChildren()) {
            assertTrue(child.isRenderAsNode(), "childless packages render as rectangle nodes");
            assertEquals(HtmlReport.renderSafePackageNodeId(child.getName()), child.getNodeId());
        }
    }

    @DisplayName("packages that need to be broken up are flagged for pink fill")
    @Test
    void buildPackageHierarchy_flagsBreakUpPackages() {
        // Given: org.hjug.cbc <-> org.hjug.dsm with cbc selected for removal
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = newGraph();
        addEdge(packageGraph, "org.hjug.cbc", "org.hjug.dsm", 1);
        addEdge(packageGraph, "org.hjug.dsm", "org.hjug.cbc", 1);

        // When: the package hierarchy is built with that removal set
        HierarchyDTO hierarchy = HierarchyBuilder.buildPackageHierarchy(
                packageGraph, dto(new HashMap<>()), Set.of(VertexInfo.forName("org.hjug.cbc")), Set.of());

        // Then: only cbc is flagged while its parent container stays neutral
        PackageNodeDTO hjug = hierarchy.getPackages().get(0);
        assertTrue(childByName(hjug, "org.hjug.cbc").isNeedsBreakUp());
        assertFalse(childByName(hjug, "org.hjug.dsm").isNeedsBreakUp());
        assertFalse(hjug.isNeedsBreakUp());
    }

    // ------------------------------------------------------------------
    // break-up list consistency
    // ------------------------------------------------------------------

    /** Collects the names of every package flagged needsBreakUp across the tree. */
    private static Set<String> collectNeedsBreakUp(HierarchyDTO hierarchy) {
        Set<String> flagged = new LinkedHashSet<>();
        collectNeedsBreakUp(hierarchy.getPackages(), flagged);
        return flagged;
    }

    private static void collectNeedsBreakUp(List<PackageNodeDTO> packages, Set<String> flagged) {
        for (PackageNodeDTO pkg : packages) {
            if (pkg.isNeedsBreakUp()) {
                flagged.add(pkg.getName());
            }
            collectNeedsBreakUp(pkg.getChildren(), flagged);
        }
    }

    @DisplayName("class hierarchy flags every break-up package even without rendered classes")
    @Test
    void buildClassHierarchy_flagsEveryBreakUpPackage() {
        // Given: classes only in org.a, and a break-up list that also contains org.z,
        // a package none of whose classes appear in the class graph
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        addEdge(classGraph, "org.a.A", "org.a.B", 1);

        // When: the class hierarchy is built
        HierarchyDTO hierarchy = HierarchyBuilder.buildClassHierarchy(
                classGraph,
                dto(new HashMap<>()),
                Set.of(),
                Set.of(VertexInfo.forName("org.a"), VertexInfo.forName("org.z")),
                Set.of());

        // Then: the class map's break-up list matches the input exactly — org.z
        // renders as its own flagged cluster even though it holds no classes
        assertEquals(Set.of("org.a", "org.z"), collectNeedsBreakUp(hierarchy));
    }

    @DisplayName("a pure-ancestor break-up package survives cluster collapse")
    @Test
    void buildClassHierarchy_keepsBreakUpPackageThroughCollapse() {
        // Given: classes only in com.foo.bar while the pure ancestor com.foo is flagged
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        addEdge(classGraph, "com.foo.bar.Baz", "com.other.Zed", 1);

        // When: the class hierarchy is built
        HierarchyDTO hierarchy = HierarchyBuilder.buildClassHierarchy(
                classGraph, dto(new HashMap<>()), Set.of(), Set.of(VertexInfo.forName("com.foo")), Set.of());

        // Then: com.foo keeps its own flagged cluster instead of collapsing into com.foo.bar
        PackageNodeDTO com = hierarchy.getPackages().get(0);
        assertEquals("com", com.getName());
        PackageNodeDTO comFoo = childByName(com, "com.foo");
        assertTrue(comFoo.isNeedsBreakUp());
        assertTrue(comFoo.getClasses().isEmpty());
        assertNotNull(classByFqn(childByName(comFoo, "com.foo.bar"), "com.foo.bar.Baz"));
    }

    @DisplayName("package hierarchy flags every break-up package even without edges")
    @Test
    void buildPackageHierarchy_flagsEveryBreakUpPackage() {
        // Given: one package edge and a break-up package that participates in no edge
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = newGraph();
        addEdge(packageGraph, "org.x", "org.y", 1);

        // When: the package hierarchy is built
        HierarchyDTO hierarchy = HierarchyBuilder.buildPackageHierarchy(
                packageGraph,
                dto(new HashMap<>()),
                Set.of(VertexInfo.forName("org.x"), VertexInfo.forName("org.w")),
                Set.of());

        // Then: both are flagged, and the edge-less break-up package renders as a
        // pink rectangle node so the package map lists it
        assertEquals(Set.of("org.x", "org.w"), collectNeedsBreakUp(hierarchy));
        PackageNodeDTO w = hierarchy.getPackages().stream()
                .flatMap(root -> root.getChildren().stream())
                .filter(pkg -> pkg.getName().equals("org.w"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected break-up package org.w in the tree"));
        assertTrue(w.isRenderAsNode());
        assertNotNull(w.getNodeId());
    }

    @DisplayName("class and package hierarchies flag identical break-up lists")
    @Test
    void hierarchies_flagMatchingBreakUpPackageLists() {
        // Given: a class graph with one relationship, a package graph edge, and a
        // break-up list containing a package absent from both graphs' content
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        addEdge(classGraph, "org.a.A", "org.b.B", 1);
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = newGraph();
        addEdge(packageGraph, "org.a", "org.b", 1);
        Set<VertexInfo> packagesToRemove = Set.of(VertexInfo.forName("org.a"), VertexInfo.forName("org.z"));

        // When: both hierarchies are built from the same break-up list
        HierarchyDTO classHierarchy = HierarchyBuilder.buildClassHierarchy(
                classGraph, dto(new HashMap<>()), Set.of(), packagesToRemove, Set.of());
        HierarchyDTO packageHierarchy =
                HierarchyBuilder.buildPackageHierarchy(packageGraph, dto(new HashMap<>()), packagesToRemove, Set.of());

        // Then: the class graph and the package graph show the same packages to break up
        assertEquals(Set.of("org.a", "org.z"), collectNeedsBreakUp(classHierarchy));
        assertEquals(Set.of("org.a", "org.z"), collectNeedsBreakUp(packageHierarchy));
    }

    // ------------------------------------------------------------------
    // determinism
    // ------------------------------------------------------------------

    @DisplayName("cluster ids are deterministic, DOT-legal, and collision-free; orderings are sorted")
    @Test
    void hierarchy_idsAndOrderingsAreDeterministic() {
        // Given: a two-package cycle inserted in reverse alphabetical order
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        addEdge(classGraph, "zeta.ZClass", "alpha.AClass", 1);
        addEdge(classGraph, "alpha.AClass", "zeta.ZClass", 2);

        // When: the hierarchy is built twice
        HierarchyDTO first =
                HierarchyBuilder.buildClassHierarchy(classGraph, dto(new HashMap<>()), Set.of(), Set.of(), Set.of());
        HierarchyDTO second =
                HierarchyBuilder.buildClassHierarchy(classGraph, dto(new HashMap<>()), Set.of(), Set.of(), Set.of());

        // Then: ids are stable across runs, DOT-legal hex ids, distinct per package
        assertEquals(
                first.getPackages().get(0).getId(), second.getPackages().get(0).getId());
        assertTrue(first.getPackages().get(0).getId().matches("cluster_[0-9a-f]{24}"));
        assertNotEquals(
                first.getPackages().get(0).getId(), first.getPackages().get(1).getId());

        // And: roots, children, and edges are emitted in sorted order
        assertEquals(
                List.of("alpha", "zeta"),
                first.getPackages().stream().map(PackageNodeDTO::getName).toList());
        assertEquals(
                List.of("AClass", "ZClass"),
                first.getEdges().stream().map(EdgeDTO::getSource).toList());
        assertEquals(2, edgeBetween(first, "AClass", "ZClass").getWeight());
    }
}

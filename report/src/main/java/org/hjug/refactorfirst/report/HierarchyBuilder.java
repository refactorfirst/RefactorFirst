package org.hjug.refactorfirst.report;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 * Builds the nested package/class hierarchy for every generated DOT graph from the VertexInfo
 * class and package graphs.
 *
 * <p>The hierarchy is the single source of truth for graph structure: packages nest as ancestor
 * clusters with single-child chains collapsed into their deepest package, classes resolve to
 * their owning package (including {@code $}-nested classes and Kotlin {@code <anonymous>}
 * classes via the enclosing class's package), and packageless classes land in the synthetic
 * {@link #DEFAULT_PACKAGE} bucket. The package graph additionally marks the packages that
 * participate in relationships as rectangle nodes nested inside their ancestor clusters.
 *
 * <p>Node ids and labels reuse the exact rules of the previous DOT emitters — including
 * source-aware ids for anonymous vertices and FQN fallback on simple-name collisions — so the
 * DOT builders (Java and JavaScript) consume them verbatim. Packages flagged
 * {@link PackageNodeDTO#isNeedsBreakUp()} render with a pink fill; classes and edges flagged
 * {@link ClassNodeDTO#isMarkedForRemoval()} / {@link EdgeDTO#isMarkedForRemoval()} keep the red
 * fill and highlighting used today.
 */
public final class HierarchyBuilder {

    /** Synthetic bucket for classes without a package. */
    public static final String DEFAULT_PACKAGE = "(default package)";

    private HierarchyBuilder() {}

    /**
     * Builds the class-map hierarchy: package clusters containing the classes that participate
     * in at least one class relationship, matching the vertices the class map renders today.
     *
     * @param classGraph the class reference graph
     * @param codebaseGraphDTO the DTO carrying the {@code vertex -> source file path} mapping
     * @param classesToRemove classes selected for removal to break cycles
     * @param packagesToRemove packages selected for removal to break cycles
     * @param classRelationshipsToRemove class relationships selected for removal
     * @return the nested package + class hierarchy with resolved ids, labels, and flags
     */
    public static HierarchyDTO buildClassHierarchy(
            Graph<VertexInfo, DefaultWeightedEdge> classGraph,
            CodebaseGraphDTO codebaseGraphDTO,
            Set<VertexInfo> classesToRemove,
            Set<VertexInfo> packagesToRemove,
            Set<DefaultWeightedEdge> classRelationshipsToRemove) {
        // the class map renders the endpoints of every class edge
        Set<String> vertexNames = new LinkedHashSet<>();
        for (DefaultWeightedEdge edge : classGraph.edgeSet()) {
            vertexNames.add(classGraph.getEdgeSource(edge).getName());
            vertexNames.add(classGraph.getEdgeTarget(edge).getName());
        }
        return buildClassLevelHierarchy(
                classGraph,
                codebaseGraphDTO,
                classesToRemove,
                packagesToRemove,
                classRelationshipsToRemove,
                vertexNames,
                classGraph.edgeSet(),
                true);
    }

    /**
     * Builds the cycle-map hierarchy restricted to the given cycle's classes and edges while
     * consulting the full class graph for node id collision rules, so ids match the class map.
     *
     * @param cycle the ranked cycle to restrict the hierarchy to
     * @return the nested package + class hierarchy for the cycle's members
     */
    public static HierarchyDTO buildCycleHierarchy(
            Graph<VertexInfo, DefaultWeightedEdge> classGraph,
            CodebaseGraphDTO codebaseGraphDTO,
            Set<VertexInfo> classesToRemove,
            Set<VertexInfo> packagesToRemove,
            Set<DefaultWeightedEdge> classRelationshipsToRemove,
            RankedCycle cycle) {
        return buildClassLevelHierarchy(
                classGraph,
                codebaseGraphDTO,
                classesToRemove,
                packagesToRemove,
                classRelationshipsToRemove,
                new LinkedHashSet<>(cycle.getVertexSet()),
                cycle.getEdgeSet(),
                false);
    }

    /**
     * Builds the package-map hierarchy: packages participating in at least one package
     * relationship become rectangle nodes nested inside their (collapsed) ancestor clusters.
     *
     * @param packageGraph the package reference graph
     * @param packagesToRemove packages selected for removal to break cycles
     * @param packageRelationshipsToRemove package relationships selected for removal
     * @return the nested package hierarchy with rectangle node packages and their edges
     */
    public static HierarchyDTO buildPackageHierarchy(
            Graph<VertexInfo, DefaultWeightedEdge> packageGraph,
            Set<VertexInfo> packagesToRemove,
            Set<DefaultWeightedEdge> packageRelationshipsToRemove) {
        // the package map renders the endpoints of every package edge
        Set<String> nodePackages = new LinkedHashSet<>();
        for (DefaultWeightedEdge edge : packageGraph.edgeSet()) {
            nodePackages.add(packageGraph.getEdgeSource(edge).getName());
            nodePackages.add(packageGraph.getEdgeTarget(edge).getName());
        }
        // Every break-up package must appear in the package map's flagged list, so seed the
        // node set with the full removal list — belt and braces for members that take part in
        // no rendered edge, keeping the Class and Package graphs' break-up lists identical.
        for (VertexInfo packageToRemove : packagesToRemove) {
            nodePackages.add(packageToRemove.getName());
        }

        Map<String, Pkg> packages = new HashMap<>();
        for (String name : nodePackages) {
            Pkg pkg = packageNode(packages, name);
            pkg.renderAsNode = true;
            pkg.nodeId = HtmlReport.renderSafePackageNodeId(name);
        }

        List<EdgeDTO> edges = new ArrayList<>();
        for (DefaultWeightedEdge edge : packageGraph.edgeSet()) {
            String source = packageGraph.getEdgeSource(edge).getName();
            String target = packageGraph.getEdgeTarget(edge).getName();
            edges.add(EdgeDTO.builder()
                    .source(HtmlReport.renderSafePackageNodeId(source))
                    .target(HtmlReport.renderSafePackageNodeId(target))
                    .weight((int) packageGraph.getEdgeWeight(edge))
                    .markedForRemoval(packageRelationshipsToRemove.contains(edge))
                    .build());
        }

        HierarchyDTO hierarchy = assemble(packages, edges, packagesToRemove);
        // Packages with children render as clusters — their relationships attach to an
        // invisible anchor inside them via compound ltail/lhead edges — and only
        // childless packages render as visible rectangle nodes, so every package name
        // appears exactly once in the package graph.
        markLeafPackagesAsNodes(hierarchy.getPackages());
        return hierarchy;
    }

    /**
     * Flips renderAsNode to leaf-ness on the assembled package tree: packages with kept
     * children become cluster-only (their nodeId remains as the invisible edge anchor),
     * childless packages become the visible rectangle nodes.
     */
    private static void markLeafPackagesAsNodes(List<PackageNodeDTO> packages) {
        for (PackageNodeDTO pkg : packages) {
            List<PackageNodeDTO> children = pkg.getChildren() == null ? List.of() : pkg.getChildren();
            pkg.setRenderAsNode(children.isEmpty());
            markLeafPackagesAsNodes(children);
        }
    }

    /**
     * Shared class/cycle assembly: resolves ids, labels, paths, and removal flags for the given
     * vertex names, converts the given edges to id-referencing DTOs (dropping edges that touch
     * suppressed sink vertices), and nests the classes into the package tree. When
     * {@code includeAllBreakUpPackages} is set, every break-up package joins the tree even when
     * none of its classes render, so the class map's flagged list always matches the package
     * map's.
     */
    private static HierarchyDTO buildClassLevelHierarchy(
            Graph<VertexInfo, DefaultWeightedEdge> classGraph,
            CodebaseGraphDTO codebaseGraphDTO,
            Set<VertexInfo> classesToRemove,
            Set<VertexInfo> packagesToRemove,
            Set<DefaultWeightedEdge> classRelationshipsToRemove,
            Set<String> vertexNames,
            Set<DefaultWeightedEdge> edges,
            boolean includeAllBreakUpPackages) {
        Map<String, ClassNodeDTO> classesByName = new LinkedHashMap<>();
        for (String vertex : vertexNames) {
            // same render-time noise filter as the class/cycle DOT emitters
            if (HtmlReport.isSinkAnonymousOrSyntheticVertex(classGraph, vertex)) {
                continue;
            }
            classesByName.put(
                    vertex,
                    ClassNodeDTO.builder()
                            .fqn(vertex)
                            .id(HtmlReport.renderSafeNodeId(vertex, classGraph, codebaseGraphDTO))
                            .label(
                                    HtmlReport.isAnonymousFqn(vertex)
                                            ? HtmlReport.anonymousOwnerLabel(vertex, codebaseGraphDTO)
                                            : SimpleHtmlReport.getClassName(vertex))
                            .path(classSourcePath(vertex, codebaseGraphDTO))
                            .markedForRemoval(classesToRemove.contains(VertexInfo.forName(vertex)))
                            .build());
        }

        List<EdgeDTO> edgeDtos = new ArrayList<>();
        for (DefaultWeightedEdge edge : edges) {
            String source = classGraph.getEdgeSource(edge).getName();
            String target = classGraph.getEdgeTarget(edge).getName();
            if (!classesByName.containsKey(source) || !classesByName.containsKey(target)) {
                continue; // edge touches a suppressed sink-only anonymous/synthetic vertex
            }
            edgeDtos.add(EdgeDTO.builder()
                    .source(classesByName.get(source).getId())
                    .target(classesByName.get(target).getId())
                    .weight((int) classGraph.getEdgeWeight(edge))
                    .markedForRemoval(classRelationshipsToRemove.contains(edge))
                    .build());
        }

        Map<String, Pkg> packages = new HashMap<>();
        for (ClassNodeDTO cls : classesByName.values()) {
            packageNode(packages, packageOf(cls.getFqn())).classes.add(cls);
        }
        if (includeAllBreakUpPackages) {
            // Guarantee the class map flags the full break-up list even when a package's
            // classes do not render: seed its cluster so pruning and collapse keep it.
            for (VertexInfo packageToRemove : packagesToRemove) {
                String name = packageToRemove.getName();
                if (!DEFAULT_PACKAGE.equals(name)) {
                    packageNode(packages, name);
                }
            }
        }

        return assemble(packages, edgeDtos, packagesToRemove);
    }

    /**
     * Derives the owning package of a class vertex name. Nested ({@code $}) classes belong to
     * the package of their name's last dot segment; Kotlin anonymous classes belong to the
     * package of their enclosing class; packageless names have no package ({@code null}).
     */
    static String packageOf(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        String simple = lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
        if (simple.startsWith("<")) {
            // Kotlin anonymous: the enclosing construct is a class, e.g. pkg.Outer
            if (lastDot <= 0) {
                return null;
            }
            String enclosing = fqn.substring(0, lastDot);
            int enclosingDot = enclosing.lastIndexOf('.');
            return enclosingDot > 0 ? enclosing.substring(0, enclosingDot) : null;
        }
        return lastDot > 0 ? fqn.substring(0, lastDot) : null;
    }

    /** Returns the source file path relative to the project root, or an empty string. */
    private static String classSourcePath(String vertex, CodebaseGraphDTO codebaseGraphDTO) {
        if (codebaseGraphDTO == null || codebaseGraphDTO.getClassToSourceFilePathMapping() == null) {
            return "";
        }
        String path = codebaseGraphDTO.getClassToSourceFilePathMapping().get(vertex);
        return path == null ? "" : path;
    }

    /** Creates (or returns) the package node for the name, creating and linking all ancestors. */
    private static Pkg packageNode(Map<String, Pkg> packages, String packageName) {
        String name = packageName == null || packageName.isEmpty() ? DEFAULT_PACKAGE : packageName;
        Pkg existing = packages.get(name);
        if (existing != null) {
            return existing;
        }
        Pkg pkg = new Pkg(name);
        packages.put(name, pkg);
        String parent = parentPackage(name);
        if (parent != null) {
            packageNode(packages, parent).children.add(pkg);
        }
        return pkg;
    }

    /** Returns the parent package name, or {@code null} for top-level packages and the default bucket. */
    private static String parentPackage(String name) {
        if (DEFAULT_PACKAGE.equals(name)) {
            return null;
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : null;
    }

    /**
     * Flags breakup packages, prunes empty branches, collapses single-child chains, sorts every
     * collection deterministically, and converts the internal tree to the DTO tree.
     */
    private static HierarchyDTO assemble(
            Map<String, Pkg> packages, List<EdgeDTO> edges, Set<VertexInfo> packagesToRemove) {
        for (Pkg pkg : packages.values()) {
            if (!DEFAULT_PACKAGE.equals(pkg.name)) {
                pkg.needsBreakUp = packagesToRemove.contains(VertexInfo.forName(pkg.name));
            }
        }

        List<PackageNodeDTO> roots = new ArrayList<>();
        for (Pkg pkg : packages.values()) {
            if (parentPackage(pkg.name) == null) {
                Pkg pruned = pruneAndCollapse(pkg);
                if (pruned != null) {
                    roots.add(toDto(pruned));
                }
            }
        }
        roots.sort(Comparator.comparing(PackageNodeDTO::getName));

        edges.sort(Comparator.comparing(EdgeDTO::getSource).thenComparing(EdgeDTO::getTarget));
        return HierarchyDTO.builder().packages(roots).edges(edges).build();
    }

    /**
     * Prunes branches without rendered content and collapses single-child chains: a package
     * with no content of its own and exactly one kept child is replaced by that child so chains
     * like {@code com -> com.foo -> com.foo.bar} render as a single cluster labelled with the
     * deepest package name.
     */
    private static Pkg pruneAndCollapse(Pkg pkg) {
        List<Pkg> kept = new ArrayList<>();
        for (Pkg child : pkg.children) {
            Pkg pruned = pruneAndCollapse(child);
            if (pruned != null) {
                kept.add(pruned);
            }
        }
        // A flagged package is content of its own: it must never be pruned away for being
        // empty, nor merged into its single child — the class map must keep listing every
        // break-up package so its list matches the package map's.
        boolean hasContent = pkg.renderAsNode || !pkg.classes.isEmpty() || pkg.needsBreakUp;
        if (!hasContent && kept.isEmpty()) {
            return null; // nothing rendered at or below this package
        }
        if (!hasContent && kept.size() == 1) {
            return kept.get(0); // pure ancestor of a single child: merge into the child
        }
        pkg.children.clear();
        pkg.children.addAll(kept);
        return pkg;
    }

    /** Converts a pruned package subtree to the DTO tree with sorted children and classes. */
    private static PackageNodeDTO toDto(Pkg pkg) {
        List<PackageNodeDTO> children = new ArrayList<>();
        for (Pkg child : pkg.children) {
            children.add(toDto(child));
        }
        children.sort(Comparator.comparing(PackageNodeDTO::getName));

        List<ClassNodeDTO> classes = new ArrayList<>(pkg.classes);
        classes.sort(Comparator.comparing(ClassNodeDTO::getFqn));

        return PackageNodeDTO.builder()
                .name(pkg.name)
                .id(clusterId(pkg.name))
                .nodeId(pkg.nodeId)
                .renderAsNode(pkg.renderAsNode)
                .needsBreakUp(pkg.needsBreakUp)
                .children(children)
                .classes(classes)
                .build();
    }

    /** Returns the deterministic DOT-safe cluster id {@code cluster_<hex>} for a package name. */
    static String clusterId(String packageName) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(packageName.getBytes(StandardCharsets.UTF_8));
            return "cluster_" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", e);
        }
    }

    /** Mutable internal package tree node used while assembling the hierarchy. */
    private static final class Pkg {
        final String name;
        final List<Pkg> children = new ArrayList<>();
        final List<ClassNodeDTO> classes = new ArrayList<>();
        String nodeId;
        boolean renderAsNode;
        boolean needsBreakUp;

        Pkg(String name) {
            this.name = name;
        }
    }
}

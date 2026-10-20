package org.hjug.refactorfirst.report;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hjug.refactorfirst.report.model.ClassNodeDTO;
import org.hjug.refactorfirst.report.model.EdgeDTO;
import org.hjug.refactorfirst.report.model.HierarchyDTO;
import org.hjug.refactorfirst.report.model.PackageNodeDTO;

/**
 * Renders a {@link HierarchyDTO} as nested DOT: packages become labelled clusters (rectangles),
 * package-map participants become rectangle nodes inside their ancestor clusters, and classes
 * become ellipse nodes inside their owning package cluster.
 *
 * <p>Visual contract: classes marked for removal keep the {@code color=red style=filled} fill;
 * packages that need to be broken up render pink — clusters via {@code style=filled
 * fillcolor=pink}, package nodes via {@code color=pink style=filled} — replacing the red
 * previously used for packages.
 *
 * <p>Clusters are emitted before the edges so every node keeps its cluster scope: in DOT a node
 * belongs to the cluster where it is first defined, and edges declared afterwards may freely
 * connect nodes across clusters. Edges keep the exact attribute format of the previous emitters
 * and reference the ids pre-resolved in the hierarchy.
 */
public final class NestedDotBuilder {

    private NestedDotBuilder() {}

    /**
     * Renders the hierarchy as nested DOT.
     *
     * @param hierarchy the nested package/class hierarchy
     * @param repoUrl repository URL prefix for class hyperlinks
     * @param includeHyperlinks whether class nodes carry {@code URL} attributes
     * @return DOT source for a strict digraph whose packages nest as clusters
     */
    public static String buildNestedDot(HierarchyDTO hierarchy, String repoUrl, boolean includeHyperlinks) {
        StringBuilder dot = new StringBuilder();
        dot.append("strict digraph G {\n");
        if (hierarchy != null) {
            if (hierarchy.getPackages() != null) {
                for (PackageNodeDTO pkg : hierarchy.getPackages()) {
                    renderPackage(pkg, repoUrl, includeHyperlinks, dot);
                }
            }
            if (hierarchy.getEdges() != null) {
                for (EdgeDTO edge : hierarchy.getEdges()) {
                    renderEdge(edge, dot);
                }
            }
        }
        dot.append("}");
        return dot.toString();
    }

    /**
     * Renders the package-map hierarchy as DOT for @hpcc-js/wasm-graphviz (the only renderer
     * that honors compound cluster edges): packages with children render once as labelled
     * clusters, childless packages render once as filled rectangles, and edges attach to
     * cluster-represented packages through invisible anchor nodes clipped to the cluster
     * borders via {@code ltail}/{@code lhead} under {@code compound=true}.
     *
     * @param hierarchy the package-map hierarchy
     * @return DOT source for a strict compound digraph
     */
    public static String buildPackageDot(HierarchyDTO hierarchy) {
        StringBuilder dot = new StringBuilder();
        dot.append("strict digraph G {\n");
        dot.append("compound=true;\n");
        if (hierarchy != null) {
            List<PackageNodeDTO> packages = hierarchy.getPackages() == null ? List.of() : hierarchy.getPackages();
            List<EdgeDTO> edges = hierarchy.getEdges() == null ? List.of() : hierarchy.getEdges();

            Set<String> referencedIds = new HashSet<>();
            Map<String, PackageNodeDTO> packagesByNodeId = new HashMap<>();
            for (EdgeDTO edge : edges) {
                referencedIds.add(edge.getSource());
                referencedIds.add(edge.getTarget());
            }
            indexPackagesByNodeId(packages, packagesByNodeId);

            for (PackageNodeDTO pkg : packages) {
                renderPackageGraphPackage(pkg, referencedIds, dot);
            }
            for (EdgeDTO edge : edges) {
                renderPackageGraphEdge(edge, packagesByNodeId, dot);
            }
        }
        dot.append("}");
        return dot.toString();
    }

    /** Indexes the tree's packages by their node ids so edges can resolve cluster endpoints. */
    private static void indexPackagesByNodeId(List<PackageNodeDTO> packages, Map<String, PackageNodeDTO> byNodeId) {
        for (PackageNodeDTO pkg : packages) {
            if (pkg.getNodeId() != null) {
                byNodeId.put(pkg.getNodeId(), pkg);
            }
            indexPackagesByNodeId(pkg.getChildren() == null ? List.of() : pkg.getChildren(), byNodeId);
        }
    }
    /**
     * Appends one package-map shape: childless packages as filled rectangles, packages with
     * children as labelled clusters whose invisible anchor is emitted only when an edge
     * references it.
     */
    private static void renderPackageGraphPackage(PackageNodeDTO pkg, Set<String> referencedIds, StringBuilder dot) {
        if (pkg.isRenderAsNode()) {
            dot.append(pkg.getNodeId())
                    .append(" [label=\"")
                    .append(HtmlReport.escapeDotQuoted(pkg.getName()))
                    .append("\" shape=rectangle style=filled fillcolor=")
                    .append(pkg.isNeedsBreakUp() ? "pink" : "white")
                    .append("];\n");
            return;
        }
        dot.append("subgraph ").append(pkg.getId()).append(" {\n");
        dot.append("label=\"").append(HtmlReport.escapeDotQuoted(pkg.getName())).append("\";\n");
        dot.append("style=filled fillcolor=")
                .append(pkg.isNeedsBreakUp() ? "pink" : "white")
                .append(";\n");
        if (pkg.getNodeId() != null && referencedIds.contains(pkg.getNodeId())) {
            dot.append(pkg.getNodeId()).append(" [shape=point style=invis];\n");
        }
        List<PackageNodeDTO> children = pkg.getChildren() == null ? List.of() : pkg.getChildren();
        for (PackageNodeDTO child : children) {
            renderPackageGraphPackage(child, referencedIds, dot);
        }
        dot.append("}\n");
    }

    /**
     * Appends one package relationship, clipping at the cluster border of any endpoint
     * whose package is cluster-represented.
     */
    private static void renderPackageGraphEdge(
            EdgeDTO edge, Map<String, PackageNodeDTO> packagesByNodeId, StringBuilder dot) {
        dot.append(edge.getSource()).append(" -> ").append(edge.getTarget());
        dot.append(" [ ");
        dot.append("label = \"").append(edge.getWeight()).append("\" ");
        dot.append("weight = \"").append(edge.getWeight()).append("\"");
        if (edge.isMarkedForRemoval()) {
            dot.append(" color = \"red\"");
        }
        PackageNodeDTO source = packagesByNodeId.get(edge.getSource());
        if (source != null && !source.isRenderAsNode()) {
            dot.append(" ltail = \"").append(source.getId()).append("\"");
        }
        PackageNodeDTO target = packagesByNodeId.get(edge.getTarget());
        if (target != null && !target.isRenderAsNode()) {
            dot.append(" lhead = \"").append(target.getId()).append("\"");
        }
        dot.append(" ];\n");
    }

    /** Appends one package cluster: label, explicit fill, its node, its classes, and children. */
    private static void renderPackage(
            PackageNodeDTO pkg, String repoUrl, boolean includeHyperlinks, StringBuilder dot) {
        dot.append("subgraph ").append(pkg.getId()).append(" {\n");
        dot.append("label=\"").append(HtmlReport.escapeDotQuoted(pkg.getName())).append("\";\n");
        // Every cluster declares an explicit fill: vizdom propagates a styled cluster's fill
        // to its nested cluster rects, so an unflagged child of a pink break-up cluster must
        // say white explicitly or it would inherit the pink. White is vizdom's default
        // cluster fill, so unflagged clusters look unchanged. In the package map the pink
        // signal belongs to the rectangle node, not its own cluster box.
        if (pkg.isNeedsBreakUp() && !pkg.isRenderAsNode()) {
            dot.append("style=filled fillcolor=pink;\n");
        } else {
            dot.append("style=filled fillcolor=white;\n");
        }
        if (pkg.isRenderAsNode()) {
            dot.append(pkg.getNodeId())
                    .append(" [label=\"")
                    .append(HtmlReport.escapeDotQuoted(pkg.getName()))
                    .append("\" shape=rectangle");
            if (pkg.isNeedsBreakUp()) {
                dot.append(" color=pink style=filled");
            }
            dot.append("];\n");
        }
        List<ClassNodeDTO> classes = pkg.getClasses() == null ? List.of() : pkg.getClasses();
        for (ClassNodeDTO cls : classes) {
            renderClassNode(cls, repoUrl, includeHyperlinks, dot);
        }
        List<PackageNodeDTO> children = pkg.getChildren() == null ? List.of() : pkg.getChildren();
        for (PackageNodeDTO child : children) {
            renderPackage(child, repoUrl, includeHyperlinks, dot);
        }
        dot.append("}\n");
    }

    /** Appends one class node: hyperlink, label, ellipse shape, and the red removal fill. */
    private static void renderClassNode(
            ClassNodeDTO cls, String repoUrl, boolean includeHyperlinks, StringBuilder dot) {
        dot.append(cls.getId()).append(" [");
        if (includeHyperlinks && cls.getPath() != null && !cls.getPath().isBlank()) {
            String url = (repoUrl == null ? "" : repoUrl) + cls.getPath();
            dot.append("URL=\"")
                    .append(HtmlReport.escapeDotQuoted(url))
                    .append("\"")
                    .append(" target=\"_blank\"");
        }
        if (!cls.getLabel().equals(cls.getId())) {
            dot.append(" label=\"")
                    .append(HtmlReport.escapeDotQuoted(cls.getLabel()))
                    .append("\"");
        }
        dot.append(" shape=ellipse");
        if (cls.isMarkedForRemoval()) {
            dot.append(" color=red style=filled");
        }
        dot.append("];\n");
    }

    /** Appends one weighted edge, red when the relationship is marked for removal. */
    private static void renderEdge(EdgeDTO edge, StringBuilder dot) {
        dot.append(edge.getSource()).append(" -> ").append(edge.getTarget());
        dot.append(" [ ");
        dot.append("label = \"").append(edge.getWeight()).append("\" ");
        dot.append("weight = \"").append(edge.getWeight()).append("\"");
        if (edge.isMarkedForRemoval()) {
            dot.append(" color = \"red\"");
        }
        dot.append(" ];\n");
    }
}

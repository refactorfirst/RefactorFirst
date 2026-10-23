package org.hjug.refactorfirst.report;

import java.util.List;
import org.hjug.refactorfirst.report.model.ClassNodeDTO;
import org.hjug.refactorfirst.report.model.EdgeDTO;
import org.hjug.refactorfirst.report.model.HierarchyDTO;
import org.hjug.refactorfirst.report.model.PackageNodeDTO;

/**
 * Renders a {@link HierarchyDTO} in two modes: the class/cycle maps as nested DOT (packages
 * become labelled clusters, classes become ellipse nodes inside their owning package cluster),
 * and the package map as the flat relationship graph — one ellipse per participating package
 * with only the relationship edges, matching the main-branch package map.
 *
 * <p>Visual contract: classes marked for removal keep the {@code color=red style=filled} fill
 * in the class/cycle maps; packages flagged for breakup fill red in the flat package map,
 * and relationships flagged for removal render red everywhere.
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

    /** Appends one package cluster: label, explicit fill, its node, its classes, and children. */
    private static void renderPackage(
            PackageNodeDTO pkg, String repoUrl, boolean includeHyperlinks, StringBuilder dot) {
        dot.append("subgraph ").append(pkg.getId()).append(" {\n");
        dot.append("label=\"").append(HtmlReport.escapeDotQuoted(pkg.getName())).append("\";\n");
        // Every cluster declares an explicit fill: vizdom propagates a styled cluster's fill
        // to its nested cluster rects, so an unflagged child of a pink break-up cluster must
        // say white explicitly or it would inherit the pink. White is vizdom's default
        // cluster fill, so unflagged clusters look unchanged.
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

    /**
     * Appends one class node: label, ellipse shape, the red removal fill, then the hyperlink.
     * The {@code URL}/{@code target} attributes come last — mirroring the package nodes — and
     * are emitted only when the constructed URL passes the http(s) validation the package
     * hyperlinks apply.
     */
    private static void renderClassNode(
            ClassNodeDTO cls, String repoUrl, boolean includeHyperlinks, StringBuilder dot) {
        dot.append(cls.getId()).append(" [");
        if (!cls.getLabel().equals(cls.getId())) {
            dot.append(" label=\"")
                    .append(HtmlReport.escapeDotQuoted(cls.getLabel()))
                    .append("\"");
        }
        dot.append(" shape=ellipse");
        if (cls.isMarkedForRemoval()) {
            dot.append(" color=red style=filled");
        }
        if (includeHyperlinks && cls.getPath() != null && !cls.getPath().isBlank()) {
            String url = (repoUrl == null ? "" : repoUrl) + cls.getPath();
            if (isHttpUrl(url)) {
                dot.append(" URL=\"").append(HtmlReport.escapeDotQuoted(url)).append("\" target=\"_blank\"");
            }
        }
        dot.append("];\n");
    }

    /**
     * Renders the package-map hierarchy as the flat relationship graph: every
     * package that participates in at least one relationship (or was selected for
     * removal to break cycles) renders once as an ellipse node carrying its name,
     * and the edges carry only the relationship weights — matching the
     * main-branch package map, rendered by vizdom like the class and cycle maps.
     * Packages flagged {@code needsBreakUp} fill red; relationships flagged for
     * removal render red. Packages with a source path also carry a hyperlink —
     * {@code URL} + {@code target="_blank"} — built from the repository URL
     * prefix; only http(s) URLs are ever emitted. The full hierarchy stays in
     * refactor-first.json for LLM consumption; only the relationship information
     * renders.
     *
     * @param hierarchy the package-map hierarchy
     * @param repoUrl repository URL prefix for package hyperlinks
     * @return DOT source for a strict digraph of ellipse package nodes and edges
     */
    public static String buildPackageDot(HierarchyDTO hierarchy, String repoUrl) {
        StringBuilder dot = new StringBuilder();
        dot.append("strict digraph G {\n");
        if (hierarchy != null) {
            List<PackageNodeDTO> packages = hierarchy.getPackages() == null ? List.of() : hierarchy.getPackages();
            renderFlatPackageNodes(packages, repoUrl, dot);
            List<EdgeDTO> edges = hierarchy.getEdges() == null ? List.of() : hierarchy.getEdges();
            for (EdgeDTO edge : edges) {
                renderEdge(edge, dot);
            }
        }
        dot.append("}");
        return dot.toString();
    }

    /** Appends one ellipse node for every package in the tree that carries a node id. */
    private static void renderFlatPackageNodes(List<PackageNodeDTO> packages, String repoUrl, StringBuilder dot) {
        for (PackageNodeDTO pkg : packages) {
            if (pkg.getNodeId() != null) {
                dot.append(pkg.getNodeId())
                        .append(" [label=\"")
                        .append(HtmlReport.escapeDotQuoted(pkg.getName()))
                        .append("\" shape=ellipse");
                if (pkg.isNeedsBreakUp()) {
                    dot.append(" color=red style=filled");
                }
                String url = packageHyperlinkUrl(pkg, repoUrl);
                if (url != null) {
                    dot.append(" URL=\"")
                            .append(HtmlReport.escapeDotQuoted(url))
                            .append("\" target=\"_blank\"");
                }
                dot.append("];\n");
            }
            renderFlatPackageNodes(pkg.getChildren() == null ? List.of() : pkg.getChildren(), repoUrl, dot);
        }
    }

    /**
     * Returns the package node's hyperlink target when the repository URL and the
     * package's source directory resolve to an http(s) URL; {@code null} otherwise.
     */
    private static String packageHyperlinkUrl(PackageNodeDTO pkg, String repoUrl) {
        if (pkg.getPath() == null || pkg.getPath().isEmpty()) {
            return null;
        }
        String url = (repoUrl == null ? "" : repoUrl) + pkg.getPath();
        return isHttpUrl(url) ? url : null;
    }

    /** Only http(s) URLs may become links. */
    private static boolean isHttpUrl(String url) {
        try {
            String scheme = java.net.URI.create(url).getScheme();
            return "http".equals(scheme) || "https".equals(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
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

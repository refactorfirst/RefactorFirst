package org.hjug.refactorfirst.report.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One package in the nested hierarchy.
 *
 * <p>Every emitted package is a rectangle: a labelled cluster in the class/cycle graphs, or a
 * rectangle node ({@code renderAsNode}) nested inside its ancestor clusters in the package
 * graph. Packages flagged with {@code needsBreakUp} are rendered with a pink fill rather than
 * the red previously used, distinguishing package-level breakup candidates from classes marked
 * for removal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageNodeDTO {

    /** Package name (FQN), or the synthetic {@code (default package)} bucket. */
    private String name;

    /** Pre-resolved DOT-safe cluster id ({@code cluster_<hex>}), stable and collision-free. */
    private String id;

    /** Pre-resolved DOT node id when this package renders as a node; {@code null} otherwise. */
    private String nodeId;

    /** Whether this package is a rectangle node in the package graph. */
    private boolean renderAsNode;

    /** Whether the package was selected for removal to break cycles (pink fill). */
    private boolean needsBreakUp;

    /** Child package clusters, sorted by name, with single-child chains collapsed. */
    @Builder.Default
    private List<PackageNodeDTO> children = new ArrayList<>();

    /** Classes owned by this package, sorted by FQN; empty in the package graph. */
    @Builder.Default
    private List<ClassNodeDTO> classes = new ArrayList<>();
}

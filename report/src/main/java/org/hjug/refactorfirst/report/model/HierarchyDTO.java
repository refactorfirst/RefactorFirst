package org.hjug.refactorfirst.report.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Nested package/class hierarchy that replaces raw DOT in refactor-first.json and drives every
 * nested package + class DOT graph.
 *
 * <p>Packages form a tree of clusters ({@link PackageNodeDTO}); classes ({@link ClassNodeDTO})
 * hang off their owning package; edges ({@link EdgeDTO}) reference the pre-resolved DOT node ids
 * so renderers never re-derive id or label logic.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HierarchyDTO {

    /** Root package clusters in sorted order, with single-child chains collapsed. */
    private List<PackageNodeDTO> packages;

    /** Edges referencing the resolved DOT node ids, sorted by source then target. */
    private List<EdgeDTO> edges;
}

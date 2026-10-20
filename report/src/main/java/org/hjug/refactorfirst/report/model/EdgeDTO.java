package org.hjug.refactorfirst.report.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One graph edge in the nested hierarchy, referencing the pre-resolved DOT node ids of its
 * endpoints (class node ids in the class/cycle graphs, package node ids in the package graph).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeDTO {

    /** DOT node id of the edge source. */
    private String source;

    /** DOT node id of the edge target. */
    private String target;

    /** Edge weight (relationship count), rendered as the edge label. */
    private int weight;

    /** Whether the relationship was selected for removal to break cycles (red edge). */
    private boolean markedForRemoval;
}

package org.hjug.refactorfirst.report.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One class in the nested hierarchy, resolved to its owning package cluster.
 *
 * <p>Carries the pre-resolved DOT node id and display label so renderers reuse the exact id
 * logic of the previous DOT emitters, including source-aware ids for Kotlin anonymous classes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassNodeDTO {

    /** Fully qualified class name (or literal anonymous vertex name). */
    private String fqn;

    /** Pre-resolved DOT-safe node id from the existing safe-id rules. */
    private String id;

    /** Display label: the simple class name, or the anonymous owner label. */
    private String label;

    /** Source file path relative to the project root, or an empty string when unknown. */
    private String path;

    /** Whether the class was selected for removal to break cycles (red fill). */
    private boolean markedForRemoval;
}

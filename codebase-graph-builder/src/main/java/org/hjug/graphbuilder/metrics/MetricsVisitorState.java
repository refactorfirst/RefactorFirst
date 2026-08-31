package org.hjug.graphbuilder.metrics;

/**
 * Mutable per-traversal state shared between concrete metrics visitors and
 * the static helpers in {@link MetricsVisitorLogic}. Lives outside the
 * concrete visitor classes so the J-level extraction logic can be shared
 * between Java and Kotlin source trees without inheritance coupling.
 *
 * <p>One {@code MetricsVisitorState} instance is allocated per traversal
 * (per compilation unit batch); it is reset as the visitor descends into
 * nested classes/methods via save/restore fields in {@link MetricsVisitorLogic}.
 */
public class MetricsVisitorState {
    public String currentPackageName;
    public String currentClassName;
    public String currentMethodSignature;
    public ClassMetrics currentClassMetrics;
    public MethodMetrics currentMethodMetrics;
    public String currentSourcePath;
}

package org.hjug.graphbuilder.metrics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import org.hjug.graphbuilder.DependencyCollector;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

@Getter
public class GraphMetricsCollector implements DependencyCollector {

    private final Graph<String, DefaultWeightedEdge> classGraph;
    private final Graph<String, DefaultWeightedEdge> packageGraph;
    private final Map<String, ClassMetrics> classMetrics = new HashMap<>();
    private final Map<String, String> classToSourceFileMapping = new HashMap<>();
    /**
     * Lazily-computed cache of {@link #hasKotlinMetrics()}; {@code null} means
     * "not yet computed". Set on first call and reused for subsequent calls
     * because {@link #finalizeMetrics()} and detection both run after the
     * visitor walk is complete and no further classes are added.
     */
    private Boolean hasKotlinMetricsCache;

    /** Maps callee full-qualified method signature → set of caller method signatures (CM). */
    private final Map<String, Set<String>> calleeToCallerMethods = new HashMap<>();
    /** Maps callee full-qualified method signature → set of caller class FQNs (CC). */
    private final Map<String, Set<String>> calleeToCallerClasses = new HashMap<>();

    public GraphMetricsCollector(
            Graph<String, DefaultWeightedEdge> classGraph, Graph<String, DefaultWeightedEdge> packageGraph) {
        this.classGraph = classGraph;
        this.packageGraph = packageGraph;
    }

    @Override
    public void addClassDependency(String fromClass, String toClass) {
        if (!classGraph.containsVertex(fromClass)) {
            classGraph.addVertex(fromClass);
        }
        if (!classGraph.containsVertex(toClass)) {
            classGraph.addVertex(toClass);
        }

        DefaultWeightedEdge edge = classGraph.getEdge(fromClass, toClass);
        if (edge == null) {
            edge = classGraph.addEdge(fromClass, toClass);
            if (edge != null) {
                classGraph.setEdgeWeight(edge, 1.0);
            }
        } else {
            double weight = classGraph.getEdgeWeight(edge);
            classGraph.setEdgeWeight(edge, weight + 1.0);
        }

        getOrCreateClassMetrics(fromClass).addDependency(toClass);
    }

    @Override
    public DefaultWeightedEdge addPackageDependency(String fromPackage, String toPackage) {
        if (!packageGraph.containsVertex(fromPackage)) {
            packageGraph.addVertex(fromPackage);
        }
        if (!packageGraph.containsVertex(toPackage)) {
            packageGraph.addVertex(toPackage);
        }

        DefaultWeightedEdge edge = packageGraph.getEdge(fromPackage, toPackage);
        if (edge == null) {
            edge = packageGraph.addEdge(fromPackage, toPackage);
            if (edge != null) {
                packageGraph.setEdgeWeight(edge, 1.0);
            }
        } else {
            double weight = packageGraph.getEdgeWeight(edge);
            packageGraph.setEdgeWeight(edge, weight + 1.0);
        }

        return edge;
    }

    @Override
    public void recordClassLocation(String classFqn, String sourceFilePath) {
        classToSourceFileMapping.put(classFqn, sourceFilePath);
    }

    @Override
    public void registerPackage(String packageName) {
        if (!packageGraph.containsVertex(packageName)) {
            packageGraph.addVertex(packageName);
        }
    }

    @Override
    public void registerClassVertex(String classFqn) {
        classGraph.addVertex(classFqn);
    }

    public Set<String> getPackagesInCodebase() {
        return packageGraph.vertexSet();
    }

    public void recordClassMetric(String className, String metricName, Object value) {
        // Pre-finalize path: runs in visitor order before finalizeMetrics()
        // flips the per-instance frozen flag. Mutation remains legal here.
        ClassMetrics metrics = getOrCreateClassMetrics(className);
        switch (metricName) {
            case "LOC":
                metrics.setLinesOfCode((Integer) value);
                break;
            case "NOA":
                metrics.setNumberOfAttributes((Integer) value);
                break;
            case "ATFD":
                metrics.setAccessToForeignData((Integer) value);
                break;
            case "TCC":
                metrics.setTightClassCohesion((Double) value);
                break;
            default:
                break;
        }
    }

    public void recordMethodMetric(String className, String methodSignature, String metricName, Object value) {
        ClassMetrics classMetrics = getOrCreateClassMetrics(className);
        MethodMetrics methodMetrics = classMetrics.getMethods().get(methodSignature);

        if (methodMetrics == null) {
            methodMetrics = new MethodMetrics(null, methodSignature);
            classMetrics.addMethod(methodMetrics);
        }

        switch (metricName) {
            case "LOC":
                methodMetrics.setLinesOfCode((Integer) value);
                break;
            case "CYCLO":
                methodMetrics.setCyclomaticComplexity((Integer) value);
                break;
            case "MAXNESTING":
                methodMetrics.setMaxNestingDepth((Integer) value);
                break;
            case "NOP":
                methodMetrics.setNumberOfParameters((Integer) value);
                break;
            default:
                break;
        }
    }

    /**
     * Returns {@code true} iff at least one collected {@link ClassMetrics}
     * carries a Kotlin-specific signal — i.e. {@link ClassMetrics#isDataClass()},
     * {@link ClassMetrics#isSealed()}, {@link ClassMetrics#getNumberOfExtensionFunctions()}
     * > 0, a non-empty {@link ClassMetrics#getExtensionReceiverTypes()}, or a
     * non-empty {@link ClassMetrics#getSealedHierarchyAncestors()}.
     *
     * <p>This is the gate for the Kotlin-specific disharmony detectors
     * ({@link DisharmonyDetector#detectExcessiveExtensions},
     * {@link DisharmonyDetector#detectLargeSealedHierarchy},
     * {@link DisharmonyDetector#detectDataClassWithLogic}) in the builder wiring:
     * for Java-only codebases it avoids three detector invocations — including
     * {@link DisharmonyDetector#detectLargeSealedHierarchy} which is O(N²) —
     * that would otherwise run over every Java build despite being unable to
     * flag a Java class. The Kotlin-only metric flags are {@code false}/0/empty
     * for every Java class, so when this method returns {@code false} the
     * detectors would have returned empty lists anyway — the gate is safe by
     * construction.
     *
     * <p>The result is cached after the first call. Callers should invoke this
     * only after {@link #finalizeMetrics()} has run (detection always runs
     * post-finalization); before finalization the cached value is still
     * computed on demand but may not reflect {@link #computeKotlinDerivedMetrics()}
     * derived flags such as {@link ClassMetrics#isHasExplicitLogic()}.
     *
     * @return {@code true} if any class carries a Kotlin-specific metric signal,
     *         {@code false} for an empty or Java-only collector
     */
    public boolean hasKotlinMetrics() {
        if (hasKotlinMetricsCache != null) {
            return hasKotlinMetricsCache;
        }
        boolean found = false;
        for (ClassMetrics metrics : classMetrics.values()) {
            if (metrics.isDataClass()
                    || metrics.isSealed()
                    || metrics.getNumberOfExtensionFunctions() > 0
                    || !metrics.getExtensionReceiverTypes().isEmpty()
                    || !metrics.getSealedHierarchyAncestors().isEmpty()) {
                found = true;
                break;
            }
        }
        hasKotlinMetricsCache = found;
        return found;
    }

    /**
     * Read-only lookup of a class's metrics. Returns {@code null} when the
     * class has never been registered with this collector. Prefer
     * {@link #getOrCreateClassMetrics(String)} from visitor logic that
     * intends to mutate the returned instance and have the mutation
     * reflected by {@link #getAllClassMetrics()}.
     */
    public ClassMetrics getClassMetrics(String className) {
        return classMetrics.get(className);
    }

    public Map<String, ClassMetrics> getAllClassMetrics() {
        return classMetrics;
    }

    public void recordIncomingCall(String calleeFqnSig, String callerClassFqn, String callerMethodSig) {
        calleeToCallerMethods
                .computeIfAbsent(calleeFqnSig, k -> new HashSet<>())
                .add(callerMethodSig);
        calleeToCallerClasses
                .computeIfAbsent(calleeFqnSig, k -> new HashSet<>())
                .add(callerClassFqn);
    }

    public void finalizeMetrics() {
        for (ClassMetrics metrics : classMetrics.values()) {
            metrics.calculateAccessToForeignData();
            metrics.calculateTightClassCohesion();
            // Populate CM/CC (Changing Methods / Changing Classes) for each method
            for (MethodMetrics method : metrics.getMethods().values()) {
                String calleeFqnSig = metrics.getFullyQualifiedName() + "." + method.getSignature();
                Set<String> callerMethods = calleeToCallerMethods.get(calleeFqnSig);
                Set<String> callerClasses = calleeToCallerClasses.get(calleeFqnSig);
                if (callerMethods != null) {
                    callerMethods.forEach(method::addChangingMethod);
                }
                if (callerClasses != null) {
                    callerClasses.forEach(method::addChangingClass);
                }
            }
        }
        computeKotlinDerivedMetrics();
        //
        // Freeze-all pass (review item #9): every derived computation above
        // has finished for *all* instances before any instance is frozen.
        // computeKotlinDerivedMetrics() walks ancestor ClassMetrics
        // (setSealedHierarchyDepth / setHasExplicitLogic) so interleaving a
        // per-instance freeze with that pass would IllegalStateException on an
        // ancestor whose depth a descendant's computation tries to write. The
        // two-pass derive-all / freeze-all ordering is the only sound one.
        for (ClassMetrics metrics : classMetrics.values()) {
            for (MethodMetrics m : metrics.getMethods().values()) {
                m.freeze();
            }
            metrics.freeze();
        }
    }

    /**
     * Derived Kotlin metrics computed <em>after</em> the visitor walk because
     * they require the whole class population:
     *
     * <ul>
     *   <li>{@link ClassMetrics#setSealedHierarchyDepth(int)} — root sealed
     *       class has depth 1; each direct permittee inherits depth 2, and
     *       so on. Computed by walking up the sealed-hierarchy ancestor
     *       chain until reaching a class whose {@link ClassMetrics#isSealed()}
     *       flag is {@code false}.</li>
     *   <li>{@link ClassMetrics#setHasExplicitLogic(boolean)} — true when a
     *       Kotlin {@code data class} declares any non-accessor method,
     *       feeding the {@code (hasExplicitLogic || WMC > 14)} criterion of
     *       {@code Data Class with Logic}.</li>
     * </ul>
     *
     * Idempotent: safe to call multiple times.
     */
    private void computeKotlinDerivedMetrics() {
        for (ClassMetrics metrics : classMetrics.values()) {
            if (metrics.isDataClass()) {
                boolean hasNonAccessor =
                        metrics.getMethods().values().stream().anyMatch(m -> !m.isAccessor() && !m.isConstructor());
                metrics.setHasExplicitLogic(hasNonAccessor);
            }
            int depth = computeSealedDepth(metrics);
            if (depth > 0) {
                metrics.setSealedHierarchyDepth(depth);
            }
        }
    }

    private int computeSealedDepth(ClassMetrics metrics) {
        if (metrics.isSealed()) {
            return 1;
        }
        Set<String> ancestors = metrics.getSealedHierarchyAncestors();
        if (ancestors.isEmpty()) {
            return 0;
        }
        // Find first ancestor that is itself sealed; derive depth as
        // ancestor_depth + 1 (recursing through indirection).
        int maxAncestorDepth = 0;
        for (String ancestorFqn : ancestors) {
            ClassMetrics ancestor = classMetrics.get(ancestorFqn);
            if (ancestor == null) {
                // Ancestor not in this codebase batch (third-party): treat sealed
                // hierarchy membership as depth 2 when at least one ancestor is
                // observable as sealed (records the relationship).
                continue;
            }
            if (ancestor.isSealed()) {
                maxAncestorDepth = Math.max(maxAncestorDepth, computeSealedDepth(ancestor) + 1);
            }
        }
        return maxAncestorDepth;
    }

    /**
     * Canonical get-or-create entry point used by {@link MetricsVisitorLogic}
     * and the metrics-collecting visitors. Returns the existing
     * {@link ClassMetrics} for {@code className} if present, otherwise
     * creates one, stores it in {@link #getAllClassMetrics()}, and returns
     * it.
     * <p>The returned instance is the <em>same object</em> later returned by
     * {@link #getAllClassMetrics()}. This is the invariant the historical
     * {@code instanceof GraphMetricsCollector} branch in
     * {@link MetricsVisitorLogic#enterClass} emulated: the
     * {@link ClassMetrics} the visitor mutates during the walk is the
     * instance the downstream disharmony detectors read from
     * {@link #getAllClassMetrics()}. Any get-or-create path that builds an
     * instance without storing it would silently discard every class's
     * metrics.
     */
    public ClassMetrics getOrCreateClassMetrics(String className) {
        return classMetrics.computeIfAbsent(className, ClassMetrics::new);
    }
}

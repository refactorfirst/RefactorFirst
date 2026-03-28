package org.hjug.graphbuilder.metrics;

import java.util.Map;
import org.hjug.graphbuilder.DependencyCollector;

public interface MetricsCollector extends DependencyCollector {

    void recordClassMetric(String className, String metricName, Object value);

    void recordMethodMetric(String className, String methodSignature, String metricName, Object value);

    /** Record that callerMethodSig (in callerClassFqn) calls the method identified by calleeFqnSig. */
    default void recordIncomingCall(String calleeFqnSig, String callerClassFqn, String callerMethodSig) {
        // no-op default for implementations that don't track incoming calls
    }

    ClassMetrics getClassMetrics(String className);

    Map<String, ClassMetrics> getAllClassMetrics();

    void finalizeMetrics();
}

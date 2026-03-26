package org.hjug.graphbuilder.metrics;

import java.util.Map;
import org.hjug.graphbuilder.DependencyCollector;

public interface MetricsCollector extends DependencyCollector {

    void recordClassMetric(String className, String metricName, Object value);

    void recordMethodMetric(String className, String methodSignature, String metricName, Object value);

    ClassMetrics getClassMetrics(String className);

    Map<String, ClassMetrics> getAllClassMetrics();

    void finalizeMetrics();
}

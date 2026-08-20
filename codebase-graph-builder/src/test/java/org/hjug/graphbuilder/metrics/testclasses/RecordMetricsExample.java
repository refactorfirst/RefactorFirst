package org.hjug.graphbuilder.metrics.testclasses;

import java.util.List;

public record RecordMetricsExample(String name, int value, List<String> tags) {
    public String getDisplayName() {
        return name + " - " + value;
    }

    public static RecordMetricsExample create() {
        return new RecordMetricsExample("test", 42, List.of("a", "b"));
    }
}

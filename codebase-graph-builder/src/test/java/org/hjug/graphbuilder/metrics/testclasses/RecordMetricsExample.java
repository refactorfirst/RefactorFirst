package org.hjug.graphbuilder.metrics.testclasses;

public record RecordMetricsExample(String name, int value, java.util.List<String> tags) {
    public String getDisplayName() {
        return name + " - " + value;
    }

    public static RecordMetricsExample create() {
        return new RecordMetricsExample("test", 42, java.util.List.of("a", "b"));
    }
}

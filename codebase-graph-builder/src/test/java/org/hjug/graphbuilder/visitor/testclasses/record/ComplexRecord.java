package org.hjug.graphbuilder.visitor.testclasses.record;

import java.util.List;
import java.util.Map;

public record ComplexRecord(
        String name, List<String> tags, Map<String, Integer> counts, NestedRecord nested, SimpleRecord[] simpleArray) {
    public record NestedRecord(int id, String data) {}
}

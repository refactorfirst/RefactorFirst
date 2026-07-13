package org.hjug.graphbuilder.visitor.testclasses.record;

import java.util.List;

public record GenericRecord<T extends Comparable<T>>(T value, List<T> values) {}

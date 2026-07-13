package org.hjug.graphbuilder.visitor.testclasses.record;

import java.io.Serializable;

public record AnnotatedRecord(@Deprecated String deprecatedField, String normalField) implements Serializable {

    public AnnotatedRecord {
        if (normalField == null) {
            throw new IllegalArgumentException("normalField cannot be null");
        }
    }

    public String getNormalField() {
        return normalField.toUpperCase();
    }

    public static AnnotatedRecord create(String deprecatedField, String normalField) {
        return new AnnotatedRecord(deprecatedField, normalField);
    }
}

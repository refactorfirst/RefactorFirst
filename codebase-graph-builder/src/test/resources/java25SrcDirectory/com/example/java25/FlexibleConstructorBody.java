package com.example.java25;

/**
 * Java 25 language feature (JEP 513, final in 25): flexible constructor
 * bodies — statements may appear before an explicit super() invocation.
 * Parsers constrained to source level 21 or earlier reject this file.
 */
public class FlexibleConstructorBody {

    private final int value;

    /**
     * Creates an instance after validating the constructor argument before delegation.
     *
     * @param raw the non-negative value to retain
     */
    public FlexibleConstructorBody(int raw) {
        if (raw < 0) {
            throw new IllegalArgumentException("value must not be negative");
        }
        // Statement before explicit constructor invocation: Java 25 (JEP 513).
        super();
        this.value = raw;
    }

    /** Returns the value supplied at construction time. */
    public int getValue() {
        return value;
    }
}

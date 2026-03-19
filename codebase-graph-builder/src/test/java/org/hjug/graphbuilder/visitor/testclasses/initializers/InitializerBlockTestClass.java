package org.hjug.graphbuilder.visitor.testclasses.initializers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InitializerBlockTestClass {

    private List<String> items;
    private Map<String, Integer> counters;
    private StringBuilder builder;

    // Instance initializer block
    {
        items = new ArrayList<>();
        counters = new HashMap<>();
        builder = new StringBuilder("Initialized");
    }

    // Static initializer block
    static {
        System.out.println("Static initializer");
    }

    // Another instance initializer block with method invocations
    {
        items.add("default");
        counters.put("default", 0);
        builder.append(" with defaults");
    }

    public InitializerBlockTestClass() {
        // Constructor
    }
}

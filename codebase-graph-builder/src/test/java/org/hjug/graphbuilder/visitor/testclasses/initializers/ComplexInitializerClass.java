package org.hjug.graphbuilder.visitor.testclasses.initializers;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ComplexInitializerClass {

    private static ConcurrentHashMap<String, String> staticCache;
    private static AtomicInteger instanceCounter;

    private DataProcessor processor;
    private HelperService helper;

    // Static initializer with new class instantiations
    static {
        staticCache = new ConcurrentHashMap<>();
        instanceCounter = new AtomicInteger(0);
        staticCache.put("initialized", "true");
    }

    // Instance initializer with dependencies
    {
        processor = new DataProcessor();
        helper = new HelperService();
        instanceCounter.incrementAndGet();
    }

    // Another static initializer
    static {
        staticCache.put("version", "1.0");
    }

    public void process() {
        processor.execute();
    }

    static class DataProcessor {
        public void execute() {}
    }

    static class HelperService {}
}

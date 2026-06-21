package org.hjug.graphbuilder.metrics.testclasses.javadoc;

/**
 * Class whose methods reference external fields only in Javadoc.
 */
public class MethodWithJavadocReference {

    /**
     * Does nothing in its body.
     * See {@link JavaDocServiceClass#serviceName} for context.
     */
    public void doSomething() {
        // intentionally empty - JavaDocServiceClass.serviceName is not accessed here
    }
}

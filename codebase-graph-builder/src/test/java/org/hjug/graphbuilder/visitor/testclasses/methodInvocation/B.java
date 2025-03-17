package org.hjug.graphbuilder.visitor.testclasses.methodInvocation;

public class B<T> {

    static <T extends B> A invocationTest(T type) {
        return new A();
    }
}

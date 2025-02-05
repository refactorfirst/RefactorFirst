package org.hjug.parser.visitor.testclasses.methodInvocation;

public class B<T> {

    static <T extends B> A invocationTest(T type) {
        return new A();
    }
}

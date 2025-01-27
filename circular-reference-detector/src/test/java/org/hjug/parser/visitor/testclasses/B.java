package org.hjug.parser.visitor.testclasses;

public class B<T> {

    static <T extends B> D invocationTest(T type) {
        return new D();
    }

    static class InnerB extends A{}

}
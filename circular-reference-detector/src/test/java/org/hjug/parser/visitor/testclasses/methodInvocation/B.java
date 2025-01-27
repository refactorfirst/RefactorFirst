package org.hjug.parser.visitor.testclasses.methodInvocation;

public class B<T> {

    static <T extends B> void invocationTest(T type) {}

}
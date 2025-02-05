package org.hjug.parser.visitor.testclasses.methodInvocation;

public class A {

    A doSomething() {
        B.<C>invocationTest(new D());
        A a = B.<C>invocationTest(new D());
        // TODO: add visitor for J.ReturnType
        return B.<C>invocationTest(new D());
    }
}

package org.hjug.parser.visitor.testclasses.methodInvocation;

public class A {

    void doSomething() {
        B.<H>invocationTest(new H());
    }
}

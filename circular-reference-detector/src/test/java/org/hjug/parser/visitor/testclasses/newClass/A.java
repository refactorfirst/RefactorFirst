package org.hjug.parser.visitor.testclasses.newClass;

public class A {

    B newClassMethod() {
        D d = null;
        new D();
        C c = new C();
        return new B(c, d);
    }
}

package org.hjug.parser.visitor.testclasses.newClass;

public class A {

    B newClassMethod() {
        new C();
        C c = new C();

        // TODO: add visitor for J.ReturnType
        return new B(c);
    }
}

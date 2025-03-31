package org.hjug.graphbuilder.visitor.testclasses.newClass;

import java.util.ArrayList;
import java.util.List;

public class A {

    B newClassMethod() {
        new C();
        C c = new C();

        // var treated like "B", counts as 2
        var b = new B(null);
        // <> treated like <B>, counts as 2
        List<B> listB = new ArrayList<>();

        // TODO: add visitor for J.ReturnType
        return new B(c);
    }
}

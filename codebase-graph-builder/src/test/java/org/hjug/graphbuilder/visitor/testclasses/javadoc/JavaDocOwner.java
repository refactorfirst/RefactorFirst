package org.hjug.graphbuilder.visitor.testclasses.javadoc;

import java.util.ArrayList;
import java.util.List;

/**
 * A class that references {@link JavaDocSibling} only in documentation.
 */
public class JavaDocOwner {
    private List<String> items = new ArrayList<>();

    /**
     * Does something unrelated to JavaDocSibling.
     * See also {@link JavaDocSibling#publicMethod()} for a related operation.
     *
     * @param name the name
     */
    public void doSomething(String name) {
        items.add(name);
    }
}

package org.hjug.graphbuilder.visitor.testclasses.anonymous;

/** Dependency of {@link AnonymousOwner}; also the superclass of the anonymous subclass fixture. */
public class AnonymousTarget {

    public String runIt() {
        return "from-target";
    }
}

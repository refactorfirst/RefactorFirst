package org.hjug.graphbuilder.visitor.testclasses.tryCatch;

public class TryCatchOwner {
    public void method() {
        try {
            // nothing
            throw new CaughtDependency();
        } catch (CaughtDependency e) {
            // nothing
        }
    }
}

package org.hjug.graphbuilder.visitor.testclasses.lambda;

public class HelperClass {

    public String process(String input) {
        return input.toUpperCase();
    }

    public static String staticProcess(String input) {
        return input.toLowerCase();
    }
}

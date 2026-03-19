package org.hjug.graphbuilder.visitor.testclasses.lambda;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LambdaTestClass {

    private List<String> items = new ArrayList<>();
    private HelperClass helper = new HelperClass();

    public void processWithLambda() {
        // Lambda with method invocation on helper class
        items.forEach(item -> helper.process(item));

        // Lambda with multiple method invocations
        items.stream().map(s -> s.toUpperCase()).filter(s -> s.length() > 5).collect(Collectors.toList());

        // Lambda with new class instantiation - creates dependency on DataProcessor
        items.stream().map(s -> new DataProcessor().transform(s)).collect(Collectors.toList());

        // Lambda with new StringBuilder instantiation
        items.stream().map(s -> new StringBuilder(s)).collect(Collectors.toList());

        // Nested lambda
        items.stream()
                .map(s -> s.chars().mapToObj(c -> String.valueOf((char) c)).collect(Collectors.joining()))
                .collect(Collectors.toList());

        // Lambda with static method reference
        items.stream().map(HelperClass::staticProcess).forEach(System.out::println);

        // Lambda with type cast
        items.stream().map(s -> (CharSequence) s).collect(Collectors.toList());
    }

    public void lambdaWithLocalVariable() {
        items.forEach(item -> {
            DataProcessor processor = new DataProcessor();
            String processed = processor.transform(item);
            System.out.println(processed);
        });
    }
}

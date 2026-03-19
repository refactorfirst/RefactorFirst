package org.hjug.graphbuilder.visitor.testclasses.lambda;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NestedLambdaTestClass {

    private List<List<String>> nestedItems = new ArrayList<>();
    private HelperClass helper = new HelperClass();

    public void processNestedLambdas() {
        // Nested lambda with DataProcessor instantiation in inner lambda
        nestedItems.stream()
                .map(innerList -> innerList.stream()
                        .map(s -> new DataProcessor().transform(s))
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());

        // Nested lambda with HelperClass method invocation in inner lambda
        nestedItems.stream()
                .flatMap(innerList -> innerList.stream().map(s -> helper.process(s)))
                .collect(Collectors.toList());

        // Triple nested lambda with multiple dependencies
        nestedItems.stream()
                .map(outerList -> outerList.stream()
                        .map(middleItem -> middleItem
                                .chars()
                                .mapToObj(c -> new DataProcessor().transform(String.valueOf((char) c)))
                                .collect(Collectors.joining()))
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    public void deeplyNestedLambdaWithNewClass() {
        // Deeply nested lambda creating new instances at each level
        nestedItems.stream()
                .map(level1 -> {
                    DataProcessor processor1 = new DataProcessor();
                    return level1.stream()
                            .map(level2 -> {
                                HelperClass helper2 = new HelperClass();
                                return helper2.process(processor1.transform(level2));
                            })
                            .collect(Collectors.toList());
                })
                .collect(Collectors.toList());
    }
}

package org.hjug.graphbuilder.visitor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.hjug.graphbuilder.GraphDependencyCollector;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;

class JavaLambdaVisitorTest {

    @Test
    void visitLambdaBodiesRecursively() throws IOException {

        String pathString = "src/test/java/org/hjug/graphbuilder/visitor/testclasses/lambda";
        File srcDirectory = new File(pathString);

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        Graph<String, DefaultWeightedEdge> classReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graph<String, DefaultWeightedEdge> packageReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphDependencyCollector dependencyCollector =
                new GraphDependencyCollector(classReferencesGraph, packageReferencesGraph);

        String repo = srcDirectory.toURI().toString().replace("/" + pathString, "");
        JavaVisitor<ExecutionContext> classDeclarationVisitor = new JavaVisitor<>(repo, dependencyCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            classDeclarationVisitor.visit(cu, ctx);
        });

        // Verify that the main test class is in the graph
        Assertions.assertTrue(
                classReferencesGraph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.lambda.LambdaTestClass"),
                "LambdaTestClass should be in the graph");

        // Verify that HelperClass is captured as a dependency
        // This is from field declaration AND from lambda body: helper.process(item)
        Assertions.assertTrue(
                classReferencesGraph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.lambda.HelperClass"),
                "HelperClass should be captured from lambda body method invocation");

        // Verify edge from LambdaTestClass to HelperClass exists
        Assertions.assertTrue(
                classReferencesGraph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.LambdaTestClass",
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.HelperClass"),
                "Should have edge from LambdaTestClass to HelperClass");

        // Verify that DataProcessor is captured from lambda body: new DataProcessor()
        Assertions.assertTrue(
                classReferencesGraph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.lambda.DataProcessor"),
                "DataProcessor should be captured from new class instantiation in lambda body");

        // Verify edge from LambdaTestClass to DataProcessor exists
        Assertions.assertTrue(
                classReferencesGraph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.LambdaTestClass",
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.DataProcessor"),
                "Should have edge from LambdaTestClass to DataProcessor from lambda body");

        // Verify that StringBuilder is captured from lambda body: new StringBuilder(s)
        Assertions.assertTrue(
                classReferencesGraph.containsVertex("java.lang.StringBuilder"),
                "StringBuilder should be captured from new class instantiation in lambda body");

        // Verify edge from LambdaTestClass to StringBuilder exists
        Assertions.assertTrue(
                classReferencesGraph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.LambdaTestClass", "java.lang.StringBuilder"),
                "Should have edge from LambdaTestClass to StringBuilder from lambda body");

        // Verify that String is captured (from method invocations like s.toUpperCase())
        Assertions.assertTrue(
                classReferencesGraph.containsVertex("java.lang.String"),
                "String should be captured from method invocations in lambda body");

        // Verify edge weight - multiple lambda usages should increase edge weight
        DefaultWeightedEdge edge = classReferencesGraph.getEdge(
                "org.hjug.graphbuilder.visitor.testclasses.lambda.LambdaTestClass",
                "org.hjug.graphbuilder.visitor.testclasses.lambda.DataProcessor");

        // DataProcessor is used twice: once in processWithLambda() and once in lambdaWithLocalVariable()
        Assertions.assertTrue(
                classReferencesGraph.getEdgeWeight(edge) >= 2.0,
                "Edge weight should reflect multiple uses of DataProcessor in lambda bodies");
    }

    @Test
    void visitNestedLambdaBodiesRecursively() throws IOException {

        String pathString = "src/test/java/org/hjug/graphbuilder/visitor/testclasses/lambda";
        File srcDirectory = new File(pathString);

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        Graph<String, DefaultWeightedEdge> classReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graph<String, DefaultWeightedEdge> packageReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        GraphDependencyCollector dependencyCollector =
                new GraphDependencyCollector(classReferencesGraph, packageReferencesGraph);

        String repo = srcDirectory.toURI().toString().replace("/" + pathString, "");
        JavaVisitor<ExecutionContext> classDeclarationVisitor = new JavaVisitor<>(repo, dependencyCollector);

        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> {
            classDeclarationVisitor.visit(cu, ctx);
        });

        // Verify that the nested lambda test class is in the graph
        Assertions.assertTrue(
                classReferencesGraph.containsVertex(
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.NestedLambdaTestClass"),
                "NestedLambdaTestClass should be in the graph");

        // Verify DataProcessor is captured from INNER lambda: new DataProcessor() inside nested lambda
        Assertions.assertTrue(
                classReferencesGraph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.lambda.DataProcessor"),
                "DataProcessor should be captured from inner nested lambda body");

        // Verify edge from NestedLambdaTestClass to DataProcessor exists
        Assertions.assertTrue(
                classReferencesGraph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.NestedLambdaTestClass",
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.DataProcessor"),
                "Should have edge from NestedLambdaTestClass to DataProcessor from nested lambda");

        // Verify HelperClass is captured from nested lambda method invocation
        Assertions.assertTrue(
                classReferencesGraph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.lambda.HelperClass"),
                "HelperClass should be captured from nested lambda method invocation");

        // Verify edge from NestedLambdaTestClass to HelperClass exists
        Assertions.assertTrue(
                classReferencesGraph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.NestedLambdaTestClass",
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.HelperClass"),
                "Should have edge from NestedLambdaTestClass to HelperClass from nested lambda");

        // Verify edge weight reflects multiple nested lambda usages
        DefaultWeightedEdge dataProcessorEdge = classReferencesGraph.getEdge(
                "org.hjug.graphbuilder.visitor.testclasses.lambda.NestedLambdaTestClass",
                "org.hjug.graphbuilder.visitor.testclasses.lambda.DataProcessor");

        // DataProcessor is used in multiple nested lambdas: processNestedLambdas() and deeplyNestedLambdaWithNewClass()
        Assertions.assertTrue(
                classReferencesGraph.getEdgeWeight(dataProcessorEdge) >= 2.0,
                "Edge weight should reflect multiple uses of DataProcessor in nested lambda bodies");

        // Verify that deeply nested instantiations are captured
        DefaultWeightedEdge helperEdge = classReferencesGraph.getEdge(
                "org.hjug.graphbuilder.visitor.testclasses.lambda.NestedLambdaTestClass",
                "org.hjug.graphbuilder.visitor.testclasses.lambda.HelperClass");

        // HelperClass is used in field declaration and in nested lambdas
        Assertions.assertTrue(
                classReferencesGraph.getEdgeWeight(helperEdge) >= 2.0,
                "Edge weight should reflect HelperClass usage in nested lambda blocks");
    }
}

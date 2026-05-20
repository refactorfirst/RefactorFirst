package org.hjug.graphbuilder.visitor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.hjug.graphbuilder.DependencyCollector;
import org.hjug.graphbuilder.GraphDependencyCollector;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;

class JavaVisitorTest {

    private static final String TESTCLASSES = "src/test/java/org/hjug/graphbuilder/visitor/testclasses";
    private static final String LAMBDA = TESTCLASSES + "/lambda";
    private static final String METHOD_INVOCATION = TESTCLASSES + "/methodInvocation";
    private static final String NEW_CLASS = TESTCLASSES + "/newClass";
    private static final String INITIALIZERS = TESTCLASSES + "/initializers";

    private static String repoFrom(String pathString) {
        return new File(pathString).toURI().toString().replace("/" + pathString, "");
    }

    private static void visitAll(JavaVisitor<ExecutionContext> visitor, String pathString) throws IOException {
        File srcDirectory = new File(pathString);
        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);
        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser.parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx).forEach(cu -> visitor.visit(cu, ctx));
    }

    private static Graph<String, DefaultWeightedEdge> buildAndVisit(String pathString) throws IOException {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graph<String, DefaultWeightedEdge> pkgGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);
        JavaVisitor<ExecutionContext> visitor = new JavaVisitor<>(repoFrom(pathString), collector);
        visitAll(visitor, pathString);
        return classGraph;
    }

    private static Graph<String, DefaultWeightedEdge> buildAndVisitSimple(String pathString) throws IOException {
        Graph<String, DefaultWeightedEdge> classGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graph<String, DefaultWeightedEdge> pkgGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);
        JavaVisitor<ExecutionContext> visitor = new JavaVisitor<>(repoFrom(pathString), collector);
        visitAll(visitor, pathString);
        return classGraph;
    }

    private static JavaVisitor<ExecutionContext> buildVisitor(String repo) {
        GraphDependencyCollector collector = new GraphDependencyCollector(
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class));
        return new JavaVisitor<>(repo, collector);
    }

    @Test
    void visitClasses_registersExpectedPackageCount() throws IOException {
        File srcDirectory = new File(TESTCLASSES);
        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);
        GraphDependencyCollector dependencyCollector = new GraphDependencyCollector(
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class));
        JavaVisitor<ExecutionContext> javaVisitor = new JavaVisitor<>(repoFrom(TESTCLASSES), dependencyCollector);
        List<Path> list = Files.walk(Paths.get(srcDirectory.getAbsolutePath())).collect(Collectors.toList());
        javaParser
                .parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx)
                .forEach(cu -> javaVisitor.visit(cu, ctx));
        assertEquals(5, dependencyCollector.getPackagesInCodebase().size());
    }

    @Test
    void instanceInnerClassFqnIsTrackedInMapping() throws IOException {
        JavaVisitor<ExecutionContext> visitor = buildVisitor(repoFrom(TESTCLASSES));
        visitAll(visitor, TESTCLASSES);
        assertTrue(visitor.getClassToSourceFilePathMapping()
                .containsKey("org.hjug.graphbuilder.visitor.testclasses.A$InnerClass"));
    }

    @Test
    void staticNestedClassFqnIsTrackedInMapping() throws IOException {
        JavaVisitor<ExecutionContext> visitor = buildVisitor(repoFrom(TESTCLASSES));
        visitAll(visitor, TESTCLASSES);
        assertTrue(visitor.getClassToSourceFilePathMapping()
                .containsKey("org.hjug.graphbuilder.visitor.testclasses.A$StaticInnerClass"));
    }

    @Test
    void deeplyNestedInnerClassFqnIsTrackedInMapping() throws IOException {
        JavaVisitor<ExecutionContext> visitor = buildVisitor(repoFrom(TESTCLASSES));
        visitAll(visitor, TESTCLASSES);
        assertTrue(visitor.getClassToSourceFilePathMapping()
                .containsKey("org.hjug.graphbuilder.visitor.testclasses.A$InnerClass$InnerInner$MegaInner"));
    }

    @Test
    void innerClassPathIsSimplifiedFromFqnWhenRepoContainsJunitDash() throws IOException {
        JavaVisitor<ExecutionContext> visitor = buildVisitor("/tmp/junit-fake-repo");
        visitAll(visitor, TESTCLASSES);
        assertEquals(
                "org/hjug/graphbuilder/visitor/testclasses/A.java",
                visitor.getClassToSourceFilePathMapping()
                        .get("org.hjug.graphbuilder.visitor.testclasses.A$InnerClass"));
    }

    @Test
    void recordClassLocationIsCalledForEachInnerClass() throws IOException {
        DependencyCollector mockCollector = mock(DependencyCollector.class);
        JavaVisitor<ExecutionContext> visitor = new JavaVisitor<>(repoFrom(TESTCLASSES), mockCollector);
        visitAll(visitor, TESTCLASSES);
        verify(mockCollector)
                .recordClassLocation(eq("org.hjug.graphbuilder.visitor.testclasses.A$InnerClass"), anyString());
        verify(mockCollector)
                .recordClassLocation(eq("org.hjug.graphbuilder.visitor.testclasses.A$StaticInnerClass"), anyString());
    }

    @Test
    void visitClasses_capturesAllExpectedVertices() throws IOException {
        Graph<String, DefaultWeightedEdge> graph = buildAndVisit(TESTCLASSES);
        assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.A"));
        assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.B"));
        assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.C"));
        assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.D"));
        assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.E"));
        assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.F"));
        assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.G"));
        assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.MyAnnotation"));
    }

    @Test
    void visitMethodDeclarations_capturesMethodOwnerVertex() throws IOException {
        Graph<String, DefaultWeightedEdge> graph = buildAndVisit(TESTCLASSES);
        assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.A"));
    }

    @Test
    void visitLambdaBodiesRecursively() throws IOException {
        Graph<String, DefaultWeightedEdge> graph = buildAndVisit(LAMBDA);

        assertTrue(
                graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.lambda.LambdaTestClass"),
                "LambdaTestClass should be in the graph");
        assertTrue(
                graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.lambda.HelperClass"),
                "HelperClass should be captured from lambda body method invocation");
        assertTrue(
                graph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.LambdaTestClass",
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.HelperClass"),
                "Should have edge from LambdaTestClass to HelperClass");
        assertTrue(
                graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.lambda.DataProcessor"),
                "DataProcessor should be captured from new class instantiation in lambda body");
        assertTrue(
                graph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.LambdaTestClass",
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.DataProcessor"),
                "Should have edge from LambdaTestClass to DataProcessor from lambda body");
        assertTrue(
                graph.containsVertex("java.lang.StringBuilder"),
                "StringBuilder should be captured from new class instantiation in lambda body");
        assertTrue(
                graph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.LambdaTestClass", "java.lang.StringBuilder"),
                "Should have edge from LambdaTestClass to StringBuilder from lambda body");
        assertTrue(
                graph.containsVertex("java.lang.String"),
                "String should be captured from method invocations in lambda body");

        DefaultWeightedEdge edge = graph.getEdge(
                "org.hjug.graphbuilder.visitor.testclasses.lambda.LambdaTestClass",
                "org.hjug.graphbuilder.visitor.testclasses.lambda.DataProcessor");
        assertTrue(
                graph.getEdgeWeight(edge) >= 2.0,
                "Edge weight should reflect multiple uses of DataProcessor in lambda bodies");
    }

    @Test
    void visitNestedLambdaBodiesRecursively() throws IOException {
        Graph<String, DefaultWeightedEdge> graph = buildAndVisit(LAMBDA);

        assertTrue(
                graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.lambda.NestedLambdaTestClass"),
                "NestedLambdaTestClass should be in the graph");
        assertTrue(
                graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.lambda.DataProcessor"),
                "DataProcessor should be captured from inner nested lambda body");
        assertTrue(
                graph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.NestedLambdaTestClass",
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.DataProcessor"),
                "Should have edge from NestedLambdaTestClass to DataProcessor from nested lambda");
        assertTrue(
                graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.lambda.HelperClass"),
                "HelperClass should be captured from nested lambda method invocation");
        assertTrue(
                graph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.NestedLambdaTestClass",
                        "org.hjug.graphbuilder.visitor.testclasses.lambda.HelperClass"),
                "Should have edge from NestedLambdaTestClass to HelperClass from nested lambda");

        DefaultWeightedEdge dataProcessorEdge = graph.getEdge(
                "org.hjug.graphbuilder.visitor.testclasses.lambda.NestedLambdaTestClass",
                "org.hjug.graphbuilder.visitor.testclasses.lambda.DataProcessor");
        assertTrue(
                graph.getEdgeWeight(dataProcessorEdge) >= 2.0,
                "Edge weight should reflect multiple uses of DataProcessor in nested lambda bodies");

        DefaultWeightedEdge helperEdge = graph.getEdge(
                "org.hjug.graphbuilder.visitor.testclasses.lambda.NestedLambdaTestClass",
                "org.hjug.graphbuilder.visitor.testclasses.lambda.HelperClass");
        assertTrue(
                graph.getEdgeWeight(helperEdge) >= 2.0,
                "Edge weight should reflect HelperClass usage in nested lambda blocks");
    }

    @Test
    void visitMethodInvocations() throws IOException {
        Graph<String, DefaultWeightedEdge> graph = buildAndVisitSimple(METHOD_INVOCATION);

        assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.methodInvocation.A"));
        assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.methodInvocation.B"));
        assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.methodInvocation.C"));
        assertEquals(
                3,
                graph.getEdgeWeight(graph.getEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.methodInvocation.A",
                        "org.hjug.graphbuilder.visitor.testclasses.methodInvocation.B")));
        assertEquals(
                3,
                graph.getEdgeWeight(graph.getEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.methodInvocation.A",
                        "org.hjug.graphbuilder.visitor.testclasses.methodInvocation.C")));
    }

    @Test
    void visitNewClass() throws IOException {
        Graph<String, DefaultWeightedEdge> graph = buildAndVisitSimple(NEW_CLASS);

        assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.newClass.A"));
        assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.newClass.B"));
        assertTrue(graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.newClass.C"));
        assertEquals(
                6,
                graph.getEdgeWeight(graph.getEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.newClass.A",
                        "org.hjug.graphbuilder.visitor.testclasses.newClass.B")));
        assertEquals(
                3,
                graph.getEdgeWeight(graph.getEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.newClass.A",
                        "org.hjug.graphbuilder.visitor.testclasses.newClass.C")));
    }

    @Test
    void visitInstanceInitializerBlocks() throws IOException {
        Graph<String, DefaultWeightedEdge> graph = buildAndVisit(INITIALIZERS);

        assertTrue(
                graph.containsVertex(
                        "org.hjug.graphbuilder.visitor.testclasses.initializers.InitializerBlockTestClass"),
                "InitializerBlockTestClass should be in the graph");
        assertTrue(
                graph.containsVertex("java.util.ArrayList"),
                "ArrayList should be captured from instance initializer block");
        assertTrue(
                graph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.initializers.InitializerBlockTestClass",
                        "java.util.ArrayList"),
                "Should have edge from InitializerBlockTestClass to ArrayList from initializer block");
        assertTrue(
                graph.containsVertex("java.util.HashMap"),
                "HashMap should be captured from instance initializer block");
        assertTrue(
                graph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.initializers.InitializerBlockTestClass",
                        "java.util.HashMap"),
                "Should have edge from InitializerBlockTestClass to HashMap from initializer block");
        assertTrue(
                graph.containsVertex("java.lang.StringBuilder"),
                "StringBuilder should be captured from instance initializer block");
        assertTrue(
                graph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.initializers.InitializerBlockTestClass",
                        "java.lang.StringBuilder"),
                "Should have edge from InitializerBlockTestClass to StringBuilder from initializer block");
    }

    @Test
    void visitStaticInitializerBlocks() throws IOException {
        Graph<String, DefaultWeightedEdge> graph = buildAndVisit(INITIALIZERS);

        assertTrue(
                graph.containsVertex("org.hjug.graphbuilder.visitor.testclasses.initializers.ComplexInitializerClass"),
                "ComplexInitializerClass should be in the graph");
        assertTrue(
                graph.containsVertex("java.util.concurrent.ConcurrentHashMap"),
                "ConcurrentHashMap should be captured from static initializer block");
        assertTrue(
                graph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.initializers.ComplexInitializerClass",
                        "java.util.concurrent.ConcurrentHashMap"),
                "Should have edge from ComplexInitializerClass to ConcurrentHashMap from static initializer");
        assertTrue(
                graph.containsVertex("java.util.concurrent.atomic.AtomicInteger"),
                "AtomicInteger should be captured from static initializer block");
        assertTrue(
                graph.containsEdge(
                        "org.hjug.graphbuilder.visitor.testclasses.initializers.ComplexInitializerClass",
                        "java.util.concurrent.atomic.AtomicInteger"),
                "Should have edge from ComplexInitializerClass to AtomicInteger from static initializer");
        assertTrue(
                graph.containsVertex(
                        "org.hjug.graphbuilder.visitor.testclasses.initializers.ComplexInitializerClass$DataProcessor"),
                "DataProcessor nested class should be captured from instance initializer");
        assertTrue(
                graph.containsVertex(
                        "org.hjug.graphbuilder.visitor.testclasses.initializers.ComplexInitializerClass$HelperService"),
                "HelperService nested class should be captured from instance initializer");
    }
}

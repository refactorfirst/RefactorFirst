package org.hjug.graphbuilder.graphbuilder;

import java.util.Map;
import org.hjug.graphbuilder.GraphDependencyCollector;
import org.hjug.graphbuilder.metrics.ClassMetrics;
import org.hjug.graphbuilder.metrics.GraphMetricsCollector;
import org.hjug.graphbuilder.metrics.MethodMetrics;

/** Replays extractor facts into the same collectors the Java and Kotlin visitors feed. */
final class CSharpFactsReplayer {

    private CSharpFactsReplayer() {}

    static void replay(
            CSharpFacts facts,
            GraphDependencyCollector dependencies,
            GraphMetricsCollector metrics,
            Map<String, String> classToSourceFilePathMapping) {

        facts.namespaces().forEach(dependencies::registerPackage);

        for (CSharpFacts.TypeFacts type : facts.types()) {
            dependencies.registerClassVertex(type.fqn());
            dependencies.recordClassLocation(type.fqn(), type.sourceFile());
            classToSourceFilePathMapping.put(type.fqn(), type.sourceFile());

            ClassMetrics classMetrics = metrics.getOrCreateClassMetrics(type.fqn());
            classMetrics.setClassName(type.name());
            classMetrics.setPackageName(type.namespace());
            classMetrics.setSourceFilePath(type.sourceFile());
            classMetrics.setLinesOfCode(type.loc());
            if (type.parent() != null) {
                classMetrics.setParentClass(type.parent());
            }
            type.attributes().forEach(a -> classMetrics.addAttribute(a.name(), a.isPublic()));
            // What Refused Parent Bequest and Tradition Breaker are computed from.
            classMetrics.setNumberOfProtectedMembers(type.protectedMembers());
            type.overriddenMethods().forEach(classMetrics::addOverriddenMethod);
            type.usedParentMembers().forEach(classMetrics::addUsedParentMember);

            for (CSharpFacts.DependencyFacts dependency : type.dependencies()) {
                classMetrics.addDependency(dependency.to());
                // The first call creates the edge at weight 1; each further call adds 1.
                for (int i = 0; i < dependency.count(); i++) {
                    dependencies.addClassDependency(type.fqn(), dependency.to());
                }
            }

            for (CSharpFacts.MethodFacts method : type.methods()) {
                MethodMetrics methodMetrics = new MethodMetrics(method.name(), method.signature());
                methodMetrics.setLinesOfCode(method.loc());
                methodMetrics.setCyclomaticComplexity(method.cyclo());
                methodMetrics.setMaxNestingDepth(method.maxNesting());
                methodMetrics.setNumberOfParameters(method.parameters());
                methodMetrics.setAccessor(method.isAccessor());
                methodMetrics.setConstructor(method.isConstructor());
                method.accessedVariables().forEach(methodMetrics::addAccessedVariable);
                method.accessedOwnAttributes().forEach(methodMetrics::addAccessedOwnAttribute);
                method.accessedForeignAttributes().forEach(methodMetrics::addAccessedForeignAttribute);
                method.accessedForeignClasses().forEach(methodMetrics::addAccessedForeignClass);
                method.calledForeignMethods().forEach(methodMetrics::addCalledForeignMethod);
                method.calledForeignMethodClasses().forEach(methodMetrics::addCalledForeignMethodClass);
                classMetrics.addMethod(methodMetrics);

                // Same shape the Java visitor uses: callee signature, caller class, "CallerClass::callerSignature".
                String caller = type.fqn() + "::" + method.signature();
                method.calledForeignMethods().forEach(callee -> metrics.recordIncomingCall(callee, type.fqn(), caller));
            }
        }
    }
}

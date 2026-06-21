package org.hjug.graphbuilder.metrics;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.tree.Javadoc;

@Slf4j
public class MetricsCollectingVisitor extends JavaIsoVisitor<ExecutionContext> {

    private final MetricsCollector metricsCollector;
    private String currentPackageName;
    private String currentClassName;
    private String currentMethodSignature;
    private ClassMetrics currentClassMetrics;
    private MethodMetrics currentMethodMetrics;
    private String currentSourcePath;

    public MetricsCollectingVisitor(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    /**
     * Returns a JavadocVisitor that does nothing.  This is done to prevent the visitor from including references in
     * Javadocs as metric counts
     * @return JavadocVisitor that does nothing.
     */
    @Override
    protected JavadocVisitor<ExecutionContext> getJavadocVisitor() {
        return new JavadocVisitor<>(this) {
            @Override
            public Javadoc visitDocComment(Javadoc.DocComment docComment, ExecutionContext ctx) {
                return docComment;
            }
        };
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
        currentSourcePath = cu.getSourcePath().toString(); // .toUri().toString();
        return super.visitCompilationUnit(cu, ctx);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        JavaType.FullyQualified type = classDecl.getType();
        if (type == null) {
            return classDecl;
        }

        String previousPackageName = currentPackageName;
        String previousClassName = currentClassName;
        ClassMetrics previousClassMetrics = currentClassMetrics;

        currentClassName = type.getFullyQualifiedName();
        currentPackageName = type.getPackageName();

        // Get or create metrics - this ensures it's stored in the collector
        if (metricsCollector instanceof GraphMetricsCollector) {
            GraphMetricsCollector gmc = (GraphMetricsCollector) metricsCollector;
            currentClassMetrics = gmc.getAllClassMetrics().computeIfAbsent(currentClassName, ClassMetrics::new);
        } else {
            currentClassMetrics = metricsCollector.getClassMetrics(currentClassName);
            if (currentClassMetrics == null) {
                currentClassMetrics = new ClassMetrics(currentClassName);
            }
        }

        currentClassMetrics.setSourceFilePath(currentSourcePath);

        currentClassMetrics.setPackageName(type.getPackageName());
        currentClassMetrics.setClassName(type.getClassName());

        int loc = calculateLinesOfCode(classDecl);
        currentClassMetrics.setLinesOfCode(loc);

        // Track parent class
        if (classDecl.getExtends() != null && classDecl.getExtends().getType() instanceof JavaType.FullyQualified) {
            JavaType.FullyQualified parentType =
                    (JavaType.FullyQualified) classDecl.getExtends().getType();
            currentClassMetrics.setParentClass(parentType.getFullyQualifiedName());
        }

        // Count protected members
        int protectedMembers = 0;
        for (Statement statement : classDecl.getBody().getStatements()) {
            if (statement instanceof J.VariableDeclarations) {
                J.VariableDeclarations varDecl = (J.VariableDeclarations) statement;
                if (varDecl.getModifiers().stream().anyMatch(mod -> mod.getType() == J.Modifier.Type.Protected)) {
                    protectedMembers++;
                }
            } else if (statement instanceof J.MethodDeclaration) {
                J.MethodDeclaration methodDecl = (J.MethodDeclaration) statement;
                if (methodDecl.getModifiers().stream().anyMatch(mod -> mod.getType() == J.Modifier.Type.Protected)) {
                    protectedMembers++;
                }
            }
        }
        currentClassMetrics.setNumberOfProtectedMembers(protectedMembers);

        J.ClassDeclaration result = super.visitClassDeclaration(classDecl, ctx);

        metricsCollector.recordClassMetric(currentClassName, "LOC", loc);

        currentPackageName = previousPackageName;
        currentClassName = previousClassName;
        currentClassMetrics = previousClassMetrics;

        return result;
    }

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        if (currentClassName == null) {
            return super.visitMethodDeclaration(method, ctx);
        }

        String previousMethodSignature = currentMethodSignature;
        MethodMetrics previousMethodMetrics = currentMethodMetrics;

        String methodName = method.getSimpleName();
        currentMethodSignature = buildMethodSignature(method);
        currentMethodMetrics = new MethodMetrics(methodName, currentMethodSignature);

        int parameters = method.getParameters().size();
        currentMethodMetrics.setNumberOfParameters(parameters);

        int loc = calculateLinesOfCode(method);
        currentMethodMetrics.setLinesOfCode(loc);

        if (method.getBody() != null) {
            String bodyText = method.getBody().printTrimmed();
            List<String> bodyLines = new ArrayList<>();
            for (String line : bodyText.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()
                        && !trimmed.equals("{")
                        && !trimmed.equals("}")
                        && !trimmed.startsWith("//")
                        && !trimmed.startsWith("*")) {
                    bodyLines.add(trimmed);
                }
            }
            currentMethodMetrics.setNormalizedBodyLines(bodyLines);
        }

        boolean isAccessor = isAccessorMethod(method);
        currentMethodMetrics.setAccessor(isAccessor);

        boolean isConstructor = method.isConstructor();
        currentMethodMetrics.setConstructor(isConstructor);

        // Track overridden methods
        boolean isOverridden = method.getLeadingAnnotations().stream()
                .anyMatch(annotation -> annotation.getSimpleName().equals("Override"));
        if (isOverridden) {
            currentClassMetrics.addOverriddenMethod(currentMethodSignature);
        }

        if (method.getBody() != null) {
            ComplexityCalculator complexityCalculator = new ComplexityCalculator();
            complexityCalculator.visit(method.getBody(), ctx);
            currentMethodMetrics.setCyclomaticComplexity(complexityCalculator.getCyclomaticComplexity());
            currentMethodMetrics.setMaxNestingDepth(complexityCalculator.getMaxNestingDepth());
        }

        J.MethodDeclaration result = super.visitMethodDeclaration(method, ctx);

        if (currentClassMetrics != null) {
            currentClassMetrics.addMethod(currentMethodMetrics);
        }

        metricsCollector.recordMethodMetric(currentClassName, currentMethodSignature, "LOC", loc);
        metricsCollector.recordMethodMetric(
                currentClassName, currentMethodSignature, "CYCLO", currentMethodMetrics.getCyclomaticComplexity());
        metricsCollector.recordMethodMetric(
                currentClassName, currentMethodSignature, "MAXNESTING", currentMethodMetrics.getMaxNestingDepth());
        metricsCollector.recordMethodMetric(currentClassName, currentMethodSignature, "NOP", parameters);

        currentMethodSignature = previousMethodSignature;
        currentMethodMetrics = previousMethodMetrics;

        return result;
    }

    @Override
    public J.VariableDeclarations visitVariableDeclarations(
            J.VariableDeclarations multiVariable, ExecutionContext ctx) {
        if (currentClassName != null && currentMethodSignature == null) {
            for (J.VariableDeclarations.NamedVariable var : multiVariable.getVariables()) {
                String varName = var.getSimpleName();
                boolean isPublic = multiVariable.hasModifier(J.Modifier.Type.Public);
                if (currentClassMetrics != null) {
                    currentClassMetrics.addAttribute(varName, isPublic);
                }
            }
        }

        if (currentMethodMetrics != null) {
            for (J.VariableDeclarations.NamedVariable var : multiVariable.getVariables()) {
                currentMethodMetrics.addAccessedVariable(var.getSimpleName());
            }
        }

        return super.visitVariableDeclarations(multiVariable, ctx);
    }

    @Override
    public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
        if (currentMethodMetrics != null && identifier.getFieldType() != null) {
            JavaType.Variable fieldType = identifier.getFieldType();
            if (fieldType.getOwner() instanceof JavaType.FullyQualified) {
                JavaType.FullyQualified owner = (JavaType.FullyQualified) fieldType.getOwner();
                String ownerFqn = owner.getFullyQualifiedName();
                String attributeName = identifier.getSimpleName();
                if (!ownerFqn.equals(currentClassName)) {
                    currentMethodMetrics.addAccessedForeignClass(ownerFqn);
                    currentMethodMetrics.addAccessedForeignAttribute(ownerFqn + "." + attributeName);
                    if (currentClassMetrics != null && ownerFqn.equals(currentClassMetrics.getParentClass())) {
                        currentClassMetrics.addUsedParentMember(attributeName);
                    }
                } else {
                    currentMethodMetrics.addAccessedOwnAttribute(attributeName);
                }
            }
            currentMethodMetrics.addAccessedVariable(identifier.getSimpleName());
        }
        return super.visitIdentifier(identifier, ctx);
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        if (currentMethodMetrics != null) {
            JavaType.Method methodType = method.getMethodType();
            if (methodType != null && !methodType.isConstructor()) {
                JavaType declaringType = methodType.getDeclaringType();
                if (declaringType instanceof JavaType.FullyQualified) {
                    String declaringFqn = ((JavaType.FullyQualified) declaringType).getFullyQualifiedName();
                    if (!declaringFqn.equals(currentClassName)) {
                        StringBuilder sig = new StringBuilder();
                        sig.append(declaringFqn)
                                .append(".")
                                .append(methodType.getName())
                                .append("(");
                        java.util.List<JavaType> params = methodType.getParameterTypes();
                        for (int i = 0; i < params.size(); i++) {
                            if (i > 0) sig.append(",");
                            sig.append(params.get(i));
                        }
                        sig.append(")");
                        currentMethodMetrics.addCalledForeignMethod(sig.toString());
                        currentMethodMetrics.addCalledForeignMethodClass(declaringFqn);
                        if (currentClassMetrics != null && declaringFqn.equals(currentClassMetrics.getParentClass())) {
                            currentClassMetrics.addUsedParentMember(methodType.getName());
                        }
                        // Record the reverse (incoming) edge for Shotgun Surgery (CM/CC)
                        String callerMethodSig = currentClassName + "::" + currentMethodSignature;
                        metricsCollector.recordIncomingCall(sig.toString(), currentClassName, callerMethodSig);
                    }
                }
            }
        }
        return super.visitMethodInvocation(method, ctx);
    }

    @Override
    public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
        if (currentMethodMetrics != null && fieldAccess.getType() != null) {
            JavaType type = fieldAccess.getType();
            if (type instanceof JavaType.Variable) {
                JavaType.Variable varType = (JavaType.Variable) type;
                if (varType.getOwner() instanceof JavaType.FullyQualified) {
                    JavaType.FullyQualified owner = (JavaType.FullyQualified) varType.getOwner();
                    String ownerFqn = owner.getFullyQualifiedName();
                    String attributeName = fieldAccess.getSimpleName();
                    if (!ownerFqn.equals(currentClassName)) {
                        currentMethodMetrics.addAccessedForeignClass(ownerFqn);
                        currentMethodMetrics.addAccessedForeignAttribute(ownerFqn + "." + attributeName);
                        if (currentClassMetrics != null && ownerFqn.equals(currentClassMetrics.getParentClass())) {
                            currentClassMetrics.addUsedParentMember(attributeName);
                        }
                    } else {
                        currentMethodMetrics.addAccessedOwnAttribute(attributeName);
                    }
                }
            }
            currentMethodMetrics.addAccessedVariable(fieldAccess.getSimpleName());
        }
        return super.visitFieldAccess(fieldAccess, ctx);
    }

    private int calculateLinesOfCode(J tree) {
        if (tree.getMarkers()
                .findFirst(org.openrewrite.marker.SearchResult.class)
                .isPresent()) {
            return 0;
        }
        String source = tree.printTrimmed();
        if (source.isEmpty()) {
            return 0;
        }
        return (int) source.lines().count();
    }

    private String buildMethodSignature(J.MethodDeclaration method) {
        StringBuilder sig = new StringBuilder();
        sig.append(method.getSimpleName()).append("(");
        boolean first = true;
        for (org.openrewrite.java.tree.Statement param : method.getParameters()) {
            if (param instanceof J.VariableDeclarations) {
                J.VariableDeclarations varDecl = (J.VariableDeclarations) param;
                if (!first) {
                    sig.append(",");
                }
                if (varDecl.getTypeExpression() != null) {
                    sig.append(varDecl.getTypeExpression().getType());
                }
                first = false;
            }
        }
        sig.append(")");
        return sig.toString();
    }

    private boolean isAccessorMethod(J.MethodDeclaration method) {
        String name = method.getSimpleName();
        if (name.startsWith("get") || name.startsWith("is") || name.startsWith("set")) {
            if (method.getBody() == null) {
                return false;
            }
            int statements = method.getBody().getStatements().size();
            return statements <= 1;
        }
        return false;
    }
}

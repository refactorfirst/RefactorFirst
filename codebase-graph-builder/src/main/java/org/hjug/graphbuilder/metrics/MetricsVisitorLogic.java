package org.hjug.graphbuilder.metrics;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.openrewrite.Cursor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.marker.SearchResult;

/**
 * Static metrics-collection logic shared between
 * {@link MetricsCollectingVisitor} (Java) and
 * {@link KotlinMetricsCollectingVisitor} (Kotlin).
 *
 * <p>Both visitors keep their own {@link MetricsVisitorState} and call into
 * these helpers from their J-level {@code visitXxx} overrides. State is
 * threaded in/out via the {@code state} parameter so the helpers can mutate
 * {@code currentClassName}/{@code currentMethodMetrics}/etc. and have the
 * caller observe the new values.
 *
 * <p>This shared static surface supports the RefactorFirst design decision
 * "Refactor J-level logic into protected hooks on abstract bases — no
 * fork-and-drift". Java's single inheritance prohibits a single abstract
 * visitor that both {@code JavaIsoVisitor} and {@code KotlinIsoVisitor}
 * extend (they share {@code JavaVisitor} as ancestor but not the J-level
 * {@code JavaIsoVisitor} overrides), so composition is used here instead
 * of inheritance.
 */
@Slf4j
public final class MetricsVisitorLogic {

    private MetricsVisitorLogic() {}

    // -------------------- Compilation unit --------------------

    /**
     * Records the surrounding compilation unit's source path on {@code state}
     * for later use by {@link #enterClass} when populating
     * {@link ClassMetrics#setSourceFilePath(String)}.
     */
    public static void enterCompilationUnit(MetricsVisitorState state, String sourcePath) {
        state.currentSourcePath = sourcePath;
    }

    // -------------------- Class declaration --------------------

    /**
     * Records the start of a class visit: pushes the previous class state
     * onto local variables (the caller restores them via {@link #leaveClass}),
     * populates {@link ClassMetrics}, and returns the saved snapshot.
     */
    public static ClassStateSnapshot enterClass(
            MetricsVisitorState state, GraphMetricsCollector collector, J.ClassDeclaration classDecl) {
        JavaType.FullyQualified type = classDecl.getType();
        if (type == null) {
            return null;
        }

        ClassStateSnapshot snapshot =
                new ClassStateSnapshot(state.currentPackageName, state.currentClassName, state.currentClassMetrics);

        state.currentClassName = type.getFullyQualifiedName();
        state.currentPackageName = type.getPackageName();

        /* Get or create metrics - this ensures it's stored in the collector.
        getOrCreateClassMetrics stores the instance in the collector's
        classMetrics map, so the same instance is returned later by
        getAllClassMetrics() (the invariant the previous
        `instanceof GraphMetricsCollector` branch hand-rolled).*/
        state.currentClassMetrics = collector.getOrCreateClassMetrics(state.currentClassName);

        state.currentClassMetrics.setSourceFilePath(state.currentSourcePath);
        state.currentClassMetrics.setPackageName(type.getPackageName());
        state.currentClassMetrics.setClassName(type.getClassName());

        int loc = calculateLinesOfCode(classDecl);
        state.currentClassMetrics.setLinesOfCode(loc);

        // Track parent class
        if (classDecl.getExtends() != null && classDecl.getExtends().getType() instanceof JavaType.FullyQualified) {
            JavaType.FullyQualified parentType =
                    (JavaType.FullyQualified) classDecl.getExtends().getType();
            state.currentClassMetrics.setParentClass(parentType.getFullyQualifiedName());
        } else if (classDecl.getImplements() != null
                && !classDecl.getImplements().isEmpty()) {
            // Kotlin `class Foo : Bar()` inheritance surfaces on the
            // implements list (Kotlin has no `extends` keyword; OpenRewrite
            // Kotlin parser places the single supertype on `implements`). When
            // there is no Java `extends` declaration, treat the first
            // implemented type that is a non-interface class (i.e. has
            // Kind.CLASS) as the parent class — recording Java interface
            // implementations as `parentClass` would wrongly trigger the
            // Refused Parent Bequest / Tradition Breaker detectors below.
            for (TypeTree impl : classDecl.getImplements()) {
                JavaType implType = impl.getType();
                if (implType instanceof JavaType.FullyQualified fq
                        && fq.getKind() == JavaType.FullyQualified.Kind.Class) {
                    state.currentClassMetrics.setParentClass(fq.getFullyQualifiedName());
                    break;
                }
            }
        }

        // Record FQNs of class-level type-parameter bounds on the owning
        // class metrics (e.g. `class Foo<T : Bar>` adds Bar).
        collectTypeParameterFqns(classDecl.getTypeParameters(), state.currentClassMetrics);

        // Handle record components
        boolean isRecord = classDecl.getKind() == J.ClassDeclaration.Kind.Type.Record;
        if (isRecord) {
            List<Statement> primaryConstructor = classDecl.getPrimaryConstructor();
            if (primaryConstructor != null) {
                for (Statement stmt : primaryConstructor) {
                    if (stmt instanceof J.VariableDeclarations varDecl) {
                        for (J.VariableDeclarations.NamedVariable var : varDecl.getVariables()) {
                            String varName = var.getSimpleName();
                            state.currentClassMetrics.addAttribute(varName, true);
                        }
                    }
                }
            }
        }

        // Count protected members
        int protectedMembers = 0;
        if (classDecl.getBody() != null && classDecl.getBody().getStatements() != null) {
            for (Statement statement : classDecl.getBody().getStatements()) {
                if (statement instanceof J.VariableDeclarations varDecl) {
                    if (varDecl.getModifiers().stream().anyMatch(mod -> mod.getType() == J.Modifier.Type.Protected)) {
                        protectedMembers++;
                    }
                } else if (statement instanceof J.MethodDeclaration methodDecl) {
                    if (methodDecl.getModifiers().stream()
                            .anyMatch(mod -> mod.getType() == J.Modifier.Type.Protected)) {
                        protectedMembers++;
                    }
                }
            }
        }
        state.currentClassMetrics.setNumberOfProtectedMembers(protectedMembers);

        // Detect Kotlin `data class` / `sealed class` keywords encoded as
        // J.Modifier entries of type LanguageExtension with the keyword string set
        // accordingly (the OpenRewrite Kotlin parser surfaces Kotlin-specific
        // keywords this way rather than through Java's J.Modifier.Type enum).
        for (J.Modifier mod : classDecl.getModifiers()) {
            String keyword = mod.getKeyword();
            if (keyword == null) {
                continue;
            }
            if ("data".equals(keyword)) {
                state.currentClassMetrics.setDataClass(true);
            } else if ("sealed".equals(keyword)) {
                state.currentClassMetrics.setSealed(true);
            }
        }

        // Kotlin sealed subtypes surface as `implements` ancestors (e.g.
        // `data class Circle : Shape()` → J.ClassDeclaration.implements=[Shape]).
        // Record each implemented ancestor FQN so finalizeMetrics can flag the
        // relationship when the ancestor is itself a sealed class.
        if (classDecl.getImplements() != null) {
            for (TypeTree impl : classDecl.getImplements()) {
                JavaType implType = impl.getType();
                if (implType instanceof JavaType.FullyQualified fq) {
                    state.currentClassMetrics.addSealedHierarchyAncestor(fq.getFullyQualifiedName());
                }
            }
        }

        return snapshot;
    }

    /**
     * Finalizes the class metric recording and restores prior state from the
     * snapshot.
     */
    public static void leaveClass(
            MetricsVisitorState state,
            GraphMetricsCollector collector,
            J.ClassDeclaration classDecl,
            ClassStateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        int loc = calculateLinesOfCode(classDecl);
        collector.recordClassMetric(state.currentClassName, "LOC", loc);

        state.currentPackageName = snapshot.previousPackageName;
        state.currentClassName = snapshot.previousClassName;
        state.currentClassMetrics = snapshot.previousClassMetrics;
    }

    // -------------------- Method declaration --------------------

    /**
     * Records the start of a method visit: saves the previous method state,
     * allocates a {@link MethodMetrics}, and returns the saved snapshot.
     */
    public static MethodStateSnapshot enterMethod(
            MetricsVisitorState state, J.MethodDeclaration method, OverridePredicate overridePredicate) {
        if (state.currentClassName == null) {
            return null;
        }

        String previousMethodSignature = state.currentMethodSignature;
        MethodMetrics previousMethodMetrics = state.currentMethodMetrics;

        String methodName = method.getSimpleName();
        state.currentMethodSignature = buildMethodSignature(method);
        state.currentMethodMetrics = new MethodMetrics(methodName, state.currentMethodSignature);

        int parameters = method.getParameters().size();
        state.currentMethodMetrics.setNumberOfParameters(parameters);

        // Record FQNs of method-level type-parameter bounds on the method
        // metrics and the owning class metrics (e.g. `fun <U : Bar> foo()`
        // adds Bar to both).
        collectTypeParameterFqns(method.getTypeParameters(), state.currentMethodMetrics, state.currentClassMetrics);

        int loc = calculateLinesOfCode(method);
        state.currentMethodMetrics.setLinesOfCode(loc);

        if (method.getBody() != null) {
            String bodyText = method.getBody().printTrimmed();
            List<String> bodyLines = new ArrayList<>();
            for (String line : bodyText.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()
                        && !"{".equals(trimmed)
                        && !"}".equals(trimmed)
                        && !trimmed.startsWith("//")
                        && !trimmed.startsWith("*")) {
                    bodyLines.add(trimmed);
                }
            }
            state.currentMethodMetrics.setNormalizedBodyLines(bodyLines);
        }

        boolean isAccessor = isAccessorMethod(method);
        state.currentMethodMetrics.setAccessor(isAccessor);

        boolean isConstructor = method.isConstructor();
        state.currentMethodMetrics.setConstructor(isConstructor);

        if (state.currentClassMetrics != null) {
            boolean isOverridden = method.getLeadingAnnotations().stream()
                    .anyMatch(annotation -> overridePredicate.isOverrideAnnotation(annotation.getSimpleName()));
            if (isOverridden) {
                state.currentClassMetrics.addOverriddenMethod(state.currentMethodSignature);
            }
        }

        if (method.getBody() != null) {
            ComplexityCalculator complexityCalculator = new ComplexityCalculator();
            complexityCalculator.visit(method.getBody(), null);
            state.currentMethodMetrics.setCyclomaticComplexity(complexityCalculator.getCyclomaticComplexity());
            state.currentMethodMetrics.setMaxNestingDepth(complexityCalculator.getMaxNestingDepth());
        }

        return new MethodStateSnapshot(previousMethodSignature, previousMethodMetrics, loc, parameters);
    }

    /**
     * Records the method metric entries on the collector and restores prior
     * method state from the snapshot. Should be called *after*
     * {@code super.visitMethodDeclaration(method, p)} in the concrete
     * visitor so the inner node walks finish first.
     */
    public static void leaveMethod(
            MetricsVisitorState state, GraphMetricsCollector collector, MethodStateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        if (state.currentClassMetrics != null) {
            state.currentClassMetrics.addMethod(state.currentMethodMetrics);
        }

        collector.recordMethodMetric(state.currentClassName, state.currentMethodSignature, "LOC", snapshot.loc);
        collector.recordMethodMetric(
                state.currentClassName,
                state.currentMethodSignature,
                "CYCLO",
                state.currentMethodMetrics.getCyclomaticComplexity());
        collector.recordMethodMetric(
                state.currentClassName,
                state.currentMethodSignature,
                "MAXNESTING",
                state.currentMethodMetrics.getMaxNestingDepth());
        collector.recordMethodMetric(state.currentClassName, state.currentMethodSignature, "NOP", snapshot.parameters);

        state.currentMethodSignature = snapshot.previousMethodSignature;
        state.currentMethodMetrics = snapshot.previousMethodMetrics;
    }

    // -------------------- Field / variable / identifier / invocation --------------------

    /**
     * Counts class-level {@link J.VariableDeclarations}s as attributes and
     * every local method variable declaration as an accessed variable.
     */
    public static void handleVariableDeclarations(
            MetricsVisitorState state, Cursor cursor, J.VariableDeclarations multiVariable) {
        handleVariableDeclarations(state, cursor, multiVariable, false);
    }

    /**
     * Variant for Kotlin source files. Kotlin has no language-level default
     * visibility; properties declared without an explicit modifier are
     * {@code public}, so when {@code defaultPublicWhenAbsent == true} the
     * caller instructs this helper to treat an absent visibility modifier as
     * public rather than private (Java's default behaviour). Used by
     * {@link KotlinMetricsCollectingVisitor} so Kotlin {@code var x: Int}
     * properties at class scope are recorded with {@code numberOfPublicAttributes}.
     */
    public static void handleVariableDeclarations(
            MetricsVisitorState state,
            Cursor cursor,
            J.VariableDeclarations multiVariable,
            boolean defaultPublicWhenAbsent) {
        if (state.currentClassName != null && state.currentMethodSignature == null) {
            // Skip record components in primary constructor - they're already counted in enterClass
            J.ClassDeclaration enclosingClass = cursor.firstEnclosing(J.ClassDeclaration.class);
            if (enclosingClass != null && enclosingClass.getKind() == J.ClassDeclaration.Kind.Type.Record) {
                Object grandParent = cursor.getParent().getParent().getValue();
                List<Statement> primaryConstructor = enclosingClass.getPrimaryConstructor();
                if (primaryConstructor != null) {
                    if (grandParent == primaryConstructor
                            || (grandParent instanceof JContainer<?> container
                                    && primaryConstructor.equals(container.getElements()))) {
                        return;
                    }
                }
            }

            for (J.VariableDeclarations.NamedVariable var : multiVariable.getVariables()) {
                String varName = var.getSimpleName();
                boolean hasPublic = multiVariable.hasModifier(J.Modifier.Type.Public);
                boolean hasPrivate = multiVariable.hasModifier(J.Modifier.Type.Private);
                boolean hasProtected = multiVariable.hasModifier(J.Modifier.Type.Protected);
                boolean hasInternal = hasInternalModifier(multiVariable);
                boolean isPublic;
                if (hasPublic) {
                    isPublic = true;
                } else if (hasPrivate || hasProtected || hasInternal) {
                    isPublic = false;
                } else {
                    isPublic = defaultPublicWhenAbsent;
                }
                if (state.currentClassMetrics != null) {
                    state.currentClassMetrics.addAttribute(varName, isPublic);
                }
            }
        }

        if (state.currentMethodMetrics != null) {
            for (J.VariableDeclarations.NamedVariable var : multiVariable.getVariables()) {
                state.currentMethodMetrics.addAccessedVariable(var.getSimpleName());
            }
        }
    }

    /**
     * Kotlin's {@code internal} visibility modifier is surfaced by the
     * OpenRewrite parser as a {@link J.Modifier} of type
     * {@code LanguageExtension} with keyword {@code "internal"} (the parser
     * does not surface it as {@code J.Modifier.Type.Private} or any of the
     * standard Java modifier enum entries). Returns {@code true} when any
     * modifier on the supplied declaration carries that keyword.
     */
    private static boolean hasInternalModifier(J.VariableDeclarations multiVariable) {
        for (J.Modifier mod : multiVariable.getModifiers()) {
            if ("internal".equals(mod.getKeyword())) {
                return true;
            }
        }
        return false;
    }

    public static void handleIdentifier(MetricsVisitorState state, J.Identifier identifier) {
        if (state.currentMethodMetrics != null && identifier.getFieldType() != null) {
            JavaType.Variable fieldType = identifier.getFieldType();
            if (fieldType.getOwner() instanceof JavaType.FullyQualified) {
                JavaType.FullyQualified owner = (JavaType.FullyQualified) fieldType.getOwner();
                String ownerFqn = owner.getFullyQualifiedName();
                String attributeName = identifier.getSimpleName();
                if (!ownerFqn.equals(state.currentClassName)) {
                    state.currentMethodMetrics.addAccessedForeignClass(ownerFqn);
                    state.currentMethodMetrics.addAccessedForeignAttribute(ownerFqn + "." + attributeName);
                    if (state.currentClassMetrics != null
                            && ownerFqn.equals(state.currentClassMetrics.getParentClass())) {
                        state.currentClassMetrics.addUsedParentMember(attributeName);
                    }
                } else {
                    state.currentMethodMetrics.addAccessedOwnAttribute(attributeName);
                }
            }
            state.currentMethodMetrics.addAccessedVariable(identifier.getSimpleName());
        }
    }

    public static void handleMethodInvocation(
            MetricsVisitorState state, GraphMetricsCollector collector, J.MethodInvocation method) {
        if (state.currentMethodMetrics == null) {
            return;
        }
        JavaType.Method methodType = method.getMethodType();
        if (methodType == null || methodType.isConstructor()) {
            return;
        }
        JavaType declaringType = methodType.getDeclaringType();
        if (!(declaringType instanceof JavaType.FullyQualified qualified)) {
            return;
        }
        String declaringFqn = qualified.getFullyQualifiedName();
        if (declaringFqn.equals(state.currentClassName)) {
            return;
        }
        StringBuilder sig = new StringBuilder();
        sig.append(declaringFqn).append(".").append(methodType.getName()).append("(");
        List<JavaType> params = methodType.getParameterTypes();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sig.append(",");
            }
            sig.append(params.get(i));
        }
        sig.append(")");
        state.currentMethodMetrics.addCalledForeignMethod(sig.toString());
        state.currentMethodMetrics.addCalledForeignMethodClass(declaringFqn);
        if (state.currentClassMetrics != null && declaringFqn.equals(state.currentClassMetrics.getParentClass())) {
            state.currentClassMetrics.addUsedParentMember(methodType.getName());
        }
        // Record the reverse (incoming) edge for Shotgun Surgery (CM/CC)
        String callerMethodSig = state.currentClassName + "::" + state.currentMethodSignature;
        collector.recordIncomingCall(sig.toString(), state.currentClassName, callerMethodSig);
    }

    public static void handleFieldAccess(MetricsVisitorState state, J.FieldAccess fieldAccess) {
        if (state.currentMethodMetrics == null || fieldAccess.getType() == null) {
            return;
        }
        JavaType type = fieldAccess.getType();
        if (type instanceof JavaType.Variable varType) {
            if (varType.getOwner() instanceof JavaType.FullyQualified) {
                JavaType.FullyQualified owner = (JavaType.FullyQualified) varType.getOwner();
                String ownerFqn = owner.getFullyQualifiedName();
                String attributeName = fieldAccess.getSimpleName();
                if (!ownerFqn.equals(state.currentClassName)) {
                    state.currentMethodMetrics.addAccessedForeignClass(ownerFqn);
                    state.currentMethodMetrics.addAccessedForeignAttribute(ownerFqn + "." + attributeName);
                    if (state.currentClassMetrics != null
                            && ownerFqn.equals(state.currentClassMetrics.getParentClass())) {
                        state.currentClassMetrics.addUsedParentMember(attributeName);
                    }
                } else {
                    state.currentMethodMetrics.addAccessedOwnAttribute(attributeName);
                }
            }
        }
        state.currentMethodMetrics.addAccessedVariable(fieldAccess.getSimpleName());
    }

    /**
     * Records a method/constructor reference ({@code Klass::method}, Kotlin
     * callable references). Increments {@link MethodMetrics}'s
     * {@code numberOfCallableReferences} counter, records the callee's
     * declaring class as an accessed foreign class for ATFD, records the
     * foreign method signature for Shotgun Surgery (CM/CC via
     * {@link GraphMetricsCollector#recordIncomingCall}), and feeds the called
     * foreign methods / classes sets (CINT / CDISP).
     *
     * <p>Field references (e.g. {@code Klass::fieldName}) bump the counter
     * too — they reference a foreign attribute, feeding ATFD.
     */
    public static void handleMemberReference(
            MetricsVisitorState state, GraphMetricsCollector collector, J.MemberReference memberRef) {
        if (state.currentMethodMetrics == null) {
            return;
        }
        JavaType referenceType = memberRef.getType();
        if (referenceType == null) {
            return;
        }

        state.currentMethodMetrics.incrementCallableReferences();

        if (referenceType instanceof JavaType.Method methodType) {
            JavaType declaringType = methodType.getDeclaringType();
            if (declaringType instanceof JavaType.FullyQualified qualified) {
                String declaringFqn = qualified.getFullyQualifiedName();
                if (declaringFqn.equals(state.currentClassName)) {
                    // Same-class callable reference: still bump the counter (above)
                    // but don't double-count ATFD/CINT; skip the foreign-class bookkeeping.
                    return;
                }
                StringBuilder sig = new StringBuilder();
                sig.append(declaringFqn)
                        .append(".")
                        .append(methodType.getName())
                        .append("(");
                List<JavaType> params = methodType.getParameterTypes();
                for (int i = 0; i < params.size(); i++) {
                    if (i > 0) {
                        sig.append(",");
                    }
                    sig.append(params.get(i));
                }
                sig.append(")");
                state.currentMethodMetrics.addCalledForeignMethod(sig.toString());
                state.currentMethodMetrics.addCalledForeignMethodClass(declaringFqn);
                state.currentMethodMetrics.addAccessedForeignClass(declaringFqn);
                if (state.currentClassMetrics != null
                        && declaringFqn.equals(state.currentClassMetrics.getParentClass())) {
                    state.currentClassMetrics.addUsedParentMember(methodType.getName());
                }
                // Record the reverse (incoming) edge for Shotgun Surgery (CM/CC)
                String callerMethodSig = state.currentClassName + "::" + state.currentMethodSignature;
                collector.recordIncomingCall(sig.toString(), state.currentClassName, callerMethodSig);
            }
        } else if (referenceType instanceof JavaType.Variable varType) {
            if (varType.getOwner() instanceof JavaType.FullyQualified qualified) {
                String ownerFqn = qualified.getFullyQualifiedName();
                if (!ownerFqn.equals(state.currentClassName)) {
                    state.currentMethodMetrics.addAccessedForeignClass(ownerFqn);
                    state.currentMethodMetrics.addAccessedForeignAttribute(
                            ownerFqn + "." + memberRef.getReference().getSimpleName());
                    if (state.currentClassMetrics != null
                            && ownerFqn.equals(state.currentClassMetrics.getParentClass())) {
                        state.currentClassMetrics.addUsedParentMember(
                                memberRef.getReference().getSimpleName());
                    }
                }
            }
        }
    }

    // -------------------- Kotlin extension functions --------------------

    /**
     * Kotlin extension-function bookkeeping. Called by
     * {@link KotlinMetricsCollectingVisitor#visitMethodDeclaration(J.MethodDeclaration, ExecutionContext)}
     * for every method declaration encountered. Detects the
     * {@code org.openrewrite.kotlin.marker.Extension} marker on the
     * {@link J.MethodDeclaration} (the OpenRewrite Kotlin parser tags
     * extension functions with it). When present, increments the owning
     * class's {@link ClassMetrics#numberOfExtensionFunctions} counter and
     * records the first parameter's resolved FQN as the extension receiver
     * type — the receiver type is carried by the first element of
     * {@link JavaType.Method#getParameterTypes()} (the receiver type itself
     * is not surfaced on the parameter AST node, whose name is the
     * placeholder {@code "<receiverType>"} with null type info).
     *
     * <p>Top-level extension functions (declared at file scope, outside any
     * class — {@code state.currentClassMetrics == null}) are intentionally
     * skipped; the disharmony is "class declares ≥10 extension functions
     * across ≥5 foreign receiver types" and so only extension functions
     * physically declared inside a class count toward the metric.
     */
    public static void handleKotlinExtensionFunction(
            MetricsVisitorState state, J.MethodDeclaration methodDeclaration, boolean hasExtensionMarker) {
        if (state.currentClassMetrics == null || !hasExtensionMarker || methodDeclaration == null) {
            return;
        }
        state.currentClassMetrics.setNumberOfExtensionFunctions(
                state.currentClassMetrics.getNumberOfExtensionFunctions() + 1);
        JavaType methodType = methodDeclaration.getMethodType();
        if (!(methodType instanceof JavaType.Method mt)) {
            return;
        }
        List<JavaType> params = mt.getParameterTypes();
        if (params == null || params.isEmpty()) {
            return;
        }
        String receiverFqn = resolveFqn(params.get(0));
        if (receiverFqn != null && !receiverFqn.isEmpty()) {
            state.currentClassMetrics.addExtensionReceiverType(receiverFqn);
        }
    }

    private static String resolveFqn(JavaType type) {
        if (type == null || type instanceof JavaType.Unknown) {
            return null;
        }
        if (type instanceof JavaType.FullyQualified fq) {
            return fq.getFullyQualifiedName();
        }
        if (type instanceof JavaType.Parameterized p) {
            return p.getFullyQualifiedName();
        }
        if (type instanceof JavaType.Array a) {
            return resolveFqn(a.getElemType());
        }
        if (type instanceof JavaType.Primitive p) {
            // Primitive receiver types (Int, Boolean, Double, ...) are distinct
            // types in their own right — they must each contribute to the
            // receiver-type-set cardinality that gates the
            // EXCESSIVE_EXTENSIONS disharmony (else a class extending 10
            // primitive types would never trip the disharmony).
            String kw = p.getKeyword();
            if (kw == null || kw.isEmpty()) {
                return null;
            }
            return "primitive:" + kw;
        }
        return null;
    }

    // -------------------- Type parameters & type aliases --------------------

    /**
     * Walks every {@link J.TypeParameter} bound and records each bound
     * class's FQN on the supplied class metrics ({@code typeParameterFqns}).
     * Mirrors {@link org.hjug.graphbuilder.visitor.BaseTypeProcessor#processTypeParameter}
     * but writes into metrics state instead of the dependency graph. Used
     * for class-level, method-level, and Kotlin property-level generic
     * bounds.
     */
    public static void collectTypeParameterFqns(List<J.TypeParameter> typeParameters, ClassMetrics classMetrics) {
        if (typeParameters == null || classMetrics == null) {
            return;
        }
        for (J.TypeParameter typeParameter : typeParameters) {
            collectBoundFqns(typeParameter, classMetrics, null);
        }
    }

    /**
     * Walks an arbitrary {@link JavaType} (the initializer expression of
     * a {@link org.openrewrite.kotlin.tree.K.TypeAlias}, an extension
     * property's receiver type, etc.) and records every referenced
     * fully-qualified class on the supplied {@link ClassMetrics}'s
     * {@code typeParameterFqns}. Used where there is no
     * {@link J.TypeParameter} list to walk but a single type expression
     * still needs its references accounted for.
     */
    public static void collectTypeParameterFqnsFromType(JavaType type, ClassMetrics classMetrics) {
        collectFqnsRecursive(type, classMetrics, null);
    }

    /**
     * Variant that records method-level type-parameter bounds on the
     * supplied {@link MethodMetrics} (and also folds them into the owning
     * {@link ClassMetrics} so {@link ClassMetrics#getTypeParameterFqns()}
     * aggregates across the whole class).
     */
    public static void collectTypeParameterFqns(
            List<J.TypeParameter> typeParameters, MethodMetrics methodMetrics, ClassMetrics classMetrics) {
        if (typeParameters == null || methodMetrics == null) {
            return;
        }
        for (J.TypeParameter typeParameter : typeParameters) {
            collectBoundFqns(typeParameter, classMetrics, methodMetrics);
        }
    }

    private static void collectBoundFqns(
            J.TypeParameter typeParameter, ClassMetrics classMetrics, MethodMetrics methodMetrics) {
        if (typeParameter == null || typeParameter.getBounds() == null) {
            return;
        }
        for (TypeTree bound : typeParameter.getBounds()) {
            JavaType boundType = bound.getType();
            if (boundType == null || boundType instanceof JavaType.Unknown) {
                continue;
            }
            collectFqnsRecursive(boundType, classMetrics, methodMetrics);
        }
    }

    private static void collectFqnsRecursive(JavaType type, ClassMetrics classMetrics, MethodMetrics methodMetrics) {
        if (type == null || type instanceof JavaType.Unknown) {
            return;
        }
        if (type instanceof JavaType.FullyQualified fq) {
            recordFqn(fq.getFullyQualifiedName(), classMetrics, methodMetrics);
        } else if (type instanceof JavaType.Parameterized parameterized) {
            recordFqn(parameterized.getFullyQualifiedName(), classMetrics, methodMetrics);
            if (parameterized.getTypeParameters() != null) {
                for (JavaType typeParam : parameterized.getTypeParameters()) {
                    collectFqnsRecursive(typeParam, classMetrics, methodMetrics);
                }
            }
        } else if (type instanceof JavaType.Array array) {
            collectFqnsRecursive(array.getElemType(), classMetrics, methodMetrics);
        } else if (type instanceof JavaType.GenericTypeVariable variable) {
            if (variable.getBounds() != null) {
                for (JavaType bound : variable.getBounds()) {
                    collectFqnsRecursive(bound, classMetrics, methodMetrics);
                }
            }
        }
    }

    private static void recordFqn(String fqn, ClassMetrics classMetrics, MethodMetrics methodMetrics) {
        if (fqn == null || fqn.isEmpty()) {
            return;
        }
        if (classMetrics != null) {
            classMetrics.addTypeParameterFqn(fqn);
        }
        if (methodMetrics != null) {
            methodMetrics.addTypeParameterFqn(fqn);
        }
    }

    // -------------------- Calculator helpers --------------------

    public static int calculateLinesOfCode(J tree) {
        if (tree.getMarkers().findFirst(SearchResult.class).isPresent()) {
            return 0;
        }
        String source = tree.printTrimmed();
        if (source.isEmpty()) {
            return 0;
        }
        return (int) source.lines().count();
    }

    public static String buildMethodSignature(J.MethodDeclaration method) {
        StringBuilder sig = new StringBuilder();
        sig.append(method.getSimpleName()).append("(");
        boolean first = true;
        for (Statement param : method.getParameters()) {
            if (param instanceof J.VariableDeclarations varDecl) {
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

    public static boolean isAccessorMethod(J.MethodDeclaration method) {
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

    // -------------------- Helper types --------------------

    /** Snapshot of the class-traversal state pushed when entering a nested class. */
    public static class ClassStateSnapshot {
        final String previousPackageName;
        final String previousClassName;
        final ClassMetrics previousClassMetrics;

        ClassStateSnapshot(String pkg, String name, ClassMetrics metrics) {
            this.previousPackageName = pkg;
            this.previousClassName = name;
            this.previousClassMetrics = metrics;
        }
    }

    /** Snapshot of the method-traversal state pushed when entering a nested method. */
    public static class MethodStateSnapshot {
        final String previousMethodSignature;
        final MethodMetrics previousMethodMetrics;
        final int loc;
        final int parameters;

        MethodStateSnapshot(String sig, MethodMetrics metrics, int loc, int parameters) {
            this.previousMethodSignature = sig;
            this.previousMethodMetrics = metrics;
            this.loc = loc;
            this.parameters = parameters;
        }
    }

    /** Strategy for recognising {@code @Override}-style markers. */
    @FunctionalInterface
    public interface OverridePredicate {
        boolean isOverrideAnnotation(String simpleName);
    }
}

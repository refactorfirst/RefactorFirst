package org.hjug.graphbuilder.visitor;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.DependencyCollector;
import org.openrewrite.java.tree.*;

@Slf4j
public class JavaClassDeclarationVisitor<P> extends BaseCodebaseVisitor<P> {

    private final BaseTypeProcessor typeProcessor;
    private String currentOwnerFqn;

    public JavaClassDeclarationVisitor(DependencyCollector dependencyCollector) {
        super(dependencyCollector);
        this.typeProcessor = new BaseTypeProcessor() {
            @Override
            protected DependencyCollector getDependencyCollector() {
                return dependencyCollector;
            }
        };
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
        JavaType.FullyQualified type = classDecl.getType();
        if (type == null) {
            log.warn("ClassDeclaration has null type, skipping: {}", classDecl.getSimpleName());
            return classDecl;
        }

        String owningFqn = type.getFullyQualifiedName();
        String previousOwner = currentOwnerFqn;
        currentOwnerFqn = owningFqn;

        try {
            typeProcessor.processType(owningFqn, type);

            TypeTree extendsTypeTree = classDecl.getExtends();
            if (null != extendsTypeTree) {
                typeProcessor.processType(owningFqn, extendsTypeTree.getType());
            }

            List<TypeTree> implementsTypeTree = classDecl.getImplements();
            if (null != implementsTypeTree) {
                for (TypeTree typeTree : implementsTypeTree) {
                    typeProcessor.processType(owningFqn, typeTree.getType());
                }
            }

            for (J.Annotation leadingAnnotation : classDecl.getLeadingAnnotations()) {
                typeProcessor.processAnnotation(owningFqn, leadingAnnotation, getCursor());
            }

            if (null != classDecl.getTypeParameters()) {
                for (J.TypeParameter typeParameter : classDecl.getTypeParameters()) {
                    typeProcessor.processTypeParameter(owningFqn, typeParameter, getCursor());
                }
            }

            return super.visitClassDeclaration(classDecl, p);
        } finally {
            currentOwnerFqn = previousOwner;
        }
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, P p) {
        J.MethodInvocation methodInvocation = super.visitMethodInvocation(method, p);
        if (currentOwnerFqn == null) {
            return methodInvocation;
        }

        JavaType.Method methodType = methodInvocation.getMethodType();
        if (null != methodType && null != methodType.getDeclaringType()) {
            typeProcessor.processType(currentOwnerFqn, methodType.getDeclaringType());
        }

        if (null != methodInvocation.getTypeParameters()
                && !methodInvocation.getTypeParameters().isEmpty()) {
            for (Expression typeParameter : methodInvocation.getTypeParameters()) {
                typeProcessor.processType(currentOwnerFqn, typeParameter.getType());
            }
        }

        return methodInvocation;
    }

    @Override
    public J.NewClass visitNewClass(J.NewClass newClass, P p) {
        J.NewClass result = super.visitNewClass(newClass, p);
        if (currentOwnerFqn != null) {
            typeProcessor.processType(currentOwnerFqn, newClass.getType());
        }
        return result;
    }

    @Override
    public J.Lambda visitLambda(J.Lambda lambda, P p) {
        if (currentOwnerFqn != null && lambda.getType() != null) {
            typeProcessor.processType(currentOwnerFqn, lambda.getType());
        }

        // Recursively visit the lambda body to capture method invocations and type references
        // The super.visitLambda call will traverse into the lambda's body and parameters
        return super.visitLambda(lambda, p);
    }

    @Override
    public J.If visitIf(J.If iff, P p) {
        return super.visitIf(iff, p);
    }

    @Override
    public J.ForLoop visitForLoop(J.ForLoop forLoop, P p) {
        return super.visitForLoop(forLoop, p);
    }

    @Override
    public J.ForEachLoop visitForEachLoop(J.ForEachLoop forEachLoop, P p) {
        return super.visitForEachLoop(forEachLoop, p);
    }

    @Override
    public J.WhileLoop visitWhileLoop(J.WhileLoop whileLoop, P p) {
        return super.visitWhileLoop(whileLoop, p);
    }

    @Override
    public J.DoWhileLoop visitDoWhileLoop(J.DoWhileLoop doWhileLoop, P p) {
        return super.visitDoWhileLoop(doWhileLoop, p);
    }

    @Override
    public J.Switch visitSwitch(J.Switch switchStatement, P p) {
        return super.visitSwitch(switchStatement, p);
    }

    @Override
    public J.Try visitTry(J.Try tryStatement, P p) {
        J.Try result = super.visitTry(tryStatement, p);
        if (currentOwnerFqn != null && tryStatement.getCatches() != null) {
            for (J.Try.Catch catchClause : tryStatement.getCatches()) {
                if (catchClause.getParameter().getTree() instanceof J.VariableDeclarations) {
                    J.VariableDeclarations varDecl =
                            (J.VariableDeclarations) catchClause.getParameter().getTree();
                    if (varDecl.getTypeExpression() != null) {
                        typeProcessor.processType(
                                currentOwnerFqn, varDecl.getTypeExpression().getType());
                    }
                }
            }
        }
        return result;
    }

    @Override
    public J.InstanceOf visitInstanceOf(J.InstanceOf instanceOf, P p) {
        J.InstanceOf result = super.visitInstanceOf(instanceOf, p);
        if (currentOwnerFqn != null && instanceOf.getClazz() != null && instanceOf.getClazz() instanceof TypeTree) {
            typeProcessor.processType(currentOwnerFqn, ((TypeTree) instanceOf.getClazz()).getType());
        }
        return result;
    }

    @Override
    public J.TypeCast visitTypeCast(J.TypeCast typeCast, P p) {
        J.TypeCast result = super.visitTypeCast(typeCast, p);
        if (currentOwnerFqn != null && typeCast.getClazz() != null) {
            typeProcessor.processType(
                    currentOwnerFqn, typeCast.getClazz().getTree().getType());
        }
        return result;
    }

    @Override
    public J.MemberReference visitMemberReference(J.MemberReference memberRef, P p) {
        J.MemberReference result = super.visitMemberReference(memberRef, p);
        if (currentOwnerFqn != null && memberRef.getType() != null) {
            typeProcessor.processType(currentOwnerFqn, memberRef.getType());
        }
        return result;
    }

    @Override
    public J.NewArray visitNewArray(J.NewArray newArray, P p) {
        J.NewArray result = super.visitNewArray(newArray, p);
        if (currentOwnerFqn != null && newArray.getType() != null) {
            typeProcessor.processType(currentOwnerFqn, newArray.getType());
        }
        return result;
    }

    @Override
    protected String getCurrentOwnerFqn() {
        return currentOwnerFqn;
    }
}

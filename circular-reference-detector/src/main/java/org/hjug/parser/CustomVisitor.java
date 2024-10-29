package org.hjug.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;

@Slf4j
public class CustomVisitor extends JavaIsoVisitor<ExecutionContext> {

    JavaFqnCapturingVisitor javaFqnCapturingVisitor = new JavaFqnCapturingVisitor();

    @Getter
    private final List<String> fqdns = new ArrayList<>();

    // used for looking up declared generic FQDNs
    @Getter
    private final Map<String, String> importedAndDeclaredClassFQDNsByName = new HashMap<>();

    @Getter
    private List<String> genericTypeParameters = new ArrayList<>();

    // Get imported classes to check declared generics
    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
        J.CompilationUnit compilationUnit = super.visitCompilationUnit(cu, executionContext);

        for (J.Import mport : compilationUnit.getImports()) {
            importedAndDeclaredClassFQDNsByName.put(mport.getClassName(), mport.getTypeName());
        }

        for (J.ClassDeclaration aClass : compilationUnit.getClasses()) {
            importedAndDeclaredClassFQDNsByName.put(
                    aClass.getName().getSimpleName(), aClass.getType().getFullyQualifiedName());
        }

        return compilationUnit;
    }

    // collect generic types and then add them to the FQDN list once all visitors are processed
    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
        J.ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, executionContext);

        //        fqdns.add(classDeclaration.getType().getFullyQualifiedName());

        System.out.println("Declared Type: " + classDeclaration.getType().getFullyQualifiedName());

        // make this into a tail recursive call
        //        for (J.TypeParameter typeParameter : classDeclaration.getTypeParameters()) {
        //            System.out.println("Type Parameter: " + typeParameter.getBounds().get(0));
        //        }

        List<JavaType> typeParameters = classDeclaration.getType().getTypeParameters();

        if (!typeParameters.isEmpty()) {
            System.out.println("Type type parameters: " + typeParameters);
            for (JavaType genericTypeParameter : typeParameters) {
                System.out.println("Generic Type: " + genericTypeParameter);
                System.out.println("Bounds: " + ((JavaType.GenericTypeVariable) genericTypeParameter).getBounds());
            }
        }

        classDeclaration.getExtends();
        // recursively check for type params on extends type
        classDeclaration.getImplements();
        // recursively check for type params on implemented interfaces

        return classDeclaration;
    }

    //    @Override
    //    public J.TypeParameter visitTypeParameter(J.TypeParameter typeParam, ExecutionContext executionContext) {
    //        for (TypeTree bound : typeParam.getBounds()) {
    //            System.out.println("Type Parameter type: " + bo);
    //        }
    //
    //
    //        return super.visitTypeParameter(typeParam, executionContext);
    //    }

    // member reference: System.out::println
    @Override
    public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext executionContext) {
        J.MemberReference memberReference = super.visitMemberReference(memberRef, executionContext);

        System.out.println(
                "Member reference type: " + memberReference.getVariableType().getType());
        System.out.println("Owning type: " + memberReference.getVariableType().getOwner());

        return memberReference;
    }

    @Override
    public J.VariableDeclarations.NamedVariable visitVariable(
            J.VariableDeclarations.NamedVariable variable, ExecutionContext executionContext) {
        J.VariableDeclarations.NamedVariable visitVariable = super.visitVariable(variable, executionContext);

        System.out.println("Variable type: " + visitVariable.getVariableType().getType());
        System.out.println("Owning type: " + visitVariable.getVariableType().getOwner());

        return visitVariable;
    }

    @Override
    public J.VariableDeclarations visitVariableDeclarations(
            J.VariableDeclarations multiVariable, ExecutionContext executionContext) {

        System.out.println("Variable declaration type: " + multiVariable.getType());
        System.out.println("Variable declaration owning type: "
                + multiVariable.getVariables().get(0).getVariableType().getOwner());

        return super.visitVariableDeclarations(multiVariable, executionContext);
    }

    @Override
    public J.NullableType visitNullableType(J.NullableType nullableType, ExecutionContext executionContext) {
        return super.visitNullableType(nullableType, executionContext);
    }

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
        J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(method, executionContext);

        for (Statement parameter : method.getParameters()) {
            JavaType type;
            if (parameter instanceof J.VariableDeclarations) {
                for (J.VariableDeclarations.NamedVariable variable :
                        ((J.VariableDeclarations) parameter).getVariables()) {
                    type = variable.getType();
                }
            } else if (parameter instanceof Expression) {
                type = ((Expression) parameter).getType();
            }

            // do something with the type

        }

        methodDeclaration.getReturnTypeExpression().getType();

        return methodDeclaration;
    }

    @Override
    public J.Block visitBlock(J.Block block, ExecutionContext executionContext) {

        //        block.getStatements();

        return super.visitBlock(block, executionContext);
    }
}

package org.hjug.parser;

import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

public class JavaVariableVisitor<P> extends JavaIsoVisitor<P> {

    /**
     * Variable type is B
     * Owning type is A
     *
     * class A {
     *     B b1, b2;
     * }
     */
    @Override
    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, P p) {
        J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, p);

        // TODO: getAllAnnotations() is deprecated - need to get Cursor and call
        // AnnotationService.getAllAnnotations(cursor)
        // but I'm not sure how to get a cursor
        // All types, including primitives can be annotated
        System.out.println("Type annotations: " + variableDeclarations.getAllAnnotations());

        // skip primitive variable declarations
        if (variableDeclarations.getTypeExpression() instanceof J.Primitive) {
            return variableDeclarations;
        } else if (variableDeclarations.getTypeExpression() instanceof J.ParameterizedType) {

            // A<E> --> A
            // get Variable type
            System.out.println("Parametrized Variable type: "
                    + ((J.ParameterizedType) variableDeclarations.getTypeExpression())
                            .getClazz()
                            .getType());

            // A<E> --> E
            // gets variable's type parameters
            System.out.println("Type parameters: "
                    + ((J.ParameterizedType) variableDeclarations.getTypeExpression()).getTypeParameters());
        } else if (variableDeclarations.getTypeExpression() instanceof J.ArrayType) {
            // D[] --> D
            System.out.println(
                    "Array Element type: " + ((J.ArrayType) variableDeclarations.getTypeExpression()).getElementType());

        } else if (variableDeclarations.getTypeExpression() instanceof J.Identifier) {
            // G --> G
            System.out.println("Identifier type: " + (variableDeclarations.getTypeExpression()).getType());
        }

        // *should* always have get(0)
        System.out.println( "Varible Owner: " +
            variableDeclarations.getVariables().get(0).getVariableType().getOwner());

        System.out.println( "Varible Owner Class: " +
                variableDeclarations.getVariables().get(0).getVariableType().getOwner().getClass());

 /*       for (J.VariableDeclarations.NamedVariable variable : variableDeclarations.getVariables()) {
            System.out.println(
                    "Varible VariableType Owner: " + variable.getVariableType().getOwner());
            System.out.println("Varible type: " + variable.getType());
            System.out.println("Varible name: " + variable.getName());
        }*/

        System.out.println("*************************");
        System.out.println(variableDeclarations.toString());
        System.out.println("*************************");

        return variableDeclarations;
    }
}

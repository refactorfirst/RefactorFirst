package org.hjug.metrics.rules;

import java.util.HashSet;
import java.util.Set;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTFormalParameter;
import net.sourceforge.pmd.lang.java.ast.ASTLocalVariableDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTType;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;
import net.sourceforge.pmd.lang.java.symbols.JTypeDeclSymbol;
import net.sourceforge.pmd.lang.java.types.JTypeMirror;
import net.sourceforge.pmd.properties.NumericConstraints;
import net.sourceforge.pmd.properties.PropertyBuilder;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;

/**
 * Copy of PMD's CouplingBetweenObjectsRule
 * but generates the originally intended message containing coupling count
 */
public class CBORule extends AbstractJavaRule {
    private static final PropertyDescriptor<Integer> THRESHOLD_DESCRIPTOR = ((PropertyBuilder.GenericPropertyBuilder)
                    ((PropertyBuilder.GenericPropertyBuilder)
                                    ((PropertyBuilder.GenericPropertyBuilder) PropertyFactory.intProperty("threshold")
                                                    .desc("Unique type reporting threshold"))
                                            .require(NumericConstraints.positive()))
                            .defaultValue(20))
            .build();
    private int couplingCount;
    private boolean inInterface;
    private final Set<JTypeMirror> typesFoundSoFar = new HashSet();

    private String message;

    public CBORule() {
        this.definePropertyDescriptor(THRESHOLD_DESCRIPTOR);
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public Object visit(ASTCompilationUnit cu, Object data) {
        super.visit(cu, data);
        if (this.couplingCount > 20) { // (Integer) this.getProperty(THRESHOLD_DESCRIPTOR)) {
            message = "A value of " + this.couplingCount + " may denote a high amount of coupling within the class";
            this.addViolation(data, cu, message);
            this.setMessage(message);
        }

        this.couplingCount = 0;
        this.typesFoundSoFar.clear();
        return null;
    }

    public Object visit(ASTClassOrInterfaceDeclaration node, Object data) {
        boolean prev = this.inInterface;
        this.inInterface = node.isInterface();
        super.visit(node, data);
        this.inInterface = prev;
        return null;
    }

    public Object visit(ASTMethodDeclaration node, Object data) {
        ASTType type = node.getResultTypeNode();
        this.checkVariableType(type);
        return super.visit(node, data);
    }

    public Object visit(ASTLocalVariableDeclaration node, Object data) {
        ASTType type = node.getTypeNode();
        this.checkVariableType(type);
        return super.visit(node, data);
    }

    public Object visit(ASTFormalParameter node, Object data) {
        ASTType type = node.getTypeNode();
        this.checkVariableType(type);
        return super.visit(node, data);
    }

    public Object visit(ASTFieldDeclaration node, Object data) {
        ASTType type = node.getTypeNode();
        this.checkVariableType(type);
        return super.visit(node, data);
    }

    private void checkVariableType(ASTType typeNode) {
        if (!this.inInterface && typeNode != null) {
            JTypeMirror t = typeNode.getTypeMirror();
            if (!this.ignoreType(typeNode, t) && this.typesFoundSoFar.add(t)) {
                ++this.couplingCount;
            }
        }
    }

    private boolean ignoreType(ASTType typeNode, JTypeMirror t) {
        if (typeNode.getEnclosingType() != null
                && typeNode.getEnclosingType().getSymbol().equals(t.getSymbol())) {
            return true;
        } else {
            JTypeDeclSymbol symbol = t.getSymbol();
            return symbol == null
                    || "java.lang".equals(symbol.getPackageName())
                    || t.isPrimitive()
                    || t.isBoxedPrimitive();
        }
    }
}

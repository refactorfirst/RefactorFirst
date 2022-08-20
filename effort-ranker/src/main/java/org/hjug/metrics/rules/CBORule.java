package org.hjug.metrics.rules;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.*;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;
import net.sourceforge.pmd.lang.java.symboltable.ClassScope;
import net.sourceforge.pmd.properties.PropertyBuilder;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.properties.constraints.NumericConstraints;

/**
 * Copy of PMD's CouplingBetweenObjectsRule
 * but generates the originally intended message containing coupling count
 */
public class CBORule extends AbstractJavaRule {
    private int couplingCount;
    private Set<String> typesFoundSoFar;
    private static final PropertyDescriptor<Integer> THRESHOLD_DESCRIPTOR = ((PropertyBuilder.GenericPropertyBuilder)
                    ((PropertyBuilder.GenericPropertyBuilder)
                                    ((PropertyBuilder.GenericPropertyBuilder) PropertyFactory.intProperty("threshold")
                                                    .desc("Unique type reporting threshold"))
                                            .require(NumericConstraints.positive()))
                            .defaultValue(20))
            .build();

    public CBORule() {
        this.definePropertyDescriptor(THRESHOLD_DESCRIPTOR);
    }

    public Object visit(ASTCompilationUnit cu, Object data) {
        this.typesFoundSoFar = new HashSet();
        this.couplingCount = 0;
        Object returnObj = super.visit(cu, data);
        if (this.couplingCount > (Integer) this.getProperty(THRESHOLD_DESCRIPTOR)) {
            // only the line below is different from PMD's CouplingBetweenObjectsRule class
            this.addViolationWithMessage(
                    data,
                    cu,
                    "A value of " + this.couplingCount + " may denote a high amount of coupling within the class");
        }

        return returnObj;
    }

    public Object visit(ASTResultType node, Object data) {
        for (int x = 0; x < node.getNumChildren(); ++x) {
            Node tNode = node.getChild(x);
            if (tNode instanceof ASTType) {
                Node reftypeNode = tNode.getChild(0);
                if (reftypeNode instanceof ASTReferenceType) {
                    Node classOrIntType = reftypeNode.getChild(0);
                    if (classOrIntType instanceof ASTClassOrInterfaceType) {
                        this.checkVariableType(classOrIntType, classOrIntType.getImage());
                    }
                }
            }
        }

        return super.visit(node, data);
    }

    public Object visit(ASTLocalVariableDeclaration node, Object data) {
        this.handleASTTypeChildren(node);
        return super.visit(node, data);
    }

    public Object visit(ASTFormalParameter node, Object data) {
        this.handleASTTypeChildren(node);
        return super.visit(node, data);
    }

    public Object visit(ASTFieldDeclaration node, Object data) {
        for (int x = 0; x < node.getNumChildren(); ++x) {
            Node firstStmt = node.getChild(x);
            if (firstStmt instanceof ASTType) {
                ASTType tp = (ASTType) firstStmt;
                Node nd = tp.getChild(0);
                this.checkVariableType(nd, nd.getImage());
            }
        }

        return super.visit(node, data);
    }

    private void handleASTTypeChildren(Node node) {
        for (int x = 0; x < node.getNumChildren(); ++x) {
            Node sNode = node.getChild(x);
            if (sNode instanceof ASTType) {
                Node nameNode = sNode.getChild(0);
                this.checkVariableType(nameNode, nameNode.getImage());
            }
        }
    }

    private void checkVariableType(Node nameNode, String variableType) {
        List<ASTClassOrInterfaceDeclaration> parentTypes =
                nameNode.getParentsOfType(ASTClassOrInterfaceDeclaration.class);
        if (!parentTypes.isEmpty()) {
            if (!((ASTClassOrInterfaceDeclaration) parentTypes.get(0)).isInterface()) {
                ClassScope clzScope =
                        (ClassScope) ((JavaNode) nameNode).getScope().getEnclosingScope(ClassScope.class);
                if (!clzScope.getClassName().equals(variableType)
                        && !this.filterTypes(variableType)
                        && !this.typesFoundSoFar.contains(variableType)) {
                    ++this.couplingCount;
                    this.typesFoundSoFar.add(variableType);
                }
            }
        }
    }

    private boolean filterTypes(String variableType) {
        return variableType != null
                && (variableType.startsWith("java.lang.")
                        || "String".equals(variableType)
                        || this.filterPrimitivesAndWrappers(variableType));
    }

    private boolean filterPrimitivesAndWrappers(String variableType) {
        return "int".equals(variableType)
                || "Integer".equals(variableType)
                || "char".equals(variableType)
                || "Character".equals(variableType)
                || "double".equals(variableType)
                || "long".equals(variableType)
                || "short".equals(variableType)
                || "float".equals(variableType)
                || "byte".equals(variableType)
                || "boolean".equals(variableType);
    }
}

package org.hjug.graphbuilder.metrics;

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

public class ComplexityCalculator extends JavaIsoVisitor<ExecutionContext> {

    private int cyclomaticComplexity = 1;
    private int nestingLevel = 0;
    private int maxNestingDepth = 0;

    public int getCyclomaticComplexity() {
        return cyclomaticComplexity;
    }

    public int getMaxNestingDepth() {
        return maxNestingDepth;
    }

    @Override
    public J.If visitIf(J.If iff, ExecutionContext ctx) {
        cyclomaticComplexity++;
        nestingLevel++;
        maxNestingDepth = Math.max(maxNestingDepth, nestingLevel);
        J.If result = super.visitIf(iff, ctx);
        nestingLevel--;
        return result;
    }

    @Override
    public J.ForLoop visitForLoop(J.ForLoop forLoop, ExecutionContext ctx) {
        cyclomaticComplexity++;
        nestingLevel++;
        maxNestingDepth = Math.max(maxNestingDepth, nestingLevel);
        J.ForLoop result = super.visitForLoop(forLoop, ctx);
        nestingLevel--;
        return result;
    }

    @Override
    public J.ForEachLoop visitForEachLoop(J.ForEachLoop forEachLoop, ExecutionContext ctx) {
        cyclomaticComplexity++;
        nestingLevel++;
        maxNestingDepth = Math.max(maxNestingDepth, nestingLevel);
        J.ForEachLoop result = super.visitForEachLoop(forEachLoop, ctx);
        nestingLevel--;
        return result;
    }

    @Override
    public J.WhileLoop visitWhileLoop(J.WhileLoop whileLoop, ExecutionContext ctx) {
        cyclomaticComplexity++;
        nestingLevel++;
        maxNestingDepth = Math.max(maxNestingDepth, nestingLevel);
        J.WhileLoop result = super.visitWhileLoop(whileLoop, ctx);
        nestingLevel--;
        return result;
    }

    @Override
    public J.DoWhileLoop visitDoWhileLoop(J.DoWhileLoop doWhileLoop, ExecutionContext ctx) {
        cyclomaticComplexity++;
        nestingLevel++;
        maxNestingDepth = Math.max(maxNestingDepth, nestingLevel);
        J.DoWhileLoop result = super.visitDoWhileLoop(doWhileLoop, ctx);
        nestingLevel--;
        return result;
    }

    @Override
    public J.Case visitCase(J.Case _case, ExecutionContext ctx) {
        if (!_case.getExpressions().isEmpty()) {
            cyclomaticComplexity++;
        }
        return super.visitCase(_case, ctx);
    }

    @Override
    public J.Try.Catch visitCatch(J.Try.Catch _catch, ExecutionContext ctx) {
        cyclomaticComplexity++;
        nestingLevel++;
        maxNestingDepth = Math.max(maxNestingDepth, nestingLevel);
        J.Try.Catch result = super.visitCatch(_catch, ctx);
        nestingLevel--;
        return result;
    }

    @Override
    public J.Binary visitBinary(J.Binary binary, ExecutionContext ctx) {
        if (binary.getOperator() == J.Binary.Type.And || binary.getOperator() == J.Binary.Type.Or) {
            cyclomaticComplexity++;
        }
        return super.visitBinary(binary, ctx);
    }

    @Override
    public J.Ternary visitTernary(J.Ternary ternary, ExecutionContext ctx) {
        cyclomaticComplexity++;
        return super.visitTernary(ternary, ctx);
    }
}

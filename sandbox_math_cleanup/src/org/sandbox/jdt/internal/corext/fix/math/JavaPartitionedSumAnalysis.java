/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import org.eclipse.jdt.core.dom.*;

/** Java predicate admission only; child polynomial identities are proved by the SDK. */
final class JavaPartitionedSumAnalysis {
    private JavaPartitionedSumAnalysis() { }
    record Branches(Assignment first,Assignment second,Expression split,int period,int residue,
            boolean inclusive,boolean firstOnLow) { }
    static Branches inspect(ASTNode node,JavaRuntimeIterationAnalysis.Header header,IfStatement branch) {
        if(!(node instanceof ForStatement) || !header.stride().equals(BigInteger.ONE))throw invalid();
        Assignment first=update(branch.getThenStatement()),second=branch.getElseStatement()==null?null:update(branch.getElseStatement());
        var target=JavaFiniteSumAnalysis.variable(first.getLeftHandSide());
        if(target==null || second!=null && !same(second.getLeftHandSide(),target))throw invalid();
        Expression predicate=unwrap(branch.getExpression());
        if(!(predicate instanceof InfixExpression infix) || infix.hasExtendedOperands())throw invalid();
        Expression left=unwrap(infix.getLeftOperand()),right=unwrap(infix.getRightOperand());
        if(infix.getOperator()==InfixExpression.Operator.EQUALS && left instanceof InfixExpression remainder
                && remainder.getOperator()==InfixExpression.Operator.REMAINDER && !remainder.hasExtendedOperands()
                && same(unwrap(remainder.getLeftOperand()),header.counter())) {
            int period=literal(unwrap(remainder.getRightOperand())),residue=literal(right);
            if(period<1 || period>32 || residue<0 || residue>=period)throw invalid();
            return new Branches(first,second,null,period,residue,false,true);
        }
        var operator=infix.getOperator();
        boolean less=operator==InfixExpression.Operator.LESS || operator==InfixExpression.Operator.LESS_EQUALS;
        boolean greater=operator==InfixExpression.Operator.GREATER || operator==InfixExpression.Operator.GREATER_EQUALS;
        if(!less && !greater)throw invalid();
        boolean counterLeft=same(left,header.counter());
        if(!counterLeft && !same(right,header.counter()))throw invalid();
        boolean firstOnLow=counterLeft?less:greater;
        boolean equality=operator==InfixExpression.Operator.LESS_EQUALS || operator==InfixExpression.Operator.GREATER_EQUALS;
        // The low interval ends at split+1 exactly when it contains equality.
        boolean inclusive=firstOnLow?equality:!equality;
        return new Branches(first,second,counterLeft?right:left,0,0,inclusive,firstOnLow);
    }
    private static Assignment update(Statement statement) {
        if(statement instanceof Block block && block.statements().size()==1)statement=(Statement)block.statements().getFirst();
        if(!(statement instanceof ExpressionStatement expression) || !(expression.getExpression() instanceof Assignment assignment)
                || assignment.getOperator()!=Assignment.Operator.PLUS_ASSIGN && assignment.getOperator()!=Assignment.Operator.MINUS_ASSIGN)throw invalid();
        return assignment;
    }
    private static int literal(Expression expression) {
        if(!(expression instanceof NumberLiteral))throw invalid();
        Object value=expression.resolveConstantExpressionValue();
        long number=value instanceof Integer n?n:value instanceof Long n?n:-1;
        if(number<0 || number>32)throw invalid();return (int)number;
    }
    private static Expression unwrap(Expression expression) {
        while(expression instanceof ParenthesizedExpression p)expression=p.getExpression();return expression;
    }
    private static boolean same(Expression expression,IVariableBinding binding) {
        var variable=JavaFiniteSumAnalysis.variable(expression);return variable!=null && variable.isEqualTo(binding);
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("PARTITION_PREDICATE_NOT_ADMITTED");}
}

/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.List;
import org.eclipse.jdt.core.dom.*;

/** Exact source scheduling recognition. Arithmetic names supply no evidence. */
final class JavaFixedPowerSource {
    private JavaFixedPowerSource(){ }
    record Loop(IMethodBinding power,IMethodBinding multiplication,IMethodBinding square,IVariableBinding identity,String sourceFingerprint) { }
    static Loop recognize(CompilationUnit ast,IMethodBinding power,JavaValueSourceSummary.Work work) {
        work.charge(1);
        if(power==null || power.isRecovered() || power.isConstructor() || power.isGenericMethod() || power.isVarargs()
            || Modifier.isStatic(power.getModifiers()) || Modifier.isNative(power.getModifiers()) || Modifier.isSynchronized(power.getModifiers())
            || power.getParameterTypes().length!=1 || !JavaValueSourceSummary.big(power.getParameterTypes()[0])
            || !power.getReturnType().isEqualTo(power.getDeclaringClass()))throw unavailable();
        var definition=JavaSourceContext.definition(ast,power.getMethodDeclaration());
        if(definition==null || !(definition.declaration() instanceof MethodDeclaration method) || method.getBody()==null || method.resolveBinding()==null || method.getModifiers()!=power.getModifiers())throw unavailable();
        method.accept(new ASTVisitor(){@Override public void preVisit(ASTNode node){work.charge(1);}});
        var exponent=((SingleVariableDeclaration)method.parameters().getFirst()).resolveBinding();
        List<?> statements=method.getBody().statements();int offset=0;
        if(statements.size()==5 && statements.getFirst() instanceof IfStatement branch) {
            if(branch.getElseStatement()!=null || !(branch.getExpression() instanceof InfixExpression condition)
                || condition.hasExtendedOperands() || condition.getOperator()!=InfixExpression.Operator.LESS
                || !integer(condition.getRightOperand(),0) || !bigCall(condition.getLeftOperand(),exponent,"signum",0))throw unavailable(); //$NON-NLS-1$
            offset=1;
        }
        if(statements.size()!=offset+4)throw unavailable();
        var result=declaration(statements.get(offset));var base=declaration(statements.get(offset+1));
        var identity=JavaValueSourceSummary.variable(result.getInitializer());
        if(identity==null || !identity.isField() || !Modifier.isStatic(identity.getModifiers()) || !Modifier.isFinal(identity.getModifiers())
            || !identity.getDeclaringClass().isEqualTo(power.getDeclaringClass()) || !identity.getType().isEqualTo(power.getDeclaringClass())
            || !(result.getInitializer() instanceof SimpleName) || !(base.getInitializer() instanceof ThisExpression self) || self.getQualifier()!=null
            || !result.resolveBinding().getType().isEqualTo(power.getDeclaringClass()) || !base.resolveBinding().getType().isEqualTo(power.getDeclaringClass()))throw unavailable();
        if(!(statements.get(offset+2) instanceof ForStatement loop) || loop.initializers().size()!=1 || loop.updaters().size()!=1
            || !(loop.initializers().getFirst() instanceof VariableDeclarationExpression initial) || initial.fragments().size()!=1
            || !(initial.fragments().getFirst() instanceof VariableDeclarationFragment index) || !integer(index.getInitializer(),0)
            || !"int".equals(index.resolveBinding().getType().getQualifiedName()) || !(loop.getExpression() instanceof InfixExpression condition) //$NON-NLS-1$
            || condition.hasExtendedOperands() || condition.getOperator()!=InfixExpression.Operator.LESS
            || !same(condition.getLeftOperand(),index.resolveBinding()) || !bigCall(condition.getRightOperand(),exponent,"bitLength",0) //$NON-NLS-1$
            || !increment((Expression)loop.updaters().getFirst(),index.resolveBinding()) || !(loop.getBody() instanceof Block body)
            || body.statements().size()!=2 || !(body.statements().getFirst() instanceof IfStatement bit) || bit.getElseStatement()!=null
            || !bigCall(bit.getExpression(),exponent,"testBit",1) || !same((Expression)((MethodInvocation)bit.getExpression()).arguments().getFirst(),index.resolveBinding()))throw unavailable(); //$NON-NLS-1$
        var product=assignment(single(bit.getThenStatement()));var squared=assignment(body.statements().get(1));
        if(!same(product.getLeftHandSide(),result.resolveBinding()) || !(product.getRightHandSide() instanceof MethodInvocation multiply)
            || !same(multiply.getExpression(),result.resolveBinding()) || multiply.arguments().size()!=1 || !same((Expression)multiply.arguments().getFirst(),base.resolveBinding())
            || !same(squared.getLeftHandSide(),base.resolveBinding()) || !(squared.getRightHandSide() instanceof MethodInvocation square)
            || !same(square.getExpression(),base.resolveBinding()) || !square.arguments().isEmpty()
            || !(statements.get(offset+3) instanceof ReturnStatement returned) || !same(returned.getExpression(),result.resolveBinding()))throw unavailable();
        var mul=multiply.resolveMethodBinding();var sq=square.resolveMethodBinding();
        if(!operation(mul,power.getDeclaringClass(),1) || !operation(sq,power.getDeclaringClass(),0))throw unavailable();
        requireFresh(ast,mul,work);requireFresh(ast,sq,work);
        work.charge(definition.source().length());
        return new Loop(power,mul,sq,identity,JavaComputationTask.hash(definition.source()+JavaSourceContext.receipts(ast)));
    }
    private static boolean operation(IMethodBinding method,ITypeBinding type,int arguments) {
        return method!=null && !method.isGenericMethod() && method.getExceptionTypes().length==0 && !Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())
            && method.getDeclaringClass().isEqualTo(type) && method.getReturnType().isEqualTo(type)
            && method.getParameterTypes().length==arguments && (arguments==0 || method.getParameterTypes()[0].isEqualTo(type));
    }
    private static void requireFresh(CompilationUnit ast,IMethodBinding binding,JavaValueSourceSummary.Work work) {
        work.charge(1);var definition=JavaSourceContext.definition(ast,binding);
        if(definition==null || !(definition.declaration() instanceof MethodDeclaration method) || method.getBody()==null
            || method.resolveBinding()==null || method.getModifiers()!=binding.getModifiers() || !method.thrownExceptionTypes().isEmpty() || method.getBody().statements().isEmpty() || !(method.getBody().statements().getLast() instanceof ReturnStatement returned)
            || !(returned.getExpression() instanceof ClassInstanceCreation creation) || creation.resolveTypeBinding()==null
            || !creation.resolveTypeBinding().isEqualTo(binding.getDeclaringClass()))throw unavailable();
    }
    private static VariableDeclarationFragment declaration(Object statement) {
        if(statement instanceof VariableDeclarationStatement local && local.fragments().size()==1
            && local.fragments().getFirst() instanceof VariableDeclarationFragment fragment && fragment.resolveBinding()!=null)return fragment;
        throw unavailable();
    }
    private static Assignment assignment(Object statement) {
        if(statement instanceof ExpressionStatement operation && operation.getExpression() instanceof Assignment assignment
            && assignment.getOperator()==Assignment.Operator.ASSIGN)return assignment;throw unavailable();
    }
    private static Object single(Statement statement) {
        if(statement instanceof Block block){if(block.statements().size()!=1)throw unavailable();return block.statements().getFirst();}return statement;
    }
    private static boolean increment(Expression expression,IVariableBinding index) {
        return expression instanceof PrefixExpression prefix && prefix.getOperator()==PrefixExpression.Operator.INCREMENT && same(prefix.getOperand(),index)
            || expression instanceof PostfixExpression postfix && postfix.getOperator()==PostfixExpression.Operator.INCREMENT && same(postfix.getOperand(),index);
    }
    private static boolean bigCall(Expression expression,IVariableBinding receiver,String name,int arguments) {
        return expression instanceof MethodInvocation call && call.resolveMethodBinding()!=null && !Modifier.isStatic(call.resolveMethodBinding().getModifiers())
            && JavaValueSourceSummary.big(call.resolveMethodBinding().getDeclaringClass()) && call.getName().getIdentifier().equals(name)
            && call.arguments().size()==arguments && same(call.getExpression(),receiver);
    }
    private static boolean same(Expression expression,IVariableBinding binding) {return expression instanceof SimpleName && binding!=null && binding.isEqualTo(JavaValueSourceSummary.variable(expression));}
    private static boolean integer(Expression expression,int value) {return expression instanceof NumberLiteral && Integer.valueOf(value).equals(expression.resolveConstantExpressionValue());}
    private static IllegalArgumentException unavailable(){return JavaValueSourceSummary.unavailable("VALUE_FIXED_POWER_SOURCE_SHAPE");} //$NON-NLS-1$
}

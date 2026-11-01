/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.*;
import org.eclipse.jdt.core.dom.*;
import de.regelsuche.ast.Expr;
import de.regelsuche.sdk.optimization.JavaExpressions;

/** Bounded interpretation of exact BigInteger constants. Never invokes analyzed Java. */
final class JavaFixedPowerConstant {
    private final CompilationUnit ast;
    private final JavaValueSourceSummary.Work work;
    private final Set<String> active=new HashSet<>();
    private JavaFixedPowerConstant(CompilationUnit ast,JavaValueSourceSummary.Work work){this.ast=ast;this.work=work;}
    static BigInteger derive(CompilationUnit ast,IVariableBinding field,JavaValueSourceSummary.Work work){return new JavaFixedPowerConstant(ast,work).field(field,0);}
    static BigInteger scalar(Expr expression,JavaValueSourceSummary.Work work) {return scalar(expression,work,new IdentityHashMap<>(),0);}
    private static BigInteger scalar(Expr expression,JavaValueSourceSummary.Work work,IdentityHashMap<Expr,BigInteger> known,int depth) {
        work.charge(1);if(depth>64)throw unavailable();if(known.containsKey(expression))return known.get(expression);
        if(JavaExpressions.isLiteral(expression)) {
            Object value=JavaExpressions.literalValue(expression);
            if(value instanceof BigInteger integer)return bounded(integer);
            if(value instanceof Integer integer)return BigInteger.valueOf(integer);throw unavailable();
        }
        var operation=JavaExpressions.operationOf(expression).orElseThrow(JavaFixedPowerConstant::unavailable);var operands=JavaExpressions.operands(expression);
        BigInteger left=scalar(operands.getFirst(),work,known,depth+1),right=operands.size()>1?scalar(operands.get(1),work,known,depth+1):null;
        work.charge(left.abs().bitLength()+1L+(right==null?0:right.abs().bitLength()));
        BigInteger result=switch(operation) {
            case ADD->bounded(left.add(right));case SUBTRACT->bounded(left.subtract(right));case NEGATE->left.negate();
            case MULTIPLY->{if(left.abs().bitLength()+right.abs().bitLength()>4096)throw unavailable();yield bounded(left.multiply(right));}
            case MOD->{if(right.signum()<=0)throw unavailable();yield left.mod(right);}
            case SHIFT_LEFT->{if(right.signum()<0 || right.compareTo(BigInteger.valueOf(4096))>0 || (long)left.abs().bitLength()+right.intValue()>4096)throw unavailable();yield left.shiftLeft(right.intValue());}
            case POW->{if(right.signum()<0 || right.compareTo(BigInteger.valueOf(64))>0 || (long)left.abs().bitLength()*right.intValue()>4096)throw unavailable();yield bounded(left.pow(right.intValue()));}
            default->throw unavailable();
        };
        known.put(expression,result);return result;
    }
    private BigInteger field(IVariableBinding field,int depth) {
        work.charge(1);
        if(depth>32 || field==null || !field.isField() || !Modifier.isStatic(field.getModifiers()) || !Modifier.isFinal(field.getModifiers())
            || !JavaValueSourceSummary.big(field.getType()) || !active.add(field.getKey()))throw unavailable();
        try {
            var known=JavaSourceContext.constant(ast,field);if(known!=null)return known.value();
            var definition=JavaSourceContext.definition(ast,field);
            if(definition==null || !(definition.declaration() instanceof VariableDeclarationFragment fragment) || fragment.resolveBinding()==null
                || !Modifier.isStatic(fragment.resolveBinding().getModifiers()) || !Modifier.isFinal(fragment.resolveBinding().getModifiers()))throw unavailable();
            work.charge(definition.source().length());
            if(fragment.getInitializer()!=null)return integer(evaluate(fragment.getInitializer(),Map.of(),depth+1));
            if(!(fragment.getParent().getParent() instanceof TypeDeclaration owner))throw unavailable();
            Initializer selected=null;
            for(Object declaration:owner.bodyDeclarations())if(declaration instanceof Initializer initializer && Modifier.isStatic(initializer.getModifiers())) {
                final boolean[] assigns={false};initializer.accept(new ASTVisitor(){
                    @Override public void preVisit(ASTNode node){work.charge(1);}
                    @Override public boolean visit(Assignment assignment){if(field.isEqualTo(JavaValueSourceSummary.variable(assignment.getLeftHandSide())))assigns[0]=true;return true;}
                });
                if(assigns[0]){if(selected!=null)throw unavailable();selected=initializer;}
            }
            if(selected==null || selected.getBody().statements().size()>32)throw unavailable();
            var frame=new HashMap<String,Object>();BigInteger result=null;
            for(Object statement:selected.getBody().statements()) {
                work.charge(1);
                if(statement instanceof VariableDeclarationStatement local) {
                    if(local.fragments().size()!=1 || !(local.fragments().getFirst() instanceof VariableDeclarationFragment variable) || variable.resolveBinding()==null)throw unavailable();
                    frame.put(variable.resolveBinding().getKey(),evaluate(variable.getInitializer(),frame,depth+1));
                } else if(statement instanceof IfStatement branch) {
                    if(branch.getElseStatement()!=null || !(branch.getExpression() instanceof InfixExpression test) || test.hasExtendedOperands()
                        || test.getOperator()!=InfixExpression.Operator.NOT_EQUALS || !Integer.valueOf(0).equals(number(test.getRightOperand()))
                        || !(test.getLeftOperand() instanceof MethodInvocation signum) || !jdk(signum,"signum",0)
                        || !throwOnly(branch.getThenStatement()) || integer(evaluate(signum.getExpression(),frame,depth+1)).signum()!=0)throw unavailable(); //$NON-NLS-1$
                } else if(statement instanceof ExpressionStatement expression && expression.getExpression() instanceof Assignment assignment
                    && assignment.getOperator()==Assignment.Operator.ASSIGN && field.isEqualTo(JavaValueSourceSummary.variable(assignment.getLeftHandSide()))
                    && statement==selected.getBody().statements().getLast())result=integer(evaluate(assignment.getRightHandSide(),frame,depth+1));
                else throw unavailable();
            }
            if(result==null)throw unavailable();return result;
        } finally {active.remove(field.getKey());}
    }
    private Object evaluate(Expression expression,Map<String,Object> frame,int depth) {
        work.charge(1);if(depth>48 || expression==null)throw unavailable();
        if(expression instanceof ParenthesizedExpression parentheses)return evaluate(parentheses.getExpression(),frame,depth+1);
        var binding=JavaValueSourceSummary.variable(expression);
        if(binding!=null) {
            if(expression instanceof QualifiedName qualified && !(qualified.getQualifier().resolveBinding() instanceof ITypeBinding))throw unavailable();
            if(expression instanceof FieldAccess)throw unavailable();
            if(frame.containsKey(binding.getKey()))return frame.get(binding.getKey());
            if(JavaValueSourceSummary.big(binding.getDeclaringClass()))return switch(binding.getName()) {
                case "ZERO"->BigInteger.ZERO;case "ONE"->BigInteger.ONE;case "TWO"->BigInteger.TWO;case "TEN"->BigInteger.TEN;default->throw unavailable(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            };
            return field(binding,depth+1);
        }
        if(expression instanceof ArrayAccess access && number(access.getIndex()) instanceof Integer index
            && evaluate(access.getArray(),frame,depth+1) instanceof BigInteger[] values && index>=0 && index<values.length)return values[index];
        if(expression instanceof ClassInstanceCreation creation && creation.getAnonymousClassDeclaration()==null && creation.resolveConstructorBinding()!=null
            && JavaValueSourceSummary.big(creation.resolveConstructorBinding().getDeclaringClass()) && creation.arguments().size()>=1 && creation.arguments().size()<=2
            && creation.arguments().getFirst() instanceof StringLiteral literal && literal.getLiteralValue().length()<=1234) {
            int radix=10;if(creation.arguments().size()==2)radix=small((Expression)creation.arguments().get(1),Character.MIN_RADIX,Character.MAX_RADIX);
            return bounded(new BigInteger(literal.getLiteralValue(),radix));
        }
        if(!(expression instanceof MethodInvocation call) || call.resolveMethodBinding()==null || !JavaValueSourceSummary.big(call.resolveMethodBinding().getDeclaringClass()))throw unavailable();
        String name=call.getName().getIdentifier();
        if(Modifier.isStatic(call.resolveMethodBinding().getModifiers())) {
            if(!name.equals("valueOf") || call.arguments().size()!=1 || !(call.getExpression() instanceof Name qualifier) || !(qualifier.resolveBinding() instanceof ITypeBinding))throw unavailable(); //$NON-NLS-1$
            Object number=number((Expression)call.arguments().getFirst());
            if(!(number instanceof Integer || number instanceof Long))throw unavailable();return BigInteger.valueOf(((Number)number).longValue());
        }
        BigInteger receiver=integer(evaluate(call.getExpression(),frame,depth+1));
        if(call.arguments().isEmpty())return switch(name){case "negate"->receiver.negate();case "abs"->receiver.abs();default->throw unavailable();}; //$NON-NLS-1$ //$NON-NLS-2$
        if(call.arguments().size()!=1)throw unavailable();Expression argument=(Expression)call.arguments().getFirst();
        if(name.equals("pow")) {int exponent=small(argument,0,64);if((long)receiver.abs().bitLength()*exponent>4096)throw unavailable();work.charge(1L+exponent);return bounded(receiver.pow(exponent));} //$NON-NLS-1$
        BigInteger value=integer(evaluate(argument,frame,depth+1));work.charge(receiver.abs().bitLength()+value.abs().bitLength()+1L);
        return switch(name) {
            case "add"->bounded(receiver.add(value));case "subtract"->bounded(receiver.subtract(value)); //$NON-NLS-1$ //$NON-NLS-2$
            case "multiply"->{if(receiver.abs().bitLength()+value.abs().bitLength()>4096)throw unavailable();yield bounded(receiver.multiply(value));} //$NON-NLS-1$
            case "divideAndRemainder"->{if(value.signum()==0)throw unavailable();yield receiver.divideAndRemainder(value);} //$NON-NLS-1$
            case "divide"->{if(value.signum()==0)throw unavailable();yield bounded(receiver.divide(value));} //$NON-NLS-1$
            default->throw unavailable();
        };
    }
    private static Object number(Expression expression){
        if(!(expression instanceof NumberLiteral || expression instanceof PrefixExpression prefix && prefix.getOperand() instanceof NumberLiteral
            && (prefix.getOperator()==PrefixExpression.Operator.PLUS || prefix.getOperator()==PrefixExpression.Operator.MINUS)))throw unavailable();
        return expression.resolveConstantExpressionValue();
    }
    private static int small(Expression expression,int minimum,int maximum){Object value=number(expression);if(value instanceof Integer number && number>=minimum && number<=maximum)return number;throw unavailable();}
    private static boolean jdk(MethodInvocation call,String name,int arguments){return call.resolveMethodBinding()!=null && JavaValueSourceSummary.big(call.resolveMethodBinding().getDeclaringClass()) && call.getName().getIdentifier().equals(name) && call.arguments().size()==arguments && !Modifier.isStatic(call.resolveMethodBinding().getModifiers());}
    private static boolean throwOnly(Statement statement){return statement instanceof ThrowStatement || statement instanceof Block body && body.statements().size()==1 && body.statements().getFirst() instanceof ThrowStatement;}
    private static BigInteger integer(Object value){if(value instanceof BigInteger integer)return integer;throw unavailable();}
    private static BigInteger bounded(BigInteger value){if(value.abs().bitLength()>4096)throw unavailable();return value;}
    private static IllegalArgumentException unavailable(){return JavaValueSourceSummary.unavailable("VALUE_FIXED_CONSTANT_SOURCE");} //$NON-NLS-1$
}

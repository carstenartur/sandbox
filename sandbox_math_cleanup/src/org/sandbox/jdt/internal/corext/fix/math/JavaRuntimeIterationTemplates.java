/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.*;
import java.util.function.LongConsumer;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.dom.*;

/** Conservative edit exclusions, not proof receipts. Matching authored structures may also be
 * preserved. Neither a name nor a comment grants assumptions or authorizes a rewrite. */
public final class JavaRuntimeIterationTemplates {
    private static final String BIG="java.math.BigInteger";
    private JavaRuntimeIterationTemplates() { }
    public static List<ISourceRange> ranges(CompilationUnit ast,LongConsumer charge) {
        var result=new ArrayList<ISourceRange>();
        ast.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) {charge.accept(1);}
            @Override public boolean visit(Block block) {
                Block core=block;
                if(block.statements().size()==1 && block.statements().getFirst() instanceof IfStatement guard
                        && guard.getElseStatement() instanceof Block nested && wrapper(block)
                        && distanceGuard(guard.getExpression(),charge) && fallback(guard.getThenStatement(),charge))core=nested;
                if(!core(core,supplier(block),charge))return true;
                result.add(new SourceRange(block.getStartPosition(),block.getLength()));return false;
            }
        });
        return List.copyOf(result);
    }
    private static boolean core(Block block,boolean stream,LongConsumer charge) {
        var declarations=new HashMap<String,VariableDeclarationFragment>();
        for(Object child:block.statements()) {
            charge.accept(1);
            if(child instanceof VariableDeclarationStatement statement)for(Object item:statement.fragments()) {
                charge.accept(1);var declaration=(VariableDeclarationFragment)item;
                declarations.put(declaration.getName().getIdentifier(),declaration);
            }
        }
        for(var start:declarations.values()) {
            charge.accept(1);String name=start.getName().getIdentifier();
            if(!name.startsWith("$series") || !name.endsWith("start"))continue;
            String prefix=name.substring(0,name.length()-5);
            var bound=declarations.get(prefix+"bound");var count=declarations.get(prefix+"count");var after=declarations.get(prefix+"after");
            if(!local(start) || !local(bound) || !local(count))continue;
            boolean primitive=type(start,"int") && type(bound,"int") && type(count,"long");
            var countFacts=facts(count.getInitializer(),charge);
            if(!countFacts.reads(start,bound))continue;
            if(primitive) {
                if(!countFacts.operators.contains(InfixExpression.Operator.MINUS) || !countFacts.longCast)continue;
                boolean normalized=false;
                for(Object child:block.statements()) {
                    charge.accept(1);
                    if(child instanceof IfStatement clamp && clamp.getElseStatement()==null
                            && negative(clamp.getExpression(),count,charge) && assigns(clamp.getThenStatement(),count,charge))normalized=true;
                    if(child instanceof ExpressionStatement statement && statement.getExpression() instanceof Assignment assignment
                            && same(assignment.getLeftHandSide(),count) && unparenthesized(assignment.getRightHandSide(),charge) instanceof ConditionalExpression conditional
                            && negative(conditional.getExpression(),count,charge) && zero(conditional.getThenExpression())
                            && facts(conditional.getElseExpression(),charge).reads(count)
                            && facts(conditional.getElseExpression(),charge).operators.containsAll(Set.of(InfixExpression.Operator.DIVIDE,InfixExpression.Operator.PLUS)))normalized=true;
                }
                if(!normalized)continue;
                if(updates(block,count,charge))return true;
            } else if(!type(start,BIG) || !type(bound,BIG) || !type(count,BIG)
                    || !facts(start.getInitializer(),charge).calls.contains("valueOf")
                    || !facts(bound.getInitializer(),charge).calls.contains("valueOf")
                    || !(unparenthesized(count.getInitializer(),charge) instanceof ConditionalExpression)
                    || !countFacts.calls.containsAll(Set.of("subtract","divide","add","signum")))continue;
            if(after!=null && (!local(after) || !type(after,primitive?"long":BIG) || !facts(after.getInitializer(),charge).reads(start,count)))continue;
            for(Object child:block.statements()) {
                charge.accept(1);
                if(!(child instanceof IfStatement guard) || !(guard.getThenStatement() instanceof Block fast)
                        || !fallback(guard.getElseStatement(),charge))continue;
                var checks=facts(guard.getExpression(),charge);
                if(!checks.reads(count) || after!=null && !checks.reads(after))continue;
                if(primitive) {
                    if(after!=null && checks.operators.containsAll(Set.of(InfixExpression.Operator.GREATER_EQUALS,InfixExpression.Operator.LESS_EQUALS))
                            && updates(fast,count,charge))return true;
                } else if((after!=null || stream) && checks.calls.contains("compareTo")) {
                    var calculation=facts(fast,charge);
                    if(calculation.reads(count) && calculation.calls.contains("multiply")
                            && (calculation.names.contains(prefix+"result0") || calculation.names.contains(prefix+"vector")))return true;
                }
            }
        }
        return false;
    }
    private static boolean local(VariableDeclarationFragment declaration) {
        var binding=declaration==null?null:declaration.resolveBinding();
        return binding!=null && !binding.isRecovered() && !binding.isField() && declaration.getInitializer()!=null;
    }
    private static boolean type(VariableDeclarationFragment declaration,String name) {
        var type=declaration.resolveBinding().getType();return type!=null && !type.isRecovered() && type.getQualifiedName().equals(name);
    }
    private static boolean same(Expression expression,VariableDeclarationFragment declaration) {
        return expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding binding
                && !binding.isRecovered() && binding.isEqualTo(declaration.resolveBinding());
    }
    private static boolean assigns(Statement statement,VariableDeclarationFragment count,LongConsumer charge) {
        charge.accept(1);
        return statement instanceof ExpressionStatement expression && expression.getExpression() instanceof Assignment assignment
                && assignment.getOperator()==Assignment.Operator.ASSIGN && same(assignment.getLeftHandSide(),count)
                && zero(unparenthesized(assignment.getRightHandSide(),charge));
    }
    private static boolean zero(Expression expression) {
        return expression instanceof NumberLiteral && expression.resolveConstantExpressionValue() instanceof Number value && value.longValue()==0;
    }
    private static boolean negative(Expression expression,VariableDeclarationFragment count,LongConsumer charge) {
        return unparenthesized(expression,charge) instanceof InfixExpression comparison && comparison.getOperator()==InfixExpression.Operator.LESS
                && comparison.extendedOperands().isEmpty() && same(comparison.getLeftOperand(),count) && zero(comparison.getRightOperand());
    }
    private static boolean updates(Block block,VariableDeclarationFragment count,LongConsumer charge) {
        for(Object item:block.statements()) {
            charge.accept(1);
            if(item instanceof ExpressionStatement statement && statement.getExpression() instanceof Assignment assignment
                    && assignment.getOperator()==Assignment.Operator.PLUS_ASSIGN && assignment.getLeftHandSide() instanceof SimpleName target
                    && target.resolveBinding() instanceof IVariableBinding binding && !binding.isRecovered() && !binding.isField()
                    && unparenthesized(assignment.getRightHandSide(),charge) instanceof InfixExpression product
                    && product.getOperator()==InfixExpression.Operator.TIMES && product.extendedOperands().isEmpty()
                    && same(product.getLeftOperand(),count) && unparenthesized(product.getRightOperand(),charge).resolveConstantExpressionValue() instanceof Number)return true;
        }return false;
    }
    private static boolean fallback(Statement statement,LongConsumer charge) {
        charge.accept(1);if(!(statement instanceof Block block))return false;
        for(Object child:block.statements()) {
            charge.accept(1);
            if(child instanceof ForStatement || child instanceof WhileStatement || child instanceof DoStatement)return true;
            if(child instanceof ReturnStatement returned && returned.getExpression() instanceof MethodInvocation call
                    && call.getName().getIdentifier().equals("sum")) {
                var binding=call.resolveMethodBinding();
                if(binding!=null && !binding.isRecovered() && binding.getDeclaringClass()!=null
                        && Set.of("java.util.stream.IntStream","java.util.stream.LongStream").contains(binding.getDeclaringClass().getQualifiedName()))return true;
            }
        }return false;
    }
    private static boolean wrapper(Block block) {return block.getParent() instanceof Statement || supplier(block);}
    private static boolean supplier(Block block) {
        if(!(block.getParent() instanceof MethodDeclaration method) || !method.parameters().isEmpty()
                || !(method.getParent() instanceof AnonymousClassDeclaration anonymous)
                || !(anonymous.getParent() instanceof ClassInstanceCreation creation))return false;
        var type=creation.getType().resolveBinding();if(type==null || type.isRecovered())return false;
        return type.getQualifiedName().equals("java.util.function.IntSupplier") && method.getName().getIdentifier().equals("getAsInt")
                || type.getQualifiedName().equals("java.util.function.LongSupplier") && method.getName().getIdentifier().equals("getAsLong");
    }
    private static boolean distanceGuard(Expression expression,LongConsumer charge) {
        if(!(unparenthesized(expression,charge) instanceof InfixExpression comparison)
                || comparison.getOperator()!=InfixExpression.Operator.LESS || !comparison.extendedOperands().isEmpty()
                || !(unparenthesized(comparison.getRightOperand(),charge) instanceof NumberLiteral limit) || !limit.getToken().endsWith("L"))return false;
        return unparenthesized(comparison.getLeftOperand(),charge) instanceof InfixExpression distance
                && distance.getOperator()==InfixExpression.Operator.MINUS && distance.extendedOperands().isEmpty()
                && longCast(distance.getLeftOperand(),charge) && longCast(distance.getRightOperand(),charge);
    }
    private static boolean longCast(Expression expression,LongConsumer charge) {
        return unparenthesized(expression,charge) instanceof CastExpression cast
                && cast.getType() instanceof PrimitiveType type && type.getPrimitiveTypeCode()==PrimitiveType.LONG;
    }
    private static Expression unparenthesized(Expression expression,LongConsumer charge) {
        while(expression instanceof ParenthesizedExpression nested) {charge.accept(1);expression=nested.getExpression();}return expression;
    }
    private static final class Facts {
        final List<IVariableBinding> bindings=new ArrayList<>();
        final Set<String> calls=new HashSet<>(),names=new HashSet<>();
        final Set<InfixExpression.Operator> operators=new HashSet<>();boolean longCast;
        boolean reads(VariableDeclarationFragment... declarations) {
            return Arrays.stream(declarations).allMatch(d->bindings.stream().anyMatch(b->b.isEqualTo(d.resolveBinding())));
        }
    }
    private static Facts facts(ASTNode node,LongConsumer charge) {
        var facts=new Facts();if(node==null)return facts;
        node.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode child) {charge.accept(1);}
            @Override public boolean visit(SimpleName name) {
                if(name.resolveBinding() instanceof IVariableBinding binding && !binding.isRecovered()) {facts.bindings.add(binding);facts.names.add(name.getIdentifier());}return true;
            }
            @Override public boolean visit(InfixExpression expression) {facts.operators.add(expression.getOperator());return true;}
            @Override public boolean visit(CastExpression cast) {facts.longCast|=cast.getType() instanceof PrimitiveType type && type.getPrimitiveTypeCode()==PrimitiveType.LONG;return true;}
            @Override public boolean visit(MethodInvocation call) {
                var binding=call.resolveMethodBinding();
                if(binding!=null && !binding.isRecovered() && binding.getDeclaringClass()!=null && !binding.getDeclaringClass().isFromSource()
                        && binding.getDeclaringClass().getQualifiedName().equals(BIG))facts.calls.add(binding.getName());return true;
            }
        });return facts;
    }
}

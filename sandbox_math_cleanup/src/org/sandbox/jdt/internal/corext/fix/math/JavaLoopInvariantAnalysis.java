/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.*;
import org.sandbox.functional.core.model.SourceDescriptor;
import de.regelsuche.sdk.optimization.CancellationToken;

/** Bounded source scheduling admission. ULR source metadata is descriptive, never a proof. */
final class JavaLoopInvariantAnalysis {
    record Context(Statement loop, Statement envelope, Block body, LoopSchedulingEvidence.Control control,
            Expression expression, List<String> inputs, boolean normalCompletionOnly) { }
    record Discovery(List<Context> contexts, long work) { }
    private JavaLoopInvariantAnalysis() { }

    static Discovery discover(CompilationUnit unit, long maximum, CancellationToken token) {
        var contexts=new ArrayList<Context>();
        var work=new Work(maximum,token);
        try {
            unit.accept(new ASTVisitor() {
                @Override public void preVisit(ASTNode node) { work.tick(); }
                @Override public boolean visit(ForStatement loop) { add(loop); return true; }
                @Override public boolean visit(EnhancedForStatement loop) { add(loop); return true; }
                @Override public boolean visit(WhileStatement loop) { add(loop); return true; }
                @Override public boolean visit(DoStatement loop) { add(loop); return true; }
                private void add(Statement loop) { contexts.addAll(inspect(unit,loop,work)); }
            });
        } catch (Limit exceeded) { contexts.clear(); }
        return new Discovery(List.copyOf(contexts),work.used);
    }

    static List<Context> inspect(CompilationUnit unit, Statement loop, Work work) {
        Statement envelope=loop;
        while(envelope.getParent() instanceof LabeledStatement label) envelope=label;
        if(!(envelope.getParent() instanceof Block parent)) return List.of();
        Statement bodyStatement;
        LoopSchedulingEvidence.Control control;
        Expression update=null;
        IVariableBinding counter=null;
        if(loop instanceof EnhancedForStatement enhanced) {
            bodyStatement=enhanced.getBody();
            var arrayType=enhanced.getExpression().resolveTypeBinding();
            if(arrayType==null || !arrayType.isArray() || arrayType.getDimensions()!=1 || !integral(arrayType.getComponentType())) return List.of();
            int length=arrayLength(unit,enhanced.getExpression(),work);
            if(length<2 || !integral(enhanced.getParameter().getType().resolveBinding())) return List.of();
            control=new LoopSchedulingEvidence.Control("ENHANCED_FOR",0,length,1,length, //$NON-NLS-1$
                    new SourceDescriptor(SourceDescriptor.SourceType.ARRAY,enhanced.getExpression().toString(),
                            enhanced.getParameter().getType().resolveBinding().getName()));
        } else {
            VariableDeclarationFragment initializer;
            Expression condition;
            String kind;
            if(loop instanceof ForStatement counted) {
                if(counted.initializers().size()!=1 || counted.updaters().size()!=1
                        || !(counted.initializers().getFirst() instanceof VariableDeclarationExpression declaration)
                        || declaration.fragments().size()!=1) return List.of();
                initializer=(VariableDeclarationFragment)declaration.fragments().getFirst();
                bodyStatement=counted.getBody(); condition=counted.getExpression();
                update=(Expression)counted.updaters().getFirst(); kind="FOR"; //$NON-NLS-1$
            } else {
                int index=parent.statements().indexOf(envelope);
                if(index<1 || !(parent.statements().get(index-1) instanceof VariableDeclarationStatement declaration)
                        || declaration.fragments().size()!=1) return List.of();
                initializer=(VariableDeclarationFragment)declaration.fragments().getFirst();
                if(loop instanceof WhileStatement counted) {
                    bodyStatement=counted.getBody(); condition=counted.getExpression(); kind="WHILE"; //$NON-NLS-1$
                } else if(loop instanceof DoStatement counted) {
                    bodyStatement=counted.getBody(); condition=counted.getExpression(); kind="DO"; //$NON-NLS-1$
                } else return List.of();
                if(!(bodyStatement instanceof Block block) || block.statements().isEmpty()
                        || !(block.statements().getLast() instanceof ExpressionStatement last)) return List.of();
                update=last.getExpression();
            }
            counter=initializer.resolveBinding();
            if(counter==null || counter.isRecovered() || !counter.getType().isPrimitive()
                    || !counter.getType().getName().equals("int")) return List.of(); //$NON-NLS-1$
            Long initial=constant(initializer.getInitializer());
            if(initial==null || !(condition instanceof InfixExpression comparison) || comparison.hasExtendedOperands()
                    || !sameVariable(comparison.getLeftOperand(),counter)) return List.of();
            Long bound=constant(comparison.getRightOperand());
            int step=step(update,counter);
            if(bound==null || step==0 || step>0 && comparison.getOperator()!=InfixExpression.Operator.LESS
                    || step<0 && comparison.getOperator()!=InfixExpression.Operator.GREATER) return List.of();
            long distance=step>0?bound-initial:initial-bound;
            long trips=loop instanceof DoStatement?Math.max(1,distance):Math.max(0,distance);
            // A do-loop must not wrap on its mandatory first update.
            long finalCounter=initial+trips*step;
            if(trips<2 || finalCounter<Integer.MIN_VALUE || finalCounter>Integer.MAX_VALUE) return List.of();
            SourceDescriptor source=loop instanceof ForStatement && step==1
                    ?new SourceDescriptor(SourceDescriptor.SourceType.EXPLICIT_RANGE,initial+","+bound,"int"):null; //$NON-NLS-1$ //$NON-NLS-2$
            control=new LoopSchedulingEvidence.Control(kind,initial,bound,step,trips,source);
        }
        if(!(bodyStatement instanceof Block body)) return List.of();
        var candidates=new ArrayList<Expression>();
        boolean[] arrayEffects={loop instanceof EnhancedForStatement};
        for(Object child:body.statements()) {
            work.tick();
            if(child instanceof VariableDeclarationStatement declaration) {
                if(!integral(declaration.getType().resolveBinding())) return List.of();
                for(Object value:declaration.fragments()) {
                    var fragment=(VariableDeclarationFragment)value;
                    if(fragment.getInitializer()!=null) {
                        if(!total(fragment.getInitializer(),true,arrayEffects,work)) return List.of();
                        candidates.add(fragment.getInitializer());
                    }
                }
            } else if(child instanceof ExpressionStatement statement) {
                Expression expression=statement.getExpression();
                if(expression instanceof Assignment assignment) {
                    if(assignment.getOperator()==Assignment.Operator.DIVIDE_ASSIGN
                            || assignment.getOperator()==Assignment.Operator.REMAINDER_ASSIGN
                            || !target(assignment.getLeftHandSide(),arrayEffects,work)
                            || !total(assignment.getRightHandSide(),true,arrayEffects,work)) return List.of();
                    candidates.add(assignment.getRightHandSide());
                } else if(updatedVariable(expression)==null || updatedVariable(expression).isField()
                        || !integral(updatedVariable(expression).getType())) return List.of();
            } else return List.of();
        }
        var writes=new HashSet<String>();
        Expression allowedUpdate=update;
        IVariableBinding loopCounter=counter;
        boolean[] counterChanged={false};
        loop.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) {
                work.tick();
                IVariableBinding binding=node instanceof Assignment assignment?variable(assignment.getLeftHandSide())
                        :node instanceof Expression expression?updatedVariable(expression):null;
                if(binding!=null) {
                    writes.add(key(binding));
                    if(loopCounter!=null && binding.isEqualTo(loopCounter) && node!=allowedUpdate) counterChanged[0]=true;
                }
            }
        });
        if(counterChanged[0]) return List.of();
        var result=new ArrayList<Context>();
        for(Expression expression:candidates) {
            if(!total(expression,false,new boolean[1],work) || commented(unit,expression)) continue;
            var inputs=new LinkedHashSet<String>();
            boolean[] available={true};
            int insertion=envelope.getStartPosition();
            expression.accept(new ASTVisitor() {
                @Override public void preVisit(ASTNode node) { work.tick(); }
                @Override public boolean visit(SimpleName name) {
                    if(!(name.resolveBinding() instanceof IVariableBinding binding) || binding.isField()) {available[0]=false; return false;}
                    ASTNode declaration=unit.findDeclaringNode(binding);
                    if(declaration==null || declaration.getStartPosition()>=insertion || writes.contains(key(binding))) available[0]=false;
                    inputs.add(key(binding)); return false;
                }
            });
            if(available[0] && !inputs.isEmpty()) result.add(new Context(loop,envelope,body,control,expression,List.copyOf(inputs),arrayEffects[0]));
        }
        return result;
    }

    private static boolean target(Expression expression, boolean[] effects, Work work) {
        return expression instanceof SimpleName && variable(expression)!=null && !variable(expression).isField()
                && integral(expression.resolveTypeBinding()) || expression instanceof ArrayAccess && total(expression,true,effects,work);
    }
    private static boolean total(Expression expression, boolean arrays, boolean[] effects, Work work) {
        work.tick();
        if(!integral(expression.resolveTypeBinding())) return false;
        if(expression instanceof NumberLiteral || expression instanceof CharacterLiteral) return true;
        if(expression instanceof SimpleName) { var binding=variable(expression); return binding!=null && !binding.isField(); }
        if(expression instanceof ParenthesizedExpression parentheses) return total(parentheses.getExpression(),arrays,effects,work);
        if(expression instanceof CastExpression cast) return total(cast.getExpression(),arrays,effects,work);
        if(expression instanceof PrefixExpression prefix) return Set.of(PrefixExpression.Operator.PLUS,PrefixExpression.Operator.MINUS,
                PrefixExpression.Operator.COMPLEMENT).contains(prefix.getOperator()) && total(prefix.getOperand(),arrays,effects,work);
        if(expression instanceof InfixExpression infix) {
            if(!Set.of(InfixExpression.Operator.PLUS,InfixExpression.Operator.MINUS,InfixExpression.Operator.TIMES,
                    InfixExpression.Operator.AND,InfixExpression.Operator.OR,InfixExpression.Operator.XOR,
                    InfixExpression.Operator.LEFT_SHIFT,InfixExpression.Operator.RIGHT_SHIFT_SIGNED,
                    InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED).contains(infix.getOperator())) return false;
            if(!total(infix.getLeftOperand(),arrays,effects,work) || !total(infix.getRightOperand(),arrays,effects,work)) return false;
            for(Object operand:infix.extendedOperands()) if(!total((Expression)operand,arrays,effects,work)) return false;
            return true;
        }
        if(arrays && expression instanceof ArrayAccess access && access.getArray() instanceof SimpleName
                && variable(access.getArray())!=null && !variable(access.getArray()).isField()
                && total(access.getIndex(),true,effects,work)) { effects[0]=true; return true; }
        return false;
    }
    static int arrayLength(CompilationUnit unit, Expression expression, Work work) {
        work.tick();
        if(expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding binding
                && !binding.isField() && (binding.isEffectivelyFinal() || Modifier.isFinal(binding.getModifiers()))
                && unit.findDeclaringNode(binding) instanceof VariableDeclarationFragment declaration)
            expression=declaration.getInitializer();
        if(expression instanceof ArrayInitializer initializer) return initializer.expressions().size();
        if(!(expression instanceof ArrayCreation creation) || creation.getType().getDimensions()!=1
                || !creation.getType().getElementType().resolveBinding().isPrimitive()) return -1;
        if(creation.getInitializer()!=null) {
            for(Object value:creation.getInitializer().expressions())
                if(!total((Expression)value,false,new boolean[1],work)) return -1;
            return creation.getInitializer().expressions().size();
        }
        if(creation.dimensions().size()!=1) return -1;
        Long length=constant((Expression)creation.dimensions().getFirst());
        return length!=null && length>=0 && length<=Integer.MAX_VALUE?length.intValue():-1;
    }
    private static Long constant(Expression expression) {
        if(expression==null || expression.resolveTypeBinding()==null || !expression.resolveTypeBinding().getName().equals("int")) return null; //$NON-NLS-1$
        Object value=expression.resolveConstantExpressionValue(); return value instanceof Integer integer?integer.longValue():null;
    }
    private static int step(Expression expression, IVariableBinding counter) {
        if(expression instanceof PostfixExpression postfix && sameVariable(postfix.getOperand(),counter))
            return postfix.getOperator()==PostfixExpression.Operator.INCREMENT?1:-1;
        if(expression instanceof PrefixExpression prefix && sameVariable(prefix.getOperand(),counter)) {
            if(prefix.getOperator()==PrefixExpression.Operator.INCREMENT) return 1;
            if(prefix.getOperator()==PrefixExpression.Operator.DECREMENT) return -1;
        }
        return 0;
    }
    private static IVariableBinding updatedVariable(Expression expression) {
        if(expression instanceof PostfixExpression postfix) return variable(postfix.getOperand());
        if(expression instanceof PrefixExpression prefix && (prefix.getOperator()==PrefixExpression.Operator.INCREMENT
                || prefix.getOperator()==PrefixExpression.Operator.DECREMENT)) return variable(prefix.getOperand());
        return null;
    }
    private static IVariableBinding variable(Expression expression) {
        return expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding binding && !binding.isRecovered()?binding:null;
    }
    private static boolean sameVariable(Expression expression, IVariableBinding binding) {
        var actual=variable(expression); return actual!=null && actual.isEqualTo(binding);
    }
    static String key(IVariableBinding binding) { return binding.getVariableDeclaration().getKey(); }
    private static boolean integral(ITypeBinding type) {
        return type!=null && !type.isRecovered() && type.isPrimitive() && Set.of("byte","short","char","int","long").contains(type.getName()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    }
    private static boolean commented(CompilationUnit unit, ASTNode expression) {
        for(Object value:unit.getCommentList()) {
            ASTNode comment=(ASTNode)value;
            if(comment.getStartPosition()<expression.getStartPosition()+expression.getLength()
                    && comment.getStartPosition()+comment.getLength()>expression.getStartPosition()) return true;
        }
        return false;
    }
    static final class Work {
        private final long maximum;
        private final CancellationToken token;
        long used;
        Work(long maximum,CancellationToken token) {this.maximum=maximum;this.token=token;}
        void tick() {charge(1);}
        void charge(long amount) {
            if(amount<0 || amount>maximum-used || token.isCancelled()) throw new Limit();
            used+=amount;
        }
    }
    static final class Limit extends RuntimeException { private static final long serialVersionUID=1L; }
}

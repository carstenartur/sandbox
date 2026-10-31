/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.eclipse.jdt.core.dom.*;
import de.regelsuche.ast.*;
import de.regelsuche.ast.iteration.*;
import de.regelsuche.symbol.SymbolId;

/** Conservative Java admission; summation identities belong to the SDK. */
final class JavaFiniteSumAnalysis {
    record Reduction(IVariableBinding accumulator,FiniteIteration.Recurrence model,List<FiniteSumEvidence.Binding> bindings) { }
    record Context(ASTNode loop,List<Reduction> reductions,IVariableBinding counter,BigInteger finalCounter,boolean expression) {
        IVariableBinding accumulator(){return reductions.getFirst().accumulator();}
        FiniteIteration.Recurrence model(){return reductions.getFirst().model();}
        List<FiniteSumEvidence.Binding> bindings(){return reductions.getFirst().bindings();}
    }
    private JavaFiniteSumAnalysis() { }

    static Context inspect(CompilationUnit unit,ASTNode node,JavaLoopInvariantAnalysis.Work work) {
        work.tick();
        if(node instanceof MethodInvocation stream) return stream(unit,stream,work);
        Expression condition,update; IVariableBinding counter; Expression initializer; Statement body;
        boolean live=false,once=false;
        if(node instanceof ForStatement loop) {
            if(!(loop.getParent() instanceof Block) || loop.initializers().size()!=1 || loop.updaters().size()!=1
                    || !(loop.initializers().getFirst() instanceof VariableDeclarationExpression declaration)
                    || declaration.fragments().size()!=1) throw invalid();
            var fragment=(VariableDeclarationFragment)declaration.fragments().getFirst();
            counter=fragment.resolveBinding();initializer=fragment.getInitializer();condition=loop.getExpression();
            update=(Expression)loop.updaters().getFirst();body=loop.getBody();
        } else {
            if(node instanceof WhileStatement loop) {condition=loop.getExpression();body=loop.getBody();}
            else if(node instanceof DoStatement loop) {condition=loop.getExpression();body=loop.getBody();once=true;}
            else throw invalid();
            if(!(node.getParent() instanceof Block parent) || !(body instanceof Block block) || block.statements().size()<2) throw invalid();
            int position=parent.statements().indexOf(node);
            if(position<1 || !(parent.statements().get(position-1) instanceof VariableDeclarationStatement declaration)
                    || declaration.fragments().size()!=1) throw invalid();
            var fragment=(VariableDeclarationFragment)declaration.fragments().getFirst();
            counter=fragment.resolveBinding();initializer=fragment.getInitializer();live=true;
            if(!(block.statements().getLast() instanceof ExpressionStatement statement)) throw invalid();
            update=statement.getExpression();
        }
        if(counter==null || counter.isRecovered() || counter.isField() || !integer(counter.getType())) throw invalid();
        BigInteger start=constant(initializer);
        if(!(condition instanceof InfixExpression test) || test.hasExtendedOperands() || !same(test.getLeftOperand(),counter)) throw invalid();
        BigInteger bound=constant(test.getRightOperand()),step=stride(update,counter);
        BigInteger count=count(start,bound,step,test.getOperator(),once),end=start.add(count.multiply(step));
        int bits=type(counter.getType(),"int")?32:64; //$NON-NLS-1$
        if(start.bitLength()>bits-1 || end.bitLength()>bits-1) throw invalid();
        var statements=new ArrayList<Statement>();
        if(body instanceof Block block) for(Object statement:block.statements()) statements.add((Statement)statement);
        else statements.add(body);
        if(live) statements.removeLast();
        var reductions=reductions(unit,node,counter,start,step,count,statements,work);
        return new Context(node,reductions,live?counter:null,end,false);
    }

    private static List<Reduction> reductions(CompilationUnit unit,ASTNode node,IVariableBinding counter,BigInteger start,
            BigInteger step,BigInteger count,List<Statement> statements,JavaLoopInvariantAnalysis.Work work) {
        var assignments=new ArrayList<Assignment>();var temporaries=new LinkedHashMap<String,VariableDeclarationFragment>();
        var targets=new LinkedHashMap<String,IVariableBinding>();
        for(var statement:statements) {
            work.tick();
            if(statement instanceof VariableDeclarationStatement declaration && assignments.isEmpty()) {
                for(Object item:declaration.fragments()) {
                    var temporary=(VariableDeclarationFragment)item;var binding=temporary.resolveBinding();
                    if(binding==null || binding.isRecovered() || !integer(binding.getType()) || temporary.getInitializer()==null) throw invalid();
                    temporaries.put(key(binding),temporary);
                }
            } else if(statement instanceof ExpressionStatement expression && expression.getExpression() instanceof Assignment assignment) {
                var target=variable(assignment.getLeftHandSide());
                if(target==null || target.isField() || target.isEqualTo(counter) || temporaries.containsKey(key(target))
                        || !(unit.findDeclaringNode(target) instanceof VariableDeclaration declaration)
                        || declaration.getStartPosition()>=node.getStartPosition() || !integer(target.getType())) throw invalid();
                targets.put(key(target),target);assignments.add(assignment);
            } else throw invalid();
        }
        if(assignments.isEmpty() || targets.size()>FiniteSumEvidence.MAX_ACCUMULATORS
                || temporaries.size()>FiniteSumEvidence.MAX_TEMPORARIES || assignments.size()>FiniteSumEvidence.MAX_ORDERED_UPDATES) throw invalid();
        var result=new ArrayList<Reduction>();UUID namespace=new UUID(0,node.getStartPosition()+1L);
        SymbolId index=new SymbolId(namespace,1);
        for(var target:targets.values()) {
            String width=target.getType().getName();SymbolId state=new SymbolId(namespace,2);
            var symbols=new LinkedHashMap<String,SymbolId>();symbols.put(key(counter),index);symbols.put(key(target),state);
            var bindings=new LinkedHashMap<SymbolId,FiniteSumEvidence.Binding>();
            SymbolId initial=bind(unit,target,namespace,bindings,work);
            var local=new LinkedHashMap<String,Expr>();
            for(var temporary:temporaries.values()) {
                var binding=temporary.resolveBinding();
                // Never replace an int operation with long arithmetic during substitution.
                if(!type(binding.getType(),width) || !type(temporary.getInitializer().resolveTypeBinding(),width)) throw invalid();
                Expr value=decodeResolved(temporary.getInitializer(),width,read-> {
                    if(targets.containsKey(key(read))) throw invalid();
                    return resolve(unit,node,read,counter,index,symbols,bindings,namespace,local,work);
                },work,0);
                bounded(value,work,0,new int[]{0});
                local.put(key(binding),value);
            }
            Expr recurrence=VariableExpr.scoped(state);
            for(var assignment:assignments) {
                if(!same(assignment.getLeftHandSide(),target)) continue;
                Expr prior=recurrence;
                Expr rhs=decodeResolved(assignment.getRightHandSide(),width,read->{
                    if(read.isEqualTo(target)) return prior;
                    if(targets.containsKey(key(read))) throw invalid();
                    return resolve(unit,node,read,counter,index,symbols,bindings,namespace,local,work);
                },work,0);
                if(assignment.getOperator()==Assignment.Operator.PLUS_ASSIGN) recurrence=new BinaryExpr(prior,BinaryOperator.ADD,rhs);
                else if(assignment.getOperator()==Assignment.Operator.MINUS_ASSIGN) recurrence=new BinaryExpr(prior,BinaryOperator.SUB,rhs);
                else if(assignment.getOperator()==Assignment.Operator.ASSIGN) recurrence=rhs;
                else throw invalid();
            }
            bounded(recurrence,work,0,new int[]{0});work.charge(4096);
            var model=new FiniteIteration.Recurrence(new IndexRange(start,step,count),index,state,bindings.keySet(),VariableExpr.scoped(initial),recurrence,
                    width.equals("int")?IntegerArithmetic.JAVA_INT:IntegerArithmetic.JAVA_LONG); //$NON-NLS-1$
            result.add(new Reduction(target,model,List.copyOf(bindings.values())));
        }
        return List.copyOf(result);
    }
    private static Expr resolve(CompilationUnit unit,ASTNode node,IVariableBinding read,IVariableBinding counter,SymbolId index,
            Map<String,SymbolId> symbols,Map<SymbolId,FiniteSumEvidence.Binding> bindings,UUID namespace,Map<String,Expr> local,JavaLoopInvariantAnalysis.Work work) {
        if(read.isEqualTo(counter)) return VariableExpr.scoped(index);
        Expr temporary=local.get(key(read));if(temporary!=null) return temporary;
        SymbolId symbol=symbols.get(key(read));
        if(symbol==null) {
            if(!(unit.findDeclaringNode(read) instanceof VariableDeclaration declaration) || declaration.getStartPosition()>=node.getStartPosition()) throw invalid();
            symbol=bind(unit,read,namespace,bindings,work);symbols.put(key(read),symbol);
        }
        return VariableExpr.scoped(symbol);
    }
    private static BigInteger stride(Expression update,IVariableBinding counter) {
        if(update instanceof PostfixExpression postfix && same(postfix.getOperand(),counter))
            return postfix.getOperator()==PostfixExpression.Operator.INCREMENT?BigInteger.ONE:BigInteger.ONE.negate();
        if(update instanceof PrefixExpression prefix && same(prefix.getOperand(),counter)) {
            if(prefix.getOperator()==PrefixExpression.Operator.INCREMENT) return BigInteger.ONE;
            if(prefix.getOperator()==PrefixExpression.Operator.DECREMENT) return BigInteger.ONE.negate();
        }
        if(update instanceof Assignment assignment && same(assignment.getLeftHandSide(),counter)) {
            if(assignment.getOperator()==Assignment.Operator.PLUS_ASSIGN) return constant(assignment.getRightHandSide());
            if(assignment.getOperator()==Assignment.Operator.MINUS_ASSIGN) return constant(assignment.getRightHandSide()).negate();
            if(assignment.getOperator()==Assignment.Operator.ASSIGN && assignment.getRightHandSide() instanceof InfixExpression infix && !infix.hasExtendedOperands()) {
                if(same(infix.getLeftOperand(),counter)) {
                    if(infix.getOperator()==InfixExpression.Operator.PLUS) return constant(infix.getRightOperand());
                    if(infix.getOperator()==InfixExpression.Operator.MINUS) return constant(infix.getRightOperand()).negate();
                }
                if(infix.getOperator()==InfixExpression.Operator.PLUS && same(infix.getRightOperand(),counter)) return constant(infix.getLeftOperand());
            }
        }
        throw invalid();
    }
    private static BigInteger count(BigInteger start,BigInteger bound,BigInteger step,InfixExpression.Operator operator,boolean once) {
        boolean increasing=step.signum()>0,inclusive=operator==InfixExpression.Operator.LESS_EQUALS || operator==InfixExpression.Operator.GREATER_EQUALS;
        if(step.signum()==0 || increasing && operator!=InfixExpression.Operator.LESS && operator!=InfixExpression.Operator.LESS_EQUALS
                || !increasing && operator!=InfixExpression.Operator.GREATER && operator!=InfixExpression.Operator.GREATER_EQUALS) throw invalid();
        BigInteger distance=increasing?bound.subtract(start):start.subtract(bound);
        if(!inclusive) distance=distance.subtract(BigInteger.ONE);
        BigInteger count=distance.signum()<0?BigInteger.ZERO:distance.divide(step.abs()).add(BigInteger.ONE);
        return once?count.max(BigInteger.ONE):count;
    }
    private static void bounded(Expr value,JavaLoopInvariantAnalysis.Work work,int depth,int[] nodes) {
        work.tick();if(depth>FiniteSumEvidence.MAX_EXPRESSION_DEPTH || ++nodes[0]>FiniteSumEvidence.MAX_EXPRESSION_NODES) throw invalid();
        if(value instanceof BinaryExpr binary) {bounded(binary.left(),work,depth+1,nodes);bounded(binary.right(),work,depth+1,nodes);}
    }
    private static Context stream(CompilationUnit unit,MethodInvocation sum,JavaLoopInvariantAnalysis.Work work) {
        if(!sum.arguments().isEmpty() || !sum.getName().getIdentifier().equals("sum")) throw invalid(); //$NON-NLS-1$
        var method=sum.resolveMethodBinding();
        if(method==null || method.isRecovered()) throw invalid();
        String owner=method.getDeclaringClass().getQualifiedName();
        String width=owner.equals("java.util.stream.IntStream")?"int":owner.equals("java.util.stream.LongStream")?"long":null; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        if(width==null) throw invalid();
        Expression expression=sum.getExpression();
        expression=sequential(expression,owner);
        LambdaExpression mapper=null;
        if(expression instanceof MethodInvocation map && map.getName().getIdentifier().equals("map")) { //$NON-NLS-1$
            var binding=map.resolveMethodBinding();
            if(binding==null || !binding.getDeclaringClass().getQualifiedName().equals(owner) || map.arguments().size()!=1
                    || !(map.arguments().getFirst() instanceof LambdaExpression lambda) || lambda.parameters().size()!=1
                    || !(lambda.getBody() instanceof Expression)) throw invalid();
            mapper=lambda;expression=map.getExpression();
        }
        expression=sequential(expression,owner);
        if(!(expression instanceof MethodInvocation range) || range.arguments().size()!=2) throw invalid();
        var rangeMethod=range.resolveMethodBinding();
        if(rangeMethod==null || !rangeMethod.getDeclaringClass().getQualifiedName().equals(owner)
                || !java.lang.reflect.Modifier.isStatic(rangeMethod.getModifiers())) throw invalid();
        // A static call through an evaluated expression may have effects; only a type qualifier is admitted.
        if(!(range.getExpression() instanceof Name qualifier) || !(qualifier.resolveBinding() instanceof ITypeBinding)) throw invalid();
        boolean inclusive=range.getName().getIdentifier().equals("rangeClosed"); //$NON-NLS-1$
        if(!inclusive && !range.getName().getIdentifier().equals("range")) throw invalid(); //$NON-NLS-1$
        BigInteger start=constant((Expression)range.arguments().get(0)),bound=constant((Expression)range.arguments().get(1));
        BigInteger count=bound.subtract(start).add(inclusive?BigInteger.ONE:BigInteger.ZERO).max(BigInteger.ZERO);
        UUID namespace=new UUID(0,sum.getStartPosition()+1L);SymbolId index=new SymbolId(namespace,1),state=new SymbolId(namespace,2);
        var bindings=new LinkedHashMap<SymbolId,FiniteSumEvidence.Binding>();
        Expr term=VariableExpr.scoped(index);
        if(mapper!=null) {
            if(!(mapper.parameters().getFirst() instanceof VariableDeclaration declaration)) throw invalid();
            var counter=declaration.resolveBinding();if(counter==null || counter.isRecovered() || !type(counter.getType(),width)) throw invalid();
            term=decode((Expression)mapper.getBody(),width,read->{
                if(read.isEqualTo(counter)) return index;
                if(!(unit.findDeclaringNode(read) instanceof VariableDeclaration local) || local.getStartPosition()>=sum.getStartPosition()) throw invalid();
                return bind(unit,read,namespace,bindings,work);
            },work,0);
        }
        work.charge(4096);
        var model=new FiniteIteration.Recurrence(new IndexRange(start,BigInteger.ONE,count),index,state,bindings.keySet(),new NumberExpr(0),
                new BinaryExpr(VariableExpr.scoped(state),BinaryOperator.ADD,term),width.equals("int")?IntegerArithmetic.JAVA_INT:IntegerArithmetic.JAVA_LONG); //$NON-NLS-1$
        return new Context(sum,List.of(new Reduction(null,model,List.copyOf(bindings.values()))),null,null,true);
    }
    private static Expression sequential(Expression expression,String owner) {
        if(expression instanceof MethodInvocation invocation && invocation.getName().getIdentifier().equals("sequential")) { //$NON-NLS-1$
            var method=invocation.resolveMethodBinding();
            if(!invocation.arguments().isEmpty() || method==null || !method.getDeclaringClass().getQualifiedName().equals(owner)) throw invalid();
            return invocation.getExpression();
        }
        return expression;
    }
    private static SymbolId bind(CompilationUnit unit, IVariableBinding binding, UUID namespace,
            Map<SymbolId,FiniteSumEvidence.Binding> bindings,JavaLoopInvariantAnalysis.Work work) {
        work.tick();
        if(binding.isField() || binding.isRecovered() || !(unit.findDeclaringNode(binding) instanceof VariableDeclaration declaration)) throw invalid();
        var name=declaration.getName();
        SymbolId symbol=new SymbolId(namespace,name.getStartPosition()+3L);
        bindings.putIfAbsent(symbol,new FiniteSumEvidence.Binding(symbol,name.getStartPosition(),name.getIdentifier(),binding.getType().getName()));
        return symbol;
    }
    interface Symbols { SymbolId resolve(IVariableBinding binding); }
    static Expr decode(Expression expression,String width,Symbols symbols,JavaLoopInvariantAnalysis.Work work,int depth) {
        return decodeResolved(expression,width,binding->VariableExpr.scoped(symbols.resolve(binding)),work,depth);
    }
    interface Values { Expr resolve(IVariableBinding binding); }
    static Expr decodeResolved(Expression expression,String width,Values symbols,JavaLoopInvariantAnalysis.Work work,int depth) {
        work.tick();
        if(expression==null || depth>FiniteSumEvidence.MAX_EXPRESSION_DEPTH) throw invalid();
        var primitive=expression.resolveTypeBinding();
        if(!type(primitive,width) && !(width.equals("long") && type(primitive,"int"))) throw invalid(); //$NON-NLS-1$ //$NON-NLS-2$
        if(expression instanceof NumberLiteral || expression instanceof PrefixExpression prefix
                && prefix.getOperand() instanceof NumberLiteral && Set.of(PrefixExpression.Operator.PLUS,PrefixExpression.Operator.MINUS).contains(prefix.getOperator())) {
            Object number=expression.resolveConstantExpressionValue();
            if(number instanceof Integer integer) return new NumberExpr(integer.longValue());
            if(number instanceof Long value) return new NumberExpr(value);
            throw invalid();
        }
        if(expression instanceof SimpleName) {
            var binding=variable(expression);
            if(binding==null || binding.isField()) throw invalid();
            return symbols.resolve(binding);
        }
        if(expression instanceof ParenthesizedExpression parentheses) return decodeResolved(parentheses.getExpression(),width,symbols,work,depth+1);
        if(expression instanceof CastExpression cast && type(cast.resolveTypeBinding(),width))
            return decodeResolved(cast.getExpression(),width,symbols,work,depth+1);
        if(!type(primitive,width)) throw invalid(); // Every operator, not just the assignment, must use the ring width.
        if(expression instanceof PrefixExpression prefix && Set.of(PrefixExpression.Operator.MINUS,PrefixExpression.Operator.PLUS).contains(prefix.getOperator())) {
            Expr operand=decodeResolved(prefix.getOperand(),width,symbols,work,depth+1);
            return prefix.getOperator()==PrefixExpression.Operator.PLUS?operand:new BinaryExpr(new NumberExpr(0),BinaryOperator.SUB,operand);
        }
        if(expression instanceof InfixExpression infix) {
            BinaryOperator operator=infix.getOperator()==InfixExpression.Operator.PLUS?BinaryOperator.ADD
                    :infix.getOperator()==InfixExpression.Operator.MINUS?BinaryOperator.SUB
                    :infix.getOperator()==InfixExpression.Operator.TIMES?BinaryOperator.MUL:null;
            if(operator==null) throw invalid();
            // JDT's flat extended operands may promote to long only after an earlier int operation.
            if(width.equals("long") && type(infix.getLeftOperand().resolveTypeBinding(),"int")
                    && type(infix.getRightOperand().resolveTypeBinding(),"int")) throw invalid(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            Expr result=new BinaryExpr(decodeResolved(infix.getLeftOperand(),width,symbols,work,depth+1),operator,decodeResolved(infix.getRightOperand(),width,symbols,work,depth+1));
            for(Object value:infix.extendedOperands()) result=new BinaryExpr(result,operator,decodeResolved((Expression)value,width,symbols,work,depth+1));
            return result;
        }
        throw invalid();
    }
    static boolean type(ITypeBinding type,String width) {return type!=null && !type.isRecovered() && type.isPrimitive() && type.getName().equals(width);}
    static String key(IVariableBinding binding) {return binding.getVariableDeclaration().getKey();}
    static IVariableBinding variable(Expression expression) {
        return expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding binding && !binding.isRecovered()?binding:null;
    }
    private static boolean same(Expression expression,IVariableBinding binding) {var value=variable(expression);return value!=null && value.isEqualTo(binding);}
    private static boolean integer(ITypeBinding type) {return type(type,"int") || type(type,"long");} //$NON-NLS-1$ //$NON-NLS-2$
    private static BigInteger constant(Expression expression) {
        return constant(expression,0,new int[]{0});
    }
    private static BigInteger constant(Expression expression,int depth,int[] nodes) {
        if(depth>FiniteSumEvidence.MAX_EXPRESSION_DEPTH || ++nodes[0]>FiniteSumEvidence.MAX_EXPRESSION_NODES) throw invalid();
        if(expression==null || !integer(expression.resolveTypeBinding())) throw invalid();
        // Constant folding alone must not hide evaluation of a field qualifier.
        if(expression instanceof QualifiedName name) {
            if(!(name.getQualifier().resolveBinding() instanceof ITypeBinding)) throw invalid();
        } else if(expression instanceof SimpleName name) {
            if(!(name.resolveBinding() instanceof IVariableBinding binding) || binding.isField()) throw invalid();
        } else if(expression instanceof ParenthesizedExpression parentheses) return constant(parentheses.getExpression(),depth+1,nodes);
        else if(expression instanceof PrefixExpression prefix) {
            if(prefix.getOperator()!=PrefixExpression.Operator.PLUS && prefix.getOperator()!=PrefixExpression.Operator.MINUS) throw invalid();
            constant(prefix.getOperand(),depth+1,nodes);
        } else if(expression instanceof InfixExpression infix) {
            constant(infix.getLeftOperand(),depth+1,nodes);constant(infix.getRightOperand(),depth+1,nodes);
            for(Object value:infix.extendedOperands()) constant((Expression)value,depth+1,nodes);
        } else if(!(expression instanceof NumberLiteral)) throw invalid();
        Object value=expression.resolveConstantExpressionValue();
        if(value instanceof Integer number) return BigInteger.valueOf(number.longValue());
        if(value instanceof Long number) return BigInteger.valueOf(number);
        throw invalid();
    }
    static IllegalArgumentException invalid() {return new IllegalArgumentException("FINITE_SUM_NOT_ADMITTED");} //$NON-NLS-1$
}

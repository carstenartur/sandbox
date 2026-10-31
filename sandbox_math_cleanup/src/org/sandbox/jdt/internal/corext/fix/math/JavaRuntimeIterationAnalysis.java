/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.*;
import org.eclipse.jdt.core.dom.*;
import de.regelsuche.ast.*;
import de.regelsuche.ast.iteration.IntegerArithmetic;
import de.regelsuche.sdk.optimization.iteration.*;
import de.regelsuche.symbol.SymbolId;

/** Binding/effect/width admission only. All polynomial and transition algebra belongs to the SDK. */
final class JavaRuntimeIterationAnalysis {
    record Header(IVariableBinding counter,Expression start,Expression bound,BigInteger stride,
            boolean inclusive,boolean once,boolean live,Statement body,List<Statement> statements) { }
    record Context(ASTNode node,Header header,String width,List<IVariableBinding> targets,
            List<FiniteSumEvidence.Binding> bindings,List<PolynomialSum> sums,LinearIteration linear,
            PolynomialSeries.NestedSum nested,Expression innerBound,int innerStart,boolean innerInclusive,
            boolean stream,SymbolId startSymbol,SymbolId countSymbol,String innerCounterWidth,RuntimeIterationEvidence.Partition partition,Expression split) {
        Context(ASTNode node,Header header,String width,List<IVariableBinding> targets,List<FiniteSumEvidence.Binding> bindings,
                List<PolynomialSum> sums,LinearIteration linear,PolynomialSeries.NestedSum nested,Expression innerBound,
                int innerStart,boolean innerInclusive,boolean stream,SymbolId startSymbol,SymbolId countSymbol) {
            this(node,header,width,targets,bindings,sums,linear,nested,innerBound,innerStart,innerInclusive,stream,startSymbol,countSymbol,null,null,null);
        }
        Context(ASTNode node,Header header,String width,List<IVariableBinding> targets,List<FiniteSumEvidence.Binding> bindings,
                List<PolynomialSum> sums,LinearIteration linear,PolynomialSeries.NestedSum nested,Expression innerBound,
                int innerStart,boolean innerInclusive,boolean stream,SymbolId startSymbol,SymbolId countSymbol,String innerCounterWidth) {
            this(node,header,width,targets,bindings,sums,linear,nested,innerBound,innerStart,innerInclusive,stream,startSymbol,countSymbol,innerCounterWidth,null,null);
        }
    }
    private final CompilationUnit unit;
    private final ASTNode node;
    private final JavaLoopInvariantAnalysis.Work work;
    private final UUID namespace;
    private final LinkedHashMap<SymbolId,FiniteSumEvidence.Binding> bindings=new LinkedHashMap<>();
    private final Map<String,Expr> values=new LinkedHashMap<>();
    private final Set<String> states=new HashSet<>();
    private final SymbolId index,startSymbol,countSymbol;
    private JavaRuntimeIterationAnalysis(CompilationUnit unit,ASTNode node,JavaLoopInvariantAnalysis.Work work) {
        this.unit=unit;this.node=node;this.work=work;namespace=new UUID(1,node.getStartPosition()+1L);
        index=new SymbolId(namespace,1);startSymbol=new SymbolId(namespace,2);countSymbol=new SymbolId(namespace,3);
    }
    static Context inspect(CompilationUnit unit,ASTNode node,JavaLoopInvariantAnalysis.Work work) {
        return new JavaRuntimeIterationAnalysis(unit,node,work).inspect();
    }
    private Context inspect() {
        if(node instanceof MethodInvocation invocation) return stream(invocation);
        Header header=header(node);
                // Header values are evaluated in Java's original width and captured as parameters, never lifted algebraically.
        headerValue(header.start(),0);headerValue(header.bound(),0);
        values.put(key(header.counter()),VariableExpr.scoped(index));
        List<Statement> statements=header.statements();
        if(statements.size()==1 && statements.getFirst() instanceof ForStatement inner) return nested(header,inner);
        if(statements.size()==1 && statements.getFirst() instanceof IfStatement branch) return partition(header,branch);
        var targets=new LinkedHashMap<String,IVariableBinding>();
        for(var statement:statements) if(statement instanceof ExpressionStatement expression && expression.getExpression() instanceof Assignment assignment) {
            var target=variable(assignment.getLeftHandSide());
            if(target==null || target.isField() || target.isEqualTo(header.counter()) || !(unit.findDeclaringNode(target) instanceof VariableDeclaration declaration)
                    || declaration.getStartPosition()>=node.getStartPosition()) throw invalid();
            targets.put(key(target),target);
        }
        if(targets.isEmpty() || targets.size()>8) throw invalid();
        String width=width(targets.values().iterator().next().getType());
        states.addAll(targets.keySet());
        rejectHeaderStates(header);
        var initial=new LinkedHashMap<String,Expr>();var stateSymbols=new LinkedHashMap<String,SymbolId>();
        for(var target:targets.values()) {
            if(!width(target.getType()).equals(width)) throw invalid();
            initial.put(key(target),VariableExpr.scoped(bind(target)));
            SymbolId symbol=new SymbolId(namespace,Long.MAX_VALUE-stateSymbols.size());stateSymbols.put(key(target),symbol);
            values.put(key(target),VariableExpr.scoped(symbol));
        }
        var terms=new LinkedHashMap<String,Expr>();for(String key:states) terms.put(key,new NumberExpr(0));
        boolean additive=true;int locals=0,updates=0;
        for(var statement:statements) {
            work.tick();
            if(statement instanceof VariableDeclarationStatement declaration) {
                for(Object item:declaration.fragments()) {
                    if(++locals>32) throw invalid();var fragment=(VariableDeclarationFragment)item;
                    var binding=fragment.resolveBinding();
                    if(binding==null || !width(binding.getType()).equals(width) || fragment.getInitializer()==null) throw invalid();
                    Expr value=decode(fragment.getInitializer(),width,values);bounded(value);
                    values.put(key(binding),value);
                }
            } else if(statement instanceof ExpressionStatement expression && expression.getExpression() instanceof Assignment assignment) {
                if(++updates>32) throw invalid();var target=variable(assignment.getLeftHandSide());
                if(target==null || !states.contains(key(target))) throw invalid();
                Expr rhs=decode(assignment.getRightHandSide(),width,values),old=values.get(key(target)),next;
                bounded(rhs);var operator=assignment.getOperator();
                if(operator==Assignment.Operator.PLUS_ASSIGN) next=add(old,rhs);
                else if(operator==Assignment.Operator.MINUS_ASSIGN) next=sub(old,rhs);
                else if(operator==Assignment.Operator.TIMES_ASSIGN) {next=mul(old,rhs);additive=false;}
                else if(operator==Assignment.Operator.ASSIGN) {
                    next=rhs;
                    // Syntactic additive recognition also admits BigInteger s.add(term), after exact method checks.
                    if(rhs instanceof BinaryExpr binary && binary.left().equals(old) && (binary.operator()==BinaryOperator.ADD || binary.operator()==BinaryOperator.SUB)) {
                        rhs=binary.right();operator=binary.operator()==BinaryOperator.ADD?Assignment.Operator.PLUS_ASSIGN:Assignment.Operator.MINUS_ASSIGN;
                    } else additive=false;
                } else throw invalid();
                bounded(next);if(containsState(rhs,stateSymbols.values())) additive=false;
                if(operator==Assignment.Operator.PLUS_ASSIGN) terms.put(key(target),add(terms.get(key(target)),rhs));
                if(operator==Assignment.Operator.MINUS_ASSIGN) terms.put(key(target),sub(terms.get(key(target)),rhs));
                values.put(key(target),next);
            } else throw invalid();
        }
        Set<SymbolId> parameters=new LinkedHashSet<>(bindings.keySet());parameters.add(startSymbol);
        var sums=new ArrayList<PolynomialSum>();LinearIteration linear=null;
        if(additive) {
            for(String key:targets.keySet()) sums.add(new PolynomialSum(index,parameters,VariableExpr.scoped(startSymbol),header.stride(),terms.get(key),initial.get(key),arithmetic(width)));
        } else {
            parameters.add(countSymbol);
            var linearStates=new ArrayList<LinearIteration.State>();var updatesList=new ArrayList<LinearIteration.Update>();
            for(String key:targets.keySet()) {linearStates.add(new LinearIteration.State(stateSymbols.get(key),initial.get(key)));updatesList.add(new LinearIteration.Update(stateSymbols.get(key),values.get(key)));}
            linear=new LinearIteration(index,VariableExpr.scoped(startSymbol),header.stride(),VariableExpr.scoped(countSymbol),linearStates,parameters,updatesList,LinearIteration.UpdateOrder.SIMULTANEOUS,arithmetic(width));
        }
        if(width.equals("java.math.BigInteger")) valueOnlyUses(targets.values());
        return new Context(node,header,width,List.copyOf(targets.values()),List.copyOf(bindings.values()),sums,linear,null,null,0,false,false,startSymbol,countSymbol,null);
    }
    private Context partition(Header header,IfStatement branch) {
        var admitted=JavaPartitionedSumAnalysis.inspect(node,header,branch);
        var target=variable(admitted.first().getLeftHandSide());
        if(target==null || target.isField() || target.isEqualTo(header.counter()))throw invalid();
        String width=width(target.getType());
        if(!width.equals(header.counter().getType().getName()))throw invalid();
        states.add(key(target));rejectHeaderStates(header);
        Expr initial=VariableExpr.scoped(bind(target));
        if(admitted.split()!=null) {
            headerValue(admitted.split(),0);
            admitted.split().accept(new ASTVisitor() {
                @Override public boolean visit(SimpleName name) {
                    work.tick();if(name.resolveBinding() instanceof IVariableBinding binding
                            && (binding.isEqualTo(header.counter()) || binding.isEqualTo(target)))throw invalid();return true;
                }
            });
        }
        Expr first=partitionTerm(admitted.first(),width),second=admitted.second()==null?new NumberExpr(0):partitionTerm(admitted.second(),width);
        int firstNodes=bounded(first),secondNodes=bounded(second);
        var starts=new ArrayList<SymbolId>();var sums=new ArrayList<PolynomialSum>();
        int size=admitted.period()==0?2:admitted.period();
        for(int part=0;part<size;part++) {
            SymbolId start=new SymbolId(namespace,Long.MAX_VALUE-100-part);starts.add(start);
            var parameters=new LinkedHashSet<>(bindings.keySet());parameters.add(start);
            boolean usesFirst=admitted.period()>0?part==admitted.residue():(part==0)==admitted.firstOnLow();
            // Prepay SDK scope validation, parameter copies/unions and start/initial checks,
            // including failed constructors. Each charge also polls cancellation/deadline.
            work.charge(32L+8L*parameters.size()+4L*(usesFirst?firstNodes:secondNodes));
            sums.add(new PolynomialSum(index,parameters,VariableExpr.scoped(start),BigInteger.valueOf(Math.max(1,admitted.period())),
                    usesFirst?first:second,part==0?initial:new NumberExpr(0),arithmetic(width)));
        }
        var split=admitted.split();
        var partition=new RuntimeIterationEvidence.Partition(admitted.period(),admitted.residue(),admitted.inclusive(),admitted.firstOnLow(),
                split==null?-1:split.getStartPosition(),split==null?0:split.getLength(),starts);
        return new Context(node,header,width,List.of(target),List.copyOf(bindings.values()),sums,null,null,null,0,false,false,startSymbol,countSymbol,null,partition,split);
    }
    private Expr partitionTerm(Assignment assignment,String width) {
        Expr term=decode(assignment.getRightHandSide(),width,values);
        return assignment.getOperator()==Assignment.Operator.MINUS_ASSIGN?sub(new NumberExpr(0),term):term;
    }
    private Context nested(Header outer,ForStatement innerNode) {
        Header inner=header(innerNode);
        if(inner.live() || !inner.stride().equals(BigInteger.ONE) || inner.statements().size()!=1 || inner.counter().isEqualTo(outer.counter())) throw invalid();
        Object start=inner.start().resolveConstantExpressionValue();
        if(!(start instanceof Integer integer) || integer<0 || integer>1) throw invalid();
        Expression bound=inner.bound();while(bound instanceof ParenthesizedExpression p) bound=p.getExpression();
        if(!(bound instanceof SimpleName) || variable(bound)==null) throw invalid();
        if(!(inner.statements().getFirst() instanceof ExpressionStatement statement) || !(statement.getExpression() instanceof Assignment assignment)) throw invalid();
        var target=variable(assignment.getLeftHandSide());
        if(target==null || target.isField() || target.isEqualTo(outer.counter()) || target.isEqualTo(inner.counter())
                || !(unit.findDeclaringNode(target) instanceof VariableDeclaration declaration) || declaration.getStartPosition()>=node.getStartPosition()) throw invalid();
        String width=width(target.getType());states.add(key(target));rejectHeaderStates(outer);rejectHeaderStates(inner);
        Expr initial=VariableExpr.scoped(bind(target));SymbolId innerIndex=new SymbolId(namespace,4);
        values.put(key(inner.counter()),VariableExpr.scoped(innerIndex));
        Expr term=decode(assignment.getRightHandSide(),width,values);bounded(term);
        if(assignment.getOperator()==Assignment.Operator.MINUS_ASSIGN) term=sub(new NumberExpr(0),term);
        else if(assignment.getOperator()!=Assignment.Operator.PLUS_ASSIGN) throw invalid();
        if(contains(term,((VariableExpr)initial).symbol().orElseThrow())) throw invalid();
        Expr innerCount=add(decode(bound,inner.counter().getType().getName(),values),new NumberExpr((inner.inclusive()?1:0)-integer));
        var outerParameters=new LinkedHashSet<>(bindings.keySet());outerParameters.add(startSymbol);
        var innerParameters=new LinkedHashSet<>(outerParameters);innerParameters.add(index);
        var innerSum=new PolynomialSum(innerIndex,innerParameters,new NumberExpr(integer),BigInteger.ONE,term,new NumberExpr(0),IntegerArithmetic.EXACT);
        var nested=new PolynomialSeries.NestedSum(innerSum,innerCount,index,outerParameters,VariableExpr.scoped(startSymbol),outer.stride(),initial,IntegerArithmetic.EXACT);
        if(width.equals("java.math.BigInteger")) valueOnlyUses(List.of(target));
        return new Context(node,outer,width,List.of(target),List.copyOf(bindings.values()),List.of(),null,nested,bound,integer,inner.inclusive(),false,startSymbol,countSymbol,inner.counter().getType().getName());
    }
    private Context stream(MethodInvocation sum) {
        String owner=owner(sum);String width=owner.equals("java.util.stream.IntStream")?"int":owner.equals("java.util.stream.LongStream")?"long":null;
        if(width==null || !sum.getName().getIdentifier().equals("sum") || !sum.arguments().isEmpty()) throw invalid();
        Expression receiver=sequential(sum.getExpression(),owner);LambdaExpression lambda=null;
        if(receiver instanceof MethodInvocation map && map.getName().getIdentifier().equals("map")) {
            if(!owner(map).equals(owner) || map.arguments().size()!=1 || !(map.arguments().getFirst() instanceof LambdaExpression l)
                    || l.parameters().size()!=1 || !(l.getBody() instanceof Expression)) throw invalid();
            lambda=l;receiver=sequential(map.getExpression(),owner);
        }
        if(!(receiver instanceof MethodInvocation range) || !owner(range).equals(owner) || range.arguments().size()!=2
                || !(range.getExpression() instanceof Name name) || !(name.resolveBinding() instanceof ITypeBinding)
                || !java.lang.reflect.Modifier.isStatic(range.resolveMethodBinding().getModifiers())) throw invalid();
        boolean inclusive=range.getName().getIdentifier().equals("rangeClosed");if(!inclusive && !range.getName().getIdentifier().equals("range")) throw invalid();
        Expression start=(Expression)range.arguments().get(0),bound=(Expression)range.arguments().get(1);
        headerValue(start,0);headerValue(bound,0);
        Expr term=VariableExpr.scoped(index);IVariableBinding counter=null;
        if(lambda!=null) {
            if(!(lambda.parameters().getFirst() instanceof VariableDeclaration declaration)) throw invalid();counter=declaration.resolveBinding();
            if(counter==null || !width(counter.getType()).equals(width)) throw invalid();values.put(key(counter),term);
            term=decode((Expression)lambda.getBody(),width,values);
        }
        var parameters=new LinkedHashSet<>(bindings.keySet());parameters.add(startSymbol);
        var model=new PolynomialSum(index,parameters,VariableExpr.scoped(startSymbol),BigInteger.ONE,term,new NumberExpr(0),arithmetic(width));
        return new Context(node,new Header(counter,start,bound,BigInteger.ONE,inclusive,false,false,null,List.of()),width,List.of(),List.copyOf(bindings.values()),List.of(model),null,null,null,0,false,true,startSymbol,countSymbol,null);
    }
    private static Expression sequential(Expression expression,String owner) {
        if(expression instanceof MethodInvocation invocation && invocation.getName().getIdentifier().equals("sequential")) {
            if(!owner(invocation).equals(owner) || !invocation.arguments().isEmpty()) throw invalid();return invocation.getExpression();
        }return expression;
    }
    private void rejectHeaderStates(Header header) {
        for(Expression expression:List.of(header.start(),header.bound())) expression.accept(new ASTVisitor() {
            @Override public boolean visit(SimpleName name) {
                work.tick();if(name.resolveBinding() instanceof IVariableBinding binding && states.contains(key(binding))) throw invalid();return true;
            }
        });
    }
    private Header header(ASTNode loop) {
        Expression start,condition,update;IVariableBinding counter;Statement body;boolean live=false,once=false;
        if(loop instanceof ForStatement forLoop) {
            if(forLoop.initializers().size()!=1 || forLoop.updaters().size()!=1) throw invalid();
            Object initializer=forLoop.initializers().getFirst();
            if(initializer instanceof VariableDeclarationExpression declaration && declaration.fragments().size()==1) {
                var fragment=(VariableDeclarationFragment)declaration.fragments().getFirst();counter=fragment.resolveBinding();start=fragment.getInitializer();
            } else if(initializer instanceof Assignment assignment && assignment.getOperator()==Assignment.Operator.ASSIGN) {
                counter=variable(assignment.getLeftHandSide());start=assignment.getRightHandSide();live=true;
            } else throw invalid();
            condition=forLoop.getExpression();update=(Expression)forLoop.updaters().getFirst();body=forLoop.getBody();
        } else {
            if(loop instanceof WhileStatement whileLoop) {condition=whileLoop.getExpression();body=whileLoop.getBody();}
            else if(loop instanceof DoStatement doLoop) {condition=doLoop.getExpression();body=doLoop.getBody();once=true;}
            else throw invalid();
            if(!(condition instanceof InfixExpression infix)) throw invalid();counter=variable(infix.getLeftOperand());
            if(counter==null) throw invalid();start=infix.getLeftOperand();live=true;
            List<Statement> statements=statements(body);
            if(statements.isEmpty() || !(statements.getLast() instanceof ExpressionStatement last)) throw invalid();update=last.getExpression();
        }
        if(counter==null || counter.isField() || counter.isRecovered() || !(width(counter.getType()).equals("int") || width(counter.getType()).equals("long"))
                || !(condition instanceof InfixExpression infix) || infix.hasExtendedOperands() || !same(infix.getLeftOperand(),counter)) throw invalid();
        Expression bound=infix.getRightOperand();
        IVariableBinding induction=counter;
        bound.accept(new ASTVisitor() {@Override public boolean visit(SimpleName name) {work.tick();if(name.resolveBinding() instanceof IVariableBinding binding && binding.isEqualTo(induction))throw invalid();return true;}});
        BigInteger stride=stride(update,counter);
        BigInteger minimum=BigInteger.ONE.shiftLeft(counter.getType().getName().equals("int")?31:63).negate();
        if(stride.compareTo(minimum)<0 || stride.compareTo(minimum.negate().subtract(BigInteger.ONE))>0)throw invalid();
        var operator=infix.getOperator();boolean inclusive=operator==InfixExpression.Operator.LESS_EQUALS || operator==InfixExpression.Operator.GREATER_EQUALS;
        if(stride.signum()==0 || stride.signum()>0 && operator!=InfixExpression.Operator.LESS && operator!=InfixExpression.Operator.LESS_EQUALS
                || stride.signum()<0 && operator!=InfixExpression.Operator.GREATER && operator!=InfixExpression.Operator.GREATER_EQUALS) throw invalid();
        var statements=new ArrayList<>(statements(body));if(!(loop instanceof ForStatement)) statements.removeLast();
        return new Header(counter,start,bound,stride,inclusive,once,live,body,List.copyOf(statements));
    }
    private void valueOnlyUses(Collection<IVariableBinding> targets) {
        // A lexical suffix is insufficient across a backedge: earlier identity uses execute again.
        // Until a lifetime/control-flow proof is available, keep BigInteger updates inside repetition unchanged.
        for(ASTNode ancestor=node.getParent();ancestor!=null;ancestor=ancestor.getParent()) {
            work.tick();
            if(ancestor instanceof MethodDeclaration || ancestor instanceof LambdaExpression)break;
            if(ancestor instanceof ForStatement || ancestor instanceof EnhancedForStatement || ancestor instanceof WhileStatement || ancestor instanceof DoStatement)
                throw new IllegalArgumentException("BIG_INTEGER_ENCLOSING_REPETITION");
        }
        unit.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode occurrence) {work.tick();}
            @Override public boolean visit(SimpleName name) {
                if(name.getStartPosition()<node.getStartPosition()+node.getLength() || !(name.resolveBinding() instanceof IVariableBinding binding)
                        || targets.stream().noneMatch(target->target.isEqualTo(binding)))return true;
                ASTNode receiver=name;while(receiver.getParent() instanceof ParenthesizedExpression)receiver=receiver.getParent();
                if(receiver.getParent() instanceof InfixExpression infix && !infix.hasExtendedOperands()
                        && (infix.getOperator()==InfixExpression.Operator.EQUALS || infix.getOperator()==InfixExpression.Operator.NOT_EQUALS)
                        && (infix.getLeftOperand() instanceof NullLiteral || infix.getRightOperand() instanceof NullLiteral))return true;
                if(receiver.getParent() instanceof MethodInvocation invocation && invocation.getExpression()==receiver
                        && owner(invocation).equals("java.math.BigInteger")
                        && Set.of("intValue","longValue","floatValue","doubleValue","signum","bitLength","bitCount","toString","hashCode").contains(invocation.getName().getIdentifier())
                        && invocation.arguments().isEmpty())return true;
                throw new IllegalArgumentException("BIG_INTEGER_IDENTITY_OR_ESCAPE");
            }
        });
    }
    private void headerValue(Expression expression,int depth) {
        work.tick();if(expression==null || depth>64 || !(JavaFiniteSumAnalysis.type(expression.resolveTypeBinding(),"int") || JavaFiniteSumAnalysis.type(expression.resolveTypeBinding(),"long")))throw invalid();
        if(expression instanceof NumberLiteral)return;
        if(expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding binding) {
            if(binding.isField())throw invalid();bind(binding);return;
        }
        if(expression instanceof QualifiedName name && name.getQualifier().resolveBinding() instanceof ITypeBinding
                && name.resolveBinding() instanceof IVariableBinding field && field.isField() && field.getConstantValue()!=null)return;
        if(expression instanceof ParenthesizedExpression p) {headerValue(p.getExpression(),depth+1);return;}
        if(expression instanceof CastExpression cast) {headerValue(cast.getExpression(),depth+1);return;}
        if(expression instanceof PrefixExpression prefix && Set.of(PrefixExpression.Operator.PLUS,PrefixExpression.Operator.MINUS,PrefixExpression.Operator.COMPLEMENT).contains(prefix.getOperator())) {headerValue(prefix.getOperand(),depth+1);return;}
        if(expression instanceof InfixExpression infix && Set.of(InfixExpression.Operator.PLUS,InfixExpression.Operator.MINUS,InfixExpression.Operator.TIMES,
                InfixExpression.Operator.AND,InfixExpression.Operator.OR,InfixExpression.Operator.XOR,InfixExpression.Operator.LEFT_SHIFT,InfixExpression.Operator.RIGHT_SHIFT_SIGNED,InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED).contains(infix.getOperator())) {
            headerValue(infix.getLeftOperand(),depth+1);headerValue(infix.getRightOperand(),depth+1);for(Object operand:infix.extendedOperands())headerValue((Expression)operand,depth+1);return;
        }throw invalid();
    }
    private Expr decode(Expression expression,String width,Map<String,Expr> local) {
        if(!width.equals("java.math.BigInteger")) return JavaFiniteSumAnalysis.decodeResolved(expression,width,read->resolve(read,local),work,0);
        return big(expression,local,0);
    }
    private Expr big(Expression expression,Map<String,Expr> local,int depth) {
        work.tick();if(expression==null || depth>64 || !bigType(expression.resolveTypeBinding())) throw invalid();
        if(expression instanceof ParenthesizedExpression p) return big(p.getExpression(),local,depth+1);
        if(expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding variable && !variable.isField()) return resolve(variable,local);
        if(expression instanceof Name name && name.resolveBinding() instanceof IVariableBinding field && field.isField()
                && field.getDeclaringClass().getQualifiedName().equals("java.math.BigInteger") && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            if(name instanceof QualifiedName qualified && !(qualified.getQualifier().resolveBinding() instanceof ITypeBinding)) throw invalid();
            return switch(field.getName()) {case "ZERO"->new NumberExpr(0);case "ONE"->new NumberExpr(1);case "TEN"->new NumberExpr(10);default->throw invalid();};
        }
        if(!(expression instanceof MethodInvocation call) || !owner(call).equals("java.math.BigInteger")) throw invalid();
        String method=call.getName().getIdentifier();
        if(method.equals("valueOf") && call.arguments().size()==1 && java.lang.reflect.Modifier.isStatic(call.resolveMethodBinding().getModifiers())
                && call.getExpression() instanceof Name name && name.resolveBinding() instanceof ITypeBinding) {
            Expression value=(Expression)call.arguments().getFirst();
            if(value instanceof CastExpression cast && JavaFiniteSumAnalysis.type(cast.getType().resolveBinding(),"long")) value=cast.getExpression();
            if(value instanceof SimpleName variable && variable.resolveBinding() instanceof IVariableBinding binding && !binding.isField()) return resolve(binding,local);
            decode(value,value.resolveTypeBinding().getName(),local);Object constant=value.resolveConstantExpressionValue();
            if(constant instanceof Integer number) return new NumberExpr(number.longValue());if(constant instanceof Long number) return new NumberExpr(number);
            throw invalid();
        }
        if(java.lang.reflect.Modifier.isStatic(call.resolveMethodBinding().getModifiers()) || call.getExpression()==null) throw invalid();
        Expr receiver=big(call.getExpression(),local,depth+1);
        if(method.equals("negate") && call.arguments().isEmpty()) return sub(new NumberExpr(0),receiver);
        if(call.arguments().size()!=1) throw invalid();
        if(method.equals("pow")) {
            decode((Expression)call.arguments().getFirst(),"int",local);Object exponent=((Expression)call.arguments().getFirst()).resolveConstantExpressionValue();
            if(!(exponent instanceof Integer number) || number<0 || number>8) throw invalid();
            Expr result=new NumberExpr(1);for(int i=0;i<number;i++)result=mul(result,receiver);return result;
        }
        Expr argument=big((Expression)call.arguments().getFirst(),local,depth+1);
        return switch(method) {case "add"->add(receiver,argument);case "subtract"->sub(receiver,argument);case "multiply"->mul(receiver,argument);default->throw invalid();};
    }
    private Expr resolve(IVariableBinding read,Map<String,Expr> local) {
        Expr value=local.get(key(read));if(value!=null) return value;
        if(states.contains(key(read))) throw invalid();return VariableExpr.scoped(bind(read));
    }
    private SymbolId bind(IVariableBinding binding) {
        work.tick();if(binding.isField() || binding.isRecovered() || !(unit.findDeclaringNode(binding) instanceof VariableDeclaration declaration)
                || declaration.getStartPosition()>=node.getStartPosition()) throw invalid();
        width(binding.getType());SymbolId symbol=new SymbolId(namespace,declaration.getName().getStartPosition()+10L);
        bindings.putIfAbsent(symbol,new FiniteSumEvidence.Binding(symbol,declaration.getName().getStartPosition(),binding.getName(),width(binding.getType())));return symbol;
    }
    private int bounded(Expr expression) {
        record Node(Expr value,int depth) { }
        var pending=new ArrayDeque<Node>();pending.push(new Node(expression,1));int count=0;
        while(!pending.isEmpty()) {
            work.tick();var current=pending.pop();
            if(++count>2048 || current.depth()>64)throw new IllegalArgumentException("RUNTIME_SOURCE_EXPRESSION_LIMIT");
            if(current.value() instanceof BinaryExpr binary) {pending.push(new Node(binary.left(),current.depth()+1));pending.push(new Node(binary.right(),current.depth()+1));}
        }
        return count;
    }
    private static boolean containsState(Expr expression,Collection<SymbolId> ids) {return ids.stream().anyMatch(id->contains(expression,id));}
    private static boolean contains(Expr expression,SymbolId id) {
        if(expression instanceof VariableExpr variable) return variable.symbol().orElseThrow().equals(id);
        return expression instanceof BinaryExpr binary && (contains(binary.left(),id)||contains(binary.right(),id));
    }
    private static String owner(MethodInvocation call) {
        var method=call.resolveMethodBinding();if(method==null || method.isRecovered() || method.getDeclaringClass()==null) throw invalid();
        return method.getDeclaringClass().getQualifiedName();
    }
    private BigInteger stride(Expression update,IVariableBinding counter) {
        if(update instanceof PostfixExpression postfix && same(postfix.getOperand(),counter)) return postfix.getOperator()==PostfixExpression.Operator.INCREMENT?BigInteger.ONE:BigInteger.ONE.negate();
        if(update instanceof PrefixExpression prefix && same(prefix.getOperand(),counter)) {
            if(prefix.getOperator()==PrefixExpression.Operator.INCREMENT) return BigInteger.ONE;if(prefix.getOperator()==PrefixExpression.Operator.DECREMENT) return BigInteger.ONE.negate();
        }
        if(update instanceof Assignment assignment && same(assignment.getLeftHandSide(),counter)) {
            headerValue(assignment.getRightHandSide(),0);Object value=assignment.getRightHandSide().resolveConstantExpressionValue();BigInteger step=value instanceof Integer n?BigInteger.valueOf(n):value instanceof Long n?BigInteger.valueOf(n):null;
            if(step!=null) {if(assignment.getOperator()==Assignment.Operator.PLUS_ASSIGN) return step;if(assignment.getOperator()==Assignment.Operator.MINUS_ASSIGN) return step.negate();}
        }throw invalid();
    }
    private static List<Statement> statements(Statement body) {if(body instanceof Block block) {var result=new ArrayList<Statement>();for(Object item:block.statements()) result.add((Statement)item);return result;}return List.of(body);}
    private static boolean same(Expression expression,IVariableBinding binding) {var value=variable(expression);return value!=null&&value.isEqualTo(binding);}
    private static IVariableBinding variable(Expression expression) {return JavaFiniteSumAnalysis.variable(expression);}
    private static String key(IVariableBinding binding) {return JavaFiniteSumAnalysis.key(binding);}
    private static boolean bigType(ITypeBinding type) {return type!=null&&!type.isRecovered()&&type.getQualifiedName().equals("java.math.BigInteger");}
    private static String width(ITypeBinding type) {if(JavaFiniteSumAnalysis.type(type,"int")) return "int";if(JavaFiniteSumAnalysis.type(type,"long")) return "long";if(bigType(type)) return "java.math.BigInteger";throw invalid();}
    private static IntegerArithmetic arithmetic(String width) {return width.equals("int")?IntegerArithmetic.JAVA_INT:width.equals("long")?IntegerArithmetic.JAVA_LONG:IntegerArithmetic.EXACT;}
    private static Expr add(Expr a,Expr b) {return new BinaryExpr(a,BinaryOperator.ADD,b);}
    private static Expr sub(Expr a,Expr b) {return new BinaryExpr(a,BinaryOperator.SUB,b);}
    private static Expr mul(Expr a,Expr b) {return new BinaryExpr(a,BinaryOperator.MUL,b);}
    static IllegalArgumentException invalid() {return new IllegalArgumentException("RUNTIME_ITERATION_NOT_ADMITTED");}
}

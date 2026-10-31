/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.sandbox.functional.core.model.ElementDescriptor;
import org.sandbox.functional.core.model.FunctionalExpression;
import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.model.NumericIteration;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.operation.FilterOp;
import org.sandbox.functional.core.operation.MapOp;
import org.sandbox.functional.core.operation.PeekOp;
import de.regelsuche.ast.Expr;
import de.regelsuche.ast.FunctionExpr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.sdk.optimization.CancellationToken;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.SourceEvaluationTrace;
import de.regelsuche.search.program.ComputationBackend.Type;
import de.regelsuche.search.program.JointComputationPlan;

/** JDT source structure and bindings; the ULR descriptor never authorizes a rewrite. */
final class JavaNumericIterationContexts {
    record Context(NumericIteration iteration, MethodInvocation terminal, List<MethodInvocation> stages,
            boolean standardMapChain) { }
    record Pair(JavaComputationRegion region, MethodInvocation first, MethodInvocation second,
            LambdaExpression originalLambda, String parameterName) { }
    record Composition(Pair pair, long work) { }
    record Discovery(List<Context> contexts, long work) { }
    private JavaNumericIterationContexts() { }

    static Discovery discover(CompilationUnit unit, String source, long maximum, CancellationToken token) {
        var contexts = new ArrayList<Context>();
        var work = new Work(maximum, token);
        try {
            unit.accept(new ASTVisitor() {
                @Override public void preVisit(ASTNode node) { work.tick(); }
                @Override public boolean visit(MethodInvocation call) {
                    if (Set.of("sum", "reduce", "min", "max").contains(call.getName().getIdentifier())) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        Context context = inspect(call, source, work);
                        if (context != null) contexts.add(context);
                    }
                    return true;
                }
            });
        } catch (Limit bounded) { contexts.clear(); /* Incomplete discovery cannot authorize topology changes. */ }
        return new Discovery(List.copyOf(contexts), work.used);
    }

    private static Context inspect(MethodInvocation terminal, String source, Work work) {
        String primitive = streamKind(terminal.resolveMethodBinding());
        if (primitive == null || terminal.getExpression() == null) return null;
        var stages = new ArrayList<MethodInvocation>();
        Expression receiver = terminal.getExpression();
        boolean ordered = true, onlyMaps = true;
        NumericIteration.Execution explicitExecution = null;
        while (receiver instanceof MethodInvocation stage && !Modifier.isStatic(modifiers(stage))) {
            work.tick();
            if (stages.size() >= 32 || !primitive.equals(streamKind(stage.resolveMethodBinding()))) return null;
            String name = stage.getName().getIdentifier();
            // Walk from the terminal backwards: the first explicit mode is the last authored one.
            if (explicitExecution == null && name.equals("parallel")) explicitExecution = NumericIteration.Execution.PARALLEL; //$NON-NLS-1$
            if (explicitExecution == null && name.equals("sequential")) explicitExecution = NumericIteration.Execution.SEQUENTIAL; //$NON-NLS-1$
            ordered &= !name.equals("unordered"); //$NON-NLS-1$
            onlyMaps &= name.equals("map"); //$NON-NLS-1$
            if (!Set.of("map", "filter", "peek", "parallel", "sequential", "unordered").contains(name)) return null; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
            stages.add(stage); receiver = stage.getExpression();
            if (receiver == null) return null;
        }
        Collections.reverse(stages);
        var model = new LoopModel(new SourceDescriptor(SourceDescriptor.SourceType.STREAM, text(source,receiver), primitive,
                text(source,receiver)), new ElementDescriptor("element", primitive), null); //$NON-NLS-1$
        for (var stage : stages) {
            work.tick();
            String name = stage.getName().getIdentifier();
            if (stage.arguments().size() != 1) continue;
            Expression callback = (Expression)stage.arguments().getFirst();
            var function = function(source, callback);
            if (function == null) return null;
            if (name.equals("map")) model.addOperation(new MapOp(text(source,callback), function.outputType(), function.parameterName(), false, function)); //$NON-NLS-1$
            else if (name.equals("filter")) model.addOperation(new FilterOp(text(source,callback), function)); //$NON-NLS-1$
            else if (name.equals("peek")) model.addOperation(new PeekOp(text(source,callback), function)); //$NON-NLS-1$
        }
        String operation = terminal.getName().getIdentifier();
        String identity = null;
        NumericIteration.Accumulator accumulator = null;
        if (operation.equals("reduce")) { //$NON-NLS-1$
            int arity = terminal.arguments().size();
            if (arity != 1 && arity != 2) return null;
            if (arity == 2) identity = text(source,(Expression)terminal.arguments().getFirst());
            Expression callback = (Expression)terminal.arguments().getLast();
            var type = callback.resolveTypeBinding(); var sam = type == null ? null : type.getFunctionalInterfaceMethod();
            if (sam == null || sam.isRecovered() || sam.getParameterTypes().length != 2) return null;
            accumulator = new NumericIteration.Accumulator(text(source,callback),
                    List.of(sam.getParameterTypes()[0].getQualifiedName(),sam.getParameterTypes()[1].getQualifiedName()),
                    sam.getReturnType().getQualifiedName(),type.getQualifiedName());
        } else if (!terminal.arguments().isEmpty()) return null;
        var binding = terminal.resolveMethodBinding();
        String result = binding.getReturnType().getQualifiedName();
        var numericTerminal = new NumericIteration.Terminal(operation,result,result.startsWith("java.util.Optional"),identity,accumulator, //$NON-NLS-1$
                source.substring(terminal.getExpression().getStartPosition()+terminal.getExpression().getLength(),terminal.getStartPosition()+terminal.getLength()));
        boolean knownSource = standardSource(receiver,primitive);
        var execution = explicitExecution != null ? explicitExecution
                : knownSource ? NumericIteration.Execution.SEQUENTIAL : NumericIteration.Execution.UNKNOWN;
        var encounterOrder = !ordered ? NumericIteration.EncounterOrder.UNORDERED
                : knownSource ? NumericIteration.EncounterOrder.ORDERED : NumericIteration.EncounterOrder.UNKNOWN;
        return new Context(new NumericIteration(model,primitive,execution,encounterOrder,numericTerminal),terminal,List.copyOf(stages),
                onlyMaps && execution == NumericIteration.Execution.SEQUENTIAL && ordered && knownSource);
    }

    private static FunctionalExpression function(String source, Expression callback) {
        var type = callback.resolveTypeBinding(); var sam = type == null ? null : type.getFunctionalInterfaceMethod();
        if (sam == null || sam.isRecovered() || sam.getParameterTypes().length != 1) return null;
        String parameter = callback instanceof LambdaExpression lambda && lambda.parameters().size() == 1
                ? ((VariableDeclaration)lambda.parameters().getFirst()).getName().getIdentifier() : null;
        return new FunctionalExpression(text(source,callback),parameter,sam.getParameterTypes()[0].getQualifiedName(),
                sam.getReturnType().getQualifiedName(),type.getQualifiedName(),true,false);
    }

    private static boolean standardSource(Expression expression, String primitive) {
        if (!(expression instanceof MethodInvocation factory)) return false;
        var method = factory.resolveMethodBinding();
        if (method == null || method.isRecovered() || !Modifier.isStatic(method.getModifiers()) || !factory.typeArguments().isEmpty()) return false;
        String owner = method.getDeclaringClass().getQualifiedName(), name = method.getName();
        String expected = "java.util.stream." + (primitive.equals("int") ? "IntStream" : primitive.equals("long") ? "LongStream" : "DoubleStream"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        if (!factory.resolveTypeBinding().getQualifiedName().equals(expected)) return false;
        if (owner.equals(expected)) return Set.of("of","empty","range","rangeClosed").contains(name); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        return owner.equals("java.util.Arrays") && name.equals("stream") && method.getParameterTypes().length > 0 //$NON-NLS-1$ //$NON-NLS-2$
                && method.getParameterTypes()[0].isArray() && method.getParameterTypes()[0].getElementType().getName().equals(primitive);
    }

    static Composition compose(CompilationUnit unit, String source, Context context, int index, MathCleanUpOptions options,
            long maximum, CancellationToken token) {
        var work = new Work(maximum,token);
        Pair pair = null;
        try { pair = composePair(unit,source,context,index,options,work); }
        catch (Limit bounded) { /* Return inspected work even when admission stops early. */ }
        return new Composition(pair,work.used);
    }

    private static Pair composePair(CompilationUnit unit, String source, Context context, int index,
            MathCleanUpOptions options, Work work) {
        if (!context.standardMapChain() || !Set.of("int","long").contains(context.iteration().elementType())) return null; //$NON-NLS-1$ //$NON-NLS-2$
        var first = context.stages().get(index); var second = context.stages().get(index+1);
        if (second.getExpression() != first || first.arguments().size()!=1 || second.arguments().size()!=1
                || !(first.arguments().getFirst() instanceof LambdaExpression left)
                || !(second.arguments().getFirst() instanceof LambdaExpression right)
                || !(left.getBody() instanceof Expression leftBody) || !(right.getBody() instanceof Expression rightBody)
                || left.parameters().size()!=1 || right.parameters().size()!=1) return null;
        // Keep every comment in the changed span. Inspection of a commented chain remains valid.
        for (Object entry : unit.getCommentList()) {
            work.tick();
            ASTNode comment=(ASTNode)entry;
            if (comment.getStartPosition() >= first.getExpression().getStartPosition()+first.getExpression().getLength()
                    && comment.getStartPosition() < second.getStartPosition()+second.getLength()) return null;
        }
        try {
            String primitive = context.iteration().elementType();
            if (!pure(leftBody, primitive,work) || !pure(rightBody, primitive,work)) return null;
            var extractor=new JavaComputationExtractor();
            chargeSourceScan(unit,work);
            var a=extractor.extractRuntimeExpression(unit,source,leftBody,options);
            chargeSourceScan(unit,work);
            var b=extractor.extractRuntimeExpression(unit,source,rightBody,options);
            if (a.regions().size()!=1 || b.regions().size()!=1) return null;
            var parts=List.of(a.regions().getFirst(),b.regions().getFirst());
            if (parts.stream().anyMatch(part -> !part.arrayReads().isEmpty() || !part.receiverGuards().isEmpty()
                    || !part.assumptions().isEmpty() || part.outputs().size()!=1 || !part.internalBindings().isEmpty()
                    || !JavaArrayReadInputs.totalPrefix(part.trace().occurrences(),part.outputs()))) return null;
            String fresh="_mathElement"; //$NON-NLS-1$
            var reserved=new HashSet<String>();
            parts.forEach(part -> reserved.addAll(part.reservedNames()));
            while (reserved.contains(fresh)) fresh+="_"; //$NON-NLS-1$
            var inputs=new LinkedHashMap<String,Type>(); var names=new LinkedHashMap<String,String>();
            var byBinding=new HashMap<String,Expr>(); var occurrences=new ArrayList<SourceEvaluationTrace.Occurrence>();
            Expr output=null;
            for (int partIndex=0;partIndex<2;partIndex++) {
                var part=parts.get(partIndex); var lambda=partIndex==0?left:right;
                var parameter=((VariableDeclaration)lambda.parameters().getFirst()).resolveBinding();
                var bindings=new HashMap<String,IVariableBinding>();
                lambda.getBody().accept(new ASTVisitor() {
                    @Override public boolean visit(SimpleName name) {
                        work.tick();
                        if (name.resolveBinding() instanceof IVariableBinding variable) {
                            var old=bindings.putIfAbsent(name.getIdentifier(),variable);
                            if (old!=null && !old.isEqualTo(variable)) throw new Limit();
                        }
                        return true;
                    }
                });
                var substitutions=new HashMap<String,Expr>();
                for (var input:part.plan().inputs().entrySet()) {
                    work.tick();
                    var binding=bindings.get(part.inputNames().get(input.getKey()));
                    if (binding==null || binding.isRecovered()) return null;
                    Expr value;
                    if (binding.isEqualTo(parameter) && partIndex==1) value=output;
                    else {
                        value=byBinding.get(binding.getKey());
                        if (value==null) {
                            String id="streamInput"+inputs.size(); value=new VariableExpr(id); //$NON-NLS-1$
                            byBinding.put(binding.getKey(),value); inputs.put(id,input.getValue());
                            names.put(id,binding.isEqualTo(parameter)?fresh:binding.getName());
                        }
                    }
                    substitutions.put(input.getKey(),value);
                }
                output=substitute(part.plan().outputs().getFirst().expression(),substitutions,work,0);
                for (var occurrence:part.trace().occurrences()) occurrences.add(new SourceEvaluationTrace.Occurrence(
                        "map"+partIndex+":"+occurrence.sourceId(),substitute(occurrence.expression(),substitutions,work,0), //$NON-NLS-1$ //$NON-NLS-2$
                        occurrence.declaredKind(),occurrence.evaluatedKind()));
            }
            int start=second.getStartPosition(),length=second.getLength();
            var kind=primitive.equals("int")?NumericKind.INT:NumericKind.LONG; //$NON-NLS-1$
            var out=new JavaComputationRegion.OutputBinding("streamOutput","",kind,start,length,start,length,false,"streamKernel",true); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            var plan=new JointComputationPlan(inputs,Map.of(),List.of(new JointComputationPlan.Output(out.id(),kind.type(),output)));
            plan.searchExpression();
            return new Pair(new JavaComputationRegion(plan,new SourceEvaluationTrace(occurrences),List.of(out),names,
                    Set.of(),reserved,Set.of(),start,length),first,second,left,fresh);
        } catch (Limit bounded) { return null; }
    }

    /** Bound and charge each expression extractor's complete source-binding scan. */
    private static void chargeSourceScan(CompilationUnit unit, Work work) {
        unit.accept(new ASTVisitor() { @Override public void preVisit(ASTNode node) { work.tick(); } });
    }

    private static boolean pure(Expression expression, String primitive, Work work) {
        boolean[] accepted={true};
        expression.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) {
                work.tick();
                // No calls, heap reads, mutable values, conversions or parameter writes.
                if (node instanceof Expression value) {
                    var type=value.resolveTypeBinding();
                    if (type==null || type.isRecovered() || !type.isPrimitive() || !type.getName().equals(primitive)) accepted[0]=false;
                }
                if (!(node instanceof org.eclipse.jdt.core.dom.InfixExpression
                        || node instanceof org.eclipse.jdt.core.dom.ParenthesizedExpression
                        || node instanceof org.eclipse.jdt.core.dom.PrefixExpression
                        || node instanceof org.eclipse.jdt.core.dom.NumberLiteral || node instanceof SimpleName)) accepted[0]=false;
                if (node instanceof SimpleName name) {
                    if (!(name.resolveBinding() instanceof IVariableBinding binding) || binding.isField()
                            || !(Modifier.isFinal(binding.getModifiers()) || binding.isEffectivelyFinal())) accepted[0]=false;
                }
            }
        });
        return accepted[0];
    }

    private static Expr substitute(Expr expression, Map<String,Expr> substitutions,Work work,int depth) {
        work.tick(); if(depth>32) throw new Limit();
        if(expression instanceof VariableExpr variable) {
            Expr result=substitutions.get(variable.name()); if(result==null) throw new Limit();
            bound(result,work,depth); return result;
        }
        if(expression instanceof FunctionExpr function) return new FunctionExpr(function.name(),function.arguments().stream()
                .map(child -> substitute(child,substitutions,work,depth+1)).toList());
        return expression;
    }
    private static void bound(Expr expression,Work work,int depth) {
        work.tick(); if(depth>32) throw new Limit();
        if(expression instanceof FunctionExpr function) for(var child:function.arguments()) bound(child,work,depth+1);
    }
    private static int modifiers(MethodInvocation call) { var method=call.resolveMethodBinding(); return method==null?0:method.getModifiers(); }
    private static String streamKind(IMethodBinding method) {
        if(method==null || method.isRecovered() || method.getDeclaringClass()==null) return null;
        return switch(method.getDeclaringClass().getQualifiedName()) {
            case "java.util.stream.IntStream" -> "int"; //$NON-NLS-1$ //$NON-NLS-2$
            case "java.util.stream.LongStream" -> "long"; //$NON-NLS-1$ //$NON-NLS-2$
            case "java.util.stream.DoubleStream" -> "double"; //$NON-NLS-1$ //$NON-NLS-2$
            default -> null;
        };
    }
    private static String text(String source,ASTNode node) { return source.substring(node.getStartPosition(),node.getStartPosition()+node.getLength()); }
    private static final class Work {
        private final long maximum; private final CancellationToken token; private long used;
        Work(long maximum,CancellationToken token) { this.maximum=Math.min(8192,Math.max(0,maximum));this.token=token; }
        void tick() { if(used>=maximum || token.isCancelled()) throw new Limit();used++; }
    }
    private static final class Limit extends RuntimeException {
        private static final long serialVersionUID=1L;
        Limit(){super(null,null,false,false);}
    }
}

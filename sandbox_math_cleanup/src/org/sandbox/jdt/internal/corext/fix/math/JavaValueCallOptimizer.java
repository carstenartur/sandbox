/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.*;
import de.regelsuche.sdk.optimization.*;
import de.regelsuche.search.program.JointComputationPlan;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;

/** Source-proved constant specialization at the original call position, with exact fallback. */
final class JavaValueCallOptimizer {
    private JavaValueCallOptimizer() { }
    static void optimize(CompilationUnit ast,String source,MathCleanUpOptions options,IProgressMonitor monitor,
            int selectionOffset,int selectionLength,Map<String,String> compiler,MathematicalAnalysis.SourceEnvironment environment,
            List<MathematicalAnalysis.Replacement> replacements,List<MathematicalAnalysis.VerifiedRegion> evidence,
            List<MathematicalAnalysis.Diagnostic> diagnostics,JavaValueSourceSummary.Work work,MathematicalJavadoc documentation) {
        if(!options.mathematicalOptIn() || !options.kinds().contains(NumericKind.BIG_INTEGER))return;
        boolean eligible=false;long preflight=0;
        for(Object item:ast.types())if(item instanceof TypeDeclaration type)for(var field:type.getFields()) {
            preflight++;
            ITypeBinding value=field.getType().resolveBinding();
            if(Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()) && JavaValueAccess.possiblyDirect(value))eligible=true;
        }
        var calls=new ArrayList<MethodInvocation>();
        try {
        work.charge(preflight);if(!eligible)return;
        ast.accept(new ASTVisitor(){
            @Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(MethodInvocation call) {
            // Keep authored conditionals and retained original fallbacks inside their existing envelope.
            for(ASTNode node=call.getParent();node!=null && !(node instanceof MethodDeclaration);node=node.getParent()) {
                work.charge(1);if(node instanceof ConditionalExpression)return true;
            }
            if(call.getExpression() instanceof SimpleName receiver && receiver.resolveBinding() instanceof IVariableBinding
                && call.arguments().size()==1 && call.arguments().getFirst() instanceof Expression argument) {
                IVariableBinding constant=constantField(argument);
                var receiverType=receiver.resolveTypeBinding();var target=call.resolveMethodBinding();
                // The emitted getters and factory belong to this exact receiver value type.
                if(constant!=null && receiverType!=null && !receiverType.isRecovered()
                && !JavaValueSourceSummary.big(receiverType) && constant.getType().isEqualTo(receiverType)
                && target!=null && target.getReturnType().isEqualTo(receiverType))calls.add(call);
            }
            return true;
        }});
        if(calls.isEmpty())return;
            work.charge(source.length());
            for(var call:calls) {
                int start=call.getStartPosition(),length=call.getLength();
                if(selectionOffset>=0 && !overlaps(selectionOffset,Math.max(1,selectionLength),start,length)
                    || replacements.stream().anyMatch(edit->overlaps(start,length,edit.offset(),edit.length())))continue;
                try {
                    var method=parentMethod(call);var owner=method.resolveBinding().getDeclaringClass();
                    var receiver=(SimpleName)call.getExpression();var receiverBinding=(IVariableBinding)receiver.resolveBinding();
                    var argument=(Expression)call.arguments().getFirst();var constant=constantField(argument);
                    if(!constant.getDeclaringClass().isEqualTo(owner) || receiverBinding.isField() &&
                        (!Modifier.isFinal(receiverBinding.getModifiers()) || Modifier.isStatic(receiverBinding.getModifiers())))continue;
                    var session=new JavaValueSourceSummary.Session(ast,options,work);
                    var access=JavaValueAccess.discover(ast,session,receiver.resolveTypeBinding(),work);
                    var object=session.symbolic(access.shape(),receiver.getIdentifier());
                    var constantValue=(JavaValueSourceSummary.Value)session.constant(constant);
                    var projected=(JavaValueSourceSummary.Value)session.invoke(call.resolveMethodBinding(),object,List.of(materialize(constantValue,work)));
                    var summary=session.summary(projected);
                    if(!session.onlyOwner(access.shape().type()))throw JavaValueSourceSummary.unavailable("VALUE_CALL_FOREIGN_INITIALIZATION");
                    String constantProjection=source.substring(argument.getStartPosition(),argument.getStartPosition()+argument.getLength());
                    var guards=guards(summary,access,receiver.toString(),constant,constantProjection,constantValue,owner,work);
                    long allowance=Math.min(1_000_000,work.remaining()/4);if(allowance<6000)continue;
                    var optimizer=new ComputationOptimizer();var request=summary.request(options,work.allocate(allowance,options));
                    var result=optimizer.optimize(request,work.token());work.check();
                    if(result instanceof OptimizationResult.BudgetExceeded stopped && stopped.work()>allowance)work.charge(stopped.work()-allowance);
                    if(!(result instanceof OptimizationResult.Candidate candidate))throw JavaValueSourceSummary.unavailable("VALUE_CALL_NO_CANDIDATE: "+result); //$NON-NLS-1$
                    var names=new LinkedHashMap<String,String>();
                    for(int i=0;i<summary.inputs().size();i++)names.put(summary.inputs().get(i).id(),"_mathValueLeaf"+i); //$NON-NLS-1$
                    var outputs=new ArrayList<String>();var resolved=candidate.plan().resolveOutputs(work::charge);
                    var factoryArguments=JavaValueAccess.factoryArguments(ast,access,resolved.expressions(),summary.staticValues(),options,work);
                    for(int i=0;i<factoryArguments.size();i++) {
                        var single=new JointComputationPlan(candidate.plan().inputs(),Map.of(),List.of(new JointComputationPlan.Output("part",NumericKind.BIG_INTEGER.type(),factoryArguments.get(i)))); //$NON-NLS-1$
                        work.charge(10L*resolved.expandedNodes());var prepared=ComputationOptimizer.prepare(single);work.charge(prepared.cost().inspectionWork());
                        var emission=new JavaComputationEmitter().emit(prepared,names,Set.of(),options.targetJava());
                        if(!emission.statements().isEmpty())throw JavaValueSourceSummary.unavailable("VALUE_CALL_REQUIRES_STATEMENTS"); //$NON-NLS-1$
                        outputs.add(accessExpression(emission.outputValues().get("part"),access,receiver.toString(),work)); //$NON-NLS-1$
                    }
                    String original=source.substring(start,start+length);
                    String replacement="(/* Mathematics constant value: fresh source factory; verified components. */ "+guards+" ? "+access.construct(outputs)+" : "+original+")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    String generated=source.substring(0,start)+replacement+source.substring(start+length);work.charge(generated.length());
                    var after=MathematicalAnalysis.checkGeneratedSource(ast,source,generated,compiler,options.targetJava(),monitor,environment,start,length,replacement.length());work.check();
                    var conditional=(ConditionalExpression)((ParenthesizedExpression)NodeFinder.perform(after,start,replacement.length())).getExpression();
                    verifyGuardBindings(conditional.getExpression(),receiverBinding,constant,summary.staticValues(),work);
                    if(!(conditional.getElseExpression() instanceof MethodInvocation fallback) || fallback.resolveMethodBinding()==null
                        || !fallback.resolveMethodBinding().isEqualTo(call.resolveMethodBinding()) || fallback.arguments().size()!=1
                        || !receiverBinding.isEqualTo(JavaValueSourceSummary.variable(fallback.getExpression()))
                        || !constant.isEqualTo(constantField((Expression)fallback.arguments().getFirst())))
                        throw JavaValueSourceSummary.unavailable("VALUE_CALL_FALLBACK_BINDINGS"); //$NON-NLS-1$
                    JavaValueSourceSummary.Summary replay;
                    try(var context=JavaSourceContext.open(after,generated,environment,monitor,options,work.remaining())) {
                        var replaySession=new JavaValueSourceSummary.Session(after,options,work);
                        var replayFactory=(MethodInvocation)conditional.getThenExpression();
                        if(!access.factory().isEqualTo(replayFactory.resolveMethodBinding()))throw JavaValueSourceSummary.unavailable("VALUE_CALL_FACTORY_BINDING"); //$NON-NLS-1$
                        var replayType=replayFactory.resolveTypeBinding();var replayObject=replaySession.symbolic(replaySession.shape(replayType),receiver.getIdentifier());
                        var replayReceiver=findVariable(conditional.getThenExpression(),receiver.getIdentifier());
                        var value=(JavaValueSourceSummary.Value)replaySession.project(replayFactory,Map.of(replayReceiver.getKey(),replayObject));
                        replay=replaySession.summary(value);context.checkpoint().requireCurrent();work.check();
                    }
                    if(!summary.inputs().equals(replay.inputs()))throw JavaValueSourceSummary.unavailable("VALUE_CALL_REPLAY_INPUTS"); //$NON-NLS-1$
                    var proof=optimizer.verify(summary.request(options,work.allocate(allowance,options)),replay.plan(),work.token());work.check();
                    if(proof instanceof VerificationResult.BudgetExceeded stopped && stopped.work()>allowance)work.charge(stopped.work()-allowance);
                    if(!(proof instanceof VerificationResult.Verified verified))throw JavaValueSourceSummary.unavailable("VALUE_CALL_REPLAY_PROOF: "+proof); //$NON-NLS-1$
                    var cost=cost(summary,replay,request,guards,work);
                    if(!cost.estimatedRuntimeImprovement())continue;
                    var plan=new JavaSourceRewritePlan("Source.java",JavaComputationTask.hash(source),source.length(),start,length,List.of(),List.of(),Map.of(), //$NON-NLS-1$
                        List.of(new JavaSourceRewritePlan.Change(start,length,JavaComputationTask.hash(original),replacement)));
                    documentation.add(ast,start,length,"Explicit mathematical mode: BigInteger component reference identity may change. Constant specialization uses a source-proved fresh factory and retains the original call for exceptional or unguarded inputs."); //$NON-NLS-1$
                    replacements.add(new MathematicalAnalysis.Replacement(start,length,replacement,"Verified source-defined constant specialization; fresh factory and original fallback")); //$NON-NLS-1$
                    evidence.add(new MathematicalAnalysis.VerifiedRegion(start,length,verified.evidence(),cost,original,replacement,null,plan));
                } catch(IllegalArgumentException unavailable) {
                    diagnostics.add(new MathematicalAnalysis.Diagnostic("VALUE_CALL_UNAVAILABLE",unavailable.getMessage(),start,length,MathematicalAnalysis.Stage.SOURCE)); //$NON-NLS-1$
                }
            }
        } catch(IllegalArgumentException exhausted) {
            diagnostics.add(new MathematicalAnalysis.Diagnostic("VALUE_CALL_LIMIT",exhausted.getMessage(),0,source.length(),MathematicalAnalysis.Stage.SOURCE)); //$NON-NLS-1$
        }
    }
    static OptimizationResult.CostAssessment cost(JavaValueSourceSummary.Summary before,JavaValueSourceSummary.Summary after,
            OptimizationRequest request,String guards,JavaValueSourceSummary.Work work) {
        work.charge(10000);var original=ComputationOptimizer.prepare(before.plan());var emitted=ComputationOptimizer.prepare(after.plan());
        var source=NumericExecutionCost.estimateSource(request,original).orElseThrow(()->JavaValueSourceSummary.unavailable("VALUE_CALL_SOURCE_COST")); //$NON-NLS-1$
        var replayRequest=new OptimizationRequest(after.plan(),after.trace(),request.selectedKinds(),request.semanticsRevision(),request.assumptions(),
            request.safetyProfile(),request.goal(),request.budget(),request.checkedPolicy());
        var target=NumericExecutionCost.estimateSource(replayRequest,emitted).orElseThrow(()->JavaValueSourceSummary.unavailable("VALUE_CALL_TARGET_COST")); //$NON-NLS-1$
        long guardWork=7L*(guards.split(" && ",-1).length)+2L*after.inputs().size(); //$NON-NLS-1$
        work.charge(original.cost().inspectionWork()+emitted.cost().inspectionWork()+source.inspectionWork()+target.inspectionWork());
        var sourceCost=new de.regelsuche.search.program.PreparedJointComputation.Cost(source.operationWork(),source.peakWords(),original.cost().retainedOutputStorage(),
            original.cost().outputBindings(),original.cost().operationCount(),source.inspectionWork());
        var targetCost=new de.regelsuche.search.program.PreparedJointComputation.Cost(target.operationWork()+guardWork,target.peakWords(),emitted.cost().retainedOutputStorage(),
            emitted.cost().outputBindings(),emitted.cost().operationCount(),target.inspectionWork());
        return new OptimizationResult.CostAssessment(sourceCost.weighted(10,1,1,1),targetCost.weighted(10,1,1,1),guardWork,original.cost().operationCount(),
            sourceCost,targetCost,targetCost.operationWork()<sourceCost.operationWork());
    }
    private static String accessExpression(String source,JavaValueAccess.Access access,String receiver,JavaValueSourceSummary.Work work) {
        work.charge(source.length());var parser=ASTParser.newParser(AST.getJLSLatest());parser.setKind(ASTParser.K_EXPRESSION);parser.setSource(source.toCharArray());
        var edits=new ArrayList<MathematicalAnalysis.Replacement>();parser.createAST(null).accept(new ASTVisitor(){
            @Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(SimpleName name) {
                for(int i=0;i<access.shape().components().size();i++)if(name.getIdentifier().equals("_mathValueLeaf"+i)) //$NON-NLS-1$
                    edits.add(new MathematicalAnalysis.Replacement(name.getStartPosition(),name.getLength(),access.leaf(receiver,i),"source getter")); //$NON-NLS-1$
                return true;
            }
        });
        var result=new StringBuilder(source);edits.sort(Comparator.comparingInt(MathematicalAnalysis.Replacement::offset).reversed());
        for(var edit:edits)result.replace(edit.offset(),edit.offset()+edit.length(),edit.replacement());return result.toString();
    }
    private static JavaValueSourceSummary.Value materialize(JavaValueSourceSummary.Value value,JavaValueSourceSummary.Work work) {
        var components=new ArrayList<JavaValueSourceSummary.Term>();
        for(var component:value.components())components.add(new JavaValueSourceSummary.Scalar(JavaExpressions.literal(evaluate((JavaValueSourceSummary.Scalar)component,work))));
        return new JavaValueSourceSummary.Value(value.shape(),components);
    }
    private static IVariableBinding findVariable(ASTNode node,String name) {
        var found=new ArrayList<IVariableBinding>();node.accept(new ASTVisitor(){@Override public boolean visit(SimpleName part) {
            if(part.getIdentifier().equals(name) && part.resolveBinding() instanceof IVariableBinding variable)found.add(variable);return true;}});
        if(found.isEmpty())throw JavaValueSourceSummary.unavailable("VALUE_CALL_REPLAY_RECEIVER");return found.getFirst(); //$NON-NLS-1$
    }
    private static String guards(JavaValueSourceSummary.Summary summary,JavaValueAccess.Access access,String receiver,IVariableBinding constant,String constantProjection,
            JavaValueSourceSummary.Value constantValue,ITypeBinding owner,JavaValueSourceSummary.Work work) {
        var guards=new LinkedHashSet<String>();guards.add(receiver+" != null"); //$NON-NLS-1$
        for(int i=0;i<access.shape().components().size();i++) {
            String leaf=access.leaf(receiver,i);guards.add(leaf+" != null");guards.add(leaf+".getClass() == java.math.BigInteger.class");guards.add(leaf+".abs().bitLength() <= 4096"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        for(var value:summary.staticValues()) {
            if(!value.field().getDeclaringClass().isEqualTo(owner) && !value.field().getDeclaringClass().isEqualTo(access.shape().type()))
                throw JavaValueSourceSummary.unavailable("VALUE_CALL_FOREIGN_INITIALIZATION");
            if(value.value() instanceof JavaValueSourceSummary.Value && !value.field().isEqualTo(constant))
                throw JavaValueSourceSummary.unavailable("VALUE_CALL_UNGUARDED_STATIC_VALUE");
            if(!value.field().getDeclaringClass().isEqualTo(owner) && !Modifier.isPublic(value.field().getModifiers()))throw JavaValueSourceSummary.unavailable("VALUE_CALL_STATIC_ACCESS"); //$NON-NLS-1$
            if(value.value() instanceof JavaValueSourceSummary.Scalar scalar) {
                String path=value.path();guards.add(path+" != null");guards.add(path+".getClass() == java.math.BigInteger.class"); //$NON-NLS-1$ //$NON-NLS-2$
                guards.add(path+".equals("+literal(scalar,work)+")"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
        String path=constantProjection;guards.add(path+" != null"); //$NON-NLS-1$
        for(int i=0;i<constantValue.components().size();i++) {
            String leaf=access.leaf(path,i);guards.add(leaf+" != null");guards.add(leaf+".getClass() == java.math.BigInteger.class"); //$NON-NLS-1$ //$NON-NLS-2$
            guards.add(leaf+".equals("+literal((JavaValueSourceSummary.Scalar)constantValue.components().get(i),work)+")"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return String.join(" && ",guards); //$NON-NLS-1$
    }
    /** Static field reads may omit a qualifier or use a type name; expression qualifiers still evaluate Java code. */
    private static IVariableBinding constantField(Expression expression) {
        IBinding binding;
        if(expression instanceof SimpleName name)binding=name.resolveBinding();
        else if(expression instanceof QualifiedName name && name.getQualifier().resolveBinding() instanceof ITypeBinding)
            binding=name.resolveBinding();
        else return null;
        return binding instanceof IVariableBinding field && field.isField() && !field.isRecovered()
            && Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())?field:null;
    }
    /** Bind guard reads to admitted source fields and emitted types/constructors to the actual JDK declarations. */
    private static void verifyGuardBindings(Expression guard,IVariableBinding receiver,IVariableBinding constant,
            List<JavaValueSourceSummary.StaticValue> statics,JavaValueSourceSummary.Work work) {
        var allowed=new HashSet<String>();allowed.add(receiver.getVariableDeclaration().getKey());allowed.add(constant.getVariableDeclaration().getKey());
        for(var value:statics)allowed.add(value.field().getVariableDeclaration().getKey());
        boolean[] constantSeen={false};
        guard.accept(new ASTVisitor(){
            @Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(SimpleName name) {
                if(name.resolveBinding() instanceof IVariableBinding variable) {
                    if(!allowed.contains(variable.getVariableDeclaration().getKey()))throw JavaValueSourceSummary.unavailable("VALUE_CALL_GUARD_BINDING"); //$NON-NLS-1$
                    if(variable.isEqualTo(constant))constantSeen[0]=true;
                }
                return true;
            }
            @Override public boolean visit(QualifiedName name) {
                if(name.resolveBinding() instanceof IVariableBinding field && (!Modifier.isStatic(field.getModifiers())
                    || !(name.getQualifier().resolveBinding() instanceof ITypeBinding)))
                    throw JavaValueSourceSummary.unavailable("VALUE_CALL_GUARD_QUALIFIER"); //$NON-NLS-1$
                return true;
            }
            @Override public boolean visit(FieldAccess field){throw JavaValueSourceSummary.unavailable("VALUE_CALL_GUARD_QUALIFIER");} //$NON-NLS-1$
            @Override public boolean visit(TypeLiteral literal) {
                if(!jdkType(literal.getType().resolveBinding(),"java.math.BigInteger"))throw JavaValueSourceSummary.unavailable("VALUE_CALL_GUARD_TYPE"); //$NON-NLS-1$ //$NON-NLS-2$
                return true;
            }
            @Override public boolean visit(ClassInstanceCreation creation) {
                var constructor=creation.resolveConstructorBinding();
                if(constructor==null || constructor.isRecovered() || !constructor.isConstructor()
                    || !jdkType(constructor.getDeclaringClass(),"java.math.BigInteger") || constructor.getParameterTypes().length!=1 //$NON-NLS-1$
                    || !jdkType(constructor.getParameterTypes()[0],"java.lang.String")) //$NON-NLS-1$
                    throw JavaValueSourceSummary.unavailable("VALUE_CALL_GUARD_CONSTRUCTOR"); //$NON-NLS-1$
                return true;
            }
        });
        if(!constantSeen[0])throw JavaValueSourceSummary.unavailable("VALUE_CALL_GUARD_CONSTANT_MISSING"); //$NON-NLS-1$
    }
    private static boolean jdkType(ITypeBinding type,String qualifiedName) {
        return type!=null && !type.isRecovered() && !type.isFromSource() && !type.isNested() && qualifiedName.equals(type.getQualifiedName());
    }
    private static String literal(JavaValueSourceSummary.Scalar scalar,JavaValueSourceSummary.Work work) {
        return "new java.math.BigInteger(\""+evaluate(scalar,work)+"\")"; //$NON-NLS-1$ //$NON-NLS-2$
    }
    private static java.math.BigInteger evaluate(JavaValueSourceSummary.Scalar scalar,JavaValueSourceSummary.Work work) {
        var plan=new JointComputationPlan(Map.of(),Map.of(),List.of(new JointComputationPlan.Output("value",NumericKind.BIG_INTEGER.type(),scalar.expression()))); //$NON-NLS-1$
        work.charge(1000);var prepared=ComputationOptimizer.prepare(plan);work.charge(prepared.cost().inspectionWork());
        // This request is only a bounded cost query: no source trace or verification receipt is manufactured for initializer metadata.
        var costRequest=new OptimizationRequest(plan,new SourceEvaluationTrace(List.of()),Set.of(NumericKind.BIG_INTEGER),ComputationOptimizer.SEMANTICS_REVISION,
            Set.of(),SafetyProfile.PRESERVE_JAVA,OptimizationGoal.LOWER_ESTIMATED_RUNTIME,new OptimizationBudget(Math.max(1,work.remaining()),1,64,1),CheckedPolicy.NONE);
        var estimate=NumericExecutionCost.estimate(costRequest,prepared).orElseThrow(()->JavaValueSourceSummary.unavailable("VALUE_CONSTANT_MAGNITUDE_BOUND")); //$NON-NLS-1$
        work.charge(estimate.inspectionWork()+estimate.operationWork());
        var result=(java.math.BigInteger)prepared.execute(Map.of()).get("value");work.check(); //$NON-NLS-1$
        if(result.abs().bitLength()>4096)throw JavaValueSourceSummary.unavailable("VALUE_CONSTANT_MAGNITUDE_BOUND");return result; //$NON-NLS-1$
    }
    private static MethodDeclaration parentMethod(ASTNode node) {
        while(node!=null && !(node instanceof MethodDeclaration))node=node.getParent();
        if(node==null)throw JavaValueSourceSummary.unavailable("VALUE_CALL_METHOD_CONTEXT");return (MethodDeclaration)node; //$NON-NLS-1$
    }
    private static boolean overlaps(int a,int n,int b,int m){return a<(long)b+m && b<(long)a+n;}
}

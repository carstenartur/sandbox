/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.*;
import de.regelsuche.sdk.optimization.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;

/** Shares source-pure value calls without moving the first evaluation or exposing allocation identity. */
final class JavaValueReuseOptimizer {
    private JavaValueReuseOptimizer() { }
    private record Group(List<MethodInvocation> calls,SimpleName base,JavaValueSourceSummary.Summary summary,String guard,String name,JavaValueAccess.Access access,long factoryWork,List<JavaValueSourceSummary.StaticValue> statics) { }
    static void optimize(CompilationUnit ast,String source,MathCleanUpOptions options,IProgressMonitor monitor,
            int selectionOffset,int selectionLength,Map<String,String> compiler,MathematicalAnalysis.SourceEnvironment environment,
            List<MathematicalAnalysis.Replacement> replacements,List<MathematicalAnalysis.VerifiedRegion> evidence,
            List<MathematicalAnalysis.Diagnostic> diagnostics,JavaValueSourceSummary.Work work,MathematicalJavadoc documentation) {
        if(!options.mathematicalOptIn() || !options.kinds().contains(NumericKind.BIG_INTEGER))return;
        var found=new LinkedHashMap<String,List<MethodInvocation>>();
        try {
        work.check();
        if(options.valueRuntime()==MathCleanUpOptions.ValueRuntime.UNSPECIFIED)
            throw JavaValueSourceSummary.unavailable("VALUE_RUNTIME_UNSPECIFIED"); //$NON-NLS-1$
        // Discovery does not open source dependencies; only final non-JDK value receiver types can enter source proof.
        ast.accept(new ASTVisitor(){
            @Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(MethodInvocation call) {
            SimpleName base=base(call);var type=call.resolveTypeBinding();
            if(base!=null && base.resolveBinding() instanceof IVariableBinding binding && JavaValueAccess.possiblyDirect(type)
                && type.isEqualTo(base.resolveTypeBinding()) && !binding.isField()) {
                work.charge(call.getLength());String key=binding.getKey()+":"+source.substring(call.getStartPosition(),call.getStartPosition()+call.getLength()); //$NON-NLS-1$
                found.computeIfAbsent(key,ignored->new ArrayList<>()).add(call);
            }
            return true;
        }});
        found.values().removeIf(calls->calls.size()<2);if(found.isEmpty())return;
            var runtime=ValueRuntimeEvidence.qualify(ast,options,work);
            work.charge(source.length());var byBlock=new LinkedHashMap<Block,List<List<MethodInvocation>>>();
            for(var calls:found.values()) {
                Block block=block(calls.getFirst());if(block!=null && parentMethod(block)!=null && calls.stream().allMatch(call->block(call)==block))byBlock.computeIfAbsent(block,ignored->new ArrayList<>()).add(calls);
            }
            for(var entry:byBlock.entrySet()) {
                Block block=entry.getKey();int start=block.getStartPosition(),length=block.getLength();
                if(selectionOffset>=0 && !overlaps(selectionOffset,Math.max(1,selectionLength),start,length)
                    || replacements.stream().anyMatch(edit->overlaps(start,length,edit.offset(),edit.length())))continue;
                var groups=new ArrayList<Group>();var names=new HashSet<String>();block.accept(new ASTVisitor(){
                    @Override public void preVisit(ASTNode node){work.charge(1);}
                    @Override public boolean visit(SimpleName name){names.add(name.getIdentifier());return true;}});
                for(var calls:entry.getValue())try {
                    var first=calls.getFirst();var base=base(first);var binding=(IVariableBinding)base.resolveBinding();
                    unchanged(block,binding,first.getStartPosition(),calls.getLast().getStartPosition(),work);
                    var session=new JavaValueSourceSummary.Session(ast,options,work);var access=JavaValueAccess.discover(ast,session,base.resolveTypeBinding(),work);
                    var object=session.symbolic(access.shape(),base.getIdentifier());
                    var value=(JavaValueSourceSummary.Value)session.project(first,Map.of(binding.getKey(),object));var summary=session.summary(value);
                    var uses=new JavaValueUseProof(ast,session,work);var bindings=sourceBindings(first,work);
                    for(var call:calls) {
                        if(!bindings.equals(sourceBindings(call,work)))throw JavaValueSourceSummary.unavailable("VALUE_REUSE_OCCURRENCE_BINDINGS"); //$NON-NLS-1$
                        uses.scalarOrigins(call);uses.admitConsumerShape(call,parentMethod(call));
                        if(call!=first) {
                            var occurrence=new JavaValueSourceSummary.Session(ast,options,work);
                            var input=occurrence.symbolic(occurrence.shape(base.resolveTypeBinding()),base.getIdentifier());
                            var projected=(JavaValueSourceSummary.Value)occurrence.project(call,Map.of(binding.getKey(),input));
                            var other=occurrence.summary(projected);
                            if(!summary.inputs().equals(other.inputs()) || !summary.staticValues().equals(other.staticValues())
                                || !summary.plan().resolveOutputs(work::charge).expressions().equals(other.plan().resolveOutputs(work::charge).expressions()))
                                throw JavaValueSourceSummary.unavailable("VALUE_REUSE_OCCURRENCE_SOURCE"); //$NON-NLS-1$
                        }
                    }
                    var factory=JavaValueDetachment.factory(ast,session,access,summary,work);
                    String name="mathSharedValue"+groups.size();while(!names.add(name))name+="_"; //$NON-NLS-1$ //$NON-NLS-2$
                    String guards=guard(summary,access,base.getIdentifier())+" && "+JavaValueDetachment.exactStatics(factory.staticValues(),access,work)
                        +" && "+JavaValueDetachment.guard(access,name,List.of(base.getIdentifier()),factory.staticValues());
                    groups.add(new Group(calls,base,summary,guards,name,access,JavaValueDetachment.factoryCost(ast,access,options,work,4096),factory.staticValues()));
                } catch(IllegalArgumentException unavailable) {
                    diagnostics.add(new MathematicalAnalysis.Diagnostic("VALUE_REUSE_UNAVAILABLE",unavailable.getMessage(),start,length,MathematicalAnalysis.Stage.SOURCE)); //$NON-NLS-1$
                }
                if(groups.isEmpty())continue;
                var mappings=new IdentityHashMap<MethodInvocation,Group>();var declarations=new StringBuilder("\n// Mathematics value reuse: first evaluations retained; detached scalar identities; audited OpenJDK 25.0.5+8 deployment required.\n"); //$NON-NLS-1$
                for(var group:groups){for(var call:group.calls())mappings.put(call,group);declarations.append(group.base().resolveTypeBinding().getQualifiedName()).append(' ').append(group.name()).append(" = null;\n");} //$NON-NLS-1$
                String original=source.substring(start,start+length);String rewritten=render(block,source,mappings,null);String replacement=rewritten.substring(0,1)+declarations+rewritten.substring(1);
                String generated=source.substring(0,start)+replacement+source.substring(start+length);work.charge(generated.length());
                var after=MathematicalAnalysis.checkGeneratedSource(ast,generated,compiler,options.targetJava(),monitor,environment);work.check();
                var factoryProofs=new ArrayList<VerificationEvidence>();
                var proofs=new ArrayList<VerificationResult.Verified>();var operationWork=new IdentityHashMap<Group,Long>();
                try(var context=JavaSourceContext.open(after,generated,environment,monitor,options,work.remaining())) {
                    for(var group:groups) {
                        var initializer=findFirstAssignment(after,group.name(),work);
                        if(!(initializer.getRightHandSide() instanceof MethodInvocation actual))throw JavaValueSourceSummary.unavailable("VALUE_REUSE_REPLAY_FIRST"); //$NON-NLS-1$
                        var session=new JavaValueSourceSummary.Session(after,options,work);var actualBase=findBase(actual,group.base().getIdentifier(),work);var actualBinding=(IVariableBinding)actualBase.resolveBinding();
                        if(!sameVariable((IVariableBinding)group.base().resolveBinding(),actualBinding))throw JavaValueSourceSummary.unavailable("VALUE_REUSE_REPLAY_BASE_BINDING"); //$NON-NLS-1$
                        var actualObject=session.symbolic(session.shape(actualBase.resolveTypeBinding()),actualBase.getIdentifier());
                        var frame=new HashMap<String,JavaValueSourceSummary.Term>();frame.put(actualBinding.getKey(),actualObject);
                        var replayed=(JavaValueSourceSummary.Value)replay(actual,session,frame,after,groups,new HashSet<>(),work);
                        var summary=session.summary(replayed);
                        var actualAccess=JavaValueAccess.discover(after,session,actualBase.resolveTypeBinding(),work);
                        var factory=JavaValueDetachment.factory(after,session,actualAccess,summary,work);
                        factoryProofs.add(JavaValueDetachment.verifyFactory(summary,factory,options,work).evidence());
                        if(!group.summary().inputs().equals(summary.inputs()))throw JavaValueSourceSummary.unavailable("VALUE_REUSE_REPLAY_INPUTS"); //$NON-NLS-1$
                        long cap=Math.min(300_000,work.remaining()/3);if(cap<6000)throw JavaValueSourceSummary.unavailable("VALUE_REUSE_PROOF_BUDGET"); //$NON-NLS-1$
                        var request=group.summary().request(options,work.allocate(cap,options));var verification=new ComputationOptimizer().verify(request,summary.plan(),work.token());
                        if(verification instanceof VerificationResult.BudgetExceeded exceeded && exceeded.work()>cap)work.charge(exceeded.work()-cap);
                        if(!(verification instanceof VerificationResult.Verified proof))throw JavaValueSourceSummary.unavailable("VALUE_REUSE_REPLAY_PROOF");proofs.add(proof); //$NON-NLS-1$
                        // Verify every generated later use and its unchanged original fallback against the admitted schedule.
                        verifyUses(after,group,groups,options,work);
                        work.charge(10000);var prepared=ComputationOptimizer.prepare(group.summary().plan());
                        var estimate=NumericExecutionCost.estimateSource(request,prepared).orElseThrow();work.charge(estimate.inspectionWork()+prepared.cost().inspectionWork());
                        operationWork.put(group,estimate.operationWork());
                    }
                    context.checkpoint().requireCurrent();work.check();
                }
                long sourceWork=0,targetWork=0;
                for(var mapping:mappings.entrySet()) {
                    var call=mapping.getKey();
                    boolean nested=false;for(ASTNode parent=call.getParent();parent!=block;parent=parent.getParent())if(parent instanceof MethodInvocation invocation && mappings.containsKey(invocation)){nested=true;break;}
                    if(!nested){sourceWork+=operationWork.get(mapping.getValue());targetWork+=realizedWork(call,mappings,operationWork);}
                }
                if(targetWork>=sourceWork)continue;
                var cost=LoopSchedulingEvidence.cost(sourceWork,targetWork,options.goal());
                var plan=new JavaSourceRewritePlan("Source.java",JavaComputationTask.hash(source),source.length(),start,length,List.of(),List.of(),Map.of(), //$NON-NLS-1$
                    List.of(new JavaSourceRewritePlan.Change(start,length,JavaComputationTask.hash(original),replacement)),runtime.withFactoryProofs(factoryProofs));
                documentation.add(ast,start,length,"Explicit mathematical mode: value reuse requires deployment on the audited OpenJDK 25.0.5+8 scalar implementation. The first evaluation remains in place; later guarded results use a fresh source factory and separate fresh BigInteger components, with the original expression retained as fallback."); //$NON-NLS-1$
                replacements.add(new MathematicalAnalysis.Replacement(start,length,replacement,"Verified value reuse at original evaluation positions; exact guards and closed numeric uses")); //$NON-NLS-1$
                evidence.add(new MathematicalAnalysis.VerifiedRegion(start,length,proofs.getFirst().evidence(),cost,original,replacement,null,plan));
            }
        } catch(IllegalArgumentException unavailable) {
            diagnostics.add(new MathematicalAnalysis.Diagnostic("VALUE_REUSE_LIMIT",unavailable.getMessage(),0,source.length(),MathematicalAnalysis.Stage.SOURCE)); //$NON-NLS-1$
        }
    }
    private static long realizedWork(MethodInvocation call,IdentityHashMap<MethodInvocation,Group> groups,Map<Group,Long> operationWork) {
        var group=groups.get(call);
        if(call!=group.calls().getFirst())return JavaValueDetachment.guardCost(group.guard(),4096)+JavaValueDetachment.copyCost(group.access(),4096)+group.factoryWork(); //$NON-NLS-1$
        long[] result={operationWork.get(group)+1};
        call.accept(new ASTVisitor(){@Override public boolean visit(MethodInvocation child) {
            if(child!=call && groups.containsKey(child)){result[0]+=realizedWork(child,groups,operationWork)-operationWork.get(groups.get(child));return false;}return true;
        }});return result[0];
    }
    private static List<String> sourceBindings(Expression expression,JavaValueSourceSummary.Work work) {
        var result=new ArrayList<String>();expression.accept(new ASTVisitor(){
            @Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(SimpleName name) {
                var binding=name.resolveBinding();if(binding==null || binding.isRecovered() || binding.getKey()==null)throw JavaValueSourceSummary.unavailable("VALUE_REUSE_SOURCE_BINDING"); //$NON-NLS-1$
                result.add(binding.getKey());return true;
            }
        });return result;
    }
    private static SimpleName findBase(Expression expression,String identifier,JavaValueSourceSummary.Work work) {
        var names=new ArrayList<SimpleName>();expression.accept(new ASTVisitor(){
            @Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(SimpleName name){if(name.getIdentifier().equals(identifier) && name.resolveBinding() instanceof IVariableBinding)names.add(name);return true;}});
        if(names.isEmpty())throw JavaValueSourceSummary.unavailable("VALUE_REUSE_REPLAY_BASE");return names.getFirst(); //$NON-NLS-1$
    }
    private static JavaValueSourceSummary.Term replay(Expression expression,JavaValueSourceSummary.Session session,
            Map<String,JavaValueSourceSummary.Term> frame,CompilationUnit ast,List<Group> groups,Set<String> active,JavaValueSourceSummary.Work work) {
        work.charge(1);
        if(expression instanceof ParenthesizedExpression parentheses)return replay(parentheses.getExpression(),session,frame,ast,groups,active,work);
        if(expression instanceof Assignment assignment && assignment.getLeftHandSide() instanceof SimpleName name
            && groups.stream().anyMatch(group->group.name().equals(name.getIdentifier()))) {
            if(findFirstAssignment(ast,name.getIdentifier(),work)!=assignment)throw JavaValueSourceSummary.unavailable("VALUE_REUSE_REPLAY_ASSIGNMENT"); //$NON-NLS-1$
            var result=replay(assignment.getRightHandSide(),session,frame,ast,groups,active,work);frame.put(name.resolveBinding().getKey(),result);return result;
        }
        if(expression instanceof SimpleName name && groups.stream().anyMatch(group->group.name().equals(name.getIdentifier()))) {
            String key=name.resolveBinding().getKey();var known=frame.get(key);if(known!=null)return known;
            if(!active.add(key))throw JavaValueSourceSummary.unavailable("VALUE_REUSE_REPLAY_CYCLE"); //$NON-NLS-1$
            try {var result=replay(findFirstAssignment(ast,name.getIdentifier(),work).getRightHandSide(),session,frame,ast,groups,active,work);frame.put(key,result);return result;}
            finally {active.remove(key);}
        }
        if(expression instanceof ConditionalExpression conditional) {
            var group=detachedGroup(conditional,groups);
            var name=JavaValueDetachment.decode(conditional.getThenExpression(),group.access(),work); //$NON-NLS-1$
            var parser=ASTParser.newParser(AST.getJLSLatest());parser.setKind(ASTParser.K_EXPRESSION);parser.setSource(group.guard().toCharArray());
            if(!parser.createAST(null).toString().equals(conditional.getExpression().toString()))throw JavaValueSourceSummary.unavailable("VALUE_REUSE_REPLAY_GUARD"); //$NON-NLS-1$
            var cached=replay(name,session,frame,ast,groups,active,work);
            var fallback=replay(conditional.getElseExpression(),session,frame,ast,groups,active,work);
            if(!cached.equals(fallback))throw JavaValueSourceSummary.unavailable("VALUE_REUSE_REPLAY_FALLBACK");return cached; //$NON-NLS-1$
        }
        if(expression instanceof MethodInvocation call && call.resolveMethodBinding()!=null && !JavaValueSourceSummary.big(call.resolveMethodBinding().getDeclaringClass())) {
            var object=call.getExpression()==null?null:replay(call.getExpression(),session,frame,ast,groups,active,work);
            var arguments=new ArrayList<JavaValueSourceSummary.Term>();for(Object item:call.arguments())arguments.add(replay((Expression)item,session,frame,ast,groups,active,work));
            return session.invoke(call.resolveMethodBinding(),(JavaValueSourceSummary.Value)object,arguments);
        }
        return session.project(expression,frame);
    }
    private static void verifyUses(CompilationUnit after,Group group,List<Group> groups,MathCleanUpOptions options,JavaValueSourceSummary.Work work) {
        var assignments=new ArrayList<Assignment>();var uses=new ArrayList<ConditionalExpression>();after.accept(new ASTVisitor(){
            @Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(Assignment value){if(value.getLeftHandSide() instanceof SimpleName name && name.getIdentifier().equals(group.name()))assignments.add(value);return true;}
            @Override public boolean visit(ConditionalExpression value){if(contains(value.getThenExpression(),group.name()))uses.add(value);return true;}
        });
        if(assignments.size()!=1 || uses.size()!=group.calls().size()-1)throw JavaValueSourceSummary.unavailable("VALUE_REUSE_REPLAY_SCHEDULE"); //$NON-NLS-1$
        var binding=((SimpleName)assignments.getFirst().getLeftHandSide()).resolveBinding();
        var parser=ASTParser.newParser(AST.getJLSLatest());parser.setKind(ASTParser.K_EXPRESSION);parser.setSource(group.guard().toCharArray());
        String expected=parser.createAST(null).toString();
        for(var use:uses) {
            if(!expected.equals(use.getExpression().toString()))throw JavaValueSourceSummary.unavailable("VALUE_REUSE_REPLAY_GUARD");
            JavaValueGuardBindings.verify(use.getExpression(),group.access(),List.of(binding,group.base().resolveBinding()),group.statics(),List.of(),work);
        }
        for(var use:uses) {
            if(!binding.isEqualTo(JavaValueDetachment.decode(use.getThenExpression(),group.access(),work).resolveBinding()) || use.getStartPosition()<assignments.getFirst().getStartPosition()
                || !(use.getElseExpression() instanceof MethodInvocation fallback) || fallback.resolveMethodBinding()==null || !fallback.resolveMethodBinding().isEqualTo(group.calls().getFirst().resolveMethodBinding()))
                throw JavaValueSourceSummary.unavailable("VALUE_REUSE_REPLAY_BINDING"); //$NON-NLS-1$
            var actualBase=findBase(use.getElseExpression(),group.base().getIdentifier(),work);var actualBinding=(IVariableBinding)actualBase.resolveBinding();
            if(!sameVariable((IVariableBinding)group.base().resolveBinding(),actualBinding))throw JavaValueSourceSummary.unavailable("VALUE_REUSE_REPLAY_BASE_BINDING"); //$NON-NLS-1$
            var session=new JavaValueSourceSummary.Session(after,options,work);
            var input=session.symbolic(session.shape(actualBase.resolveTypeBinding()),actualBase.getIdentifier());
            var frame=new HashMap<String,JavaValueSourceSummary.Term>();frame.put(actualBinding.getKey(),input);
            replay(use,session,frame,after,groups,new HashSet<>(),work);
        }
    }
    private static boolean contains(ASTNode node,String identifier) {
        boolean[] found={false};node.accept(new ASTVisitor(){@Override public boolean visit(SimpleName name){found[0]|=name.getIdentifier().equals(identifier);return true;}});return found[0];
    }
    private static Group detachedGroup(ConditionalExpression expression,List<Group> groups) {
        var matching=groups.stream().filter(group->contains(expression.getThenExpression(),group.name())).toList();
        if(matching.size()!=1)throw JavaValueSourceSummary.unavailable("VALUE_REUSE_REPLAY_CONDITIONAL");return matching.getFirst();
    }
    private static Assignment findFirstAssignment(CompilationUnit ast,String name,JavaValueSourceSummary.Work work) {
        var assignments=new ArrayList<Assignment>();ast.accept(new ASTVisitor(){
            @Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(Assignment assignment){if(assignment.getLeftHandSide() instanceof SimpleName left && left.getIdentifier().equals(name))assignments.add(assignment);return true;}});
        if(assignments.size()!=1)throw JavaValueSourceSummary.unavailable("VALUE_REUSE_FIRST_ASSIGNMENT");return assignments.getFirst(); //$NON-NLS-1$
    }
    private static String render(ASTNode node,String source,IdentityHashMap<MethodInvocation,Group> mappings,ASTNode excluded) {
        if(node instanceof MethodInvocation call && mappings.containsKey(call) && node!=excluded) {
            var group=mappings.get(call);String original=render(node,source,mappings,node);
            return call==group.calls().getFirst()?"("+group.name()+" = "+original+")":"("+group.guard()+" ? "+JavaValueDetachment.emit(group.access(),group.name())+" : "+original+")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
        }
        var children=new ArrayList<MethodInvocation>();node.accept(new ASTVisitor(){@Override public boolean visit(MethodInvocation call){if(call!=node && mappings.containsKey(call)){children.add(call);return false;}return true;}});
        StringBuilder text=new StringBuilder(source.substring(node.getStartPosition(),node.getStartPosition()+node.getLength()));children.sort(Comparator.comparingInt(ASTNode::getStartPosition).reversed());
        for(var child:children)text.replace(child.getStartPosition()-node.getStartPosition(),child.getStartPosition()-node.getStartPosition()+child.getLength(),render(child,source,mappings,null));return text.toString();
    }
    private static String guard(JavaValueSourceSummary.Summary summary,JavaValueAccess.Access access,String receiver) {
        var guards=new ArrayList<String>();guards.add(receiver+" != null"); //$NON-NLS-1$
        for(int i=0;i<access.shape().components().size();i++){String path=access.leaf(receiver,i);guards.add(path+" != null");guards.add(path+".getClass() == java.math.BigInteger.class");guards.add(path+".abs().bitLength() <= 4096");} //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        // The original first successful evaluation has already completed every dependency initialization.
        for(var value:summary.staticValues()) {
            if(!(value.value() instanceof JavaValueSourceSummary.Scalar) || !Modifier.isPublic(value.field().getModifiers()))
                throw JavaValueSourceSummary.unavailable("VALUE_REUSE_STATIC_VALUE"); //$NON-NLS-1$
            // A conditional numeric initializer fact is not an exact-runtime-class premise.
            guards.add(value.path()+" != null");guards.add(value.path()+".getClass() == java.math.BigInteger.class"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return String.join(" && ",guards); //$NON-NLS-1$
    }
    private static void unchanged(Block block,IVariableBinding binding,int first,int last,JavaValueSourceSummary.Work work) {
        block.accept(new ASTVisitor(){@Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(Assignment assignment){if(assignment.getStartPosition()>=first && assignment.getStartPosition()<last && binding.isEqualTo(JavaValueSourceSummary.variable(assignment.getLeftHandSide())))throw JavaValueSourceSummary.unavailable("VALUE_REUSE_REBOUND");return true;}}); //$NON-NLS-1$
    }
    private static Block block(MethodInvocation call) {
        ASTNode node=call;
        while(node.getParent()!=null && !(node.getParent() instanceof Block)) {
            if(node instanceof ConditionalExpression || node instanceof LambdaExpression || node instanceof Initializer || node instanceof AbstractTypeDeclaration
                || node instanceof AnonymousClassDeclaration || node instanceof InfixExpression infix && (infix.getOperator()==InfixExpression.Operator.CONDITIONAL_AND || infix.getOperator()==InfixExpression.Operator.CONDITIONAL_OR))return null;
            node=node.getParent();
        }
        return node instanceof VariableDeclarationStatement || node instanceof ReturnStatement || node instanceof ExpressionStatement?(Block)node.getParent():null;
    }
    private static SimpleName base(Expression expression) {
        while(expression instanceof MethodInvocation call && call.getExpression()!=null)expression=call.getExpression();return expression instanceof SimpleName name?name:null;
    }
    private static MethodDeclaration parentMethod(ASTNode node) {
        while(node!=null && !(node instanceof MethodDeclaration)) {
            if(node instanceof Initializer || node instanceof AbstractTypeDeclaration || node instanceof AnonymousClassDeclaration)return null;
            node=node.getParent();
        }
        return (MethodDeclaration)node;
    }
    /** Stable declaration keys retain local provenance across generated cache declarations. */
    private static boolean sameVariable(IVariableBinding original,IVariableBinding replay) {
        return !replay.isRecovered() && original.getVariableDeclaration().getKey().equals(replay.getVariableDeclaration().getKey());
    }
    private static boolean overlaps(int a,int n,int b,int m){return a<(long)b+m && b<(long)a+n;}
}

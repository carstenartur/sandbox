/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.*;
import de.regelsuche.sdk.optimization.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;

/** Removes unobserved pre-normalizations only behind total-operation guards and a verified source factory. */
final class JavaValueResidueOptimizer {
    private JavaValueResidueOptimizer() { }
    private record Argument(VariableDeclarationFragment local,VariableDeclarationStatement statement,MethodInvocation reduction,
            IVariableBinding modulus,BigInteger value,String rawName,String modulusName,String guard) { }
    static void optimize(CompilationUnit ast,String source,MathCleanUpOptions options,IProgressMonitor monitor,
            int selectionOffset,int selectionLength,Map<String,String> compiler,MathematicalAnalysis.SourceEnvironment environment,
            List<MathematicalAnalysis.Replacement> replacements,List<MathematicalAnalysis.VerifiedRegion> evidence,
            List<MathematicalAnalysis.Diagnostic> diagnostics,JavaValueSourceSummary.Work work,MathematicalJavadoc documentation) {
        optimize(ast,source,options,monitor,selectionOffset,selectionLength,compiler,environment,replacements,evidence,
            diagnostics,work,documentation,null);
    }
    static void optimize(CompilationUnit ast,String source,MathCleanUpOptions options,IProgressMonitor monitor,
            int selectionOffset,int selectionLength,Map<String,String> compiler,MathematicalAnalysis.SourceEnvironment environment,
            List<MathematicalAnalysis.Replacement> replacements,List<MathematicalAnalysis.VerifiedRegion> evidence,
            List<MathematicalAnalysis.Diagnostic> diagnostics,JavaValueSourceSummary.Work work,MathematicalJavadoc documentation,
            List<MethodInvocation> sourceCalls) {
        if(!options.mathematicalOptIn() || !options.kinds().contains(NumericKind.BIG_INTEGER))return;
        var factories=new ArrayList<MethodInvocation>();
        try {
        work.check();
        if(sourceCalls==null)ast.accept(new ASTVisitor(){
            @Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(MethodInvocation call) {
            if(factoryCandidate(call,work))factories.add(call);
            return true;
        }});
        else for(var call:sourceCalls) {
            // The mandatory original-AST freshness traversal already collected these calls.
            // Binding/argument inspection is additional work; no tree walk is repeated or refunded.
            work.charge(1);if(factoryCandidate(call,work))factories.add(call);
        }
        if(factories.isEmpty())return;
            work.charge(source.length());
            for(var factory:factories) {
                Block block=block(factory);if(block==null)continue;int start=block.getStartPosition(),length=block.getLength();
                if(selectionOffset>=0 && !overlaps(selectionOffset,Math.max(1,selectionLength),start,length)
                    || replacements.stream().anyMatch(edit->overlaps(start,length,edit.offset(),edit.length())))continue;
                try {
                    var session=new JavaValueSourceSummary.Session(ast,options,work);var access=JavaValueAccess.discover(ast,session,factory.resolveTypeBinding(),work);
                    if(!access.factory().isEqualTo(factory.resolveMethodBinding()))continue;
                    var arguments=new ArrayList<Argument>();var usedNames=new HashSet<String>();block.accept(new ASTVisitor(){@Override public boolean visit(SimpleName name){usedNames.add(name.getIdentifier());return true;}});
                    for(Object item:factory.arguments()) {
                        var name=(SimpleName)item;var binding=(IVariableBinding)name.resolveBinding();
                        if(!(ast.findDeclaringNode(binding) instanceof VariableDeclarationFragment local) || !(local.getParent() instanceof VariableDeclarationStatement statement)
                            || statement.getParent()!=block || statement.fragments().size()!=1 || statement.getStartPosition()>factory.getStartPosition()
                            || !(local.getInitializer() instanceof MethodInvocation reduction) || reduction.getExpression()==null || reduction.arguments().size()!=1
                            || reduction.resolveMethodBinding()==null || !JavaValueSourceSummary.big(reduction.resolveMethodBinding().getDeclaringClass())
                            || !reduction.getName().getIdentifier().equals("mod"))throw JavaValueSourceSummary.unavailable("VALUE_RESIDUE_PRENORMALIZATION"); //$NON-NLS-1$ //$NON-NLS-2$
                        if(!arguments.isEmpty() && statement.getStartPosition()<arguments.getLast().statement().getStartPosition())throw JavaValueSourceSummary.unavailable("VALUE_RESIDUE_ARGUMENT_ORDER"); //$NON-NLS-1$
                        onlyRead(block,binding,name,work);
                        var modulus=JavaValueSourceSummary.variable((Expression)reduction.arguments().getFirst());
                        if(modulus==null || !Modifier.isStatic(modulus.getModifiers()) || !Modifier.isFinal(modulus.getModifiers()))throw JavaValueSourceSummary.unavailable("VALUE_RESIDUE_MODULUS_SOURCE"); //$NON-NLS-1$
                        var scalar=(JavaValueSourceSummary.Scalar)session.constant(modulus);
                        if(!JavaExpressions.isLiteral(scalar.expression()) || !(JavaExpressions.literalValue(scalar.expression()) instanceof BigInteger value) || value.signum()<=0 || value.bitLength()>4096)
                            throw JavaValueSourceSummary.unavailable("VALUE_RESIDUE_POSITIVE_MODULUS"); //$NON-NLS-1$
                        String raw=fresh("mathResidueRaw"+arguments.size(),usedNames),mod=fresh("mathResidueModulus"+arguments.size(),usedNames); //$NON-NLS-1$ //$NON-NLS-2$
                        String guard=raw+" != null && "+raw+".getClass() == java.math.BigInteger.class && "+raw+".abs().bitLength() <= 4096 && "+mod+" != null && "+mod+".getClass() == java.math.BigInteger.class && "+mod+".equals(new java.math.BigInteger(\""+value+"\"))"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
                        arguments.add(new Argument(local,statement,reduction,modulus,value,raw,mod,guard));
                    }
                    independentNormalizations(ast,factory.resolveMethodBinding(),arguments,work);
                    var scalar=new JavaComputationExtractor.ScalarProjector(ast,options);var normalized=new ArrayList<JavaValueSourceSummary.Term>();
                    for(var argument:arguments) {
                        var input=session.input(argument.local().getName().getIdentifier());
                        var substitutions=new IdentityHashMap<Expression,de.regelsuche.ast.Expr>();substitutions.put(argument.reduction().getExpression(),input.expression());
                        substitutions.put((Expression)argument.reduction().arguments().getFirst(),JavaExpressions.literal(argument.value()));
                        normalized.add(new JavaValueSourceSummary.Scalar(scalar.expression(argument.reduction(),substitutions)));
                    }
                    var projected=(JavaValueSourceSummary.Value)session.invoke(factory.resolveMethodBinding(),null,normalized);var summary=session.summary(projected);
                    for(var value:summary.staticValues())if(arguments.stream().noneMatch(argument->argument.modulus().isEqualTo(value.field())))throw JavaValueSourceSummary.unavailable("VALUE_RESIDUE_UNGUARDED_CONSTANT"); //$NON-NLS-1$
                    var trace=new ArrayList<>(scalar.trace().occurrences());trace.addAll(summary.trace().occurrences());
                    summary=new JavaValueSourceSummary.Summary(summary.value(),summary.plan(),new SourceEvaluationTrace(trace),summary.assumptions(),summary.inputs(),summary.staticValues(),summary.sourceFingerprint());
                    var edits=new ArrayList<MathematicalAnalysis.Replacement>();
                    for(var argument:arguments) {
                        String raw=text(source,argument.reduction().getExpression()),modulus=text(source,(Expression)argument.reduction().arguments().getFirst());
                        String prefix="// Mathematics factory residue: retain producer/modulus order and exceptional fallback.\nfinal java.math.BigInteger "+argument.rawName()+" = "+raw+";\nfinal java.math.BigInteger "+argument.modulusName()+" = "+modulus+";\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                        String conditional="("+argument.guard()+" ? "+argument.rawName()+" : "+argument.rawName()+".mod("+argument.modulusName()+"))"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                        String statement=text(source,argument.statement());int relative=argument.reduction().getStartPosition()-argument.statement().getStartPosition();
                        edits.add(new MathematicalAnalysis.Replacement(argument.statement().getStartPosition(),argument.statement().getLength(),prefix+statement.substring(0,relative)+conditional+statement.substring(relative+argument.reduction().getLength()),"guarded residue")); //$NON-NLS-1$
                    }
                    String original=text(source,block);var rewritten=new StringBuilder(original);edits.sort(Comparator.comparingInt(MathematicalAnalysis.Replacement::offset).reversed());
                    for(var edit:edits)rewritten.replace(edit.offset()-start,edit.offset()-start+edit.length(),edit.replacement());String replacement=rewritten.toString();
                    String generated=source.substring(0,start)+replacement+source.substring(start+length);work.charge(generated.length());
                    var after=MathematicalAnalysis.checkGeneratedSource(ast,source,generated,compiler,options.targetJava(),monitor,environment,start,length,replacement.length());work.check();
                    var replayBlock=(Block)NodeFinder.perform(after,start,replacement.length());var replayFactory=findFactory(replayBlock,factory,arguments,work);
                    VerificationResult.Verified finalProof=null;JavaValueSourceSummary.Summary fast=null;
                    try(var context=JavaSourceContext.open(after,generated,environment,monitor,options,work.remaining())) {
                        int combinations=1<<arguments.size();long cap=Math.min(200_000,work.remaining()/(combinations+2));if(cap<6000)throw JavaValueSourceSummary.unavailable("VALUE_RESIDUE_PROOF_BUDGET"); //$NON-NLS-1$
                        for(int mask=1;mask<combinations;mask++) {
                            var replaySession=new JavaValueSourceSummary.Session(after,options,work);var replayScalar=new JavaComputationExtractor.ScalarProjector(after,options);var actualArguments=new ArrayList<JavaValueSourceSummary.Term>();
                            for(int i=0;i<arguments.size();i++) {
                                var argument=arguments.get(i);var actualName=(SimpleName)replayFactory.arguments().get(i);
                                var actualLocal=(VariableDeclarationFragment)after.findDeclaringNode(actualName.resolveBinding());
                                var conditional=(ConditionalExpression)((ParenthesizedExpression)actualLocal.getInitializer()).getExpression();
                                var fallback=(MethodInvocation)conditional.getElseExpression();verifyCapture(after,source,generated,argument,conditional,fallback,work);
                                var input=replaySession.input(argument.local().getName().getIdentifier());
                                if((mask&(1<<i))!=0)actualArguments.add(input);
                                else {
                                    var substitutions=new IdentityHashMap<Expression,de.regelsuche.ast.Expr>();substitutions.put(fallback.getExpression(),input.expression());substitutions.put((Expression)fallback.arguments().getFirst(),JavaExpressions.literal(argument.value()));
                                    actualArguments.add(new JavaValueSourceSummary.Scalar(replayScalar.expression(fallback,substitutions)));
                                }
                            }
                            var value=(JavaValueSourceSummary.Value)replaySession.invoke(replayFactory.resolveMethodBinding(),null,actualArguments);var replay=replaySession.summary(value);
                            if(!summary.inputs().equals(replay.inputs()))throw JavaValueSourceSummary.unavailable("VALUE_RESIDUE_REPLAY_INPUTS"); //$NON-NLS-1$
                            var verification=new ComputationOptimizer().verify(summary.request(options,work.allocate(cap,options)),replay.plan(),work.token());work.check();
                            if(verification instanceof VerificationResult.BudgetExceeded exceeded && exceeded.work()>cap)work.charge(exceeded.work()-cap);
                            if(!(verification instanceof VerificationResult.Verified verified))throw JavaValueSourceSummary.unavailable("VALUE_RESIDUE_REPLAY_PROOF: "+verification); //$NON-NLS-1$
                            finalProof=verified;fast=replay;
                        }
                        context.checkpoint().requireCurrent();work.check();
                    }
                    var request=summary.request(options,work.budget(6000,options));
                    var cost=JavaValueCallOptimizer.cost(summary,fast,request,String.join(" && ",arguments.stream().map(Argument::guard).toList()),work); //$NON-NLS-1$
                    if(!cost.estimatedRuntimeImprovement())continue;
                    var plan=new JavaSourceRewritePlan("Source.java",JavaComputationTask.hash(source),source.length(),start,length,List.of(),List.of(),Map.of(), //$NON-NLS-1$
                        List.of(new JavaSourceRewritePlan.Change(start,length,JavaComputationTask.hash(original),replacement)));
                    documentation.add(ast,start,length,"Explicit mathematical mode: BigInteger component reference identity may change. Source-proved factory normalization preserves producer/modulus evaluation order and the original exceptional-input fallback."); //$NON-NLS-1$
                    replacements.add(new MathematicalAnalysis.Replacement(start,length,replacement,"Verified source-factory residue propagation with retained producer and modulus evaluation order")); //$NON-NLS-1$
                    evidence.add(new MathematicalAnalysis.VerifiedRegion(start,length,finalProof.evidence(),cost,original,replacement,null,plan));
                } catch(IllegalArgumentException unavailable) {
                    diagnostics.add(new MathematicalAnalysis.Diagnostic("VALUE_RESIDUE_UNAVAILABLE",unavailable.getMessage(),start,length,MathematicalAnalysis.Stage.SOURCE)); //$NON-NLS-1$
                }
            }
        } catch(IllegalArgumentException unavailable) {
            diagnostics.add(new MathematicalAnalysis.Diagnostic("VALUE_RESIDUE_LIMIT",unavailable.getMessage(),0,source.length(),MathematicalAnalysis.Stage.SOURCE)); //$NON-NLS-1$
        }
    }
    private static boolean factoryCandidate(MethodInvocation call,JavaValueSourceSummary.Work work) {
        var method=call.resolveMethodBinding();var type=call.resolveTypeBinding();
        if(method==null || !Modifier.isStatic(method.getModifiers()) || !JavaValueAccess.possiblyDirect(type)
            || call.arguments().isEmpty() || call.arguments().size()>8)return false;
        for(Object argument:call.arguments()) {
            work.charge(1);
            if(!(argument instanceof SimpleName name) || !(name.resolveBinding() instanceof IVariableBinding binding)
                || binding.isField() || !JavaValueSourceSummary.big(binding.getType()))return false;
        }
        return true;
    }
    /** A rejected guard can leave a subclass in another component: source evaluation must not let it observe an unreduced peer. */
    private static void independentNormalizations(CompilationUnit ast,IMethodBinding factory,List<Argument> arguments,JavaValueSourceSummary.Work work) {
        var definition=JavaSourceContext.definition(ast,factory.getMethodDeclaration());work.charge(1);
        if(definition==null || !(definition.declaration() instanceof MethodDeclaration method) || method.getBody()==null
            || method.getBody().statements().size()!=1 || !(method.getBody().statements().getFirst() instanceof ReturnStatement returned)
            || !(returned.getExpression() instanceof ClassInstanceCreation creation) || creation.arguments().size()!=arguments.size())
            throw JavaValueSourceSummary.unavailable("VALUE_RESIDUE_FACTORY_COMPONENTS"); //$NON-NLS-1$
        var seen=new HashSet<Integer>();
        for(Object item:creation.arguments()) {
            work.charge(1);
            if(!(item instanceof MethodInvocation reduction) || !(reduction.getExpression() instanceof SimpleName receiver)
                || reduction.arguments().size()!=1 || reduction.resolveMethodBinding()==null
                || !JavaValueSourceSummary.big(reduction.resolveMethodBinding().getDeclaringClass()) || !reduction.getName().getIdentifier().equals("mod")) //$NON-NLS-1$
                throw JavaValueSourceSummary.unavailable("VALUE_RESIDUE_INTERACTING_FACTORY_ARGUMENTS"); //$NON-NLS-1$
            int parameter=-1;for(int i=0;i<method.parameters().size();i++)if(((SingleVariableDeclaration)method.parameters().get(i)).resolveBinding().isEqualTo(receiver.resolveBinding()))parameter=i;
            var modulus=JavaValueSourceSummary.variable((Expression)reduction.arguments().getFirst());
            if(parameter<0 || !seen.add(parameter) || modulus==null || !modulus.isEqualTo(arguments.get(parameter).modulus()))
                throw JavaValueSourceSummary.unavailable("VALUE_RESIDUE_FACTORY_MODULUS_OR_PARAMETER"); //$NON-NLS-1$
        }
    }
    private static void verifyCapture(CompilationUnit ast,String source,String generated,Argument argument,ConditionalExpression condition,MethodInvocation fallback,JavaValueSourceSummary.Work work) {
        work.charge(condition.getLength());var parser=ASTParser.newParser(AST.getJLSLatest());parser.setKind(ASTParser.K_EXPRESSION);parser.setSource(argument.guard().toCharArray());
        if(!parser.createAST(null).toString().equals(condition.getExpression().toString()) || !(condition.getThenExpression() instanceof SimpleName raw)
            || !raw.getIdentifier().equals(argument.rawName()) || !(fallback.getExpression() instanceof SimpleName receiver) || !receiver.resolveBinding().isEqualTo(raw.resolveBinding())
            || !fallback.resolveMethodBinding().isEqualTo(argument.reduction().resolveMethodBinding()) || fallback.arguments().size()!=1
            || !(fallback.arguments().getFirst() instanceof SimpleName mod) || !mod.getIdentifier().equals(argument.modulusName()))throw JavaValueSourceSummary.unavailable("VALUE_RESIDUE_REPLAY_GUARD"); //$NON-NLS-1$
        var rawDeclaration=(VariableDeclarationFragment)ast.findDeclaringNode(raw.resolveBinding());var modDeclaration=(VariableDeclarationFragment)ast.findDeclaringNode(mod.resolveBinding());
        if(!Modifier.isFinal(((VariableDeclarationStatement)rawDeclaration.getParent()).getModifiers()) || !Modifier.isFinal(((VariableDeclarationStatement)modDeclaration.getParent()).getModifiers())
            || !text(source,argument.reduction().getExpression()).equals(text(generated,rawDeclaration.getInitializer()))
            || !text(source,(Expression)argument.reduction().arguments().getFirst()).equals(text(generated,modDeclaration.getInitializer()))
            || rawDeclaration.getStartPosition()>modDeclaration.getStartPosition() || modDeclaration.getStartPosition()>condition.getStartPosition())
            throw JavaValueSourceSummary.unavailable("VALUE_RESIDUE_REPLAY_EVALUATION_ORDER"); //$NON-NLS-1$
    }
    private static MethodInvocation findFactory(Block block,MethodInvocation original,List<Argument> arguments,JavaValueSourceSummary.Work work) {
        var found=new ArrayList<MethodInvocation>();block.accept(new ASTVisitor(){@Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(MethodInvocation call) {
                if(call.resolveMethodBinding()!=null && call.resolveMethodBinding().isEqualTo(original.resolveMethodBinding()) && call.arguments().size()==arguments.size()) {
                    boolean same=true;for(int i=0;i<arguments.size();i++)same&=call.arguments().get(i) instanceof SimpleName name && name.getIdentifier().equals(arguments.get(i).local().getName().getIdentifier());
                    if(same)found.add(call);
                }return true;
            }});
        if(found.size()!=1)throw JavaValueSourceSummary.unavailable("VALUE_RESIDUE_REPLAY_FACTORY");return found.getFirst(); //$NON-NLS-1$
    }
    private static void onlyRead(Block block,IVariableBinding binding,SimpleName admitted,JavaValueSourceSummary.Work work) {
        block.accept(new ASTVisitor(){@Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(SimpleName name){if(!name.isDeclaration() && name!=admitted && binding.isEqualTo(name.resolveBinding()))throw JavaValueSourceSummary.unavailable("VALUE_RESIDUE_OTHER_CONSUMER");return true;}}); //$NON-NLS-1$
    }
    private static Block block(ASTNode node){while(node.getParent()!=null && !(node.getParent() instanceof Block))node=node.getParent();return node.getParent() instanceof Block block?block:null;}
    private static String fresh(String base,Set<String> names){while(!names.add(base))base+="_";return base;} //$NON-NLS-1$
    private static String text(String source,ASTNode node){return source.substring(node.getStartPosition(),node.getStartPosition()+node.getLength());}
    private static boolean overlaps(int a,int n,int b,int m){return a<(long)b+m && b<(long)a+n;}
}

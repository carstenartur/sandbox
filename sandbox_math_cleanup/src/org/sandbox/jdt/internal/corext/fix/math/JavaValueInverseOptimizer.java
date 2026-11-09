/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.*;
import de.regelsuche.sdk.optimization.*;
import de.regelsuche.sdk.optimization.power.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;

/** Composes a source-bound successful inverse theorem with an already verified CSE block. */
final class JavaValueInverseOptimizer {
    private JavaValueInverseOptimizer() { }
    private record Opportunity(Block block,VariableDeclarationFragment powered,SimpleName base,MethodInvocation square,
            MethodInvocation product,MethodInvocation retained,List<MethodInvocation> later) { }
    private record Edit(int offset,int length,String text) { }
    static void optimize(CompilationUnit ast,String source,MathCleanUpOptions options,IProgressMonitor monitor,
            int selectionOffset,int selectionLength,Map<String,String> compiler,MathematicalAnalysis.SourceEnvironment environment,
            List<MathematicalAnalysis.Replacement> replacements,List<MathematicalAnalysis.VerifiedRegion> evidence,
            List<MathematicalAnalysis.Diagnostic> diagnostics,JavaValueSourceSummary.Work work,MathematicalJavadoc documentation) {
        if(!options.mathematicalOptIn() || !options.kinds().contains(NumericKind.BIG_INTEGER))return;
        try {
            work.check();
            if(options.valueRuntime()==MathCleanUpOptions.ValueRuntime.UNSPECIFIED)throw unavailable("VALUE_RUNTIME_UNSPECIFIED");
            var opportunities=discover(ast,work);if(opportunities.isEmpty())return;
            var runtime=ValueRuntimeEvidence.qualify(ast,options,work);
            work.charge(source.length());
            for(var opportunity:opportunities) {
                var block=opportunity.block();int start=block.getStartPosition(),length=block.getLength();
                if(selectionOffset>=0 && !(selectionOffset<(long)start+length && start<(long)selectionOffset+Math.max(1,selectionLength)))continue;
                var collisions=replacements.stream().filter(edit->start<(long)edit.offset()+edit.length() && edit.offset()<(long)start+length).toList();
                var previous=evidence.stream().filter(item->item.offset()==start && item.length()==length && item.valueInverse()==null).findFirst().orElse(null);
                if(!collisions.isEmpty() && (collisions.size()!=1 || previous==null || !previous.replacement().equals(collisions.getFirst().replacement())))continue;
                try {
                    audit(opportunity,ast,options,work);
                    var proof=JavaValueInverseProof.prove(ast,opportunity.base().resolveTypeBinding(),opportunity.product().resolveMethodBinding(),
                        opportunity.square().resolveMethodBinding(),opportunity.retained().resolveMethodBinding(),options,work);
                    var session=new JavaValueSourceSummary.Session(ast,options,work);
                    var access=JavaValueAccess.discover(ast,session,opportunity.base().resolveTypeBinding(),work);
                    String original=source.substring(start,start+length),intermediate=previous==null?original:previous.replacement();
                    String intermediateSource=source.substring(0,start)+intermediate+source.substring(start+length);
                    work.charge(intermediateSource.length());var middle=MathematicalAnalysis.checkGeneratedSource(ast,source,intermediateSource,compiler,options.targetJava(),monitor,environment,start,length,intermediate.length());
                    Block middleBlock=findBlock(middle,start,intermediate.length());
                    var retained=calls(middleBlock,opportunity.retained().resolveMethodBinding(),opportunity.powered().resolveBinding(),work);
                    var later=calls(middleBlock,opportunity.retained().resolveMethodBinding(),(IVariableBinding)opportunity.base().resolveBinding(),work);
                    if(retained.size()!=1 || later.size()!=opportunity.later().size())throw unavailable("VALUE_INVERSE_INTERMEDIATE_BINDINGS");
                    String temporary=freshName(middleBlock,"mathRetainedInverse",work);
                    String detached=freshName(middleBlock,"mathInverseCandidate",work);
                    String base=opportunity.base().getIdentifier();
                    var squareCache=previous==null?null:squareCache(middleBlock,opportunity,retained.getFirst(),work);
                    String predecessor=squareCache==null?base+"."+opportunity.square().getName()+"()":squareCache.getIdentifier();
                    String candidate=predecessor+"."+opportunity.product().getName()+"("+temporary+")";
                    var detachmentBase=session.symbolic(access.shape(),"inverseBase");
                    var detachmentSource=JavaValueInverseSource.project(ast,session,opportunity.retained().resolveMethodBinding(),detachmentBase,work);
                    var factory=JavaValueDetachment.factory(ast,session,access,detachmentSource.summary(),work);
                    String guards=guards(ast,proof,access,base,temporary,work)+" && "+JavaValueDetachment.exactStatics(factory.staticValues().stream().filter(value->proof.statics().stream().noneMatch(known->known.field().isEqualTo(value.field()) && known.value().equals(value.value()))).toList(),access,work);
                    if(squareCache!=null)guards+=" && "+exactValue(access,squareCache.getIdentifier());
                    var detachedStatics=new LinkedHashMap<String,JavaValueSourceSummary.StaticValue>();
                    for(var field:proof.statics())detachedStatics.put(field.field().getKey(),field);
                    for(var field:factory.staticValues())detachedStatics.put(field.field().getKey(),field);
                    String detachedGuard=JavaValueDetachment.guard(access,detached,List.of(base),new ArrayList<>(detachedStatics.values()));
                    guards+=" && ("+detached+" = "+candidate+") != null && "+detachedGuard.substring(("java.util.Objects.nonNull("+detached+") && ").length());
                    var edits=new ArrayList<Edit>();
                    edits.add(new Edit(retained.getFirst().getStartPosition(),retained.getFirst().getLength(),"("+temporary+" = "+text(intermediateSource,retained.getFirst())+")"));
                    for(var call:later)edits.add(new Edit(call.getStartPosition(),call.getLength(),"("+guards+" ? "+JavaValueDetachment.emit(access,detached)+" : "+text(intermediateSource,call)+")"));
                    String declaration="\n// Mathematics inverse reuse: the original first inverse remains at its original evaluation position; audited OpenJDK 25.0.5+8 deployment required.\n"
                        +opportunity.base().resolveTypeBinding().getQualifiedName()+" "+temporary+" = null;\n"
                        +opportunity.base().resolveTypeBinding().getQualifiedName()+" "+detached+";\n";
                    edits.add(new Edit(start+1,0,declaration));
                    String replacement=apply(intermediate,start,edits);String generated=source.substring(0,start)+replacement+source.substring(start+length);
                    work.charge(generated.length());var after=MathematicalAnalysis.checkGeneratedSource(ast,source,generated,compiler,options.targetJava(),monitor,environment,start,length,replacement.length());
                    var premises=new UnitInverseReusePlan.Premises(BigInteger.valueOf(3),proof.algebra().sourceFingerprint(),
                        "canonical source residues mod "+proof.algebra().modulus(),opportunity.base().resolveBinding().getKey(),
                        opportunity.powered().resolveBinding().getKey(),opportunity.retained().resolveMethodBinding().getKey()+"@"+opportunity.retained().getStartPosition(),true,true);
                    var searched=new UnitInverseReuseOptimizer().search(premises,work.allocate(20_000,options),work.token());
                    if(searched.work()>20_000)work.charge(searched.work()-20_000);
                    var plan=searched.plan().orElseThrow(()->unavailable("VALUE_INVERSE_PLAN:"+searched.reason()));
                    VerificationEvidence factoryProof;
                    try(var context=JavaSourceContext.open(after,generated,environment,monitor,options,work.remaining())) {
                        factoryProof=replay(findBlock(after,start,replacement.length()),middleBlock,generated,intermediateSource,opportunity,temporary,detached,guards,squareCache,
                            plan,premises,proof,access,new ArrayList<>(detachedStatics.values()),options,work);
                        context.checkpoint().requireCurrent();work.check();
                    }
                    long originalWork=cost(proof.later().summary(),options,work,proof.algebra().modulus().bitLength()),candidateWork=costOfCandidate(ast,opportunity,options,work,squareCache!=null,proof.algebra().modulus().bitLength());
                    candidateWork+=JavaValueDetachment.factoryCost(ast,access,options,work,proof.algebra().modulus().bitLength());
                    candidateWork+=JavaValueDetachment.guardCost(guards,proof.algebra().modulus().bitLength())+JavaValueDetachment.copyCost(access,proof.algebra().modulus().bitLength());
                    if(candidateWork>=originalWork)throw unavailable("VALUE_INVERSE_COST:"+originalWork+"->"+candidateWork);
                    if(originalWork>Long.MAX_VALUE/later.size() || candidateWork>Long.MAX_VALUE/later.size())throw unavailable("VALUE_INVERSE_COST_OVERFLOW");
                    long originalTotal=originalWork*later.size(),candidateTotal=candidateWork*later.size();
                    if(previous!=null){originalTotal+=previous.cost().sourceCost().operationWork();candidateTotal+=previous.cost().candidateCost().operationWork();}
                    var cost=LoopSchedulingEvidence.cost(originalTotal,candidateTotal,options.goal());
                    var factoryProofs=new ArrayList<VerificationEvidence>();
                    if(previous!=null && previous.sourcePlan()!=null && previous.sourcePlan().valueRuntime()!=null)
                        factoryProofs.addAll(previous.sourcePlan().valueRuntime().factoryProofs());
                    factoryProofs.add(factoryProof);
                    var rewrite=new JavaSourceRewritePlan("Source.java",JavaComputationTask.hash(source),source.length(),start,length,List.of(),List.of(),Map.of(),
                        List.of(new JavaSourceRewritePlan.Change(start,length,JavaComputationTask.hash(original),replacement)),runtime.withFactoryProofs(factoryProofs));
                    var commitments=ValueInverseEvidence.commitments(proof.unitRootRequest(),proof.unitBase(),work);
                    var inverseEvidence=new ValueInverseEvidence(premises,plan,proof.algebra().sourceFingerprint(),proof.algebra().laws().stream().map(JavaValueAlgebraProof.Law::proof).toList(),
                        proof.inverseLaws(),proof.retained().inverse(),proof.successfulOccurrence(),proof.root(),proof.unitRootRequest(),proof.unitBase(),commitments.get(0),commitments.get(1),premises.knownInverseBinding(),JavaComputationTask.hash(replacement),previous);
                    documentation.add(ast,start,length,"Explicit mathematical mode: inverse reuse requires deployment on the audited OpenJDK 25.0.5+8 scalar implementation. The first successful inverse remains at its original evaluation position; later guarded inverses use a fresh source factory and separate fresh BigInteger components, with the original inverse retained as fallback.");
                    replacements.removeAll(collisions);if(previous!=null)evidence.remove(previous);
                    replacements.add(new MathematicalAnalysis.Replacement(start,length,replacement,"Verified successful original inverse reuse with source law, unit-root and emitted-plan receipts"));
                    evidence.add(new MathematicalAnalysis.VerifiedRegion(start,length,proof.inverseLaws().getFirst(),cost,original,replacement,null,rewrite,null,null,null,null,null,inverseEvidence));
                } catch(JavaValueSourceSummary.DeadlineExceeded stopped) {throw stopped;}
                catch(IllegalArgumentException rejected) {diagnostics.add(new MathematicalAnalysis.Diagnostic("VALUE_INVERSE_UNAVAILABLE",rejected.getMessage(),start,length,MathematicalAnalysis.Stage.SOURCE));}
            }
        } catch(IllegalArgumentException stopped) {diagnostics.add(new MathematicalAnalysis.Diagnostic("VALUE_INVERSE_LIMIT",stopped.getMessage(),0,source.length(),MathematicalAnalysis.Stage.SOURCE));}
    }
    private static List<Opportunity> discover(CompilationUnit ast,JavaValueSourceSummary.Work work) {
        var result=new ArrayList<Opportunity>();
        ast.accept(new ASTVisitor(){@Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(VariableDeclarationFragment powered) {
            if(!(powered.getParent() instanceof VariableDeclarationStatement declaration) || declaration.fragments().size()!=1 || !(declaration.getParent() instanceof Block block)
                || parentMethod(block)==null
                || !(powered.getInitializer() instanceof MethodInvocation product) || product.arguments().size()!=1 || !(product.arguments().getFirst() instanceof SimpleName base)
                || !(base.resolveBinding() instanceof IVariableBinding binding) || binding.isField() || !JavaValueAccess.possiblyDirect(base.resolveTypeBinding())
                || !(product.getExpression() instanceof MethodInvocation square) || !square.arguments().isEmpty() || !(square.getExpression() instanceof SimpleName squareBase)
                || !binding.isEqualTo(squareBase.resolveBinding()) || product.resolveMethodBinding()==null || square.resolveMethodBinding()==null || powered.resolveBinding()==null)return true;
            var retained=new ArrayList<MethodInvocation>();block.accept(new ASTVisitor(){@Override public void preVisit(ASTNode node){work.charge(1);}
                @Override public boolean visit(MethodInvocation call) {
                if(call.getExpression() instanceof SimpleName name && powered.resolveBinding().isEqualTo(name.resolveBinding()) && call.arguments().isEmpty()
                    && call.resolveMethodBinding()!=null && call.resolveTypeBinding()!=null && call.resolveTypeBinding().isEqualTo(base.resolveTypeBinding()) && call.getStartPosition()>powered.getStartPosition())retained.add(call);return true;
            }});
            for(var first:retained) {
                var later=calls(block,first.resolveMethodBinding(),binding,work).stream().filter(call->call.getStartPosition()>first.getStartPosition()).toList();
                if(!later.isEmpty())result.add(new Opportunity(block,powered,base,square,product,first,later));
            }
            return true;
        }});return result;
    }
    private static void audit(Opportunity opportunity,CompilationUnit ast,MathCleanUpOptions options,JavaValueSourceSummary.Work work) {
        var first=topStatement(opportunity.retained(),opportunity.block());
        if(!(first instanceof VariableDeclarationStatement) && !(first instanceof ExpressionStatement))throw unavailable("VALUE_INVERSE_FIRST_DOMINANCE");
        for(ASTNode node=opportunity.retained();node!=first;node=node.getParent())if(node instanceof ConditionalExpression || node instanceof InfixExpression || node instanceof LambdaExpression)
            throw unavailable("VALUE_INVERSE_FIRST_CONDITIONAL");
        var base=(IVariableBinding)opportunity.base().resolveBinding();var powered=opportunity.powered().resolveBinding();
        opportunity.block().accept(new ASTVisitor(){@Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(Assignment assignment){var binding=JavaValueSourceSummary.variable(assignment.getLeftHandSide());if(base.isEqualTo(binding) || powered.isEqualTo(binding))throw unavailable("VALUE_INVERSE_REBOUND");return true;}
            @Override public boolean visit(LambdaExpression node){throw unavailable("VALUE_INVERSE_LAMBDA");}
            @Override public boolean visit(AnonymousClassDeclaration node){throw unavailable("VALUE_INVERSE_ANONYMOUS");}
        });
        var session=new JavaValueSourceSummary.Session(ast,options,work);var uses=new JavaValueUseProof(ast,session,work);
        var method=parentMethod(opportunity.block());if(method==null)throw unavailable("VALUE_INVERSE_METHOD_CONTEXT");
        uses.scalarOrigins(opportunity.product());uses.admitConsumerShape(opportunity.retained(),method);
        for(var later:opportunity.later()) {
            if(topStatement(later,opportunity.block()).getStartPosition()<=first.getStartPosition())throw unavailable("VALUE_INVERSE_DOMINANCE");
            for(ASTNode node=later;node!=opportunity.block();node=node.getParent())if(node instanceof WhileStatement || node instanceof ForStatement || node instanceof DoStatement
                || node instanceof EnhancedForStatement || node instanceof TryStatement || node instanceof SynchronizedStatement)throw unavailable("VALUE_INVERSE_CONTROL_FLOW");
            uses.admitConsumerShape(later,method);
        }
    }
    private static String guards(CompilationUnit ast,JavaValueInverseProof.Receipt proof,JavaValueAccess.Access access,String base,String retained,JavaValueSourceSummary.Work work) {
        var fields=new HashSet<String>();
        for(var component:access.shape().components()){work.charge(1);fields.add(component.field().getVariableDeclaration().getKey());}
        for(var value:proof.statics()){work.charge(1);fields.add(value.field().getVariableDeclaration().getKey());}
        rejectionOrigins(ast,proof.later().rejection(),fields,new HashSet<>(),new HashSet<>(),work);
        var guards=new LinkedHashSet<String>();String modulus="new java.math.BigInteger(\""+proof.algebra().modulus()+"\")";
        for(String object:List.of(base,retained)) {
            guards.add("java.util.Objects.nonNull("+object+")");for(int i=0;i<access.shape().leaves();i++) {
                String leaf=access.leaf(object,i);guards.add(leaf+" != null");guards.add(leaf+".getClass() == java.math.BigInteger.class");
                guards.add(leaf+".signum() >= 0");guards.add(leaf+".compareTo("+modulus+") < 0");
            }
        }
        for(var value:proof.statics()) {
            if(!Modifier.isPublic(value.field().getModifiers()))throw unavailable("VALUE_INVERSE_STATIC_ACCESS");
            if(value.value() instanceof JavaValueSourceSummary.Scalar scalar) {
                String path=value.path();guards.add(path+" != null");guards.add(path+".getClass() == java.math.BigInteger.class");
                guards.add(path+".equals(new java.math.BigInteger(\""+JavaFixedPowerConstant.scalar(scalar.expression(),work)+"\"))");
            } else if(value.value() instanceof JavaValueSourceSummary.Value constant) {
                guards.add(value.path()+" != null");for(int i=0;i<constant.components().size();i++) {
                    String leaf=access.leaf(value.path(),i);guards.add(leaf+" != null");guards.add(leaf+".getClass() == java.math.BigInteger.class");
                    guards.add(leaf+".equals(new java.math.BigInteger(\""+JavaFixedPowerConstant.scalar(JavaValueSourceSummary.scalar(constant.components().get(i)),work)+"\"))");
                }
            }
        }
        if(!Modifier.isPublic(proof.later().rejection().getModifiers()))throw unavailable("VALUE_INVERSE_REJECTION_ACCESS");
        guards.add("!"+base+"."+proof.later().rejection().getName()+"()");return String.join(" && ",guards);
    }
    /** Speculative predicates need a closed syntax and already guarded field roots, beyond structural use admission. */
    private static void rejectionOrigins(CompilationUnit ast,IMethodBinding method,Set<String> fields,Set<String> active,
            Set<String> proved,JavaValueSourceSummary.Work work) {
        work.charge(1);
        if(method==null || method.isRecovered())throw unavailable("VALUE_INVERSE_REJECTION_BINDING");
        String key=method.getMethodDeclaration().getKey();if(proved.contains(key))return;
        if(!active.add(key) || active.size()>32)throw unavailable("VALUE_INVERSE_REJECTION_RECURSION");
        try {
            var definition=JavaSourceContext.definition(ast,method.getMethodDeclaration());
            if(definition==null || !(definition.declaration() instanceof MethodDeclaration source) || source.getBody()==null
                || source.resolveBinding()==null || !key.equals(source.resolveBinding().getMethodDeclaration().getKey()))
                throw unavailable("VALUE_INVERSE_REJECTION_SOURCE");
            source.getBody().accept(new ASTVisitor(){
                @Override public void preVisit(ASTNode node) {
                    work.charge(1);
                    if(node instanceof Statement && !(node instanceof Block || node instanceof ReturnStatement
                        || node instanceof IfStatement || node instanceof VariableDeclarationStatement))
                        throw unavailable("VALUE_INVERSE_REJECTION_STATEMENT");
                    if(node instanceof Expression expression) {
                        boolean allowed=switch(node.getNodeType()) {
                            case ASTNode.SIMPLE_NAME,ASTNode.QUALIFIED_NAME,ASTNode.FIELD_ACCESS,ASTNode.THIS_EXPRESSION,
                                ASTNode.BOOLEAN_LITERAL,ASTNode.NUMBER_LITERAL,ASTNode.CHARACTER_LITERAL,ASTNode.STRING_LITERAL,
                                ASTNode.TEXT_BLOCK,ASTNode.NULL_LITERAL,ASTNode.METHOD_INVOCATION,ASTNode.PARENTHESIZED_EXPRESSION,
                                ASTNode.CAST_EXPRESSION,ASTNode.CONDITIONAL_EXPRESSION,ASTNode.INSTANCEOF_EXPRESSION -> true;
                            case ASTNode.INFIX_EXPRESSION,ASTNode.PREFIX_EXPRESSION -> {
                                var type=expression.resolveTypeBinding();yield type!=null && !type.isRecovered() && type.isPrimitive();
                            }
                            default -> false;
                        };
                        if(!allowed)throw unavailable("VALUE_INVERSE_REJECTION_EXPRESSION");
                    }
                }
                @Override public boolean visit(SimpleName name) {
                    var binding=name.resolveBinding();if(binding==null || binding.isRecovered())throw unavailable("VALUE_INVERSE_REJECTION_BINDING");
                    if(binding instanceof IVariableBinding field && field.isField() && !fields.contains(field.getVariableDeclaration().getKey()))
                        throw unavailable("VALUE_INVERSE_REJECTION_UNGUARDED_ORIGIN");
                    return true;
                }
                @Override public boolean visit(MethodInvocation call) {
                    var target=call.resolveMethodBinding();if(target==null || target.isRecovered())throw unavailable("VALUE_INVERSE_REJECTION_BINDING");
                    if(!JavaValueSourceSummary.big(target.getDeclaringClass()))rejectionOrigins(ast,target,fields,active,proved,work);
                    return true;
                }
            });proved.add(key);
        } finally {active.remove(key);}
    }
    private static String exactValue(JavaValueAccess.Access access,String value) {
        var guards=new ArrayList<String>();guards.add("java.util.Objects.nonNull("+value+")");
        for(int i=0;i<access.shape().leaves();i++){String leaf=access.leaf(value,i);guards.add(leaf+" != null");guards.add(leaf+".getClass() == java.math.BigInteger.class");}
        return String.join(" && ",guards);
    }
    private static VerificationEvidence replay(Block after,Block middle,String generated,String intermediate,Opportunity opportunity,String temporary,String detached,String guards,
            SimpleName cache,UnitInverseReusePlan plan,UnitInverseReusePlan.Premises premises,JavaValueInverseProof.Receipt proof,JavaValueAccess.Access access,List<JavaValueSourceSummary.StaticValue> guardStatics,MathCleanUpOptions options,JavaValueSourceSummary.Work work) {
        if(!(after.statements().getFirst() instanceof VariableDeclarationStatement declaration) || declaration.fragments().size()!=1
            || !(declaration.fragments().getFirst() instanceof VariableDeclarationFragment variable) || !variable.getName().getIdentifier().equals(temporary)
            || !(variable.getInitializer() instanceof NullLiteral))throw unavailable("VALUE_INVERSE_REPLAY_DECLARATION");
        if(after.statements().size()<2 || !(after.statements().get(1) instanceof VariableDeclarationStatement detachedDeclaration)
            || detachedDeclaration.fragments().size()!=1 || !(detachedDeclaration.fragments().getFirst() instanceof VariableDeclarationFragment detachedVariable)
            || !detachedVariable.getName().getIdentifier().equals(detached) || detachedVariable.getInitializer()!=null)throw unavailable("VALUE_INVERSE_REPLAY_DETACHED_DECLARATION");
        var assignments=new ArrayList<Assignment>();var conditionals=new ArrayList<ConditionalExpression>();
        after.accept(new ASTVisitor(){@Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(Assignment assignment){if(variable.resolveBinding().isEqualTo(JavaValueSourceSummary.variable(assignment.getLeftHandSide())))assignments.add(assignment);return true;}
            @Override public boolean visit(ConditionalExpression conditional){
                boolean[] found={false};conditional.getThenExpression().accept(new ASTVisitor(){@Override public void preVisit(ASTNode node){work.charge(1);}
                    @Override public boolean visit(SimpleName name){found[0]|=same(name.resolveBinding(),detachedVariable.resolveBinding());return true;}});
                if(found[0])conditionals.add(conditional);return true;}
        });
        if(assignments.size()!=1 || conditionals.size()!=opportunity.later().size())throw unavailable("VALUE_INVERSE_REPLAY_SCHEDULE");
        var assignment=assignments.getFirst();
        if(!(assignment.getRightHandSide() instanceof MethodInvocation retained) || !matches(retained,opportunity.retained().resolveMethodBinding(),opportunity.powered().resolveBinding())
            || !(assignment.getParent() instanceof ParenthesizedExpression))throw unavailable("VALUE_INVERSE_REPLAY_RETAINED");
        var edits=new ArrayList<Edit>();edits.add(new Edit(assignment.getParent().getStartPosition(),assignment.getParent().getLength(),text(generated,retained)));
        FixedPowerPlan decoded=null;MethodInvocation lastFallback=null;
        var parser=ASTParser.newParser(AST.getJLSLatest());parser.setKind(ASTParser.K_EXPRESSION);parser.setSource(guards.toCharArray());String expectedGuard=parser.createAST(null).toString();
        for(var conditional:conditionals) {
            if(!conditional.getExpression().toString().equals(expectedGuard) || !(conditional.getParent() instanceof ParenthesizedExpression)
                || !(conditional.getElseExpression() instanceof MethodInvocation fallback) || !matches(fallback,opportunity.retained().resolveMethodBinding(),(IVariableBinding)opportunity.base().resolveBinding())
                || !same(JavaValueDetachment.decode(conditional.getThenExpression(),access,work).resolveBinding(),detachedVariable.resolveBinding())
                || conditional.getStartPosition()<assignment.getStartPosition())throw unavailable("VALUE_INVERSE_REPLAY_CONDITIONAL");
            lastFallback=fallback;
            var products=new ArrayList<Assignment>();conditional.getExpression().accept(new ASTVisitor(){@Override public void preVisit(ASTNode node){work.charge(1);}
                @Override public boolean visit(Assignment candidate){products.add(candidate);return true;}});
            if(products.size()!=1 || !same(JavaValueSourceSummary.variable(products.getFirst().getLeftHandSide()),detachedVariable.resolveBinding())
                || !(products.getFirst().getRightHandSide() instanceof MethodInvocation product) || !same(product.resolveMethodBinding(),opportunity.product().resolveMethodBinding())
                || product.arguments().size()!=1 || !same(JavaValueSourceSummary.variable((Expression)product.arguments().getFirst()),variable.resolveBinding()))throw unavailable("VALUE_INVERSE_REPLAY_CANDIDATE");
            var guardLocals=new ArrayList<IBinding>(List.of(opportunity.base().resolveBinding(),variable.resolveBinding(),detachedVariable.resolveBinding()));
            if(cache!=null)guardLocals.add(cache.resolveBinding());
            JavaValueGuardBindings.verify(conditional.getExpression(),access,guardLocals,guardStatics,
                List.of(opportunity.product().resolveMethodBinding(),opportunity.square().resolveMethodBinding(),proof.later().rejection()),work);
            Expression predecessor=product.getExpression();
            if(cache!=null) {
                if(!(predecessor instanceof SimpleName name) || !same(name.resolveBinding(),cache.resolveBinding()))throw unavailable("VALUE_INVERSE_REPLAY_CACHE");
                var found=squareCache(after,opportunity,retained,work);if(found==null || !same(found.resolveBinding(),name.resolveBinding()))throw unavailable("VALUE_INVERSE_REPLAY_CACHE_SOURCE");
            } else if(!(predecessor instanceof MethodInvocation square) || !matches(square,opportunity.square().resolveMethodBinding(),(IVariableBinding)opportunity.base().resolveBinding()))throw unavailable("VALUE_INVERSE_REPLAY_SQUARE");
            decoded=new FixedPowerPlan(BigInteger.TWO,List.of(new FixedPowerPlan.Multiplication(1,1)),2);
            edits.add(new Edit(conditional.getParent().getStartPosition(),conditional.getParent().getLength(),text(generated,fallback)));
        }
        if(decoded==null || !decoded.equals(plan.predecessorPower()))throw unavailable("VALUE_INVERSE_REPLAY_POWER");
        var verified=new UnitInverseReuseVerifier().verify(premises,new UnitInverseReusePlan(premises,decoded),work.allocate(20_000,options),work.token());
        if(verified.work()>20_000)work.charge(verified.work()-20_000);
        if(!verified.verified())throw unavailable("VALUE_INVERSE_REPLAY_CERTIFICATE:"+verified.reason());
        edits.add(new Edit(declaration.getStartPosition(),declaration.getLength(),""));
        edits.add(new Edit(detachedDeclaration.getStartPosition(),detachedDeclaration.getLength(),""));
        String restored=apply(text(generated,after),after.getStartPosition(),edits);
        var restore=ASTParser.newParser(AST.getJLSLatest());restore.setKind(ASTParser.K_STATEMENTS);restore.setSource(restored.substring(1,restored.length()-1).toCharArray());
        if(!restore.createAST(null).toString().equals(middle.toString()))throw unavailable("VALUE_INVERSE_REPLAY_UNCHANGED_CONTEXT");
        var session=new JavaValueSourceSummary.Session((CompilationUnit)after.getRoot(),options,work);
        var actualAccess=JavaValueAccess.discover((CompilationUnit)after.getRoot(),session,lastFallback.resolveTypeBinding(),work);
        var base=session.symbolic(actualAccess.shape(),"inverseBase");
        var original=JavaValueInverseSource.project((CompilationUnit)after.getRoot(),session,lastFallback.resolveMethodBinding(),base,work);
        var factory=JavaValueDetachment.factory((CompilationUnit)after.getRoot(),session,actualAccess,original.summary(),work);
        return JavaValueDetachment.verifyFactory(original.summary(),factory,original.inverse(),options,work).evidence();
    }
    private static SimpleName squareCache(Block block,Opportunity opportunity,MethodInvocation retained,JavaValueSourceSummary.Work work) {
        var result=new ArrayList<SimpleName>();block.accept(new ASTVisitor(){@Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(Assignment assignment) {
            if(assignment.getOperator()==Assignment.Operator.ASSIGN && assignment.getStartPosition()<retained.getStartPosition() && assignment.getLeftHandSide() instanceof SimpleName name
                && name.resolveBinding() instanceof IVariableBinding cacheBinding && !cacheBinding.isField()
                && assignment.getRightHandSide() instanceof MethodInvocation call && matches(call,opportunity.square().resolveMethodBinding(),(IVariableBinding)opportunity.base().resolveBinding())) {
                ASTNode statement=topStatement(assignment,block);boolean conditional=false;
                for(ASTNode node=assignment;node!=statement;node=node.getParent())if(node instanceof ConditionalExpression || node instanceof InfixExpression)conditional=true;
                if(!conditional && statement.getStartPosition()<topStatement(retained,block).getStartPosition())result.add(name);
            }return true;
        }});
        if(result.size()!=1)return null;var candidate=result.getFirst();int[] writes={0},initializations={0};
        block.accept(new ASTVisitor(){@Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(VariableDeclarationFragment declaration) {
                if(same(declaration.resolveBinding(),candidate.resolveBinding()) && declaration.getInitializer() instanceof NullLiteral
                    && declaration.getParent() instanceof VariableDeclarationStatement statement && statement.fragments().size()==1
                    && statement.getParent()==block && declaration.getStartPosition()<candidate.getStartPosition())initializations[0]++;
                return true;
            }
            @Override public boolean visit(Assignment assignment) {
            if(same(JavaValueSourceSummary.variable(assignment.getLeftHandSide()),candidate.resolveBinding()))writes[0]++;return true;
        }});
        // A null-initialized local with exactly one source-bound square write is either null or that square.
        // The emitted non-null guard proves the write executed, even when its control-flow path is conditional.
        return initializations[0]==1 && writes[0]==1?candidate:null;
    }
    private static long costOfCandidate(CompilationUnit ast,Opportunity opportunity,MathCleanUpOptions options,JavaValueSourceSummary.Work work,boolean cached,int bits) {
        var session=new JavaValueSourceSummary.Session(ast,options,work);var shape=session.shape(opportunity.base().resolveTypeBinding());var base=session.symbolic(shape,"base");
        var inverse=session.symbolic(shape,"retained");var squared=cached?base:(JavaValueSourceSummary.Value)session.invoke(opportunity.square().resolveMethodBinding(),base,List.of());
        return cost(session.summary((JavaValueSourceSummary.Value)session.invoke(opportunity.product().resolveMethodBinding(),squared,List.of(inverse))),options,work,bits);
    }
    private static long cost(JavaValueSourceSummary.Summary summary,MathCleanUpOptions options,JavaValueSourceSummary.Work work,int bits) {
        work.charge(10_000);var prepared=ComputationOptimizer.prepare(summary.plan());
        var assumptions=new HashSet<SemanticAssumption>();
        for(var assumption:summary.assumptions())assumptions.add(assumption.kind()==SemanticAssumption.Kind.BIG_INTEGER_BIT_LENGTH_BOUND
            ?new SemanticAssumption(assumption.kind(),assumption.subject(),Integer.toString(bits),"exact canonical residue guard"):assumption);
        var request=new OptimizationRequest(summary.plan(),summary.trace(),Set.of(NumericKind.BIG_INTEGER),ComputationOptimizer.SEMANTICS_REVISION,
            assumptions,SafetyProfile.PRESERVE_JAVA,options.goal(),work.budget(Math.max(1,work.remaining()),options),CheckedPolicy.NONE);
        var estimate=NumericExecutionCost.estimateSource(request,prepared).orElseThrow(()->unavailable("VALUE_INVERSE_COST"));
        work.charge(estimate.inspectionWork()+prepared.cost().inspectionWork());return estimate.operationWork();
    }
    private static List<MethodInvocation> calls(Block block,IMethodBinding method,IVariableBinding receiver,JavaValueSourceSummary.Work work) {
        var result=new ArrayList<MethodInvocation>();block.accept(new ASTVisitor(){@Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(MethodInvocation call){if(matches(call,method,receiver))result.add(call);return true;}});return result;
    }
    private static boolean matches(MethodInvocation call,IMethodBinding method,IVariableBinding receiver) {
        return call.arguments().isEmpty() && same(call.resolveMethodBinding(),method) && call.getExpression() instanceof SimpleName name && same(name.resolveBinding(),receiver);
    }
    private static boolean same(IBinding left,IBinding right){return left!=null && right!=null && !left.isRecovered() && !right.isRecovered() && left.getKey().equals(right.getKey());}
    private static Block findBlock(CompilationUnit ast,int start,int length) {
        ASTNode node=NodeFinder.perform(ast,start,length);if(node instanceof Block block && block.getStartPosition()==start && block.getLength()==length)return block;
        throw unavailable("VALUE_INVERSE_BLOCK");
    }
    private static ASTNode topStatement(ASTNode node,Block block){while(node.getParent()!=block){node=node.getParent();if(node==null)throw unavailable("VALUE_INVERSE_BLOCK_CONTEXT");}return node;}
    private static MethodDeclaration parentMethod(ASTNode node) {
        while(node!=null && !(node instanceof MethodDeclaration)) {
            if(node instanceof Initializer || node instanceof AbstractTypeDeclaration || node instanceof AnonymousClassDeclaration)return null;
            node=node.getParent();
        }
        return (MethodDeclaration)node;
    }
    private static String freshName(Block block,String prefix,JavaValueSourceSummary.Work work){var names=new HashSet<String>();block.accept(new ASTVisitor(){
        @Override public void preVisit(ASTNode node){work.charge(1);}
        @Override public boolean visit(SimpleName name){names.add(name.getIdentifier());return true;}});while(names.contains(prefix))prefix+="_";return prefix;}
    private static String text(String source,ASTNode node){return source.substring(node.getStartPosition(),node.getStartPosition()+node.getLength());}
    private static String apply(String source,int offset,List<Edit> edits){var result=new StringBuilder(source);edits.sort(Comparator.comparingInt(Edit::offset).reversed());for(var edit:edits)result.replace(edit.offset()-offset,edit.offset()-offset+edit.length(),edit.text());return result.toString();}
    private static IllegalArgumentException unavailable(String reason){return JavaValueSourceSummary.unavailable(reason);}
}

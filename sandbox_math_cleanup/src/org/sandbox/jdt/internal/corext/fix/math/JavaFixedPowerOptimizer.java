/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.*;
import de.regelsuche.ast.Expr;
import de.regelsuche.sdk.optimization.*;
import de.regelsuche.sdk.optimization.power.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;

/** Fixed calls to a source-proved power loop, guarded by its independently proved value algebra. */
final class JavaFixedPowerOptimizer {
    private JavaFixedPowerOptimizer(){ }
    private record Site(MethodInvocation call,IVariableBinding exponent,TypeDeclaration owner) { }
    private record Edit(int offset,int length,String replacement) { }
    static void optimize(CompilationUnit ast,String source,MathCleanUpOptions options,IProgressMonitor monitor,int selectionOffset,int selectionLength,
            Map<String,String> compiler,MathematicalAnalysis.SourceEnvironment environment,List<MathematicalAnalysis.Replacement> replacements,
            List<MathematicalAnalysis.VerifiedRegion> evidence,List<MathematicalAnalysis.Diagnostic> diagnostics,JavaValueSourceSummary.Work work) {
        if(!options.mathematicalOptIn() || !options.kinds().contains(NumericKind.BIG_INTEGER))return;
        // Same-caller constant admission requires this shallow shape; unrelated files keep their region budget.
        boolean possible=false;long preflight=0;
        for(Object item:ast.types()) {preflight++;if(item instanceof TypeDeclaration type)
            for(var field:type.getFields()) {preflight++;if(Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())
                && JavaValueSourceSummary.big(field.getType().resolveBinding()))possible=true;}}
        try {
            // Constant and loop discovery can reject before any value-algebra session exists.
            JavaSourceContext.valueWork(ast,work);
            work.charge(preflight);if(!possible)return;
            var sites=new LinkedHashMap<TypeDeclaration,List<Site>>();
            ast.accept(new ASTVisitor(){
                @Override public void preVisit(ASTNode node){work.charge(1);}
                @Override public boolean visit(MethodInvocation call) {
                    // Conditional sites include existing guarded fallbacks; keep their evaluation envelope intact.
                    for(ASTNode node=call.getParent();node!=null && !(node instanceof MethodDeclaration);node=node.getParent()) {
                        work.charge(1);if(node instanceof ConditionalExpression)return true;
                    }
                    if(call.arguments().size()!=1 || !(call.getExpression() instanceof SimpleName receiver) || !(receiver.resolveBinding() instanceof IVariableBinding local)
                        || local.isField() || call.resolveMethodBinding()==null || call.resolveTypeBinding()==null || JavaValueSourceSummary.big(call.resolveTypeBinding())
                        || !Modifier.isFinal(call.resolveTypeBinding().getModifiers()) || !call.resolveTypeBinding().isEqualTo(call.resolveMethodBinding().getDeclaringClass()) || !JavaValueSourceSummary.big(((Expression)call.arguments().getFirst()).resolveTypeBinding()))return true;
                    var exponent=JavaValueSourceSummary.variable((Expression)call.arguments().getFirst());
                    if(exponent==null || !Modifier.isStatic(exponent.getModifiers()) || !Modifier.isFinal(exponent.getModifiers())
                        || !(call.arguments().getFirst() instanceof SimpleName))return true;
                    ASTNode current=call;while(current!=null && !(current instanceof TypeDeclaration))current=current.getParent();
                    if(current==null)return true;
                    TypeDeclaration owner=(TypeDeclaration)current;
                    if(owner.isLocalTypeDeclaration() || owner.isMemberTypeDeclaration()
                        || !exponent.getDeclaringClass().isEqualTo(owner.resolveBinding()))return true;
                    if(selectionOffset>=0 && !overlaps(selectionOffset,Math.max(1,selectionLength),call.getStartPosition(),call.getLength()))return true;
                    sites.computeIfAbsent(owner,ignored->new ArrayList<>()).add(new Site(call,exponent,owner));return true;
                }
            });
            for(var entry:sites.entrySet()) {
                TypeDeclaration owner=entry.getKey();int start=owner.getStartPosition(),length=owner.getLength();
                if(replacements.stream().anyMatch(edit->overlaps(start,length,edit.offset(),edit.length())))continue;
                try (var localSources=JavaSourceContext.captureLocalSources(ast)) {
                    var edits=new ArrayList<Edit>();var chains=new ArrayList<FixedPowerEvidence.Chain>();var helpers=new StringBuilder();
                    var documentation=new MathematicalJavadoc();
                    var laws=new HashMap<String,JavaValueAlgebraProof.Receipt>();var loops=new HashMap<String,JavaFixedPowerSource.Loop>();
                    var names=new HashSet<String>();owner.accept(new ASTVisitor(){@Override public void preVisit(ASTNode node){work.charge(1);}@Override public boolean visit(SimpleName name){names.add(name.getIdentifier());return true;}});
                    long before=0,after=0;
                    for(var site:entry.getValue()) {
                        if(chains.size()>=8)break;work.check();
                        try {
                            BigInteger exponent=JavaFixedPowerConstant.derive(ast,site.exponent(),work);if(exponent.compareTo(BigInteger.ONE)<=0 || exponent.bitLength()>4096)continue;
                            var binding=site.call().resolveMethodBinding();String key=binding.getMethodDeclaration().getKey();
                            var loop=loops.get(key);if(loop==null){loop=JavaFixedPowerSource.recognize(ast,binding,work);loops.put(key,loop);}
                            var algebra=laws.get(key);
                            if(algebra==null) {
                                var session=new JavaValueSourceSummary.Session(ast,options,work);var shape=session.shape(binding.getDeclaringClass());
                                algebra=JavaValueAlgebraProof.prove(session,shape,loop.multiplication(),loop.square(),(JavaValueSourceSummary.Value)session.constant(loop.identity()),work,options);
                                laws.put(key,algebra);
                            }
                            var searched=new FixedPowerOptimizer().search(exponent,work.allocate(Math.min(40_000,work.remaining()/8),options),work.token());
                            if(searched.plan().isEmpty())continue;var plan=searched.plan().orElseThrow();verifyPlan(plan,exponent,work,options);
                            String name="_mathFixedPower"+chains.size();while(!names.add(name)){work.charge(name.length());name+="_";} //$NON-NLS-1$ //$NON-NLS-2$
                            String receiver=site.call().getExpression().toString(),constant=site.exponent().getName();
                            var guardParts=guard(ast,binding.getDeclaringClass(),receiver,constant,exponent,algebra,owner.resolveBinding(),options,work);
                            guardParts.bind(JavaValueSourceSummary.variable(site.call().getExpression()));guardParts.bind(site.exponent());
                            guardParts.jdk(site.exponent().getType(),ast.getAST().resolveWellKnownType("java.lang.Object")); //$NON-NLS-1$
                            String guard=String.join(" && ",guardParts); //$NON-NLS-1$
                            String fallback=source.substring(site.call().getStartPosition(),site.call().getStartPosition()+site.call().getLength());
                            String replacement="("+guard+" ? "+name+"("+receiver+") : "+fallback+")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                            String helper=helper(name,binding.getDeclaringClass(),loop,plan,work);
                            work.charge(guard.length()+helper.length());
                            // Scalar operation counts are source-derived. Include guard expressions and every emitted local.
                            long oldWork=(long)exponent.bitLength()*algebra.squareWork()+exponent.bitCount()*algebra.multiplicationWork()+5L*exponent.bitLength()+3L*exponent.bitCount();
                            long newWork=plan.multiplications().size()+guard.chars().filter(character->character=='(').count()+guard.split(" && ").length;
                            for(var step:plan.multiplications())newWork+=(step.left()==step.right()?algebra.squareWork()+1:algebra.multiplicationWork()+2);
                            if(newWork>=oldWork)continue;
                            var chain=new FixedPowerEvidence.Chain(name,exponent,plan,loop.multiplication().getKey(),loop.square().getKey(),algebra.sourceFingerprint(),guard,fallback,algebra.laws().stream().map(FixedPowerEvidence.Law::of).toList(),
                                JavaValueSourceSummary.variable(site.call().getExpression()).getVariableDeclaration().getKey(),
                                binding.getMethodDeclaration().getKey(),site.exponent().getVariableDeclaration().getKey(),sourcePath(site.call(),owner,work),List.copyOf(guardParts.bindings));
                            documentation.add(ast,site.call().getStartPosition(),site.call().getLength(),
                                "Explicit mathematical mode: fixed-power reassociation may change BigInteger component reference identity; the result is a fresh wrapper. Canonical-component guards retain the original power call for other inputs."); //$NON-NLS-1$
                            before+=oldWork;after+=newWork;
                            edits.add(new Edit(site.call().getStartPosition()-start,site.call().getLength(),replacement));helpers.append(helper);chains.add(chain);
                        } catch(IllegalArgumentException rejected) {diagnostics.add(new MathematicalAnalysis.Diagnostic("VALUE_POWER_UNAVAILABLE",rejected.getMessage(),site.call().getStartPosition(),site.call().getLength(),MathematicalAnalysis.Stage.SOURCE));} //$NON-NLS-1$
                    }
                    if(chains.isEmpty())continue;
                    for(var note:documentation.edits(source)) {
                        work.charge(note.replacement().length());
                        edits.add(new Edit(note.offset()-start,note.length(),note.replacement()));
                    }
                    String original=source.substring(start,start+length);var replaced=new StringBuilder(original);
                    edits.sort(Comparator.comparingInt(Edit::offset).reversed());for(var edit:edits)replaced.replace(edit.offset(),edit.offset()+edit.length(),edit.replacement());
                    replaced.insert(replaced.length()-1,helpers);String replacement=replaced.toString(),generated=source.substring(0,start)+replacement+source.substring(start+length);
                    work.charge(generated.length());if(generated.length()>1_000_000)throw JavaValueSourceSummary.unavailable("VALUE_POWER_GENERATED_SOURCE_BOUND"); //$NON-NLS-1$
                    var parsed=MathematicalAnalysis.checkGeneratedSource(ast,source,generated,compiler,options.targetJava(),monitor,environment,start,length,replacement.length());
                    var schedule=new FixedPowerEvidence(owner.resolveBinding().getKey(),JavaComputationTask.hash(replacement),chains,JavaSourceContext.receipts(ast),before,after,localTypes(localSources.declarations(),source,owner,work));
                    var plan=new JavaSourceRewritePlan("Source.java",JavaComputationTask.hash(source),source.length(),start,length,List.of(),List.of(),Map.of(), //$NON-NLS-1$
                        List.of(new JavaSourceRewritePlan.Change(start,length,JavaComputationTask.hash(original),replacement)));
                    if(schedule.cost(options.goal()).candidateScore()>=schedule.cost(options.goal()).sourceScore())continue;
                    var region=new MathematicalAnalysis.VerifiedRegion(start,length,null,schedule.cost(options.goal()),original,replacement,null,plan,null,null,null,null,schedule);
                    verifyCombined(parsed,generated,region,options,work);
                    replacements.add(new MathematicalAnalysis.Replacement(start,length,replacement,"Source-proved fixed power; guarded canonical value algebra and exact original fallback"));evidence.add(region); //$NON-NLS-1$
                } catch(IllegalArgumentException rejected) {diagnostics.add(new MathematicalAnalysis.Diagnostic("VALUE_POWER_LIMIT",rejected.getMessage(),start,length,MathematicalAnalysis.Stage.SOURCE));} //$NON-NLS-1$
            }
        } catch(IllegalArgumentException rejected) {diagnostics.add(new MathematicalAnalysis.Diagnostic("VALUE_POWER_LIMIT",rejected.getMessage(),0,source.length(),MathematicalAnalysis.Stage.SOURCE));} //$NON-NLS-1$
        finally {JavaSourceContext.valueWork(ast,null);}
    }
    private static String helper(String name,ITypeBinding type,JavaFixedPowerSource.Loop loop,FixedPowerPlan plan,JavaValueSourceSummary.Work work) {
        String qualified=type.getQualifiedName();
        long bound=(long)plan.multiplications().size()*(qualified.length()+loop.multiplication().getName().length()+loop.square().getName().length()+120L);
        if(plan.multiplications().size()>1024 || name.length()>1024 || bound>200_000)throw JavaValueSourceSummary.unavailable("VALUE_POWER_JAVA_METHOD_BOUND"); //$NON-NLS-1$
        work.charge(bound);var source=new StringBuilder("\n// Mathematics fixed power: source algebra and emitted schedule independently verified.\nprivate static "+qualified+" "+name+"("+qualified+" mathBase) {\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        for(int i=0;i<plan.multiplications().size();i++) {
            var step=plan.multiplications().get(i);source.append("final ").append(qualified).append(" mathPower").append(i+2).append(" = ").append(node(step.left())).append('.'); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            if(step.left()==step.right())source.append(loop.square().getName()).append("()"); //$NON-NLS-1$
            else source.append(loop.multiplication().getName()).append('(').append(node(step.right())).append(')');source.append(";\n"); //$NON-NLS-1$
        }
        return source.append("return ").append(node(plan.resultIndex())).append(";\n}\n").toString(); //$NON-NLS-1$ //$NON-NLS-2$
    }
    private static String node(int index){if(index<1)throw JavaValueSourceSummary.unavailable("VALUE_POWER_IDENTITY_NODE");return index==1?"mathBase":"mathPower"+index;} //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    private static Guards guard(CompilationUnit ast,ITypeBinding type,String receiver,String exponentName,BigInteger exponent,
            JavaValueAlgebraProof.Receipt algebra,ITypeBinding caller,MathCleanUpOptions options,JavaValueSourceSummary.Work work) {
        var guards=new Guards(work);guards.add(exponentName+" != null");guards.add(exponentName+".getClass() == java.math.BigInteger.class"); //$NON-NLS-1$ //$NON-NLS-2$
        guards.add(exponentName+".equals("+literal(exponent)+")"); //$NON-NLS-1$ //$NON-NLS-2$
        var session=new JavaValueSourceSummary.Session(ast,options,work);var shape=session.shape(type);var projections=new HashMap<String,List<IMethodBinding>>();
        domain(ast,shape,receiver,algebra.modulus(),null,guards,projections,work);
        if(algebra.staticValues().size()>64)throw JavaValueSourceSummary.unavailable("VALUE_POWER_CONSTANT_BOUND"); //$NON-NLS-1$
        for(var constant:algebra.staticValues()) {
            var declaring=constant.field().getDeclaringClass();int modifiers=constant.field().getModifiers();
            guards.bind(constant.field());guards.bind(declaring);
            boolean samePackage=declaring.getPackage()!=null && caller.getPackage()!=null && declaring.getPackage().getName().equals(caller.getPackage().getName());
            if(!Modifier.isPublic(modifiers) && !declaring.isEqualTo(caller) && (Modifier.isPrivate(modifiers) || !samePackage))throw JavaValueSourceSummary.unavailable("VALUE_POWER_PRIVATE_CONSTANT"); //$NON-NLS-1$
            String path=constant.path();
            if(constant.value() instanceof JavaValueSourceSummary.Scalar scalar) {
                BigInteger expected=constant(scalar.expression(),work);exact(path,expected,guards);
            } else {
                var value=(JavaValueSourceSummary.Value)constant.value();var summary=session.summary(value);
                var values=new ArrayList<BigInteger>();for(var output:summary.plan().outputs())values.add(constant(output.expression(),work));
                domain(ast,value.shape(),path,null,values.iterator(),guards,projections,work);
            }
        }
        return guards;
    }
    private static final class Guards extends AbstractSet<String> {
        private final Set<String> expressions=new LinkedHashSet<>();
        private final Set<String> bindings=new LinkedHashSet<>();
        private final JavaValueSourceSummary.Work work;private int length;
        Guards(JavaValueSourceSummary.Work work){this.work=work;}
        @Override public Iterator<String> iterator(){return expressions.iterator();}
        @Override public int size(){return expressions.size();}
        @Override public boolean add(String expression) {
            work.charge(expression.length());if(expression.length()>200_000-length)throw JavaValueSourceSummary.unavailable("VALUE_POWER_GUARD_BOUND"); //$NON-NLS-1$
            boolean added=expressions.add(expression);if(added)length+=expression.length();return added;
        }
        void bind(IBinding binding) {String key=bindingKey(binding);work.charge(key.length()+1L);bindings.add(key);}
        void jdk(ITypeBinding scalar,ITypeBinding object) {
            if(!JavaValueSourceSummary.big(scalar) || scalar.isNested() || scalar.isFromSource() || object==null
                || !"java.lang.Object".equals(object.getQualifiedName()))throw JavaValueSourceSummary.unavailable("VALUE_POWER_GUARD_JDK"); //$NON-NLS-1$ //$NON-NLS-2$
            var signatures=new HashSet<>(Set.of("<init>(java.lang.String)","equals(java.lang.Object)","signum()","bitLength()","compareTo(java.math.BigInteger)","getClass()")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
            for(var type:List.of(scalar,object)) {
                bind(type);
                for(var method:type.getDeclaredMethods()) {
                    work.charge(1L+method.getParameterTypes().length);
                    String signature=(method.isConstructor()?"<init>":method.getName())+"("+String.join(",",Arrays.stream(method.getParameterTypes()).map(ITypeBinding::getQualifiedName).toList())+")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    if(!Modifier.isStatic(method.getModifiers()) && signatures.remove(signature))bind(method);
                }
            }
            if(!signatures.isEmpty())throw JavaValueSourceSummary.unavailable("VALUE_POWER_GUARD_JDK"); //$NON-NLS-1$
        }
    }
    private static void exact(String path,BigInteger expected,Set<String> guards) {
        guards.add(path+" != null");guards.add(path+".getClass() == java.math.BigInteger.class");guards.add(path+".equals("+literal(expected)+")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }
    private static String literal(BigInteger value){return "new java.math.BigInteger(\""+value+"\")";} //$NON-NLS-1$ //$NON-NLS-2$
    private static BigInteger constant(Expr expression,JavaValueSourceSummary.Work work) {return JavaFixedPowerConstant.scalar(expression,work);}
    private static void domain(CompilationUnit ast,JavaValueSourceSummary.Shape shape,String path,BigInteger modulus,Iterator<BigInteger> exact,
            Guards guards,Map<String,List<IMethodBinding>> projections,JavaValueSourceSummary.Work work) {
        work.charge(1);if(path.length()>4096)throw JavaValueSourceSummary.unavailable("VALUE_POWER_PROJECTION_BOUND"); //$NON-NLS-1$
        guards.add(path+" != null");String key=shape.type().getKey();var getters=projections.get(key); //$NON-NLS-1$
        if(getters==null) {
            getters=new ArrayList<>();
            for(var component:shape.components()) {
                IMethodBinding found=null;
                for(var method:shape.type().getDeclaredMethods()) {
                    work.charge(1);if(!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers()) || Modifier.isSynchronized(method.getModifiers())
                        || method.isGenericMethod() || method.isVarargs() || method.getExceptionTypes().length!=0
                        || method.getParameterTypes().length!=0 || !method.getReturnType().isEqualTo(component.field().getType()))continue;
                    var definition=JavaSourceContext.definition(ast,method);
                    if(definition!=null && definition.declaration() instanceof MethodDeclaration source && source.getBody()!=null && source.resolveBinding()!=null && source.getModifiers()==method.getModifiers() && source.thrownExceptionTypes().isEmpty() && source.typeParameters().isEmpty() && source.getBody().statements().size()==1
                        && source.getBody().statements().getFirst() instanceof ReturnStatement returned && component.field().isEqualTo(JavaValueSourceSummary.variable(returned.getExpression()))
                        && (returned.getExpression() instanceof SimpleName || returned.getExpression() instanceof FieldAccess field && field.getExpression() instanceof ThisExpression self && self.getQualifier()==null))found=method;
                }
                if(found==null)throw JavaValueSourceSummary.unavailable("VALUE_POWER_ACCESSIBLE_GETTER");getters.add(found);guards.bind(found); //$NON-NLS-1$
            }
            projections.put(key,getters);
        }
        for(int i=0;i<shape.components().size();i++) {
            var component=shape.components().get(i);String child=path+"."+getters.get(i).getName()+"()"; //$NON-NLS-1$ //$NON-NLS-2$
            if(component.shape()!=null)domain(ast,component.shape(),child,modulus,exact,guards,projections,work);
            else if(exact!=null)exact(child,exact.next(),guards);
            else {guards.add(child+" != null");guards.add(child+".getClass() == java.math.BigInteger.class");guards.add(child+".signum() >= 0");guards.add(child+".bitLength() <= "+modulus.bitLength());guards.add(child+".compareTo("+literal(modulus)+") < 0");} //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        }
    }
    private static void verifyPlan(FixedPowerPlan plan,BigInteger exponent,JavaValueSourceSummary.Work work,MathCleanUpOptions options) {
        long allowance=Math.min(40_000,work.remaining()/8);if(allowance<1000)throw JavaValueSourceSummary.unavailable("VALUE_POWER_WORK_LIMIT"); //$NON-NLS-1$
        var result=new FixedPowerVerifier().verify(exponent,plan,true,work.allocate(allowance,options),work.token());work.check();if(!result.verified())throw JavaValueSourceSummary.unavailable(result.reason());
    }
    private static Map<String,String> localTypes(Set<ASTNode> declarations,String source,TypeDeclaration owner,JavaValueSourceSummary.Work work) {
        var hashes=new LinkedHashMap<String,String>();
        for(ASTNode declaration:declarations) {
            work.charge(1);
            while(declaration!=null && !(declaration instanceof TypeDeclaration)){work.charge(1);declaration=declaration.getParent();}
            if(declaration==null)throw JavaValueSourceSummary.unavailable("VALUE_POWER_LOCAL_SOURCE_UNSUPPORTED"); //$NON-NLS-1$
            TypeDeclaration type=(TypeDeclaration)declaration;
            String key=type.resolveBinding().getKey();
            // The replacement hash already binds this owner; nested dependencies keep their own declaration.
            if(type!=owner && !hashes.containsKey(key)) {
                work.charge(type.getLength());hashes.put(key,JavaComputationTask.hash(source.substring(type.getStartPosition(),type.getStartPosition()+type.getLength())));
            }
        }
        return hashes;
    }
    static void verifyCombined(CompilationUnit ast,String source,MathematicalAnalysis.VerifiedRegion region,MathCleanUpOptions options,JavaValueSourceSummary.Work work) {
        var evidence=region.fixedPower();work.check();evidence.requireCost(region.cost(),options.goal());
        ASTNode node=ast.findDeclaringNode(evidence.ownerBinding());if(!(node instanceof TypeDeclaration owner)
            || !JavaComputationTask.hash(source.substring(owner.getStartPosition(),owner.getStartPosition()+owner.getLength())).equals(evidence.replacementHash()))throw JavaValueSourceSummary.unavailable("VALUE_POWER_ENVELOPE_CHANGED"); //$NON-NLS-1$
        for(var dependency:evidence.localTypeHashes().entrySet()) {
            ASTNode local=ast.findDeclaringNode(dependency.getKey());if(!(local instanceof TypeDeclaration))throw JavaValueSourceSummary.unavailable("VALUE_POWER_LOCAL_SOURCE_CHANGED"); //$NON-NLS-1$
            work.charge(local.getLength());if(!dependency.getValue().equals(JavaComputationTask.hash(source.substring(local.getStartPosition(),local.getStartPosition()+local.getLength()))))throw JavaValueSourceSummary.unavailable("VALUE_POWER_LOCAL_SOURCE_CHANGED"); //$NON-NLS-1$
        }
        for(var chain:evidence.chains()) {
            MethodDeclaration helper=Arrays.stream(owner.getMethods()).filter(method->method.getName().getIdentifier().equals(chain.helper())).findFirst().orElseThrow(()->JavaValueSourceSummary.unavailable("VALUE_POWER_HELPER_MISSING")); //$NON-NLS-1$
            replayCall(owner,helper,source,chain,work);
            var replay=replay(helper,chain,work);verifyPlan(replay,chain.exponent(),work,options);
        }
    }
    private static List<FixedPowerEvidence.SiteStep> sourcePath(ASTNode node,TypeDeclaration owner,JavaValueSourceSummary.Work work) {
        var path=new ArrayList<FixedPowerEvidence.SiteStep>();
        while(node!=owner) {
            work.charge(1);if(path.size()>=64 || node.getParent()==null)throw JavaValueSourceSummary.unavailable("VALUE_POWER_CALL_LOCATION"); //$NON-NLS-1$
            var property=node.getLocationInParent();int index=-1;
            if(property.isChildListProperty()) {var siblings=(List<?>)node.getParent().getStructuralProperty(property);work.charge(siblings.size());index=siblings.indexOf(node);}
            path.add(new FixedPowerEvidence.SiteStep(property.getId(),index));node=node.getParent();
        }
        Collections.reverse(path);return List.copyOf(path);
    }
    private static void replayCall(TypeDeclaration owner,MethodDeclaration helper,String source,FixedPowerEvidence.Chain expected,JavaValueSourceSummary.Work work) {
        ASTNode node=owner;
        for(var step:expected.sourcePath()) {
            work.charge(1);StructuralPropertyDescriptor property=null;
            for(Object item:node.structuralPropertiesForType()) {
                work.charge(1);var candidate=(StructuralPropertyDescriptor)item;
                if(candidate.getId().equals(step.property())) {property=candidate;break;}
            }
            if(property==null)throw JavaValueSourceSummary.unavailable("VALUE_POWER_CALL_LOCATION"); //$NON-NLS-1$
            Object child=node.getStructuralProperty(property);
            if(step.index()>=0) {
                if(!(child instanceof List<?> children) || step.index()>=children.size())throw JavaValueSourceSummary.unavailable("VALUE_POWER_CALL_LOCATION"); //$NON-NLS-1$
                child=children.get(step.index());
            }
            if(!(child instanceof ASTNode next))throw JavaValueSourceSummary.unavailable("VALUE_POWER_CALL_LOCATION");node=next; //$NON-NLS-1$
        }
        if(!(node instanceof ParenthesizedExpression parenthesized) || !(parenthesized.getExpression() instanceof ConditionalExpression guarded)
            || !text(guarded.getExpression(),source,work).equals(expected.guard())
            || !text(guarded.getElseExpression(),source,work).equals(expected.fallback())
            || !(guarded.getThenExpression() instanceof MethodInvocation invocation) || invocation.getExpression()!=null
            || invocation.arguments().size()!=1 || !variable((Expression)invocation.arguments().getFirst(),expected.receiverBinding())
            || helper.resolveBinding()==null || invocation.resolveMethodBinding()==null
            || !helper.resolveBinding().isEqualTo(invocation.resolveMethodBinding())
            || !(guarded.getElseExpression() instanceof MethodInvocation fallback) || fallback.resolveMethodBinding()==null
            || !fallback.resolveMethodBinding().getMethodDeclaration().getKey().equals(expected.powerBinding())
            || !variable(fallback.getExpression(),expected.receiverBinding()) || fallback.arguments().size()!=1
            || !variable((Expression)fallback.arguments().getFirst(),expected.exponentBinding()))
            throw JavaValueSourceSummary.unavailable("VALUE_POWER_CALL_REPLAY"); //$NON-NLS-1$
        replayGuard(guarded.getExpression(),expected.guardBindings(),work);
        int[] references={0};String helperKey=helper.resolveBinding().getMethodDeclaration().getKey();
        owner.accept(new ASTVisitor(){
            @Override public void preVisit(ASTNode current){work.charge(1);}
            @Override public boolean visit(SimpleName name){
                if(name.resolveBinding() instanceof IMethodBinding method && method.getMethodDeclaration().getKey().equals(helperKey))references[0]++;
                return true;
            }
        });
        // The helper declaration and this one guarded call are its only permitted references.
        if(references[0]!=2)throw JavaValueSourceSummary.unavailable("VALUE_POWER_CALL_COUNT"); //$NON-NLS-1$
    }
    private static String bindingKey(IBinding binding) {
        if(binding==null || binding.isRecovered())throw JavaValueSourceSummary.unavailable("VALUE_POWER_GUARD_BINDING"); //$NON-NLS-1$
        return (binding instanceof IVariableBinding variable?variable.getVariableDeclaration():binding instanceof IMethodBinding method?method.getMethodDeclaration():
            binding instanceof ITypeBinding type?type.getTypeDeclaration():binding).getKey();
    }
    private static void replayGuard(Expression guard,List<String> bindings,JavaValueSourceSummary.Work work) {
        work.charge(bindings.size());var allowed=new HashSet<>(bindings);
        guard.accept(new ASTVisitor(){
            private void require(IBinding binding) {
                String key=bindingKey(binding);work.charge(key.length()+1L);
                if(!allowed.contains(key))throw JavaValueSourceSummary.unavailable("VALUE_POWER_GUARD_BINDING"); //$NON-NLS-1$
            }
            @Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(SimpleName name) {
                var binding=name.resolveBinding();
                if(binding instanceof IVariableBinding || binding instanceof IMethodBinding || binding instanceof ITypeBinding)require(binding);
                return true;
            }
            @Override public boolean visit(QualifiedName name) {
                if(name.resolveBinding() instanceof IVariableBinding field && (!Modifier.isStatic(field.getModifiers())
                    || !(name.getQualifier().resolveBinding() instanceof ITypeBinding type) || !type.isEqualTo(field.getDeclaringClass())))
                    throw JavaValueSourceSummary.unavailable("VALUE_POWER_GUARD_BINDING"); //$NON-NLS-1$
                return true;
            }
            @Override public boolean visit(FieldAccess field){throw JavaValueSourceSummary.unavailable("VALUE_POWER_GUARD_BINDING");} //$NON-NLS-1$
            @Override public boolean visit(TypeLiteral literal){require(literal.getType().resolveBinding());return true;}
            @Override public boolean visit(ClassInstanceCreation creation){require(creation.resolveConstructorBinding());return true;}
            @Override public boolean visit(MethodInvocation call){require(call.resolveMethodBinding());return true;}
        });
    }
    private static boolean variable(Expression expression,String expected) {
        return expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding binding
            && binding.getVariableDeclaration().getKey().equals(expected);
    }
    private static String text(ASTNode node,String source,JavaValueSourceSummary.Work work) {
        work.charge(node.getLength());return source.substring(node.getStartPosition(),node.getStartPosition()+node.getLength());
    }
    private static FixedPowerPlan replay(MethodDeclaration helper,FixedPowerEvidence.Chain expected,JavaValueSourceSummary.Work work) {
        if(helper.getBody()==null || helper.parameters().size()!=1 || !Modifier.isPrivate(helper.getModifiers()) || !Modifier.isStatic(helper.getModifiers()))throw JavaValueSourceSummary.unavailable("VALUE_POWER_HELPER_SHAPE"); //$NON-NLS-1$
        var nodes=new HashMap<String,Integer>();nodes.put(((SingleVariableDeclaration)helper.parameters().getFirst()).resolveBinding().getKey(),1);var steps=new ArrayList<FixedPowerPlan.Multiplication>();int result=-1;
        for(Object statement:helper.getBody().statements()) {
            work.charge(1);
            if(statement instanceof VariableDeclarationStatement local && local.fragments().size()==1 && local.fragments().getFirst() instanceof VariableDeclarationFragment variable
                && variable.getInitializer() instanceof MethodInvocation call && call.resolveMethodBinding()!=null) {
                int left=index(call.getExpression(),nodes);int right;
                if(call.resolveMethodBinding().getKey().equals(expected.squareBinding()) && call.arguments().isEmpty())right=left;
                else if(call.resolveMethodBinding().getKey().equals(expected.multiplicationBinding()) && call.arguments().size()==1)right=index((Expression)call.arguments().getFirst(),nodes);
                else throw JavaValueSourceSummary.unavailable("VALUE_POWER_EMITTED_OPERATION"); //$NON-NLS-1$
                steps.add(new FixedPowerPlan.Multiplication(left,right));nodes.put(variable.resolveBinding().getKey(),steps.size()+1);
            } else if(statement instanceof ReturnStatement returned && statement==helper.getBody().statements().getLast())result=index(returned.getExpression(),nodes);
            else throw JavaValueSourceSummary.unavailable("VALUE_POWER_EMITTED_STATEMENT"); //$NON-NLS-1$
        }
        var plan=new FixedPowerPlan(expected.exponent(),steps,result);if(!plan.equals(expected.plan()))throw JavaValueSourceSummary.unavailable("VALUE_POWER_EMITTED_PLAN_DIFFERS");return plan; //$NON-NLS-1$
    }
    private static int index(Expression expression,Map<String,Integer> nodes){var binding=JavaValueSourceSummary.variable(expression);if(!(expression instanceof SimpleName) || binding==null || !nodes.containsKey(binding.getKey()))throw JavaValueSourceSummary.unavailable("VALUE_POWER_NODE");return nodes.get(binding.getKey());} //$NON-NLS-1$
    private static boolean overlaps(int a,int n,int b,int m){return a<(long)b+m && b<(long)a+n;}
}

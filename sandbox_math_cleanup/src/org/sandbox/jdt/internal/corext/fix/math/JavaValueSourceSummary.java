/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.*;
import java.util.function.LongSupplier;
import de.regelsuche.ast.Expr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.sdk.optimization.*;
import de.regelsuche.search.program.JointComputationPlan;
import org.eclipse.jdt.core.dom.*;

/** Bounded source interpretation of immutable numeric containers; no field or inverse laws are assumed. */
final class JavaValueSourceSummary {
    private JavaValueSourceSummary() { }
    sealed interface Term permits Scalar, Value { }
    record Scalar(Expr expression) implements Term { }
    record Value(Shape shape, List<Term> components) implements Term {
        Value { components = List.copyOf(components); }
    }
    record Component(IVariableBinding field, int parameter, Shape shape) { }
    record Shape(ITypeBinding type, MethodDeclaration constructor, List<Component> components) {
        Shape { components = List.copyOf(components); }
        int leaves() { return components.stream().mapToInt(component->component.shape()==null?1:component.shape().leaves()).sum(); }
        int depth() { return 1+components.stream().mapToInt(component->component.shape()==null?0:component.shape().depth()).max().orElse(0); }
    }
    record Input(String id, String path) { }
    /** Metadata is a conditional value fact, never evidence that initialization has completed. */
    record StaticValue(IVariableBinding field, String path, Term value, String provenance) { }
    record Summary(Value value, JointComputationPlan plan, SourceEvaluationTrace trace,
            Set<SemanticAssumption> assumptions, List<Input> inputs, List<StaticValue> staticValues, String sourceFingerprint) {
        Summary { assumptions=Set.copyOf(assumptions); inputs=List.copyOf(inputs); staticValues=List.copyOf(staticValues); }
        OptimizationRequest request(MathCleanUpOptions options, OptimizationBudget budget) {
            return new OptimizationRequest(plan,trace,Set.of(NumericKind.BIG_INTEGER),ComputationOptimizer.SEMANTICS_REVISION,
                assumptions,SafetyProfile.PRESERVE_JAVA,options.goal(),budget,CheckedPolicy.NONE);
        }
    }
    /** One deadline/token survives source discovery, all methods, proof preparation, replay and explanation. */
    static final class Work implements CancellationToken {
        private final long maximum, deadline;
        private final LongSupplier clock;
        private final CancellationToken parent;
        long used;
        Work(long maximum, MathCleanUpOptions options, CancellationToken parent) {
            this(maximum, options.timeoutSeconds()*1_000_000_000L, parent, System::nanoTime);
        }
        Work(long maximum, long duration, CancellationToken parent, LongSupplier clock) {
            this.maximum=maximum;this.parent=parent;this.clock=clock;this.deadline=clock.getAsLong()+duration;
        }
        CancellationToken token() { return this; }
        @Override public boolean isCancelled() { return parent.isCancelled() || Thread.currentThread().isInterrupted() || clock.getAsLong()-deadline>=0; }
        void check() { if(isCancelled()) throw new DeadlineExceeded(); }
        void charge(long amount) {
            check();if(amount<0 || amount>maximum-used)throw unavailable("VALUE_WORK_LIMIT");used+=amount; //$NON-NLS-1$
        }
        long remaining() { check();return maximum-used; }
        OptimizationBudget allocate(long amount, MathCleanUpOptions options) {
            // Reserve the complete checker allocation, including preparation and unsuccessful attempts.
            charge(amount);return budget(amount,options);
        }
        OptimizationBudget budget(long amount, MathCleanUpOptions options) {
            return remainingTime(options.optimizationBudget(amount));
        }
        OptimizationBudget remainingTime(OptimizationBudget original) {
            check();
            return new OptimizationBudget(original.maximumWork(),original.maximumStates(),original.maximumCandidates(),
                Math.min(original.timeoutMillis(),Math.max(1,(deadline-clock.getAsLong())/1_000_000L)));
        }
    }
    static final class DeadlineExceeded extends IllegalArgumentException {
        private static final long serialVersionUID=1L;
        DeadlineExceeded() {super("VALUE_DEADLINE");} //$NON-NLS-1$
    }
    static final class Session {
        private final CompilationUnit request;
        private final Work work;
        private final JavaComputationExtractor.ScalarProjector scalar;
        private final Map<String,Shape> shapes=new LinkedHashMap<>();
        private final Map<String,String> definitions=new TreeMap<>();
        private final Map<CompilationUnit,String> unitDigests=new IdentityHashMap<>();
        private final Set<String> active=new HashSet<>();
        private final Set<String> activeShapes=new HashSet<>(), activeConstants=new HashSet<>();
        private final List<Input> inputs=new ArrayList<>();
        private final Map<String,StaticValue> statics=new LinkedHashMap<>();
        private final Set<ITypeBinding> invokedOwners=new HashSet<>();
        private int expressionDepth;
        Session(CompilationUnit request, MathCleanUpOptions options, Work work) {
            this.request=request;this.work=work;this.scalar=new JavaComputationExtractor.ScalarProjector(request,options);
            JavaSourceContext.valueWork(request,work);
        }
        Shape shape(ITypeBinding type) { return shape(type,0); }
        private Shape shape(ITypeBinding type,int depth) {
            work.charge(1);
            if(depth>=8 || type==null || type.isRecovered() || !type.isClass() || type.isGenericType() || type.isParameterizedType()
                || type.isNested() || !Modifier.isFinal(type.getModifiers()))throw unavailable("VALUE_SHAPE"); //$NON-NLS-1$
            String key=type.getTypeDeclaration().getKey();if(shapes.containsKey(key)) {
                Shape known=shapes.get(key);if(depth+known.depth()>8)throw unavailable("VALUE_DEPTH_LIMIT");return known; //$NON-NLS-1$
            }
            if(!activeShapes.add(key))throw unavailable("VALUE_RECURSIVE_SHAPE"); //$NON-NLS-1$
            try {
            var definition=definition(type.getTypeDeclaration());
            if(!(definition.declaration() instanceof TypeDeclaration declaration) || !Modifier.isFinal(declaration.getModifiers()) || declaration.getSuperclassType()!=null
                || !declaration.typeParameters().isEmpty())throw unavailable("VALUE_SUPERCLASS"); //$NON-NLS-1$
            var fields=new ArrayList<IVariableBinding>();
            for(var field:declaration.getFields()) {
                work.charge(1);if(Modifier.isStatic(field.getModifiers()))continue;
                if(!Modifier.isPrivate(field.getModifiers()) || !Modifier.isFinal(field.getModifiers()))throw unavailable("VALUE_MUTABLE_FIELD"); //$NON-NLS-1$
                for(Object item:field.fragments()) {
                    var fragment=(VariableDeclarationFragment)item;var binding=fragment.resolveBinding();
                    if(binding==null || fragment.getInitializer()!=null)throw unavailable("VALUE_COMPONENT"); //$NON-NLS-1$
                    fields.add(binding);
                }
            }
            if(fields.isEmpty() || fields.size()>8)throw unavailable("VALUE_COMPONENT_LIMIT"); //$NON-NLS-1$
            MethodDeclaration constructor=null;
            for(Object body:declaration.bodyDeclarations())if(body instanceof Initializer initializer && !Modifier.isStatic(initializer.getModifiers()))
                throw unavailable("VALUE_INITIALIZER"); //$NON-NLS-1$
            for(var method:declaration.getMethods()) {
                work.charge(1);
                if(Modifier.isNative(method.getModifiers()) || Modifier.isSynchronized(method.getModifiers())
                    || method.getName().getIdentifier().equals("finalize"))throw unavailable("VALUE_OBSERVABLE_LIFECYCLE"); //$NON-NLS-1$ //$NON-NLS-2$
                if(method.isConstructor()) {
                    if(constructor!=null)throw unavailable("VALUE_CONSTRUCTORS");constructor=method; //$NON-NLS-1$
                }
            }
            if(constructor==null || !Modifier.isPrivate(constructor.getModifiers()) || constructor.getBody()==null
                || constructor.parameters().size()!=fields.size() || constructor.getBody().statements().size()!=fields.size())
                throw unavailable("VALUE_CONSTRUCTOR"); //$NON-NLS-1$
            var assignments=new HashMap<String,Integer>();var used=new HashSet<Integer>();
            for(Object item:constructor.getBody().statements()) {
                work.charge(1);
                if(!(item instanceof ExpressionStatement statement) || !(statement.getExpression() instanceof Assignment assignment)
                    || assignment.getOperator()!=Assignment.Operator.ASSIGN || !(assignment.getLeftHandSide() instanceof FieldAccess access)
                    || !(access.getExpression() instanceof ThisExpression receiver) || receiver.getQualifier()!=null
                    || !(assignment.getRightHandSide() instanceof SimpleName name) || !(name.resolveBinding() instanceof IVariableBinding parameter))
                    throw unavailable("VALUE_CONSTRUCTOR_EFFECT"); //$NON-NLS-1$
                var field=access.resolveFieldBinding();int index=-1;
                for(int i=0;i<constructor.parameters().size();i++)if(parameter.isEqualTo(((SingleVariableDeclaration)constructor.parameters().get(i)).resolveBinding()))index=i;
                if(field==null || fields.stream().noneMatch(f->f.isEqualTo(field)) || index<0 || !used.add(index)
                    || !field.getType().isEqualTo(parameter.getType()) || assignments.put(field.getKey(),index)!=null)
                    throw unavailable("VALUE_CONSTRUCTOR_MAPPING"); //$NON-NLS-1$
            }
            var components=new ArrayList<Component>();for(var field:fields)
                components.add(new Component(field,assignments.get(field.getKey()),big(field.getType())?null:shape(field.getType(),depth+1)));
            var shape=new Shape(type,constructor,components);
            if(shape.leaves()>32)throw unavailable("VALUE_LEAF_LIMIT"); //$NON-NLS-1$
            shapes.put(key,shape);return shape;
            } finally {activeShapes.remove(key);}
        }
        Value symbolic(Shape shape,String path) {
            var components=new ArrayList<Term>();
            for(var component:shape.components()) {
                String child=path+"."+component.field().getName(); //$NON-NLS-1$
                components.add(component.shape()==null?input(child):symbolic(component.shape(),child));
            }
            return new Value(shape,components);
        }
        Scalar input(String path) {
            work.charge(1);if(inputs.size()>=64)throw unavailable("VALUE_INPUT_LIMIT"); //$NON-NLS-1$
            String id="valueInput"+inputs.size();inputs.add(new Input(id,path));return new Scalar(new VariableExpr(id)); //$NON-NLS-1$
        }
        Term invoke(IMethodBinding binding, Value receiver, List<Term> arguments) {
            work.charge(1);
            if(binding==null || binding.isRecovered() || binding.isConstructor() || binding.isGenericMethod() || binding.isParameterizedMethod()
                || binding.isVarargs() || Modifier.isNative(binding.getModifiers()) || Modifier.isSynchronized(binding.getModifiers()))
                throw unavailable("VALUE_METHOD"); //$NON-NLS-1$
            if(!Modifier.isStatic(binding.getModifiers()) && (receiver==null || !receiver.shape().type().isEqualTo(binding.getDeclaringClass())))throw unavailable("VALUE_RECEIVER"); //$NON-NLS-1$
            invokedOwners.add(binding.getDeclaringClass());
            var definition=definition(binding.getMethodDeclaration());
            if(!(definition.declaration() instanceof MethodDeclaration method) || method.getBody()==null
                || method.resolveBinding()==null || method.getModifiers()!=binding.getModifiers()
                || method.parameters().size()!=arguments.size() || method.getBody().statements().size()>64)
                throw unavailable("VALUE_METHOD_SOURCE"); //$NON-NLS-1$
            String key=binding.getMethodDeclaration().getKey();
            if(!active.add(key) || active.size()>32)throw unavailable("VALUE_RECURSION"); //$NON-NLS-1$
            try {
                noEffects(method.getBody());var frame=new HashMap<String,Term>();
                for(int i=0;i<arguments.size();i++)frame.put(((SingleVariableDeclaration)method.parameters().get(i)).resolveBinding().getKey(),arguments.get(i));
                return body(method.getBody(),receiver,frame);
            } finally {active.remove(key);}
        }
        boolean onlyOwner(ITypeBinding owner) { return invokedOwners.stream().allMatch(type->type.isEqualTo(owner)); }
        /** Frame entries are exact, caller-established bindings; "this" optionally supplies the receiver. */
        Term project(Expression expression, Map<String,Term> frame) {
            noEffects(expression);
            Term self=frame.get("this"); //$NON-NLS-1$
            return evaluate(expression,self instanceof Value value?value:null,frame);
        }
        Summary method(MethodDeclaration method, Block body) {
            Shape shape=shape(method.resolveBinding().getDeclaringClass());Value receiver=symbolic(shape,"this"); //$NON-NLS-1$
            var arguments=new ArrayList<Term>();var frame=new HashMap<String,Term>();
            for(Object item:method.parameters()) {
                var parameter=(SingleVariableDeclaration)item;var binding=parameter.resolveBinding();
                Term term=big(binding.getType())?input(binding.getName()):symbolic(shape(binding.getType()),binding.getName());
                arguments.add(term);frame.put(binding.getKey(),term);
            }
            Term result;
            if(body==method.getBody())result=invoke(method.resolveBinding(),receiver,arguments);
            else {noEffects(body);result=body(body,receiver,frame);}
            if(!(result instanceof Value value))throw unavailable("VALUE_RETURN_TYPE"); //$NON-NLS-1$
            return summary(value);
        }
        private Term body(Block body,Value receiver,Map<String,Term> frame) {
            for(Object item:body.statements()) {
                work.charge(1);
                if(item instanceof VariableDeclarationStatement statement) {
                    for(Object part:statement.fragments()) {
                        var fragment=(VariableDeclarationFragment)part;
                        if(fragment.resolveBinding()==null || fragment.getInitializer()==null)throw unavailable("VALUE_LOCAL"); //$NON-NLS-1$
                        frame.put(fragment.resolveBinding().getKey(),evaluate(fragment.getInitializer(),receiver,frame));
                    }
                } else if(item instanceof ReturnStatement returned && item==body.statements().getLast())return evaluate(returned.getExpression(),receiver,frame);
                else throw unavailable("VALUE_CONTROL_FLOW"); //$NON-NLS-1$
            }
            throw unavailable("VALUE_RETURN"); //$NON-NLS-1$
        }
        private Term evaluate(Expression expression, Value receiver, Map<String,Term> frame) {
            work.charge(1);if(expression==null)throw unavailable("VALUE_EXPRESSION"); //$NON-NLS-1$
            if(++expressionDepth>64) {expressionDepth--;throw unavailable("VALUE_EXPRESSION_DEPTH");} //$NON-NLS-1$
            try {return evaluateWithin(expression,receiver,frame);}
            finally {expressionDepth--;}
        }
        private Term evaluateWithin(Expression expression, Value receiver, Map<String,Term> frame) {
            if(expression instanceof ParenthesizedExpression parentheses)return evaluate(parentheses.getExpression(),receiver,frame);
            if(expression instanceof CastExpression cast && big(cast.resolveTypeBinding()) && big(cast.getExpression().resolveTypeBinding()))
                return evaluate(cast.getExpression(),receiver,frame);
            if(expression instanceof NumberLiteral || expression instanceof PrefixExpression prefix && prefix.getOperand() instanceof NumberLiteral)
                return new Scalar(scalar.literal(expression));
            if(expression instanceof ThisExpression self && self.getQualifier()==null && receiver!=null)return receiver;
            IVariableBinding binding=variable(expression);
            if(binding!=null) {
                Term supplied=frame.get(binding.getKey());
                if(supplied!=null && (!binding.isField() || expression instanceof SimpleName
                    || expression instanceof FieldAccess access && access.getExpression() instanceof ThisExpression self && self.getQualifier()==null))return supplied;
                if(!binding.isField()) {
                    Term value=frame.get(binding.getKey());if(value==null)throw unavailable("VALUE_UNBOUND_LOCAL");return value; //$NON-NLS-1$
                }
                if(Modifier.isStatic(binding.getModifiers())) {
                    if(expression instanceof QualifiedName name && !(name.getQualifier().resolveBinding() instanceof ITypeBinding))throw unavailable("VALUE_STATIC_QUALIFIER"); //$NON-NLS-1$
                    if(expression instanceof FieldAccess)throw unavailable("VALUE_STATIC_QUALIFIER"); //$NON-NLS-1$
                    if(big(binding.getDeclaringClass()))return new Scalar(scalar.expression(expression,Map.of()));
                    return constant(binding);
                }
                Term base=expression instanceof QualifiedName name?evaluate(name.getQualifier(),receiver,frame)
                    : expression instanceof FieldAccess access?evaluate(access.getExpression(),receiver,frame):receiver;
                if(!(base instanceof Value value))throw unavailable("VALUE_FIELD_RECEIVER"); //$NON-NLS-1$
                for(int i=0;i<value.shape().components().size();i++)if(value.shape().components().get(i).field().isEqualTo(binding))return value.components().get(i);
                throw unavailable("VALUE_FIELD"); //$NON-NLS-1$
            }
            if(expression instanceof ClassInstanceCreation creation && !big(creation.resolveTypeBinding())) {
                if(creation.getAnonymousClassDeclaration()!=null || creation.getExpression()!=null)throw unavailable("VALUE_CREATION"); //$NON-NLS-1$
                Shape shape=shape(creation.resolveTypeBinding());
                if(creation.resolveConstructorBinding()==null || !creation.resolveConstructorBinding().getMethodDeclaration().isEqualTo(shape.constructor().resolveBinding()))throw unavailable("VALUE_CONSTRUCTOR_BINDING"); //$NON-NLS-1$
                var arguments=new ArrayList<Term>();for(Object item:creation.arguments())arguments.add(evaluate((Expression)item,receiver,frame));
                return new Value(shape,shape.components().stream().map(component->arguments.get(component.parameter())).toList());
            }
            if(expression instanceof MethodInvocation call) {
                var target=call.resolveMethodBinding();if(target==null || target.isRecovered())throw unavailable("VALUE_CALL"); //$NON-NLS-1$
                if(!big(target.getDeclaringClass())) {
                    boolean isStatic=Modifier.isStatic(target.getModifiers());
                    if(isStatic && call.getExpression()!=null && !(call.getExpression() instanceof Name name && name.resolveBinding() instanceof ITypeBinding))throw unavailable("VALUE_STATIC_CALL"); //$NON-NLS-1$
                    Term object=isStatic?null:call.getExpression()==null?receiver:evaluate(call.getExpression(),receiver,frame);
                    if(!isStatic && !(object instanceof Value))throw unavailable("VALUE_CALL_RECEIVER"); //$NON-NLS-1$
                    var args=new ArrayList<Term>();for(Object item:call.arguments())args.add(evaluate((Expression)item,receiver,frame));
                    return invoke(target,(Value)object,args);
                }
                var substitutions=new IdentityHashMap<Expression,Expr>();
                if(!Modifier.isStatic(target.getModifiers()))substitutions.put(call.getExpression(),scalar(evaluate(call.getExpression(),receiver,frame)));
                for(Object item:call.arguments()) {
                    Expression argument=(Expression)item;
                    if(big(argument.resolveTypeBinding()) || argument instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding parameterBinding && frame.containsKey(parameterBinding.getKey()))
                        substitutions.put(argument,scalar(evaluate(argument,receiver,frame)));
                }
                return new Scalar(scalar.expression(expression,substitutions));
            }
            return new Scalar(scalar.expression(expression,Map.of()));
        }
        Term constant(IVariableBinding binding) {
            work.charge(1);String key=binding.getVariableDeclaration().getKey();var existing=statics.get(key);if(existing!=null)return existing.value();
            if(!Modifier.isStatic(binding.getModifiers()) || !Modifier.isFinal(binding.getModifiers()) || !activeConstants.add(key)
                || activeConstants.size()>32)throw unavailable("VALUE_STATIC_CYCLE_OR_MUTATION"); //$NON-NLS-1$
            try {
                var definition=definition(binding.getVariableDeclaration());Term value;String provenance;
                if(big(binding.getType())) {
                    var fact=JavaSourceContext.constant(request,binding);if(fact==null)throw unavailable("VALUE_STATIC_SOURCE"); //$NON-NLS-1$
                    value=new Scalar(JavaExpressions.literal(fact.value()));provenance=fact.provenance();
                } else {
                    if(!(definition.declaration() instanceof VariableDeclarationFragment fragment) || fragment.getInitializer()==null
                        || fragment.resolveBinding()==null || !Modifier.isFinal(fragment.resolveBinding().getModifiers())
                        || !Modifier.isStatic(fragment.resolveBinding().getModifiers()))throw unavailable("VALUE_STATIC_INITIALIZER"); //$NON-NLS-1$
                    noEffects(fragment.getInitializer());int tracePosition=scalar.tracePosition();
                    // The initializer supplies a conditional value, not runtime operations of the summarized method.
                    try {value=evaluate(fragment.getInitializer(),null,Map.of());}
                    finally {scalar.restoreTrace(tracePosition);}
                    if(!(value instanceof Value result) || !result.shape().type().isEqualTo(binding.getType()))throw unavailable("VALUE_STATIC_TYPE"); //$NON-NLS-1$
                    provenance="source-defined initializer; conditional on completed nonnull initialization; "+JavaSourceContext.receipts(request); //$NON-NLS-1$
                }
                statics.put(key,new StaticValue(binding,binding.getDeclaringClass().getQualifiedName()+"."+binding.getName(),value,provenance));return value; //$NON-NLS-1$
            } finally {activeConstants.remove(key);}
        }
        Summary summary(Value value) {
            var types=new LinkedHashMap<String,de.regelsuche.search.program.ComputationBackend.Type>();var assumptions=new LinkedHashSet<SemanticAssumption>();
            for(var input:inputs) {
                types.put(input.id(),NumericKind.BIG_INTEGER.type());
                assumptions.add(new SemanticAssumption(SemanticAssumption.Kind.BIG_INTEGER_VALUE_SEMANTICS,input.id(),"","guarded exact BigInteger value")); //$NON-NLS-1$ //$NON-NLS-2$
                assumptions.add(new SemanticAssumption(SemanticAssumption.Kind.BIG_INTEGER_BIT_LENGTH_BOUND,input.id(),"4096","guarded bounded BigInteger value")); //$NON-NLS-1$ //$NON-NLS-2$
            }
            var outputs=new ArrayList<JointComputationPlan.Output>();
            outputs(value,outputs);
            work.charge(outputs.size()+inputs.size());
            return new Summary(value,new JointComputationPlan(types,Map.of(),outputs),scalar.trace(),assumptions,inputs,new ArrayList<>(statics.values()),
                JavaComputationTask.hash(definitions.toString()+JavaSourceContext.receipts(request)));
        }
        private void outputs(Term term,List<JointComputationPlan.Output> outputs) {
            work.charge(1);
            if(term instanceof Scalar scalar)outputs.add(new JointComputationPlan.Output("component"+outputs.size(),NumericKind.BIG_INTEGER.type(),scalar.expression())); //$NON-NLS-1$
            else for(var component:((Value)term).components())outputs(component,outputs);
        }
        private JavaSourceContext.Definition definition(IBinding binding) {
            work.charge(1);var definition=JavaSourceContext.definition(request,binding);
            if(definition==null)throw unavailable("VALUE_DEFINITION_UNAVAILABLE"); //$NON-NLS-1$
            String digest=unitDigests.get(definition.unit());
            if(digest==null) {work.charge(definition.source().length());digest=JavaComputationTask.hash(definition.source());unitDigests.put(definition.unit(),digest);}
            definitions.put(binding.getKey(),digest);return definition;
        }
        private void noEffects(ASTNode node) {
            node.accept(new ASTVisitor() {
                @Override public void preVisit(ASTNode child) {work.charge(1);}
                @Override public boolean visit(Assignment ignored) {throw unavailable("VALUE_WRITE");} //$NON-NLS-1$
                @Override public boolean visit(PostfixExpression ignored) {throw unavailable("VALUE_WRITE");} //$NON-NLS-1$
                @Override public boolean visit(PrefixExpression prefix) {
                    if(prefix.getOperator()==PrefixExpression.Operator.INCREMENT || prefix.getOperator()==PrefixExpression.Operator.DECREMENT)throw unavailable("VALUE_WRITE");return true; //$NON-NLS-1$
                }
            });
        }
    }
    static boolean big(ITypeBinding type) { return type!=null && !type.isRecovered() && type.getQualifiedName().equals("java.math.BigInteger"); } //$NON-NLS-1$
    static IVariableBinding variable(Expression expression) {
        return expression instanceof Name name && name.resolveBinding() instanceof IVariableBinding binding?binding
            :expression instanceof FieldAccess access?access.resolveFieldBinding():null;
    }
    static Expr scalar(Term term) { if(term instanceof Scalar scalar)return scalar.expression();throw unavailable("VALUE_SCALAR_REQUIRED"); } //$NON-NLS-1$
    static IllegalArgumentException unavailable(String reason) {return new IllegalArgumentException(reason);}
}

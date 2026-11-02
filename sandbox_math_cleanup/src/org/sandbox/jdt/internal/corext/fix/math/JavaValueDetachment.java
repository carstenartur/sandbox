/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.*;
import de.regelsuche.sdk.optimization.*;
import org.eclipse.jdt.core.dom.*;

/** Source/runtime-qualified identity proof for direct value trees. No consumer is speculated or repeated. */
final class JavaValueDetachment {
    private JavaValueDetachment() { }
    static JavaValueSourceSummary.Summary factory(CompilationUnit ast,JavaValueSourceSummary.Session session,
            JavaValueAccess.Access access,JavaValueSourceSummary.Summary original,JavaValueSourceSummary.Work work) {
        new JavaValueUseProof(ast,session,work).scalarOrigins(access.factory());
        var result=(JavaValueSourceSummary.Value)session.invoke(access.factory(),null,original.value().components());
        if(!session.onlyOwner(access.shape().type()))throw unavailable("VALUE_DETACH_FACTORY_INITIALIZATION");
        var summary=session.summary(result);
        for(var field:summary.staticValues()) {
            work.charge(1);
            if(!field.field().getDeclaringClass().isEqualTo(access.shape().type())
                && original.staticValues().stream().noneMatch(known->known.field().isEqualTo(field.field())))
                throw unavailable("VALUE_DETACH_FACTORY_STATIC_INITIALIZATION");
        }
        return summary;
    }
    static VerificationResult.Verified verifyFactory(JavaValueSourceSummary.Summary original,JavaValueSourceSummary.Summary factory,
            MathCleanUpOptions options,JavaValueSourceSummary.Work work) {
        return verifyFactory(original,factory,null,options,work);
    }
    static VerificationResult.Verified verifyFactory(JavaValueSourceSummary.Summary original,JavaValueSourceSummary.Summary factory,
            SourceEvaluationTrace.Occurrence successfulInverse,MathCleanUpOptions options,JavaValueSourceSummary.Work work) {
        long cap=Math.min(300_000,work.remaining()/3);if(cap<6000)throw unavailable("VALUE_DETACH_FACTORY_BUDGET");
        var budget=work.allocate(cap,options);
        var request=successfulInverse==null?original.request(options,budget):JavaValueInverseProof.conditional(original,successfulInverse,options,budget);
        var proof=new ComputationOptimizer().verify(request,factory.plan(),work.token());
        if(proof instanceof VerificationResult.BudgetExceeded exceeded && exceeded.work()>cap)work.charge(exceeded.work()-cap);
        if(!(proof instanceof VerificationResult.Verified verified))throw unavailable("VALUE_DETACH_FACTORY_NUMERIC:"+proof);return verified;
    }
    static String exactStatics(List<JavaValueSourceSummary.StaticValue> statics,JavaValueAccess.Access access,JavaValueSourceSummary.Work work) {
        var guards=new ArrayList<String>();
        for(var value:statics) {
            if(!Modifier.isPublic(value.field().getModifiers()))throw unavailable("VALUE_DETACH_STATIC_ACCESS");
            if(value.value() instanceof JavaValueSourceSummary.Scalar scalar)exact(guards,value.path(),scalar,work);
            else if(value.value() instanceof JavaValueSourceSummary.Value object && object.shape().type().isEqualTo(access.shape().type())) {
                guards.add(value.path()+" != null");for(int i=0;i<object.components().size();i++)exact(guards,access.leaf(value.path(),i),(JavaValueSourceSummary.Scalar)object.components().get(i),work);
            } else throw unavailable("VALUE_DETACH_STATIC_SHAPE");
        }
        return guards.isEmpty()?"true":String.join(" && ",guards);
    }
    private static void exact(List<String> guards,String path,JavaValueSourceSummary.Scalar scalar,JavaValueSourceSummary.Work work) {
        guards.add(path+" != null");guards.add(path+".getClass() == java.math.BigInteger.class");
        guards.add(path+".equals(new java.math.BigInteger(\""+JavaFixedPowerConstant.scalar(scalar.expression(),work)+"\"))");
    }
    static String guard(JavaValueAccess.Access access,String value,List<String> inputs,List<JavaValueSourceSummary.StaticValue> statics) {
        var origins=new ArrayList<String>();for(String input:inputs)for(int i=0;i<access.shape().leaves();i++)origins.add(access.leaf(input,i));
        for(var field:statics)if(field.value() instanceof JavaValueSourceSummary.Scalar)origins.add(field.path());
        else for(int i=0;i<access.shape().leaves();i++)origins.add(access.leaf(field.path(),i));
        var guards=new ArrayList<String>();guards.add(value+" != null");
        for(int i=0;i<access.shape().leaves();i++) {
            String leaf=access.leaf(value,i);guards.add(leaf+" != null");guards.add(leaf+".getClass() == java.math.BigInteger.class");
            guards.add(leaf+".abs().bitLength() <= 4096");guards.add(leaf+".abs().compareTo(java.math.BigInteger.valueOf(16)) > 0");
        }
        for(int i=0;i<access.shape().leaves();i++) {
            String leaf=access.leaf(value,i);for(String origin:origins)guards.add("!"+leaf+".equals("+origin+")");
            for(int j=0;j<i;j++)guards.add("!"+leaf+".equals("+access.leaf(value,j)+")");
        }
        return String.join(" && ",guards);
    }
    static String emit(JavaValueAccess.Access access,String value) {
        var arguments=new ArrayList<String>();for(int i=0;i<access.shape().leaves();i++)arguments.add("new java.math.BigInteger("+access.leaf(value,i)+".toByteArray())");
        return access.construct(arguments);
    }
    /** Independently decode legal factory, exact getters, fresh scalar constructors and their common operand binding. */
    static SimpleName decode(Expression expression,JavaValueAccess.Access access,JavaValueSourceSummary.Work work) {
        work.charge(1);
        if(!(expression instanceof MethodInvocation factory) || !same(factory.resolveMethodBinding(),access.factory()) || factory.arguments().size()!=access.shape().leaves()
            || !(factory.getExpression() instanceof Name owner) || !(owner.resolveBinding() instanceof ITypeBinding type) || !type.isEqualTo(factory.resolveMethodBinding().getDeclaringClass()))throw unavailable("VALUE_DETACH_FACTORY_BINDING");
        SimpleName result=null;
        for(int i=0;i<factory.arguments().size();i++) {
            if(!(factory.arguments().get(i) instanceof ClassInstanceCreation copy) || !JavaValueSourceSummary.big(copy.resolveTypeBinding()) || copy.getAnonymousClassDeclaration()!=null
                || copy.getExpression()!=null || copy.arguments().size()!=1 || copy.resolveConstructorBinding()==null || copy.resolveConstructorBinding().getParameterTypes().length!=1
                || !copy.resolveConstructorBinding().getParameterTypes()[0].getQualifiedName().equals("byte[]")
                || !(copy.arguments().getFirst() instanceof MethodInvocation bytes) || !bytes.arguments().isEmpty() || bytes.resolveMethodBinding()==null
                || !JavaValueSourceSummary.big(bytes.resolveMethodBinding().getDeclaringClass()) || !bytes.getName().getIdentifier().equals("toByteArray")
                || !(bytes.getExpression() instanceof MethodInvocation getter) || !same(getter.resolveMethodBinding(),access.getters().get(i)) || !getter.arguments().isEmpty()
                || !(getter.getExpression() instanceof SimpleName name) || !(name.resolveBinding() instanceof IVariableBinding))throw unavailable("VALUE_DETACH_COPY_BINDING");
            if(result!=null && !same(result.resolveBinding(),name.resolveBinding()))throw unavailable("VALUE_DETACH_OPERAND_BINDING");result=name;
        }
        return result;
    }
    static long factoryCost(CompilationUnit ast,JavaValueAccess.Access access,MathCleanUpOptions options,JavaValueSourceSummary.Work work,int bits) {
        var session=new JavaValueSourceSummary.Session(ast,options,work);var input=session.symbolic(access.shape(),"detached");
        var summary=session.summary((JavaValueSourceSummary.Value)session.invoke(access.factory(),null,input.components()));
        var assumptions=new HashSet<SemanticAssumption>();
        for(var assumption:summary.assumptions())assumptions.add(assumption.kind()==SemanticAssumption.Kind.BIG_INTEGER_BIT_LENGTH_BOUND
            ?new SemanticAssumption(assumption.kind(),assumption.subject(),Integer.toString(bits),"proved detached output bound"):assumption);
        var request=new OptimizationRequest(summary.plan(),summary.trace(),Set.of(NumericKind.BIG_INTEGER),ComputationOptimizer.SEMANTICS_REVISION,
            assumptions,SafetyProfile.PRESERVE_JAVA,options.goal(),work.budget(Math.max(1,work.remaining()),options),CheckedPolicy.NONE);
        work.charge(10_000);var prepared=ComputationOptimizer.prepare(summary.plan());
        var cost=NumericExecutionCost.estimateSource(request,prepared).orElseThrow(()->unavailable("VALUE_DETACH_FACTORY_COST"));
        work.charge(cost.inspectionWork()+prepared.cost().inspectionWork());return cost.operationWork();
    }
    static long copyCost(JavaValueAccess.Access access,int bits) {
        long bytes=(bits+7L)/8;return 2L+access.shape().leaves()*(2*bytes+8L);
    }
    static long guardCost(String guard,int bits) {
        long cost=0;for(String clause:guard.split(" && ",-1)) {
            // Null/class/sign/bit-length checks inspect headers or the leading limb; equality/comparison may scan the magnitude.
            cost+=7;if(clause.contains(".equals(") || clause.contains(".compareTo("))cost+=(bits+31L)/32;
        }
        return cost;
    }
    private static boolean same(IBinding a,IBinding b){return a!=null && b!=null && a.getKey().equals(b.getKey());}
    private static IllegalArgumentException unavailable(String reason){return JavaValueSourceSummary.unavailable(reason);}
}

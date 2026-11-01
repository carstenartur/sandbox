/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.*;
import de.regelsuche.ast.Expr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.sdk.optimization.*;
import org.eclipse.jdt.core.dom.IMethodBinding;

/** Source binding and domain admission only: Regelsuche independently establishes every arithmetic law. */
final class JavaValueAlgebraProof {
    private JavaValueAlgebraProof() { }
    record Receipt(BigInteger modulus,String sourceFingerprint,boolean totalAssociativeMultiplicationWithIdentity,boolean squareConsistent, List<JavaValueSourceSummary.StaticValue> staticValues, VerificationEvidence scalarLawProof, long multiplicationWork, long squareWork, List<Law> laws) {
        Receipt {staticValues=List.copyOf(staticValues);laws=List.copyOf(laws);}
    }
    record Law(String name,VerificationEvidence proof) { }
    static Receipt prove(JavaValueSourceSummary.Session session,JavaValueSourceSummary.Shape shape,IMethodBinding multiplication,
            IMethodBinding square,JavaValueSourceSummary.Value identity,JavaValueSourceSummary.Work work,MathCleanUpOptions options) {
        return prove(session,shape,multiplication,square,identity,work,options,work.remaining());
    }
    static Receipt prove(JavaValueSourceSummary.Session session,JavaValueSourceSummary.Shape shape,IMethodBinding multiplication,
            IMethodBinding square,JavaValueSourceSummary.Value identity,JavaValueSourceSummary.Work work,MathCleanUpOptions options,long maximumLawWork) {
        if(maximumLawWork<1)throw unavailable("VALUE_ALGEBRA_WORK_LIMIT"); //$NON-NLS-1$
        long started=work.used;
        var x=session.symbolic(shape,"algebraLeft");var y=session.symbolic(shape,"algebraRight");var z=session.symbolic(shape,"algebraThird"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        var xy=multiply(session,multiplication,x,y);var summary=session.summary(xy);
        session.requireInitializedOwners(shape);
        fresh(xy,List.of(x,y),summary.staticValues(),work);
        var totalNodes=new IdentityHashMap<Expr,Boolean>();
        for(var occurrence:summary.trace().occurrences())total(occurrence.expression(),work,totalNodes);
        long multiplicationWork=summary.trace().occurrences().size();
        BigInteger modulus=null;
        for(var output:summary.plan().outputs()) {
            work.charge(1);Expr expression=output.expression();
            if(JavaExpressions.operationOf(expression).orElse(null)!=NumericOperation.MOD)throw unavailable("VALUE_ALGEBRA_CLOSURE"); //$NON-NLS-1$
            var divisor=JavaExpressions.operands(expression).get(1);
            if(!JavaExpressions.isLiteral(divisor) || !(JavaExpressions.literalValue(divisor) instanceof BigInteger p)
                || p.signum()<=0 || p.bitLength()>4096 || modulus!=null && !modulus.equals(p))throw unavailable("VALUE_ALGEBRA_MODULUS"); //$NON-NLS-1$
            modulus=p;total(expression,work,new IdentityHashMap<>());
        }
        if(modulus==null)throw unavailable("VALUE_ALGEBRA_EMPTY");
        for(var output:session.summary(identity).plan().outputs()) {
            BigInteger component=JavaFixedPowerConstant.scalar(output.expression(),work);
            if(component.signum()<0 || component.compareTo(modulus)>=0)throw unavailable("VALUE_ALGEBRA_IDENTITY_DOMAIN");
        } //$NON-NLS-1$
        // The associative equation is proved on the original independent symbols, a stronger domain.
        // Identity needs canonical representatives; no law is manufactured for the scalar checker.
        long allocation=Math.max(1,Math.min(work.remaining(),Math.max(0,maximumLawWork-(work.used-started)))/20);
        var associativeProof=verify(session,multiply(session,multiplication,multiply(session,multiplication,x,y),z),
            multiply(session,multiplication,x,multiply(session,multiplication,y,z)),work,options,allocation*14,"ASSOCIATIVITY"); //$NON-NLS-1$
        x=canonical(x,modulus,work);
        var leftIdentityProof=verify(session,multiply(session,multiplication,identity,x),x,work,options,allocation,"LEFT_IDENTITY"); //$NON-NLS-1$
        var rightIdentityProof=verify(session,multiply(session,multiplication,x,identity),x,work,options,allocation,"RIGHT_IDENTITY"); //$NON-NLS-1$
        long beforeSquare=session.summary(x).trace().occurrences().size();
        var squared=value(session.invoke(square,x,List.of()));
        session.requireInitializedOwners(shape);
        var squareSummary=session.summary(squared);fresh(squared,List.of(x),squareSummary.staticValues(),work);long squareWork=squareSummary.trace().occurrences().size()-beforeSquare;
        for(var occurrence:squareSummary.trace().occurrences())total(occurrence.expression(),work,totalNodes);
        for(var output:session.summary(squared).plan().outputs())total(output.expression(),work,totalNodes);
        var squareProof=verify(session,squared,multiply(session,multiplication,x,x),work,options,allocation*2,"SQUARE"); //$NON-NLS-1$
        var receipt=session.summary(squared);
        return new Receipt(modulus,receipt.sourceFingerprint(),true,true,receipt.staticValues(),squareProof,multiplicationWork,squareWork,List.of(new Law("ASSOCIATIVITY",associativeProof),new Law("LEFT_IDENTITY",leftIdentityProof),new Law("RIGHT_IDENTITY",rightIdentityProof),new Law("SQUARE",squareProof)));
    }
    private static VerificationEvidence verify(JavaValueSourceSummary.Session session,JavaValueSourceSummary.Value left,JavaValueSourceSummary.Value right,
            JavaValueSourceSummary.Work work,MathCleanUpOptions options,long allowance,String law) {
        var source=session.summary(left);var target=session.summary(right);
        // This is a synthetic algebra-law expression, not an emitted/source schedule.
        // Original Java purity and totality are established separately above.
        var budget=work.allocate(allowance,options);
        var request=new OptimizationRequest(source.plan(),syntheticTrace(source.plan().outputs().stream().map(o->o.expression()).toList(),work),Set.of(NumericKind.BIG_INTEGER),
            ComputationOptimizer.SEMANTICS_REVISION,source.assumptions(),SafetyProfile.PRESERVE_JAVA,options.goal(),budget,CheckedPolicy.NONE);
        var result=new ComputationOptimizer().verify(request,target.plan(),work.token());
        work.check();if(result instanceof VerificationResult.BudgetExceeded exceeded && exceeded.work()>allowance)work.charge(exceeded.work()-allowance);
        if(!(result instanceof VerificationResult.Verified proof))throw unavailable("VALUE_ALGEBRA_"+law+":"+result);
        return proof.evidence(); //$NON-NLS-1$ //$NON-NLS-2$
    }
    private static SourceEvaluationTrace syntheticTrace(List<Expr> expressions,JavaValueSourceSummary.Work work) {
        var occurrences=new ArrayList<SourceEvaluationTrace.Occurrence>();var seen=new IdentityHashMap<Expr,Boolean>();
        for(var expression:expressions)syntheticTrace(expression,occurrences,seen,work);
        return new SourceEvaluationTrace(occurrences);
    }
    private static void syntheticTrace(Expr expression,List<SourceEvaluationTrace.Occurrence> occurrences,
            IdentityHashMap<Expr,Boolean> seen,JavaValueSourceSummary.Work work) {
        work.charge(1);if(seen.put(expression,Boolean.TRUE)!=null || expression instanceof VariableExpr || JavaExpressions.isLiteral(expression))return;
        for(var operand:JavaExpressions.operands(expression))syntheticTrace(operand,occurrences,seen,work);
        occurrences.add(new SourceEvaluationTrace.Occurrence("algebra"+occurrences.size(),expression,NumericKind.BIG_INTEGER,NumericKind.BIG_INTEGER));
    }
    private static void fresh(JavaValueSourceSummary.Value result,List<JavaValueSourceSummary.Value> inputs,
            List<JavaValueSourceSummary.StaticValue> constants,JavaValueSourceSummary.Work work) {
        var unavailable=Collections.newSetFromMap(new IdentityHashMap<JavaValueSourceSummary.Value,Boolean>());
        for(var input:inputs)collect(input,unavailable,work);
        for(var constant:constants)if(constant.value() instanceof JavaValueSourceSummary.Value value)collect(value,unavailable,work);
        fresh(result,unavailable,Collections.newSetFromMap(new IdentityHashMap<JavaValueSourceSummary.Value,Boolean>()),work);
    }
    private static void collect(JavaValueSourceSummary.Value value,Set<JavaValueSourceSummary.Value> values,JavaValueSourceSummary.Work work) {
        work.charge(1);if(!values.add(value))return;
        for(var component:value.components())if(component instanceof JavaValueSourceSummary.Value nested)collect(nested,values,work);
    }
    private static void fresh(JavaValueSourceSummary.Value value,Set<JavaValueSourceSummary.Value> forbidden,
            Set<JavaValueSourceSummary.Value> visited,JavaValueSourceSummary.Work work) {
        work.charge(1);if(forbidden.contains(value) || !visited.add(value))throw unavailable("VALUE_ALGEBRA_FRESH_TREE"); //$NON-NLS-1$
        for(var component:value.components())if(component instanceof JavaValueSourceSummary.Value nested)fresh(nested,forbidden,visited,work);
    }
    private static JavaValueSourceSummary.Value multiply(JavaValueSourceSummary.Session session,IMethodBinding method,
            JavaValueSourceSummary.Value left,JavaValueSourceSummary.Value right) {return value(session.invoke(method,left,List.of(right)));}
    private static JavaValueSourceSummary.Value value(JavaValueSourceSummary.Term term) {
        if(term instanceof JavaValueSourceSummary.Value value)return value;throw unavailable("VALUE_ALGEBRA_RESULT"); //$NON-NLS-1$
    }
    private static JavaValueSourceSummary.Value canonical(JavaValueSourceSummary.Value value,BigInteger modulus,JavaValueSourceSummary.Work work) {
        var components=new ArrayList<JavaValueSourceSummary.Term>();
        for(var component:value.components()) {
            work.charge(1);components.add(component instanceof JavaValueSourceSummary.Value nested?canonical(nested,modulus,work)
                :new JavaValueSourceSummary.Scalar(JavaExpressions.operation(NumericKind.BIG_INTEGER,NumericOperation.MOD,
                    JavaValueSourceSummary.scalar(component),JavaExpressions.literal(modulus))));
        }
        return new JavaValueSourceSummary.Value(value.shape(),components);
    }
    private static void total(Expr expression,JavaValueSourceSummary.Work work,IdentityHashMap<Expr,Boolean> seen) {
        work.charge(1);if(seen.put(expression,Boolean.TRUE)!=null)return;
        if(expression instanceof VariableExpr || JavaExpressions.isLiteral(expression))return;
        var operation=JavaExpressions.operationOf(expression).orElseThrow(()->unavailable("VALUE_ALGEBRA_OPERATION")); //$NON-NLS-1$
        var operands=JavaExpressions.operands(expression);
        switch(operation) {
            case ADD,SUBTRACT,MULTIPLY,NEGATE -> { }
            case SHIFT_LEFT -> {if(!JavaExpressions.isLiteral(operands.get(1)) || !(JavaExpressions.literalValue(operands.get(1)) instanceof Integer distance) || distance<0 || distance>4096)throw unavailable("VALUE_ALGEBRA_SHIFT");}
            case MOD -> {
                if(!JavaExpressions.isLiteral(operands.get(1)) || !(JavaExpressions.literalValue(operands.get(1)) instanceof BigInteger modulus)
                    || modulus.signum()<=0 || modulus.bitLength()>4096)throw unavailable("VALUE_ALGEBRA_PARTIAL_MODULUS"); //$NON-NLS-1$
            }
            default -> throw unavailable("VALUE_ALGEBRA_PARTIAL_OPERATION"); //$NON-NLS-1$
        }
        for(var operand:operands)total(operand,work,seen);
    }
    private static IllegalArgumentException unavailable(String reason){return JavaValueSourceSummary.unavailable(reason);}
}

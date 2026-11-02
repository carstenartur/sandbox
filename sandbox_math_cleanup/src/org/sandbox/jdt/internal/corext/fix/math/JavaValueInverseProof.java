/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.*;
import de.regelsuche.ast.Expr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.sdk.optimization.*;
import de.regelsuche.search.program.JointComputationPlan;
import org.eclipse.jdt.core.dom.*;

/** Binds the SDK's conditional inverse and unit-root theorems to inspected Java source. */
final class JavaValueInverseProof {
    private JavaValueInverseProof() { }
    record Receipt(JavaValueAlgebraProof.Receipt algebra, IVariableBinding identity,
            List<VerificationEvidence> inverseLaws, JavaValueInverseSource.Projection retained,
            JavaValueInverseSource.Projection later, SemanticAssumption successfulOccurrence,
            ModularUnitVerifier.Verification root, OptimizationRequest unitRootRequest, Expr unitBase, List<JavaValueSourceSummary.StaticValue> statics) {
        Receipt {inverseLaws=List.copyOf(inverseLaws);statics=List.copyOf(statics);}
    }
    static Receipt prove(CompilationUnit ast,ITypeBinding type,IMethodBinding multiplication,IMethodBinding square,
            IMethodBinding inverse,MathCleanUpOptions options,JavaValueSourceSummary.Work work) {
        var lawSession=new JavaValueSourceSummary.Session(ast,options,work);var shape=lawSession.shape(type);
        var operand=lawSession.symbolic(shape,"inverseOperand");
        var formula=JavaValueInverseSource.project(ast,lawSession,inverse,operand,work);
        JavaValueAlgebraProof.Receipt algebra=null;IVariableBinding identity=null;var identityRejections=new ArrayList<String>();
        // Every candidate is source materialized and independently checked. Field spelling conveys no law.
        for(var field:type.getDeclaredFields()) {
            if(!Modifier.isStatic(field.getModifiers()) || !Modifier.isFinal(field.getModifiers()) || !field.getType().isEqualTo(type)
                || !Modifier.isPublic(field.getModifiers()))continue;
            try {
                var session=new JavaValueSourceSummary.Session(ast,options,work);var identityShape=session.shape(type);
                var constant=(JavaValueSourceSummary.Value)session.constant(field);
                algebra=JavaValueAlgebraProof.prove(session,identityShape,multiplication,square,constant,work,options,1_000_000);identity=field;break;
            } catch(JavaValueSourceSummary.DeadlineExceeded stopped) {throw stopped;}
            catch(IllegalArgumentException rejected) {work.check();identityRejections.add(field.getName()+":"+rejected.getMessage());}
        }
        if(algebra==null)throw unavailable("VALUE_INVERSE_ALGEBRA:"+identityRejections);
        admit(formula,algebra.modulus(),work);
        var inverseValue=formula.summary().value();
        var left=(JavaValueSourceSummary.Value)lawSession.invoke(multiplication,operand,List.of(inverseValue));
        var right=(JavaValueSourceSummary.Value)lawSession.invoke(multiplication,inverseValue,List.of(operand));
        var identityValue=(JavaValueSourceSummary.Value)lawSession.constant(identity);
        var laws=new ArrayList<VerificationEvidence>();
        for(var product:List.of(left,right)) {
            var summary=lawSession.summary(product);
            var request=conditional(summary,formula.inverse(),options,work.allocate(500_000,options));
            // Algebra admission already checked that the source identity's components are canonical.
            var identityExpressions=new ArrayList<Expr>();
            for(var output:lawSession.summary(identityValue).plan().outputs())identityExpressions.add(JavaExpressions.operation(NumericKind.BIG_INTEGER,
                NumericOperation.MOD,output.expression(),JavaExpressions.literal(algebra.modulus())));
            var target=summary.plan().withOutputs(identityExpressions);
            var proof=new ComputationOptimizer().verify(request,target,work.token());work.check();
            if(proof instanceof VerificationResult.BudgetExceeded stopped && stopped.work()>500_000)work.charge(stopped.work()-500_000);
            if(!(proof instanceof VerificationResult.Verified verified))throw unavailable("VALUE_INVERSE_LAW:"+proof);
            laws.add(verified.evidence());
        }
        var rootSession=new JavaValueSourceSummary.Session(ast,options,work);var base=rootSession.symbolic(rootSession.shape(type),"inverseBase");
        var squared=(JavaValueSourceSummary.Value)rootSession.invoke(square,base,List.of());
        var cube=(JavaValueSourceSummary.Value)rootSession.invoke(multiplication,squared,List.of(base));
        var retained=JavaValueInverseSource.project(ast,rootSession,inverse,cube,work);
        var baseSession=new JavaValueSourceSummary.Session(ast,options,work);
        var laterBase=baseSession.symbolic(baseSession.shape(type),"inverseBase");
        var later=JavaValueInverseSource.project(ast,baseSession,inverse,laterBase,work);
        admit(retained,algebra.modulus(),work);admit(later,algebra.modulus(),work);
        if(!retained.summary().inputs().equals(later.summary().inputs()))throw unavailable("VALUE_INVERSE_ROOT_INPUTS");
        var request=conditional(retained.summary(),retained.inverse(),options,work.allocate(750_000,options));
        var norm=JavaExpressions.operands(later.inverse().expression()).getFirst();
        var root=new ModularUnitVerifier().verifyPowerRoot(request,norm,3,work.token());work.check();
        if(root.work()>750_000)work.charge(root.work()-750_000);
        if(!root.verified())throw unavailable("VALUE_INVERSE_UNIT_ROOT:"+root.reason());
        var statics=new LinkedHashMap<String,JavaValueSourceSummary.StaticValue>();
        for(var summary:List.of(formula.summary(),retained.summary(),later.summary()))for(var value:summary.staticValues())statics.put(value.field().getKey(),value);
        for(var value:algebra.staticValues())statics.put(value.field().getKey(),value);
        var success=request.assumptions().stream().filter(assumption->assumption.kind()==SemanticAssumption.Kind.SUCCESSFUL_MODULAR_INVERSE).findFirst().orElseThrow();
        return new Receipt(algebra,identity,laws,retained,later,success,root,request,norm,new ArrayList<>(statics.values()));
    }
    static OptimizationRequest conditional(JavaValueSourceSummary.Summary summary,SourceEvaluationTrace.Occurrence inverse,
            MathCleanUpOptions options,OptimizationBudget budget) {
        var assumptions=new HashSet<>(summary.assumptions());
        assumptions.add(SemanticAssumption.successfulModularInverse(inverse,"conditional on successful exact source inverse occurrence"));
        return new OptimizationRequest(summary.plan(),summary.trace(),Set.of(NumericKind.BIG_INTEGER),ComputationOptimizer.SEMANTICS_REVISION,
            assumptions,SafetyProfile.PRESERVE_JAVA,options.goal(),budget,CheckedPolicy.NONE);
    }
    private static void admit(JavaValueInverseSource.Projection projection,BigInteger modulus,JavaValueSourceSummary.Work work) {
        var seen=new IdentityHashMap<Expr,Boolean>();
        for(var occurrence:projection.summary().trace().occurrences())total(occurrence.expression(),modulus,seen,work);
        for(var output:projection.summary().plan().outputs()) {
            var expression=output.expression();
            if(JavaExpressions.operationOf(expression).orElse(null)!=NumericOperation.MOD
                || !JavaExpressions.operands(expression).get(1).equals(JavaExpressions.literal(modulus)))throw unavailable("VALUE_INVERSE_CLOSURE");
        }
    }
    private static void total(Expr expression,BigInteger modulus,IdentityHashMap<Expr,Boolean> seen,JavaValueSourceSummary.Work work) {
        work.charge(1);if(seen.put(expression,Boolean.TRUE)!=null || expression instanceof VariableExpr || JavaExpressions.isLiteral(expression))return;
        var operands=JavaExpressions.operands(expression);var operation=JavaExpressions.operationOf(expression).orElseThrow(()->unavailable("VALUE_INVERSE_OPERATION"));
        switch(operation) {
            case ADD,SUBTRACT,MULTIPLY,NEGATE -> { }
            case MOD,MOD_INVERSE -> {if(!operands.get(1).equals(JavaExpressions.literal(modulus)))throw unavailable("VALUE_INVERSE_MODULUS");}
            case SHIFT_LEFT -> {if(!JavaExpressions.isLiteral(operands.get(1)) || !(JavaExpressions.literalValue(operands.get(1)) instanceof Integer n) || n<0 || n>4096)throw unavailable("VALUE_INVERSE_SHIFT");}
            default -> throw unavailable("VALUE_INVERSE_PARTIAL_OPERATION");
        }
        for(var operand:operands)total(operand,modulus,seen,work);
    }
    private static IllegalArgumentException unavailable(String reason){return JavaValueSourceSummary.unavailable(reason);}
}

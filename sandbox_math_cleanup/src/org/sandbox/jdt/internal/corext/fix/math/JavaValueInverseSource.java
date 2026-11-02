/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.*;
import de.regelsuche.sdk.optimization.*;
import org.eclipse.jdt.core.dom.*;

/** Source projection of an inverse-shaped continuation. This receipt establishes no inverse law or success. */
final class JavaValueInverseSource {
    private JavaValueInverseSource() { }
    record Projection(JavaValueSourceSummary.Summary summary,SourceEvaluationTrace.Occurrence inverse,IMethodBinding rejection) { }
    static Projection project(CompilationUnit ast,JavaValueSourceSummary.Session session,IMethodBinding binding,
            JavaValueSourceSummary.Value receiver,JavaValueSourceSummary.Work work) {
        if(binding==null || binding.getParameterTypes().length!=0 || Modifier.isStatic(binding.getModifiers()))throw JavaValueSourceSummary.unavailable("VALUE_INVERSE_SIGNATURE"); //$NON-NLS-1$
        new JavaValueUseProof(ast,session,work).freshSourceMethod(binding);
        var definition=JavaSourceContext.definition(ast,binding.getMethodDeclaration());
        if(definition==null || !(definition.declaration() instanceof MethodDeclaration method) || method.getBody()==null || method.getBody().statements().size()<2)
            throw JavaValueSourceSummary.unavailable("VALUE_INVERSE_SOURCE"); //$NON-NLS-1$
        var statements=method.getBody().statements();
        if(!(statements.getFirst() instanceof IfStatement guard) || guard.getElseStatement()!=null
            || !(guard.getExpression() instanceof MethodInvocation predicate) || predicate.getExpression()!=null || !predicate.arguments().isEmpty()
            || predicate.resolveMethodBinding()==null || !predicate.resolveMethodBinding().getReturnType().isPrimitive()
            || !predicate.resolveMethodBinding().getReturnType().getName().equals("boolean")
            || !(guard.getThenStatement() instanceof Block rejected) || rejected.statements().size()!=1 || !(rejected.statements().getFirst() instanceof ThrowStatement))
            throw JavaValueSourceSummary.unavailable("VALUE_INVERSE_EARLY_GUARD"); //$NON-NLS-1$ //$NON-NLS-2$
        int traceStart=session.summary(receiver).trace().occurrences().size();var frame=new HashMap<String,JavaValueSourceSummary.Term>();frame.put("this",receiver); //$NON-NLS-1$
        JavaValueSourceSummary.Value result=null;
        for(int i=1;i<statements.size();i++) {
            work.charge(1);Object statement=statements.get(i);
            if(statement instanceof VariableDeclarationStatement declaration)for(Object item:declaration.fragments()) {
                var local=(VariableDeclarationFragment)item;
                if(local.resolveBinding()==null || local.getInitializer()==null)throw JavaValueSourceSummary.unavailable("VALUE_INVERSE_LOCAL"); //$NON-NLS-1$
                frame.put(local.resolveBinding().getKey(),session.project(local.getInitializer(),frame));
            }
            else if(statement instanceof ReturnStatement returned && i==statements.size()-1) {
                var value=session.project(returned.getExpression(),frame);
                if(!(value instanceof JavaValueSourceSummary.Value object))throw JavaValueSourceSummary.unavailable("VALUE_INVERSE_RETURN");result=object; //$NON-NLS-1$
            } else throw JavaValueSourceSummary.unavailable("VALUE_INVERSE_CONTINUATION"); //$NON-NLS-1$
        }
        if(result==null)throw JavaValueSourceSummary.unavailable("VALUE_INVERSE_RETURN"); //$NON-NLS-1$
        var summary=session.summary(result);var inverses=summary.trace().occurrences().subList(traceStart,summary.trace().occurrences().size()).stream()
            .filter(occurrence->JavaExpressions.operationOf(occurrence.expression()).orElse(null)==NumericOperation.MOD_INVERSE).toList();
        if(inverses.size()!=1)throw JavaValueSourceSummary.unavailable("VALUE_INVERSE_SCALAR_OCCURRENCE"); //$NON-NLS-1$
        return new Projection(summary,inverses.getFirst(),predicate.resolveMethodBinding());
    }
}

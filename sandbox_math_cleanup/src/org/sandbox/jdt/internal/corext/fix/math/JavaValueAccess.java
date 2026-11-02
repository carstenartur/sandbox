/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.*;
import de.regelsuche.ast.Expr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.sdk.optimization.JavaExpressions;
import org.eclipse.jdt.core.dom.*;

/** Legal source access to a direct immutable numeric value. Names never imply semantics. */
final class JavaValueAccess {
    private JavaValueAccess() { }
    /** Cheap binding preflight only; the source session must still prove every shape/effect fact. */
    static boolean possiblyDirect(ITypeBinding type) {
        if(type==null || !type.isClass() || type.isNested() || !Modifier.isFinal(type.getModifiers()))return false;
        var fields=Arrays.stream(type.getDeclaredFields()).filter(field->!Modifier.isStatic(field.getModifiers())).toList();
        return !fields.isEmpty() && fields.stream().allMatch(field->Modifier.isPrivate(field.getModifiers())
            && Modifier.isFinal(field.getModifiers()) && JavaValueSourceSummary.big(field.getType()));
    }
    record Access(JavaValueSourceSummary.Shape shape,List<IMethodBinding> getters,IMethodBinding factory) {
        String leaf(String object,int index) {return object+"."+getters.get(index).getName()+"()";} //$NON-NLS-1$ //$NON-NLS-2$
        String construct(List<String> components) {
            return shape.type().getQualifiedName()+"."+factory.getName()+"("+String.join(", ",components)+")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        }
    }
    /** Invert only the factory's syntactic output context. The emitted call still receives an independent numeric proof. */
    static List<Expr> factoryArguments(CompilationUnit ast,Access access,List<Expr> outputs,List<JavaValueSourceSummary.StaticValue> guardedStatics,MathCleanUpOptions options,JavaValueSourceSummary.Work work) {
        var session=new JavaValueSourceSummary.Session(ast,options,work);var arguments=new ArrayList<JavaValueSourceSummary.Term>();
        for(int i=0;i<access.factory().getParameterTypes().length;i++)arguments.add(session.input("factoryArgument"+i)); //$NON-NLS-1$
        var result=(JavaValueSourceSummary.Value)session.invoke(access.factory(),null,arguments);var summary=session.summary(result);
        if(!session.onlyOwner(access.shape().type()))throw JavaValueSourceSummary.unavailable("VALUE_FACTORY_FOREIGN_INITIALIZATION");
        for(var value:summary.staticValues())if(guardedStatics.stream().noneMatch(guarded->guarded.field().isEqualTo(value.field()) && guarded.value().equals(value.value())))
            throw JavaValueSourceSummary.unavailable("VALUE_FACTORY_UNGUARDED_STATIC_VALUE");
        var templates=summary.plan().resolveOutputs(work::charge).expressions();var bindings=new HashMap<String,Expr>();
        for(int i=0;i<outputs.size();i++)match(templates.get(i),outputs.get(i),bindings,work);
        var values=new ArrayList<Expr>();
        for(var input:summary.inputs()) {
            Expr value=bindings.get(input.id());if(value==null)throw JavaValueSourceSummary.unavailable("VALUE_FACTORY_UNBOUND_ARGUMENT");values.add(value); //$NON-NLS-1$
        }
        return values;
    }
    private static void match(Expr template,Expr value,Map<String,Expr> bindings,JavaValueSourceSummary.Work work) {
        work.charge(1);
        if(template instanceof VariableExpr variable) {
            Expr previous=bindings.putIfAbsent(variable.name(),value);
            if(previous!=null && !previous.equals(value))throw JavaValueSourceSummary.unavailable("VALUE_FACTORY_INCONSISTENT_ARGUMENT");return; //$NON-NLS-1$
        }
        if(template.equals(value))return;
        var left=JavaExpressions.operands(template);var right=JavaExpressions.operands(value);
        if(left.isEmpty() || left.size()!=right.size() || !JavaExpressions.operationOf(template).equals(JavaExpressions.operationOf(value))
            || !JavaExpressions.castSourceKind(template).equals(JavaExpressions.castSourceKind(value))
            || JavaExpressions.resultKind(template)!=JavaExpressions.resultKind(value))throw JavaValueSourceSummary.unavailable("VALUE_FACTORY_OUTPUT_CONTEXT"); //$NON-NLS-1$
        for(int i=0;i<left.size();i++)match(left.get(i),right.get(i),bindings,work);
    }
    static Access discover(CompilationUnit ast,JavaValueSourceSummary.Session session,ITypeBinding type,
            JavaValueSourceSummary.Work work) {
        var shape=session.shape(type);
        if(shape.components().stream().anyMatch(component->component.shape()!=null))throw JavaValueSourceSummary.unavailable("VALUE_ACCESS_DIRECT_ONLY"); //$NON-NLS-1$
        var getters=new ArrayList<IMethodBinding>();
        for(var component:shape.components()) {
            IMethodBinding found=null;
            for(var method:type.getDeclaredMethods()) {
                work.charge(1);
                if(!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers()) || method.getParameterTypes().length!=0
                    || !method.getReturnType().isEqualTo(component.field().getType()))continue;
                var definition=JavaSourceContext.definition(ast,method.getMethodDeclaration());
                if(definition!=null && definition.declaration() instanceof MethodDeclaration source && source.resolveBinding()!=null
                    && source.getModifiers()==method.getModifiers() && source.getBody()!=null
                    && source.getBody().statements().size()==1 && source.getBody().statements().getFirst() instanceof ReturnStatement returned
                    && component.field().isEqualTo(JavaValueSourceSummary.variable(returned.getExpression()))) {
                    // A getter's field must belong to this, not an arbitrary instance expression.
                    Expression expression=returned.getExpression();
                    if(expression instanceof SimpleName || expression instanceof FieldAccess field && field.getExpression() instanceof ThisExpression self && self.getQualifier()==null)
                        found=method;
                }
            }
            if(found==null)throw JavaValueSourceSummary.unavailable("VALUE_ACCESS_GETTER");getters.add(found); //$NON-NLS-1$
        }
        IMethodBinding factory=null;
        for(var method:type.getDeclaredMethods()) {
            work.charge(1);
            if(!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())
                || !method.getReturnType().isEqualTo(type) || method.getParameterTypes().length!=shape.components().size()
                || Arrays.stream(method.getParameterTypes()).anyMatch(parameter->!JavaValueSourceSummary.big(parameter)))continue;
            var definition=JavaSourceContext.definition(ast,method.getMethodDeclaration());
            if(definition==null || !(definition.declaration() instanceof MethodDeclaration source) || source.resolveBinding()==null
                || source.getModifiers()!=method.getModifiers() || source.getBody()==null
                || source.getBody().statements().size()!=1 || !(source.getBody().statements().getFirst() instanceof ReturnStatement returned)
                || !(returned.getExpression() instanceof ClassInstanceCreation creation)
                || creation.resolveConstructorBinding()==null || !creation.resolveConstructorBinding().isEqualTo(shape.constructor().resolveBinding()))continue;
            factory=method;break;
        }
        if(factory==null)throw JavaValueSourceSummary.unavailable("VALUE_ACCESS_FRESH_FACTORY"); //$NON-NLS-1$
        return new Access(shape,getters,factory);
    }
}

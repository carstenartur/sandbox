/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.*;
import org.eclipse.jdt.core.dom.*;

/** Source admission for detached values. Fresh outer consumers alone are never an identity proof. */
final class JavaValueUseProof {
    private final CompilationUnit ast;
    private final JavaValueSourceSummary.Session session;
    private final JavaValueSourceSummary.Work work;
    private final Set<String> active=new HashSet<>(), proved=new HashSet<>();
    JavaValueUseProof(CompilationUnit ast,JavaValueSourceSummary.Session session,JavaValueSourceSummary.Work work) {
        this.ast=ast;this.session=session;this.work=work;
    }
    void freshSourceMethod(IMethodBinding method) {freshConsumer(method);}
    void scalarOrigins(IMethodBinding method) {inspect(method,false);}
    void scalarOrigins(Expression expression) {
        expression.accept(new ASTVisitor(){
            @Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(MethodInvocation call) {
                var method=call.resolveMethodBinding();if(method==null)throw JavaValueSourceSummary.unavailable("VALUE_IDENTITY_CALL"); //$NON-NLS-1$
                if(JavaValueSourceSummary.big(method.getDeclaringClass())) {
                    if(!scalarOriginMethod(method))throw JavaValueSourceSummary.unavailable("VALUE_SCALAR_ORIGIN_UNSUPPORTED"); //$NON-NLS-1$
                } else inspect(method,false);
                return true;
            }
        });
    }
    static boolean scalarOriginMethod(IMethodBinding method) {
        if(method==null || !JavaValueSourceSummary.big(method.getDeclaringClass()))return false;
        String name=method.getName();var parameters=method.getParameterTypes();
        if(Modifier.isStatic(method.getModifiers()))return name.equals("valueOf") && parameters.length==1 && parameters[0].getName().equals("long");
        return switch(name) {
            case "add","subtract","multiply","mod","modInverse","compareTo" -> parameters.length==1 && JavaValueSourceSummary.big(parameters[0]);
            case "equals" -> parameters.length==1 && parameters[0].getQualifiedName().equals("java.lang.Object");
            case "shiftLeft","shiftRight" -> parameters.length==1 && parameters[0].getName().equals("int");
            case "negate","abs","signum","bitLength" -> parameters.length==0;
            default -> false;
        };
    }
    /** Only a structural admission check. Callers must separately prove and emit scalar detachment. */
    void admitConsumerShape(Expression expression,MethodDeclaration owner) {
        ASTNode value=expression;while(value.getParent() instanceof ParenthesizedExpression)value=value.getParent();
        if(value.getParent() instanceof MethodInvocation consumer && (consumer.getExpression()==value || consumer.arguments().contains(value))) {
            freshConsumer(consumer.resolveMethodBinding());return;
        }
        if(value.getParent() instanceof VariableDeclarationFragment local && local.getInitializer()==value && local.resolveBinding()!=null) {
            var binding=local.resolveBinding();owner.accept(new ASTVisitor(){
                @Override public void preVisit(ASTNode node){work.charge(1);}
                @Override public boolean visit(SimpleName name) {
                    if(!name.isDeclaration() && binding.isEqualTo(name.resolveBinding()))admitConsumerShape(name,owner);return true;
                }
            });return;
        }
        throw JavaValueSourceSummary.unavailable("VALUE_IDENTITY_ESCAPES"); //$NON-NLS-1$
    }
    private void freshConsumer(IMethodBinding method) {
        if(method==null || Modifier.isStatic(method.getModifiers()) || !method.getReturnType().isEqualTo(method.getDeclaringClass()))
            throw JavaValueSourceSummary.unavailable("VALUE_IDENTITY_CONSUMER"); //$NON-NLS-1$
        inspect(method,true);
    }
    private void inspect(IMethodBinding method,boolean fresh) {
        work.charge(1);String key=method.getMethodDeclaration().getKey()+fresh;if(proved.contains(key))return;
        if(!active.add(key) || active.size()>32)throw JavaValueSourceSummary.unavailable("VALUE_IDENTITY_RECURSION"); //$NON-NLS-1$
        try {
            var shape=session.shape(method.getDeclaringClass());var definition=JavaSourceContext.definition(ast,method.getMethodDeclaration());
            if(definition==null || !(definition.declaration() instanceof MethodDeclaration source) || source.getBody()==null || source.resolveBinding()==null || source.getModifiers()!=method.getModifiers()
                || Modifier.isSynchronized(source.getModifiers()) || Modifier.isNative(source.getModifiers()))throw JavaValueSourceSummary.unavailable("VALUE_IDENTITY_SOURCE"); //$NON-NLS-1$
            source.getBody().accept(new ASTVisitor(){
                @Override public void preVisit(ASTNode node){work.charge(1);}
                @Override public boolean visit(SimpleName name) {
                    if(name.resolveBinding() instanceof IVariableBinding field && field.isField() && !Modifier.isFinal(field.getModifiers()))
                        throw JavaValueSourceSummary.unavailable("VALUE_IDENTITY_MUTABLE_STATE");return true; //$NON-NLS-1$
                }
                @Override public boolean visit(ArrayAccess node){throw JavaValueSourceSummary.unavailable("VALUE_IDENTITY_MUTABLE_ARRAY");}
                @Override public boolean visit(Assignment node){throw JavaValueSourceSummary.unavailable("VALUE_IDENTITY_WRITE");} //$NON-NLS-1$
                @Override public boolean visit(PostfixExpression node){throw JavaValueSourceSummary.unavailable("VALUE_IDENTITY_WRITE");} //$NON-NLS-1$
                @Override public boolean visit(PrefixExpression node){if(node.getOperator()==PrefixExpression.Operator.INCREMENT || node.getOperator()==PrefixExpression.Operator.DECREMENT)throw JavaValueSourceSummary.unavailable("VALUE_IDENTITY_WRITE");return true;} //$NON-NLS-1$
                @Override public boolean visit(SynchronizedStatement node){throw JavaValueSourceSummary.unavailable("VALUE_IDENTITY_LOCK");} //$NON-NLS-1$
                @Override public boolean visit(InfixExpression node) {
                    if((node.getOperator()==InfixExpression.Operator.EQUALS || node.getOperator()==InfixExpression.Operator.NOT_EQUALS)
                        && (node.getLeftOperand().resolveTypeBinding()==null || !node.getLeftOperand().resolveTypeBinding().isPrimitive()))
                        throw JavaValueSourceSummary.unavailable("VALUE_REFERENCE_COMPARISON");return true; //$NON-NLS-1$
                }
                @Override public boolean visit(MethodInvocation call) {
                    var target=call.resolveMethodBinding();if(target==null)throw JavaValueSourceSummary.unavailable("VALUE_IDENTITY_CALL"); //$NON-NLS-1$
                    if(JavaValueSourceSummary.big(target.getDeclaringClass())) {
                        if(!scalarOriginMethod(target))throw JavaValueSourceSummary.unavailable("VALUE_SCALAR_ORIGIN_UNSUPPORTED");return true;
                    }
                    if(!target.getDeclaringClass().isEqualTo(shape.type()))throw JavaValueSourceSummary.unavailable("VALUE_IDENTITY_FOREIGN_CALL"); //$NON-NLS-1$
                    inspect(target,false);return true;
                }
                @Override public boolean visit(ClassInstanceCreation creation) {
                    if(creation.resolveTypeBinding()!=null && creation.resolveTypeBinding().isEqualTo(shape.type())) {
                        if(!shape.constructor().resolveBinding().isEqualTo(creation.resolveConstructorBinding()))throw JavaValueSourceSummary.unavailable("VALUE_IDENTITY_CONSTRUCTOR"); //$NON-NLS-1$
                    } else if(!(creation.getParent() instanceof ThrowStatement) || creation.arguments().stream().anyMatch(argument->!(argument instanceof StringLiteral)))
                        throw JavaValueSourceSummary.unavailable("VALUE_IDENTITY_ALLOCATION");return true; //$NON-NLS-1$
                }
                @Override public boolean visit(ReturnStatement returned) {
                    if(fresh) {
                        Expression expression=returned.getExpression();
                        if(expression instanceof ClassInstanceCreation creation && creation.resolveTypeBinding()!=null && creation.resolveTypeBinding().isEqualTo(shape.type()))return true;
                        if(expression instanceof MethodInvocation call && call.resolveMethodBinding()!=null && call.resolveMethodBinding().getDeclaringClass().isEqualTo(shape.type())) {
                            inspect(call.resolveMethodBinding(),true);return true;
                        }
                        throw JavaValueSourceSummary.unavailable("VALUE_IDENTITY_RESULT_ALIAS"); //$NON-NLS-1$
                    }
                    return true;
                }
            });proved.add(key);
        } finally {active.remove(key);}
    }
}

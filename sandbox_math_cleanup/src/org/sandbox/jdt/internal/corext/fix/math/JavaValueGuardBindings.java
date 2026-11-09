/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.*;
import org.eclipse.jdt.core.dom.*;

/** Resolves emitted guard expressions; a package-looking name is not necessarily a type qualifier. */
final class JavaValueGuardBindings {
    private JavaValueGuardBindings() { }
    static void verify(Expression guard,JavaValueAccess.Access access,Collection<? extends IBinding> locals,
            List<JavaValueSourceSummary.StaticValue> statics,Collection<IMethodBinding> valueCalls,JavaValueSourceSummary.Work work) {
        var variables=new HashSet<String>();for(var binding:locals) {if(binding==null)throw unavailable("LOCAL");variables.add(binding.getKey());}
        var fields=new HashSet<String>();for(var value:statics)fields.add(value.field().getKey());
        var methods=new HashSet<String>();for(var getter:access.getters())methods.add(getter.getKey());for(var method:valueCalls)methods.add(method.getKey());
        guard.accept(new ASTVisitor(){
            @Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(SimpleName name) {
                if(name.resolveBinding() instanceof IVariableBinding variable) {
                    if(variable.isField()? !fields.contains(variable.getKey()):!variables.contains(variable.getKey()))throw unavailable("VARIABLE");
                }
                return true;
            }
            @Override public boolean visit(QualifiedName name) {
                if(name.resolveBinding() instanceof IVariableBinding field) {
                    if(!Modifier.isStatic(field.getModifiers()) || !fields.contains(field.getKey())
                        || !(name.getQualifier().resolveBinding() instanceof ITypeBinding owner) || !owner.isEqualTo(field.getDeclaringClass()))throw unavailable("STATIC_FIELD_QUALIFIER");
                }
                return true;
            }
            @Override public boolean visit(FieldAccess access){throw unavailable("FIELD_ACCESS");}
            @Override public boolean visit(TypeLiteral literal) {
                if(!scalarType(literal.getType().resolveBinding()))throw unavailable("CLASS_LITERAL");return true;
            }
            @Override public boolean visit(ClassInstanceCreation creation) {
                var constructor=creation.resolveConstructorBinding();
                if(constructor==null || !scalarType(constructor.getDeclaringClass()) || creation.getAnonymousClassDeclaration()!=null
                    || creation.getExpression()!=null || constructor.getParameterTypes().length!=1 || !constructor.getParameterTypes()[0].getQualifiedName().equals("java.lang.String")
                    || creation.arguments().size()!=1 || !(creation.arguments().getFirst() instanceof StringLiteral))throw unavailable("CONSTRUCTOR");return true;
            }
            @Override public boolean visit(MethodInvocation call) {
                var method=call.resolveMethodBinding();if(method==null || method.isRecovered())throw unavailable("METHOD");
                if(Modifier.isStatic(method.getModifiers())) {
                    if(!(call.getExpression() instanceof Name name) || !(name.resolveBinding() instanceof ITypeBinding owner)
                        || !owner.isEqualTo(method.getDeclaringClass()))throw unavailable("STATIC_METHOD_QUALIFIER");
                } else if(call.getExpression()==null)throw unavailable("RECEIVER");
                if(nonNull(method))return true;
                if(scalarType(method.getDeclaringClass())) {
                    if(!JavaValueUseProof.scalarOriginMethod(method))throw unavailable("SCALAR_METHOD");return true;
                }
                if(method.getDeclaringClass().getQualifiedName().equals("java.lang.Object") && method.getName().equals("getClass")
                    && !Modifier.isStatic(method.getModifiers()) && method.getParameterTypes().length==0
                    && scalarType(call.getExpression().resolveTypeBinding()))return true;
                if(!methods.contains(method.getKey()))throw unavailable("VALUE_METHOD");return true;
            }
        });
    }
    // Objects.nonNull preserves null behavior without refining ECJ's facts in the untouched fallback.
    // Keep its evaluation exact even when a source type shadows a package-looking name.
    static boolean objectsType(ITypeBinding type) {
        return type!=null && !type.isRecovered() && !type.isFromSource() && !type.isNested() && "java.util.Objects".equals(type.getBinaryName()); //$NON-NLS-1$
    }
    static boolean nonNull(IMethodBinding method) {
        return method!=null && !method.isRecovered() && objectsType(method.getDeclaringClass())
            && Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers()) && "nonNull".equals(method.getName()) //$NON-NLS-1$
            && method.getParameterTypes().length==1 && "java.lang.Object".equals(method.getParameterTypes()[0].getQualifiedName()) //$NON-NLS-1$
            && "boolean".equals(method.getReturnType().getName()); //$NON-NLS-1$
    }
    static void verifyNonNullCalls(Expression guard,JavaValueSourceSummary.Work work) {
        guard.accept(new ASTVisitor(){
            @Override public void preVisit(ASTNode node){work.charge(1);}
            @Override public boolean visit(MethodInvocation call) {
                // Other guard calls may be independently admitted source getters named nonNull.
                if("nonNull".equals(call.getName().getIdentifier()) && call.getExpression() instanceof Name name //$NON-NLS-1$
                    && "java.util.Objects".equals(name.getFullyQualifiedName()) && (!nonNull(call.resolveMethodBinding()) //$NON-NLS-1$
                    || !(name.resolveBinding() instanceof ITypeBinding type)
                    || !type.isEqualTo(call.resolveMethodBinding().getDeclaringClass())))throw unavailable("NON_NULL_METHOD"); //$NON-NLS-1$
                return true;
            }
        });
    }
    private static boolean scalarType(ITypeBinding type) {
        return JavaValueSourceSummary.big(type) && !type.isNested() && "java.math.BigInteger".equals(type.getBinaryName());
    }
    private static IllegalArgumentException unavailable(String part) {return JavaValueSourceSummary.unavailable("VALUE_GUARD_BINDING_"+part);}
}

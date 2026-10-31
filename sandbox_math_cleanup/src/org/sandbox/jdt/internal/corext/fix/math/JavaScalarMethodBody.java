/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.List;
import java.util.Set;
import de.regelsuche.sdk.optimization.NumericKind;
import org.eclipse.jdt.core.dom.*;

/** Source/binding contract only. Mathematical transformations belong to Regelsuche. */
final class JavaScalarMethodBody {
    private JavaScalarMethodBody() { }
    private static final Set<String> PRIMITIVE_SAMS = Set.of(
        "java.util.function.IntBinaryOperator", "java.util.function.LongBinaryOperator", //$NON-NLS-1$ //$NON-NLS-2$
        "java.util.function.IntUnaryOperator", "java.util.function.LongUnaryOperator", //$NON-NLS-1$ //$NON-NLS-2$
        "java.util.function.IntSupplier", "java.util.function.LongSupplier", //$NON-NLS-1$ //$NON-NLS-2$
        "java.util.function.IntToLongFunction", "java.util.function.LongToIntFunction"); //$NON-NLS-1$ //$NON-NLS-2$

    static MethodDeclaration resolve(CompilationUnit unit, MethodInvocation call, boolean mathematical) {
        return resolve(unit, call, mathematical, false);
    }

    /** Retained calls keep receiver/argument evaluation, null checks and class initialization in source. */
    static MethodDeclaration resolve(CompilationUnit unit, MethodInvocation call, boolean mathematical, boolean retainedCall) {
        IMethodBinding binding = call.resolveMethodBinding();
        if (binding == null || binding.isRecovered()) return null;
        if (retainedCall) {
            MethodDeclaration referenced = referencedMethod(unit, call, binding, mathematical);
            if (referenced != null) return referenced;
        }
        if (!admitted(binding, mathematical) || !dispatch(binding, retainedCall)) return null;
        if (Modifier.isStatic(binding.getModifiers())) {
            if (call.getExpression() != null && !typeName(call.getExpression())) return null;
        } else if (!ownThis(call.getExpression()) && !(retainedCall && local(call.getExpression()))) return null;
        // Removing an instance call cannot discard a receiver evaluation or NPE;
        // removing a static call cannot discard another class's initialization.
        if (!retainedCall && !sameCallerClass(call, binding)) return null;
        return definition(unit, binding, call.arguments().size(), mathematical, retainedCall);
    }

    private static MethodDeclaration definition(CompilationUnit unit, IMethodBinding binding, int arity,
            boolean mathematical, boolean retained) {
        ASTNode node = retained ? JavaSourceContext.method(unit, binding) : unit.findDeclaringNode(binding.getMethodDeclaration());
        if (node == null && retained) node = unit.findDeclaringNode(binding.getMethodDeclaration());
        if (!(node instanceof MethodDeclaration method) || method.getBody() == null) return null;
        IMethodBinding fresh = method.resolveBinding();
        if (!admitted(fresh, mathematical) || !dispatch(fresh, retained)
                || Modifier.isStatic(binding.getModifiers()) != Modifier.isStatic(fresh.getModifiers())
                || !binding.getMethodDeclaration().getKey().equals(fresh.getMethodDeclaration().getKey())
                || !sameSignature(binding, fresh) || method.parameters().size() != arity
                || method.getBody().statements().size() > 64) return null;
        for (Object value : method.parameters()) {
            var parameter = (SingleVariableDeclaration) value;
            if (parameter.resolveBinding() == null || !scalar(parameter.resolveBinding().getType(), mathematical)) return null;
        }
        return method;
    }

    private static boolean admitted(IMethodBinding binding, boolean mathematical) {
        return binding != null && !binding.isRecovered() && !binding.isConstructor()
            && !Modifier.isSynchronized(binding.getModifiers()) && !Modifier.isNative(binding.getModifiers())
            && !Modifier.isAbstract(binding.getModifiers()) && !binding.isVarargs() && !binding.isGenericMethod()
            && !binding.isParameterizedMethod() && scalar(binding.getReturnType(), mathematical);
    }

    private static boolean dispatch(IMethodBinding binding, boolean retained) {
        int modifiers = binding.getModifiers();
        if (Modifier.isStatic(modifiers)) return retained || Modifier.isPrivate(modifiers);
        ITypeBinding owner = binding.getDeclaringClass();
        return owner != null && !owner.isRecovered() && !owner.isInterface()
            && (Modifier.isPrivate(modifiers) || Modifier.isFinal(modifiers) || Modifier.isFinal(owner.getModifiers()));
    }

    /** No invocation is removed: only a dominated local's exact primitive SAM call is summarized. */
    private static MethodDeclaration referencedMethod(CompilationUnit unit, MethodInvocation call,
            IMethodBinding invoked, boolean mathematical) {
        if (!(call.getExpression() instanceof SimpleName name)
                || !(name.resolveBinding() instanceof IVariableBinding local) || local.isRecovered() || local.isField()
                || !(local.isEffectivelyFinal() || Modifier.isFinal(local.getModifiers()))
                || !PRIMITIVE_SAMS.contains(local.getType().getQualifiedName())
                || !(unit.findDeclaringNode(local.getVariableDeclaration()) instanceof VariableDeclarationFragment declaration)
                || !(declaration.getParent() instanceof VariableDeclarationStatement statement)
                || statement.fragments().size() != 1 || !dominates(statement, call)
                || !(declaration.getInitializer() instanceof MethodReference reference) || !reference.typeArguments().isEmpty()) return null;
        IMethodBinding sam = local.getType().getFunctionalInterfaceMethod();
        if (sam == null || sam.isRecovered()
                || !sam.getMethodDeclaration().getKey().equals(invoked.getMethodDeclaration().getKey())
                || !PRIMITIVE_SAMS.contains(invoked.getDeclaringClass().getQualifiedName())) return null;
        IMethodBinding target = reference.resolveMethodBinding();
        if (!admitted(target, mathematical) || !dispatch(target, true) || !sameSignature(sam, target)
                || !integral(sam.getReturnType())) return null;
        for (ITypeBinding parameter : sam.getParameterTypes()) if (!integral(parameter)) return null;
        if (reference instanceof ExpressionMethodReference expression) {
            // Type-qualified static, or bound exact-dispatch instance. Bound receiver
            // creation (including factories and eager null checks) remains authored Java.
            if (Modifier.isStatic(target.getModifiers()) != typeName(expression.getExpression())) return null;
        } else if (!(reference instanceof TypeMethodReference) || !Modifier.isStatic(target.getModifiers())) return null;
        return definition(unit, target, call.arguments().size(), mathematical, true);
    }

    private static boolean sameSignature(IMethodBinding left, IMethodBinding right) {
        if (!sameType(left.getReturnType(), right.getReturnType())) return false;
        ITypeBinding[] a = left.getParameterTypes(), b = right.getParameterTypes();
        if (a.length != b.length) return false;
        for (int index = 0; index < a.length; index++) if (!sameType(a[index], b[index])) return false;
        return true;
    }
    private static boolean sameType(ITypeBinding left, ITypeBinding right) {
        return left != null && right != null && !left.isRecovered() && !right.isRecovered()
            && left.getQualifiedName().equals(right.getQualifiedName());
    }
    private static boolean typeName(Expression expression) {
        return expression instanceof Name name && name.resolveBinding() instanceof ITypeBinding;
    }
    private static boolean local(Expression expression) {
        return expression instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding binding
            && !binding.isRecovered() && !binding.isField();
    }
    private static boolean ownThis(Expression expression) {
        return expression == null || expression instanceof ThisExpression receiver && receiver.getQualifier() == null;
    }
    private static boolean sameCallerClass(ASTNode call, IMethodBinding binding) {
        MethodDeclaration caller = enclosingMethod(call);
        return caller != null && caller.resolveBinding() != null
            && binding.getDeclaringClass().isEqualTo(caller.resolveBinding().getDeclaringClass());
    }
    private static boolean dominates(VariableDeclarationStatement declaration, ASTNode invocation) {
        if (!(declaration.getParent() instanceof Block block)) return false;
        for (ASTNode node = invocation; node != null; node = node.getParent()) {
            if (node instanceof LambdaExpression || node instanceof MethodDeclaration
                    || node instanceof AnonymousClassDeclaration || node instanceof AbstractTypeDeclaration
                    || node instanceof Initializer) return false;
            if (node.getParent() == block)
                return declaration.getStartPosition() + declaration.getLength() <= node.getStartPosition();
        }
        return false;
    }

    static boolean integral(ITypeBinding type) {
        return type != null && type.isPrimitive()
                && List.of("byte", "short", "char", "int", "long").contains(type.getName()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    }

    static boolean scalar(ITypeBinding type, boolean mathematical) {
        return integral(type) || mathematical
                && JavaOperationRegistry.kind(type).orElse(null) == NumericKind.BIG_INTEGER;
    }

    private static MethodDeclaration enclosingMethod(ASTNode node) {
        for (ASTNode parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof MethodDeclaration method) return method;
        }
        return null;
    }
}

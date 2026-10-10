/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;

/** Source/binding contract only. Mathematical transformations belong to Regelsuche. */
final class JavaScalarMethodBody {
    private JavaScalarMethodBody() { }

    static MethodDeclaration resolve(CompilationUnit unit, MethodInvocation call) {
        IMethodBinding binding = call.resolveMethodBinding();
        if (binding == null || binding.isRecovered() || !Modifier.isPrivate(binding.getModifiers())
                || !Modifier.isStatic(binding.getModifiers()) || Modifier.isSynchronized(binding.getModifiers())
                || Modifier.isNative(binding.getModifiers()) || binding.isVarargs()
                || binding.isGenericMethod() || !integral(binding.getReturnType())) return null;
        // Same type: removing the call must not remove another class's initialization.
        MethodDeclaration caller = enclosingMethod(call);
        if (caller == null || caller.resolveBinding() == null
                || !binding.getDeclaringClass().isEqualTo(caller.resolveBinding().getDeclaringClass())) return null;
        ASTNode node = unit.findDeclaringNode(binding.getMethodDeclaration());
        if (!(node instanceof MethodDeclaration method) || method.getBody() == null
                || method.parameters().size() != call.arguments().size()
                || method.getBody().statements().size() > 64) return null;
        for (Object value : method.parameters()) {
            var parameter = (SingleVariableDeclaration) value;
            if (parameter.resolveBinding() == null || !integral(parameter.resolveBinding().getType())) return null;
        }
        return method;
    }

    static boolean integral(ITypeBinding type) {
        return type != null && type.isPrimitive()
                && List.of("byte", "short", "char", "int", "long").contains(type.getName()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    }

    private static MethodDeclaration enclosingMethod(ASTNode node) {
        for (ASTNode parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof MethodDeclaration method) return method;
        }
        return null;
    }
}

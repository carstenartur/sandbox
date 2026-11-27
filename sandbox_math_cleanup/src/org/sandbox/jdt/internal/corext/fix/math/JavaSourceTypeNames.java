/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.function.LongConsumer;
import org.eclipse.jdt.core.dom.*;

/** Source-stable names for type and field evidence. Ancestor and import scans consume the caller's work budget. */
final class JavaSourceTypeNames {
    private JavaSourceTypeNames() {}

    static AbstractTypeDeclaration enclosingType(ASTNode use, LongConsumer charge) {
        ASTNode scope = use.getParent();
        while (scope != null && !(scope instanceof AbstractTypeDeclaration) && !(scope instanceof AnonymousClassDeclaration)) {
            charge.accept(1);
            scope = scope.getParent();
        }
        return scope instanceof AbstractTypeDeclaration type ? type : null;
    }
    static boolean directFieldRead(Name name, IVariableBinding field, LongConsumer charge) {
        var owner = field.getDeclaringClass();
        if (!field.isField() || owner == null || owner.isRecovered()) return false;
        if (name instanceof QualifiedName q) return closedTypeName(q.getQualifier(), owner, charge);
        var lexical = enclosingType(name, charge);
        return lexical != null && owner.isEqualTo(lexical.resolveBinding());
    }
    /** A selected simple type cannot acquire a higher-priority inherited or same-package meaning. */
    static boolean closedType(Type type, LongConsumer charge) {
        return type instanceof SimpleType simple && closedTypeName(simple.getName(), type.resolveBinding(), charge);
    }
    static boolean closedTypeName(Name name, ITypeBinding binding, LongConsumer charge) {
        if (!(name instanceof SimpleName) || binding == null || binding.isRecovered() || !binding.isTopLevel()
                || !binding.isEqualTo(name.resolveBinding())) return false;
        for (ASTNode scope = name.getParent(); scope != null; scope = scope.getParent()) {
            charge.accept(1);
            if (scope instanceof CompilationUnit ast) return visibleType(ast, binding, charge);
            if (scope instanceof AnonymousClassDeclaration || scope instanceof AbstractTypeDeclaration
                    && !(scope instanceof TypeDeclaration t && !t.isInterface() && t.getSuperclassType() == null
                        && t.superInterfaceTypes().isEmpty())) return false;
        }
        return false;
    }
    private static boolean visibleType(CompilationUnit ast, ITypeBinding declaring, LongConsumer charge) {
        String canonical = declaring.getQualifiedName();
        String pkg = ast.getPackage() == null ? "" : ast.getPackage().getName().getFullyQualifiedName() + ".";
        boolean visible = canonical.equals(pkg + declaring.getName());
        for (Object item : ast.imports()) {
            charge.accept(1);
            var entry = (ImportDeclaration)item;
            if (entry.isOnDemand() || entry.isStatic()) return false;
            String imported = entry.getName().getFullyQualifiedName();
            if (imported.equals(canonical)) visible = true;
        }
        return visible;
    }
}

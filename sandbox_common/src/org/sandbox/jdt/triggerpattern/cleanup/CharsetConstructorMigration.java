/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.cleanup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;

/** Shared bridge from real Charset constructor migrations to exception planning. */
public final class CharsetConstructorMigration {
    private static final String INSTALLED= CharsetConstructorMigration.class.getName();
    private static final Set<String> CHARSETS= Set.of("UTF-8", "UTF-16", "UTF-16BE", "UTF-16LE", "US-ASCII", "ISO-8859-1"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
    private CharsetConstructorMigration() { }

    /** Collect only constructors whose actual Charset overload adds IOException. */
    public static CheckedExceptionMigration.Plan plan(Collection<CompilationUnit> roots) throws CoreException {
        return plan(roots, new NullProgressMonitor());
    }

    public static CheckedExceptionMigration.Plan plan(Collection<CompilationUnit> roots, IProgressMonitor monitor) throws CoreException {
        List<ClassInstanceCreation> changes= new ArrayList<>();
        ITypeBinding[] added= { null };
        for (CompilationUnit root : roots) {
            if (root.getJavaElement() == null || !org.eclipse.jdt.internal.corext.util.JavaModelUtil.is10OrHigher(root.getJavaElement().getJavaProject())) continue;
            root.accept(new ASTVisitor() {
            @Override public boolean visit(ClassInstanceCreation node) {
                IMethodBinding target= target(node);
                if (target != null && convertible(node)) {
                    for (ITypeBinding exception : target.getExceptionTypes()) {
                        if ("java.io.IOException".equals(exception.getQualifiedName())) { //$NON-NLS-1$
                            added[0]= exception;
                            changes.add(node);
                        }
                    }
                }
                return true;
            }
            });
        }
        return changes.isEmpty() ? null : CheckedExceptionMigration.plan(roots, changes, added[0], "java.io.FileNotFoundException", monitor); //$NON-NLS-1$
    }

    public static void install(CompilationUnitRewrite rewrite, CheckedExceptionMigration.Plan plan) {
        rewrite.getASTRewrite().setProperty(INSTALLED, plan);
    }

    /** Used by the standalone DSL path as well as the encoding cleanup. */
    public static void adapt(ASTNode original, ASTNode replacement, String replacementText, CompilationUnitRewrite rewrite, TextEditGroup group) throws CoreException {
        if (rewrite.getASTRewrite().getProperty(INSTALLED) != null) return;
        if (!(original instanceof ClassInstanceCreation creation) || !(replacement instanceof ClassInstanceCreation)) return;
        IMethodBinding target= target(creation);
        if (target == null || !usesTargetOverload(creation, target, replacementText)) return;
        for (ITypeBinding exception : target.getExceptionTypes()) {
            if ("java.io.IOException".equals(exception.getQualifiedName())) { //$NON-NLS-1$
                CheckedExceptionMigration.plan(List.of(rewrite.getRoot()), List.of(original), exception,
                        "java.io.FileNotFoundException").apply(rewrite, group); //$NON-NLS-1$
            }
        }
    }

    /** Use AST edits and the shared NLS finisher, never a textual enclosing try. */
    static boolean rewriteExpression(ASTNode original, ASTNode replacement, String text,
            CompilationUnitRewrite rewrite, TextEditGroup group) {
        if (!(original instanceof ClassInstanceCreation creation) || !(replacement instanceof ClassInstanceCreation copy)) return false;
        if (rewriteStringConstructor(creation, copy, rewrite, group)) return true;
        IMethodBinding target= target(creation);
        if (target == null || !usesTargetOverload(creation, target, text)) return false;
        if (creation.arguments().size() > 1 && creation.arguments().get(1) instanceof StringLiteral literal)
            EncodingSourceRewrite.record(rewrite, literal);
        copy.arguments().set(0, rewrite.getASTRewrite().createMoveTarget((ASTNode) creation.arguments().get(0)));
        rewrite.getASTRewrite().replace(original, copy, group);
        return true;
    }

    /** Preserve sibling and nested edits instead of replacing their entire statement for NLS. */
    private static boolean rewriteStringConstructor(ClassInstanceCreation original, ClassInstanceCreation copy,
            CompilationUnitRewrite rewrite, TextEditGroup group) {
        IMethodBinding binding= original.resolveConstructorBinding();
        if (binding == null || binding.isRecovered()
                || !"java.lang.String".equals(binding.getDeclaringClass().getQualifiedName())) return false; //$NON-NLS-1$
        ITypeBinding[] parameters= binding.getParameterTypes();
        int count= parameters.length;
        if ((count != 2 && count != 4) || copy.arguments().size() != count
                || !"byte[]".equals(parameters[0].getQualifiedName()) //$NON-NLS-1$
                || !"java.lang.String".equals(parameters[count - 1].getQualifiedName()) //$NON-NLS-1$
                || TypeChangeDetector.detectCharsetTypeChange(original, copy) == null) return false;
        String type= copy.getType().toString();
        if (!type.equals(original.getType().toString()) && !"java.lang.String".equals(type)) return false; //$NON-NLS-1$
        for (int index= 0; index < count - 1; index++) {
            if (!((ASTNode) original.arguments().get(index)).subtreeMatch(new ASTMatcher(), copy.arguments().get(index))) return false;
        }
        EncodingSourceRewrite.record(rewrite, (ASTNode) original.arguments().get(count - 1));
        for (int index= 0; index < count - 1; index++) {
            copy.arguments().set(index, rewrite.getASTRewrite().createMoveTarget((ASTNode) original.arguments().get(index)));
        }
        rewrite.getASTRewrite().replace(original, copy, group);
        return true;
    }

    private static boolean usesTargetOverload(ClassInstanceCreation original, IMethodBinding target, String text) {
        ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_EXPRESSION);
        parser.setSource(text.toCharArray());
        ASTNode parsed= parser.createAST(null);
        if (!(parsed instanceof ClassInstanceCreation call) || (parsed.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0
                || call.arguments().size() != target.getParameterTypes().length) return false;
        String name= call.getType().toString();
        if (!name.equals(target.getDeclaringClass().getQualifiedName()) && !name.equals(original.getType().toString())) return false;
        if (!((ASTNode) original.arguments().get(0)).subtreeMatch(new ASTMatcher(), call.arguments().get(0))) return false;
        Expression argument= (Expression) call.arguments().get(1);
        while (argument instanceof ParenthesizedExpression parentheses) argument= parentheses.getExpression();
        // Inspect the unshortened recipe, before ImportRewrite introduces simple names.
        // A .name() call is deliberately not a typed Charset argument.
        if (argument instanceof QualifiedName field) return "java.nio.charset.StandardCharsets".equals(field.getQualifier().getFullyQualifiedName()) //$NON-NLS-1$
                && CHARSETS.contains(field.getName().getIdentifier().replace('_', '-'));
        return argument instanceof MethodInvocation method && method.arguments().isEmpty()
                && "defaultCharset".equals(method.getName().getIdentifier()) //$NON-NLS-1$
                && method.getExpression() instanceof Name owner && "java.nio.charset.Charset".equals(owner.getFullyQualifiedName()); //$NON-NLS-1$
    }

    private static boolean convertible(ClassInstanceCreation node) {
        return node.arguments().size() == 1 || node.arguments().get(1) instanceof StringLiteral literal
                && CHARSETS.contains(literal.getLiteralValue().toUpperCase(Locale.ROOT));
    }

    private static IMethodBinding target(ClassInstanceCreation node) {
        IMethodBinding old= node.resolveConstructorBinding();
        if (old == null || old.isRecovered() || node.getAnonymousClassDeclaration() != null) return null;
        ITypeBinding[] parameters= old.getParameterTypes();
        if (parameters.length == 0 || parameters.length > 3) return null;
        String owner= old.getDeclaringClass().getQualifiedName();
        String source= parameters[0].getQualifiedName();
        if (!("java.util.Scanner".equals(owner) && "java.io.File".equals(source)) //$NON-NLS-1$ //$NON-NLS-2$
                && !("java.util.Formatter".equals(owner) && ("java.io.File".equals(source) || "java.lang.String".equals(source)))) return null; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if (parameters.length > 1 && !"java.lang.String".equals(parameters[1].getQualifiedName())) return null; //$NON-NLS-1$
        for (IMethodBinding method : old.getDeclaringClass().getDeclaredMethods()) {
            ITypeBinding[] types= method.getParameterTypes();
            if (method.isConstructor() && types.length == ("java.util.Scanner".equals(owner) ? 2 : 3) //$NON-NLS-1$
                    && source.equals(types[0].getQualifiedName()) && "java.nio.charset.Charset".equals(types[1].getQualifiedName())) return method; //$NON-NLS-1$
        }
        return null;
    }
}

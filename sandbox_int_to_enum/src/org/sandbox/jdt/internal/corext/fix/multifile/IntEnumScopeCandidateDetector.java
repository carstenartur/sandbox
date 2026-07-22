/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;

/** Lightweight pre-scan used before requesting complete-project Int-to-Enum scope. */
public final class IntEnumScopeCandidateDetector {

	private IntEnumScopeCandidateDetector() {
		// utility class
	}

	/**
	 * Returns whether the current selection contains the structural prerequisites of
	 * the coordinated package-scoped migration.
	 * <p>
	 * This is deliberately a conservative gate rather than the semantic planner. A
	 * positive result may still be rejected after complete-project analysis; a
	 * negative result proves that the selected units contain no owner declaration
	 * that the current coordinated planner could migrate.
	 * </p>
	 *
	 * @param project Java project owning the current cleanup scope
	 * @param currentScope compilation units currently selected by the user or an
	 *            earlier scope-expansion iteration
	 * @param monitor progress monitor, may be {@code null}
	 * @return {@code true} when complete-project fallback analysis may be required
	 */
	public static boolean containsCandidate(IJavaProject project, Collection<ICompilationUnit> currentScope,
			IProgressMonitor monitor) {
		if (currentScope == null || currentScope.isEmpty()) {
			return false;
		}
		checkCanceled(monitor);
		Set<ICompilationUnit> units= new LinkedHashSet<>();
		for (ICompilationUnit unit : currentScope) {
			if (unit != null && unit.exists() && project.equals(unit.getJavaProject())) {
				units.add(unit.getPrimary());
			}
		}
		if (units.isEmpty()) {
			return false;
		}

		AtomicBoolean candidate= new AtomicBoolean();
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(project);
		parser.setResolveBindings(false);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.createASTs(units.toArray(ICompilationUnit[]::new), new String[0], new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				if (candidate.get()) {
					return;
				}
				ast.accept(new ASTVisitor() {
					@Override
					public boolean visit(TypeDeclaration type) {
						if (!(type.getParent() instanceof CompilationUnit) || type.isInterface()
								|| type.getSuperclassType() != null || !type.superInterfaceTypes().isEmpty()) {
							return false;
						}
						if (countPackagePrivateIntConstants(type) >= 2 && hasPackagePrivateIntStateMethod(type)) {
							candidate.set(true);
						}
						return false;
					}
				});
			}
		}, monitor);
		checkCanceled(monitor);
		return candidate.get();
	}

	private static int countPackagePrivateIntConstants(TypeDeclaration type) {
		int count= 0;
		for (FieldDeclaration field : type.getFields()) {
			int modifiers= field.getModifiers();
			if (isPackagePrivate(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
					&& isPlainInt(field.getType())) {
				count+= field.fragments().size();
			}
		}
		return count;
	}

	private static boolean hasPackagePrivateIntStateMethod(TypeDeclaration type) {
		for (MethodDeclaration method : type.getMethods()) {
			if (method.isConstructor() || method.getBody() == null || !isPackagePrivate(method.getModifiers())) {
				continue;
			}
			for (Object parameterObject : method.parameters()) {
				SingleVariableDeclaration parameter= (SingleVariableDeclaration) parameterObject;
				if (parameter.getExtraDimensions() == 0 && !parameter.isVarargs() && isPlainInt(parameter.getType())) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isPlainInt(Type type) {
		return type instanceof PrimitiveType primitive
				&& primitive.getPrimitiveTypeCode() == PrimitiveType.INT;
	}

	private static boolean isPackagePrivate(int modifiers) {
		return !Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers) && !Modifier.isPrivate(modifiers);
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}
}

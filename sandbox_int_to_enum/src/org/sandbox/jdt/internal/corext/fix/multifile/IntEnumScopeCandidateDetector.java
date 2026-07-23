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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;

/** Lightweight selected-scope detector for project-wide Int-to-Enum planning. */
public final class IntEnumScopeCandidateDetector {

	/** Binding-derived elements whose references define the required source closure. */
	public record SearchSeeds(boolean candidateFound, boolean complete, List<IJavaElement> elements) {
		public SearchSeeds {
			elements= List.copyOf(elements);
		}
	}

	private IntEnumScopeCandidateDetector() {
		// utility class
	}

	/** Returns whether the current selection contains a structural candidate. */
	public static boolean containsCandidate(IJavaProject project, Collection<ICompilationUnit> currentScope,
			IProgressMonitor monitor) {
		return findSearchSeeds(project, currentScope, monitor).candidateFound();
	}

	/**
	 * Finds the candidate methods and constants whose references define the
	 * coordinated source closure. Candidate recognition deliberately uses a first,
	 * binding-independent parse. Binding resolution is a second refinement step;
	 * when it cannot reproduce or resolve the structural candidate, the result stays
	 * a candidate but is marked incomplete so the caller uses the conservative
	 * complete-policy fallback instead of silently suppressing the cleanup.
	 */
	public static SearchSeeds findSearchSeeds(IJavaProject project, Collection<ICompilationUnit> currentScope,
			IProgressMonitor monitor) {
		Set<ICompilationUnit> units= normalize(project, currentScope);
		if (units.isEmpty() || !containsStructuralCandidate(project, units, monitor)) {
			return new SearchSeeds(false, true, List.of());
		}

		checkCanceled(monitor);
		boolean[] resolvedCandidate= { false };
		boolean[] complete= { true };
		Set<IJavaElement> elements= new LinkedHashSet<>();
		ASTParser parser= newParser(project, true);
		parser.createASTs(units.toArray(ICompilationUnit[]::new), new String[0], new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				ast.accept(new ASTVisitor() {
					@Override
					public boolean visit(TypeDeclaration type) {
						if (!isCandidateOwner(type)) {
							return false;
						}
						List<VariableDeclarationFragment> constants= packagePrivateIntConstants(type);
						List<MethodDeclaration> methods= packagePrivateIntStateMethods(type);
						if (constants.size() < 2 || methods.isEmpty()) {
							return false;
						}
						resolvedCandidate[0]= true;
						for (VariableDeclarationFragment constant : constants) {
							IVariableBinding binding= constant.resolveBinding();
							complete[0]&= addJavaElement(binding, elements);
						}
						for (MethodDeclaration method : methods) {
							IMethodBinding binding= method.resolveBinding();
							complete[0]&= addJavaElement(binding, elements);
						}
						return false;
					}
				});
			}
		}, monitor);
		checkCanceled(monitor);
		return new SearchSeeds(true, complete[0] && resolvedCandidate[0], new ArrayList<>(elements));
	}

	private static boolean containsStructuralCandidate(IJavaProject project, Set<ICompilationUnit> units,
			IProgressMonitor monitor) {
		checkCanceled(monitor);
		boolean[] candidate= { false };
		ASTParser parser= newParser(project, false);
		parser.createASTs(units.toArray(ICompilationUnit[]::new), new String[0], new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				if (candidate[0]) {
					return;
				}
				ast.accept(new ASTVisitor() {
					@Override
					public boolean visit(TypeDeclaration type) {
						if (isCandidateOwner(type)
								&& packagePrivateIntConstants(type).size() >= 2
								&& !packagePrivateIntStateMethods(type).isEmpty()) {
							candidate[0]= true;
						}
						return false;
					}
				});
			}
		}, monitor);
		checkCanceled(monitor);
		return candidate[0];
	}

	private static ASTParser newParser(IJavaProject project, boolean resolveBindings) {
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(project);
		parser.setResolveBindings(resolveBindings);
		parser.setBindingsRecovery(resolveBindings && IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		return parser;
	}

	private static Set<ICompilationUnit> normalize(IJavaProject project,
			Collection<ICompilationUnit> currentScope) {
		Set<ICompilationUnit> units= new LinkedHashSet<>();
		if (project == null || currentScope == null) {
			return units;
		}
		for (ICompilationUnit unit : currentScope) {
			if (unit != null && unit.exists() && project.equals(unit.getJavaProject())) {
				units.add(unit.getPrimary());
			}
		}
		return units;
	}

	private static boolean isCandidateOwner(TypeDeclaration type) {
		return type.getParent() instanceof CompilationUnit && !type.isInterface()
				&& type.getSuperclassType() == null && type.superInterfaceTypes().isEmpty();
	}

	private static List<VariableDeclarationFragment> packagePrivateIntConstants(TypeDeclaration type) {
		List<VariableDeclarationFragment> result= new ArrayList<>();
		for (FieldDeclaration field : type.getFields()) {
			int modifiers= field.getModifiers();
			if (!isPackagePrivate(modifiers) || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)
					|| !isPlainInt(field.getType())) {
				continue;
			}
			for (Object fragment : field.fragments()) {
				result.add((VariableDeclarationFragment) fragment);
			}
		}
		return result;
	}

	private static List<MethodDeclaration> packagePrivateIntStateMethods(TypeDeclaration type) {
		List<MethodDeclaration> result= new ArrayList<>();
		for (MethodDeclaration method : type.getMethods()) {
			if (method.isConstructor() || method.getBody() == null || !isPackagePrivate(method.getModifiers())) {
				continue;
			}
			for (Object parameterObject : method.parameters()) {
				SingleVariableDeclaration parameter= (SingleVariableDeclaration) parameterObject;
				if (parameter.getExtraDimensions() == 0 && !parameter.isVarargs() && isPlainInt(parameter.getType())) {
					result.add(method);
					break;
				}
			}
		}
		return result;
	}

	private static boolean addJavaElement(IBinding binding, Set<IJavaElement> elements) {
		IJavaElement element= binding == null ? null : binding.getJavaElement();
		if (element == null || !element.exists()) {
			return false;
		}
		elements.add(element);
		return true;
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

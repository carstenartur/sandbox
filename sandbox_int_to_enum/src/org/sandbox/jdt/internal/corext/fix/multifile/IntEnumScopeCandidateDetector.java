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
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

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
	 * coordinated source closure. A structural candidate with a missing or
	 * recovered binding is reported as incomplete so the caller can use its
	 * conservative fallback.
	 */
	public static SearchSeeds findSearchSeeds(IJavaProject project, Collection<ICompilationUnit> currentScope,
			IProgressMonitor monitor) {
		if (project == null || currentScope == null || currentScope.isEmpty()) {
			return new SearchSeeds(false, true, List.of());
		}
		checkCanceled(monitor);
		Set<ICompilationUnit> sourceUnits= new LinkedHashSet<>();
		Set<ICompilationUnit> primaryUnits= new LinkedHashSet<>();
		for (ICompilationUnit unit : currentScope) {
			if (unit != null && unit.exists() && project.equals(unit.getJavaProject())) {
				sourceUnits.add(unit);
				primaryUnits.add(unit.getPrimary());
			}
		}
		if (primaryUnits.isEmpty()) {
			return new SearchSeeds(false, true, List.of());
		}

		boolean[] candidateFound= { false };
		boolean[] complete= { true };
		Set<IJavaElement> elements= new LinkedHashSet<>();
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(project);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(project));
		parser.createASTs(primaryUnits.toArray(ICompilationUnit[]::new), new String[0], new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				ast.accept(new ASTVisitor() {
					@Override
					public boolean visit(TypeDeclaration type) {
						if (!isStructuralCandidate(type)) {
							return false;
						}
						candidateFound[0]= true;
						for (VariableDeclarationFragment constant : packagePrivateIntConstants(type)) {
							IVariableBinding binding= constant.resolveBinding();
							complete[0]&= addJavaElement(binding, elements);
						}
						for (MethodDeclaration method : packagePrivateIntStateMethods(type)) {
							IMethodBinding binding= method.resolveBinding();
							complete[0]&= addJavaElement(binding, elements);
						}
						return false;
					}
				});
			}
		}, monitor);
		if (!candidateFound[0] && containsSyntacticCandidate(project, sourceUnits, monitor)) {
			candidateFound[0]= true;
			complete[0]= false;
		}
		checkCanceled(monitor);
		return new SearchSeeds(candidateFound[0], complete[0], new ArrayList<>(elements));
	}

	private static boolean containsSyntacticCandidate(IJavaProject project,
			Collection<ICompilationUnit> units, IProgressMonitor monitor) {
		for (ICompilationUnit unit : units) {
			checkCanceled(monitor);
			CompilationUnit ast= parseSource(project, unit, monitor);
			boolean[] found= { false };
			ast.accept(new ASTVisitor() {
				@Override
				public boolean visit(TypeDeclaration type) {
					if (isStructuralCandidate(type)) {
						found[0]= true;
						return false;
					}
					return true;
				}
			});
			if (found[0]) {
				return true;
			}
		}
		return false;
	}

	private static CompilationUnit parseSource(IJavaProject project, ICompilationUnit unit,
			IProgressMonitor monitor) {
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setSource(unit);
		parser.setProject(project);
		parser.setResolveBindings(false);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(project));
		return (CompilationUnit) parser.createAST(monitor);
	}

	private static boolean isStructuralCandidate(TypeDeclaration type) {
		return type.getParent() instanceof CompilationUnit && !type.isInterface()
				&& type.getSuperclassType() == null && type.superInterfaceTypes().isEmpty()
				&& packagePrivateIntConstants(type).size() >= 2
				&& !packagePrivateIntStateMethods(type).isEmpty();
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
		if (binding == null || binding.isRecovered()) {
			return false;
		}
		IJavaElement element= binding.getJavaElement();
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

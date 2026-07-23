/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_CLASS_RULE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE;

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
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

/** Lightweight selected-scope detector for coordinated JUnit resource migration. */
public final class JUnitScopeCandidateDetector {

	/** Binding-derived resource types whose references define the required closure. */
	public record SearchSeeds(boolean candidateFound, boolean complete, List<IJavaElement> elements) {
		public SearchSeeds {
			elements= List.copyOf(elements);
		}
	}

	private JUnitScopeCandidateDetector() {
		// utility class
	}

	/** Returns whether the selection contains either side of a resource migration. */
	public static boolean containsCandidate(IJavaProject project, Collection<ICompilationUnit> currentScope,
			IProgressMonitor monitor) {
		return findSearchSeeds(project, currentScope, monitor).candidateFound();
	}

	/**
	 * Finds source resource types referenced by selected declarations or Rule fields.
	 * Syntactically recognizable candidates with missing or recovered bindings are
	 * marked incomplete so callers preserve the existing complete-policy fallback.
	 */
	public static SearchSeeds findSearchSeeds(IJavaProject project, Collection<ICompilationUnit> currentScope,
			IProgressMonitor monitor) {
		if (project == null || currentScope == null || currentScope.isEmpty()) {
			return new SearchSeeds(false, true, List.of());
		}
		checkCanceled(monitor);
		Set<ICompilationUnit> units= new LinkedHashSet<>();
		for (ICompilationUnit unit : currentScope) {
			if (unit != null && unit.exists() && project.equals(unit.getJavaProject())) {
				units.add(unit.getPrimary());
			}
		}
		if (units.isEmpty()) {
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
		parser.createASTs(units.toArray(ICompilationUnit[]::new), new String[0], new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				ast.accept(new ASTVisitor() {
					@Override
					public boolean visit(TypeDeclaration node) {
						ITypeBinding binding= node.resolveBinding();
						ITypeBinding superclass= binding == null ? null : binding.getSuperclass();
						if (ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(qualifiedName(superclass))) {
							candidateFound[0]= true;
							complete[0]&= addJavaElement(binding, elements);
							return false;
						}
						if (hasIncompleteIdentity(superclass) && node.getSuperclassType() != null
								&& "ExternalResource".equals(simpleName(node.getSuperclassType().toString()))) { //$NON-NLS-1$
							candidateFound[0]= true;
							complete[0]= false;
							return false;
						}
						return true;
					}

					@Override
					public boolean visit(FieldDeclaration node) {
						boolean ruleField= false;
						boolean unresolvedRuleAnnotation= false;
						for (Object modifier : node.modifiers()) {
							if (!(modifier instanceof Annotation annotation)) {
								continue;
							}
							ITypeBinding annotationBinding= annotation.resolveTypeBinding();
							String qualifiedName= qualifiedName(annotationBinding);
							if (ORG_JUNIT_RULE.equals(qualifiedName) || ORG_JUNIT_CLASS_RULE.equals(qualifiedName)) {
								ruleField= true;
							} else if (hasIncompleteIdentity(annotationBinding)
									&& isSyntacticRuleName(annotation.getTypeName().getFullyQualifiedName())) {
								ruleField= true;
								unresolvedRuleAnnotation= true;
							}
						}
						if (!ruleField) {
							return true;
						}
						candidateFound[0]= true;
						if (unresolvedRuleAnnotation) {
							complete[0]= false;
						}
						ITypeBinding fieldType= node.getType().resolveBinding();
						complete[0]&= addJavaElement(fieldType, elements);
						return true;
					}
				});
			}
		}, monitor);
		checkCanceled(monitor);
		return new SearchSeeds(candidateFound[0], complete[0], new ArrayList<>(elements));
	}

	private static boolean isSyntacticRuleName(String name) {
		String simple= simpleName(name);
		return "Rule".equals(simple) || "ClassRule".equals(simple); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String simpleName(String name) {
		int separator= name.lastIndexOf('.');
		return separator < 0 ? name : name.substring(separator + 1);
	}

	private static boolean hasIncompleteIdentity(ITypeBinding binding) {
		return binding == null || binding.isRecovered() || qualifiedName(binding).isEmpty();
	}

	private static String qualifiedName(ITypeBinding binding) {
		return binding == null ? "" : binding.getErasure().getQualifiedName(); //$NON-NLS-1$
	}

	private static boolean addJavaElement(ITypeBinding binding, Set<IJavaElement> elements) {
		IJavaElement element= binding == null ? null : binding.getErasure().getJavaElement();
		if (element == null || !element.exists()) {
			return false;
		}
		elements.add(element);
		return true;
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}
}

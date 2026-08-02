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
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_SUITE_SUITECLASSES;

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
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

/** Lightweight selected-scope detector for coordinated JUnit migration. */
public final class JUnitScopeCandidateDetector {

	/** Binding-derived elements and direct source dependencies defining the required closure. */
	public record SearchSeeds(boolean candidateFound, boolean complete, List<IJavaElement> elements,
			List<ICompilationUnit> directCompilationUnits) {
		public SearchSeeds {
			elements= List.copyOf(elements);
			directCompilationUnits= List.copyOf(directCompilationUnits);
		}

		/** Compatibility constructor for the original ExternalResource-only detector. */
		public SearchSeeds(boolean candidateFound, boolean complete, List<IJavaElement> elements) {
			this(candidateFound, complete, elements, List.of());
		}
	}

	private JUnitScopeCandidateDetector() {
		// utility class
	}

	/** Returns whether the selection contains either side of an ExternalResource migration. */
	public static boolean containsCandidate(IJavaProject project, Collection<ICompilationUnit> currentScope,
			IProgressMonitor monitor) {
		return findSearchSeeds(project, currentScope, monitor).candidateFound();
	}

	/** Finds ExternalResource migration seeds using the historical detector contract. */
	public static SearchSeeds findSearchSeeds(IJavaProject project, Collection<ICompilationUnit> currentScope,
			IProgressMonitor monitor) {
		return findSearchSeeds(project, currentScope, true, false, monitor);
	}

	/**
	 * Finds source dependencies required by the enabled coordinated JUnit components.
	 * ExternalResource declarations use reverse-reference search seeds; JUnit 4 suite
	 * annotations add their directly referenced source test classes.
	 */
	public static SearchSeeds findSearchSeeds(IJavaProject project, Collection<ICompilationUnit> currentScope,
			boolean migrateExternalResourceRules, boolean migrateSuites, IProgressMonitor monitor) {
		if (project == null || currentScope == null || currentScope.isEmpty()
				|| !migrateExternalResourceRules && !migrateSuites) {
			return new SearchSeeds(false, true, List.of(), List.of());
		}
		checkCanceled(monitor);
		Set<ICompilationUnit> units= new LinkedHashSet<>();
		for (ICompilationUnit unit : currentScope) {
			if (unit != null && unit.exists() && project.equals(unit.getJavaProject())) {
				units.add(unit.getPrimary());
			}
		}
		if (units.isEmpty()) {
			return new SearchSeeds(false, true, List.of(), List.of());
		}

		boolean[] candidateFound= { false };
		boolean[] complete= { true };
		Set<IJavaElement> elements= new LinkedHashSet<>();
		Set<ICompilationUnit> directUnits= new LinkedHashSet<>();
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
						if (!migrateExternalResourceRules) {
							return true;
						}
						ITypeBinding binding= node.resolveBinding();
						if (binding != null && extendsExternalResource(binding)) {
							candidateFound[0]= true;
							complete[0]&= addJavaElement(binding, elements);
							complete[0]&= addExternalResourceSuperTypes(binding, elements);
							return false;
						}
						if (binding == null && node.getSuperclassType() != null
								&& "ExternalResource".equals(simpleName(node.getSuperclassType().toString()))) { //$NON-NLS-1$
							candidateFound[0]= true;
							complete[0]= false;
						}
						return true;
					}

					@Override
					public boolean visit(FieldDeclaration node) {
						if (!migrateExternalResourceRules) {
							return true;
						}
						boolean ruleField= false;
						boolean unresolvedRuleAnnotation= false;
						for (Object modifier : node.modifiers()) {
							if (!(modifier instanceof Annotation annotation)) {
								continue;
							}
							ITypeBinding annotationBinding= annotation.resolveTypeBinding();
							if (annotationBinding != null) {
								String qualifiedName= annotationBinding.getQualifiedName();
								ruleField|= ORG_JUNIT_RULE.equals(qualifiedName)
										|| ORG_JUNIT_CLASS_RULE.equals(qualifiedName);
							} else if (isSyntacticRuleName(annotation.getTypeName().getFullyQualifiedName())) {
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
						if (fieldType != null && extendsExternalResource(fieldType)) {
							complete[0]&= addExternalResourceSuperTypes(fieldType, elements);
						}
						return true;
					}

					@Override
					public boolean visit(SingleMemberAnnotation node) {
						if (migrateSuites && isSuiteClasses(node)) {
							candidateFound[0]= true;
							complete[0]&= collectSuiteTargets(node.getValue(), directUnits);
						}
						return true;
					}

					@Override
					public boolean visit(NormalAnnotation node) {
						if (!migrateSuites || !isSuiteClasses(node)) {
							return true;
						}
						candidateFound[0]= true;
						boolean valueFound= false;
						for (Object valueObject : node.values()) {
							MemberValuePair pair= (MemberValuePair) valueObject;
							if ("value".equals(pair.getName().getIdentifier())) { //$NON-NLS-1$
								valueFound= true;
								complete[0]&= collectSuiteTargets(pair.getValue(), directUnits);
							}
						}
						complete[0]&= valueFound;
						return true;
					}
				});
			}
		}, monitor);
		checkCanceled(monitor);
		return new SearchSeeds(candidateFound[0], complete[0], new ArrayList<>(elements),
				new ArrayList<>(directUnits));
	}

	private static boolean isSuiteClasses(Annotation annotation) {
		ITypeBinding binding= annotation.resolveTypeBinding();
		if (binding != null) {
			return ORG_JUNIT_SUITE_SUITECLASSES.equals(binding.getQualifiedName());
		}
		return "SuiteClasses".equals(simpleName(annotation.getTypeName().getFullyQualifiedName())); //$NON-NLS-1$
	}

	private static boolean collectSuiteTargets(Expression expression, Set<ICompilationUnit> units) {
		if (expression instanceof TypeLiteral literal) {
			return addCompilationUnit(literal.getType().resolveBinding(), units);
		}
		if (expression instanceof ArrayInitializer initializer) {
			boolean complete= true;
			for (Object expressionObject : initializer.expressions()) {
				complete&= expressionObject instanceof Expression nested && collectSuiteTargets(nested, units);
			}
			return complete;
		}
		return false;
	}

	private static boolean isSyntacticRuleName(String name) {
		String simple= simpleName(name);
		return "Rule".equals(simple) || "ClassRule".equals(simple); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** Returns whether the superclass chain reaches {@code ExternalResource}. */
	private static boolean extendsExternalResource(ITypeBinding binding) {
		for (ITypeBinding current= binding == null ? null : binding.getSuperclass(); current != null;
				current= current.getSuperclass()) {
			if (ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(current.getErasure().getQualifiedName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Seeds every source type between a fixture and {@code ExternalResource}, because
	 * the inherited callbacks are renamed together with the fixture.
	 */
	private static boolean addExternalResourceSuperTypes(ITypeBinding binding, Set<IJavaElement> elements) {
		boolean complete= true;
		for (ITypeBinding current= binding.getSuperclass(); current != null; current= current.getSuperclass()) {
			if (ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(current.getErasure().getQualifiedName())) {
				return complete;
			}
			complete&= addJavaElement(current, elements);
		}
		return false;
	}

	private static String simpleName(String name) {
		int separator= name.lastIndexOf('.');
		return separator < 0 ? name : name.substring(separator + 1);
	}

	private static boolean addJavaElement(ITypeBinding binding, Set<IJavaElement> elements) {
		IJavaElement element= binding == null ? null : binding.getErasure().getJavaElement();
		if (element == null || !element.exists()) {
			return false;
		}
		elements.add(element);
		return true;
	}

	private static boolean addCompilationUnit(ITypeBinding binding, Set<ICompilationUnit> units) {
		IJavaElement element= binding == null ? null : binding.getErasure().getJavaElement();
		if (element == null || !element.exists()) {
			return false;
		}
		if (element instanceof IType type) {
			ICompilationUnit compilationUnit= type.getCompilationUnit();
			if (compilationUnit != null && compilationUnit.exists()) {
				units.add(compilationUnit.getPrimary());
			}
		}
		return true;
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}
}

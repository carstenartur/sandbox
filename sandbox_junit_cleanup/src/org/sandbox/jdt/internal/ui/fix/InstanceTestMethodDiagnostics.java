/*******************************************************************************
 * Copyright (c) 2021, 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_TEST;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULES_ERROR_COLLECTOR;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULES_EXPECTED_EXCEPTION;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_TEST;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.ParameterizedMigrationEligibility;

/** Jupiter tests must remain instance methods when migration removes their last instance field use. */
final class InstanceTestMethodDiagnostics extends CompilationUnitRewriteOperationWithSourceRange {
	private final Set<MethodDeclaration> candidates;

	private InstanceTestMethodDiagnostics(Set<MethodDeclaration> candidates) {
		this.candidates= candidates;
	}

	static void addIfNeeded(CompilationUnit root, EnumSet<JUnitCleanUpFixCore> fixes,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations) {
		boolean parameterized= fixes.contains(JUnitCleanUpFixCore.PARAMETERIZED);
		Set<String> fieldTypes= new HashSet<>();
		if (fixes.contains(JUnitCleanUpFixCore.RULEEXPECTEDEXCEPTION)) {
			fieldTypes.add(ORG_JUNIT_RULES_EXPECTED_EXCEPTION);
		}
		if (fixes.contains(JUnitCleanUpFixCore.RULEERRORCOLLECTOR)) {
			fieldTypes.add(ORG_JUNIT_RULES_ERROR_COLLECTOR);
		}
		if (!parameterized && fieldTypes.isEmpty()) {
			return;
		}
		Set<MethodDeclaration> methods= new LinkedHashSet<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration method) {
				boolean parametersToArguments= parameterized && method.getParent() instanceof TypeDeclaration type
						&& ParameterizedMigrationEligibility.hasParameterizedRunner(type);
				if (!parametersToArguments && fieldTypes.isEmpty()) {
					return true;
				}
				IMethodBinding binding= method.resolveBinding();
				if (binding == null || Arrays.stream(binding.getAnnotations())
						.noneMatch(a -> Set.of(ORG_JUNIT_TEST, ORG_JUNIT_JUPITER_TEST).contains(a.getAnnotationType().getQualifiedName()))) {
					return true;
				}
				method.accept(new ASTVisitor() {
					@Override
					public boolean visit(SimpleName name) {
						if (name.resolveBinding() instanceof IVariableBinding variable && variable.isField()
								&& !Modifier.isStatic(variable.getModifiers())
								&& (parametersToArguments || fieldTypes.contains(variable.getType().getErasure().getQualifiedName()))) {
							methods.add(method);
						}
						return true;
					}
				});
				return true;
			}
		});
		if (!methods.isEmpty()) {
			operations.add(new InstanceTestMethodDiagnostics(methods));
		}
	}

	@Override
	public void rewriteASTInternal(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore model) throws CoreException {
		ICompilationUnit unit= cuRewrite.getCu();
		Map<MethodDeclaration, ITrackedNodePosition> names= new LinkedHashMap<>();
		// Tracking survives overloads and the parameter lists introduced by the staged rewrite.
		String original= unit.getSource();
		Set<Integer> existing= suggestions(unit, original);
		for (MethodDeclaration method : candidates) {
			if (!existing.contains(method.getName().getStartPosition())) {
				names.put(method, cuRewrite.getASTRewrite().track(method.getName()));
			}
		}
		if (names.isEmpty()) {
			return;
		}
		var preview= cuRewrite.createChange(false, null);
		if (preview == null) {
			return;
		}
		Document document= new Document(original);
		try {
			preview.getEdit().apply(document, TextEdit.UPDATE_REGIONS);
		} catch (BadLocationException e) {
			throw new CoreException(Status.error("Cannot preview JUnit migration", e)); //$NON-NLS-1$
		}
		Set<Integer> introduced= suggestions(unit, document.get());
		TextEditGroup group= createTextEditGroup("Keep Jupiter test methods non-static", cuRewrite); //$NON-NLS-1$
		names.forEach((method, position) -> {
			if (introduced.contains(position.getStartPosition())) {
				suppressStaticSuggestion(method, cuRewrite, group);
			}
		});
	}

	private static Set<Integer> suggestions(ICompilationUnit unit, String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setProject(unit.getJavaProject());
		parser.setUnitName(unit.getPath().toPortableString());
		parser.setSource(source.toCharArray());
		parser.setResolveBindings(true);
		var options= unit.getOptions(true);
		options.put(JavaCore.COMPILER_PB_MISSING_STATIC_ON_METHOD, JavaCore.WARNING);
		options.put(JavaCore.COMPILER_PB_POTENTIALLY_MISSING_STATIC_ON_METHOD, JavaCore.WARNING);
		parser.setCompilerOptions(options);
		Set<Integer> positions= new HashSet<>();
		for (IProblem problem : ((CompilationUnit) parser.createAST(null)).getProblems()) {
			if (problem.getID() == IProblem.MethodCanBePotentiallyStatic || problem.getID() == IProblem.MethodCanBeStatic) {
				positions.add(problem.getSourceStart());
			}
		}
		return positions;
	}

	@SuppressWarnings("unchecked")
	private static void suppressStaticSuggestion(MethodDeclaration method, CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		AST ast= method.getAST();
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		StringLiteral token= ast.newStringLiteral();
		token.setLiteralValue("static-method"); //$NON-NLS-1$
		for (Object modifier : method.modifiers()) {
			if (!(modifier instanceof Annotation annotation) || annotation.resolveTypeBinding() == null
					|| !SuppressWarnings.class.getName().equals(annotation.resolveTypeBinding().getQualifiedName())) {
				continue;
			}
			Expression value= null;
			if (annotation instanceof SingleMemberAnnotation single) {
				value= single.getValue();
			} else if (annotation instanceof NormalAnnotation normal) {
				for (Object member : normal.values()) {
					if (member instanceof MemberValuePair pair && "value".equals(pair.getName().getIdentifier())) { //$NON-NLS-1$
						value= pair.getValue();
					}
				}
			}
			if (value instanceof ArrayInitializer array) {
				rewrite.getListRewrite(array, ArrayInitializer.EXPRESSIONS_PROPERTY).insertLast(token, group);
			} else if (value != null) {
				ArrayInitializer array= ast.newArrayInitializer();
				array.expressions().add(rewrite.createMoveTarget(value));
				array.expressions().add(token);
				rewrite.replace(value, array, group);
			}
			return;
		}
		SingleMemberAnnotation suppression= ast.newSingleMemberAnnotation();
		suppression.setTypeName(ast.newName(cuRewrite.getImportRewrite().addImport(SuppressWarnings.class.getName())));
		suppression.setValue(token);
		rewrite.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY).insertFirst(suppression, group);
	}
}

/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import static org.sandbox.jdt.internal.corext.fix.multifile.JUnit4ParameterizedPlan.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.cleanup.multifile.JavaProjectCompilationUnits;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan.NodeKey;
import org.sandbox.jdt.triggerpattern.cleanup.PlanAwareHintFileFixCore;

/** Applies the closed, constant-row ParameterizedClass execution contract. */
final class JUnit4ParameterizedMigration {

	private JUnit4ParameterizedMigration() {
	}

	static void addOperations(JUnit4ParameterizedPlan plan, ICompilationUnit unit, CompilationUnit root,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> processed) throws CoreException {
		if (!plan.executable() || !plan.compilationUnits().containsValue(unit.getPrimary().getHandleIdentifier())) {
			return;
		}
		validateCurrentSources(plan);
		Map<NodeKey, ASTNode> nodes= new LinkedHashMap<>();
		root.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				NodeKey key= NodeKey.from(node);
				if (key != null && plan.compilationUnits().containsKey(key)
						&& !(node instanceof SimpleName)) {
					nodes.put(key, node);
				}
			}
		});
		Set<NodeKey> expected= new HashSet<>();
		plan.compilationUnits().forEach((key, owner) -> {
			if (owner.equals(unit.getPrimary().getHandleIdentifier())) {
				expected.add(key);
			}
		});
		if (!nodes.keySet().equals(expected)) {
			throw failure("The Parameterized declaration bindings changed; retry the complete migration."); //$NON-NLS-1$
		}
		Set<NodeKey> hintTargets= new HashSet<>();
		for (Map.Entry<NodeKey, ASTNode> entry : nodes.entrySet()) {
			Set<String> roles= plan.semanticPlan().rolesByNode().get(entry.getKey());
			if (roles.contains(TEST) || roles.stream().anyMatch(role -> role.startsWith("JUNIT4_PARAMETERIZED_Before") //$NON-NLS-1$
					|| role.startsWith("JUNIT4_PARAMETERIZED_After"))) { //$NON-NLS-1$
				hintTargets.add(entry.getKey());
			}
			entry.getValue().accept(new ASTVisitor() {
				@Override
				public void preVisit(ASTNode node) {
					processed.add(node);
				}
			});
		}
		Set<NodeKey> covered= PlanAwareHintFileFixCore.findOperationsFromContent(root, hintProgram(),
				plan.semanticPlan(), unit.getJavaProject().getOptions(true), operations, processed);
		if (!covered.equals(hintTargets)) {
			throw failure("The Parameterized hint program did not cover every planned test and lifecycle method."); //$NON-NLS-1$
		}
		operations.add(new CompilationUnitRewriteOperationWithSourceRange() {
			@Override
			public void rewriteASTInternal(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel)
					throws CoreException {
				TextEditGroup group= createTextEditGroup("Migrate the coordinated Parameterized class", cuRewrite); //$NON-NLS-1$
				for (Map.Entry<NodeKey, ASTNode> entry : nodes.entrySet()) {
					Set<String> roles= plan.semanticPlan().rolesByNode().get(entry.getKey());
					if (roles.contains(RUNNER_CLASS)) {
						rewriteClass((TypeDeclaration) entry.getValue(), plan, cuRewrite, group);
					} else if (roles.contains(FIELD)) {
						FieldDeclaration field= (FieldDeclaration) entry.getValue().getParent();
						int index= plan.semanticPlan().outgoing(plan.testClass(), HAS_FIELD).stream()
								.map(relation -> relation.target()).toList().indexOf(entry.getKey());
						removeAnnotation(field, "org.junit.runners.Parameterized.Parameter", cuRewrite, group); //$NON-NLS-1$
						addAnnotation(field, "org.junit.jupiter.params.Parameter", //$NON-NLS-1$
								Map.of("value", root.getAST().newNumberLiteral(Integer.toString(index))), cuRewrite, group); //$NON-NLS-1$
					} else if (roles.contains(PROVIDER)) {
						removeAnnotation((MethodDeclaration) entry.getValue(), "org.junit.runners.Parameterized.Parameters", cuRewrite, group); //$NON-NLS-1$
					}
				}
			}
		});
	}

	private static void validateCurrentSources(JUnit4ParameterizedPlan plan) throws CoreException {
		Map<String, String> sources= new LinkedHashMap<>();
		Set<IJavaProject> projects= new HashSet<>();
		for (String handle : plan.sourceFingerprints().keySet()) {
			if (!(JavaCore.create(handle) instanceof ICompilationUnit unit) || !unit.exists() || unit.isReadOnly()) {
				throw failure("A Parameterized source is missing or read-only; retry with the complete editable scope."); //$NON-NLS-1$
			}
			projects.add(unit.getJavaProject());
		}
		for (IJavaProject project : projects) {
			for (ICompilationUnit unit : JavaProjectCompilationUnits.collect(project)) {
				sources.put(unit.getPrimary().getHandleIdentifier(), unit.getPrimary().getSource());
			}
		}
		if (!plan.isCurrent(sources)) {
			throw failure("The Parameterized sources or source-scope membership changed; retry the complete migration."); //$NON-NLS-1$
		}
	}

	private static void rewriteClass(TypeDeclaration type, JUnit4ParameterizedPlan plan,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) throws CoreException {
		AST ast= type.getAST();
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		NodeKey providerKey= plan.semanticPlan().outgoing(plan.testClass(), HAS_PROVIDER).get(0).target();
		ICompilationUnit providerUnit= (ICompilationUnit) JavaCore.create(plan.compilationUnits().get(providerKey));
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(providerUnit);
		parser.setResolveBindings(true);
		CompilationUnit providerRoot= (CompilationUnit) parser.createAST(null);
		List<MethodDeclaration> providers= new ArrayList<>();
		providerRoot.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration method) {
				if (providerKey.equals(NodeKey.from(method))) {
					providers.add(method);
				}
				return true;
			}
		});
		if (providers.size() != 1) {
			throw failure("The Parameterized provider binding changed before rewrite."); //$NON-NLS-1$
		}
		MethodDeclaration provider= providers.get(0);
		String pattern= parametersName(provider);
		Set<String> names= new HashSet<>();
		for (ITypeBinding binding= type.resolveBinding(); binding != null; binding= binding.getSuperclass()) {
			for (IMethodBinding method : binding.getDeclaredMethods()) {
				names.add(method.getName());
			}
		}
		String adapter= "jupiterArguments"; //$NON-NLS-1$
		for (int suffix= 2; names.contains(adapter); suffix++) {
			adapter= "jupiterArguments" + suffix; //$NON-NLS-1$
		}
		removeAnnotation(type, "org.junit.runner.RunWith", cuRewrite, group); //$NON-NLS-1$
		addAnnotation(type, "org.junit.jupiter.params.ParameterizedClass", //$NON-NLS-1$
				Map.of("name", literal(ast, "{argumentSetName}"), "autoCloseArguments", ast.newBooleanLiteral(false)), cuRewrite, group); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		addAnnotation(type, "org.junit.jupiter.params.provider.MethodSource", //$NON-NLS-1$
				Map.of("value", literal(ast, adapter)), cuRewrite, group); //$NON-NLS-1$
		TypeLiteral order= ast.newTypeLiteral();
		order.setType(ast.newSimpleType(ast.newName("org.junit.jupiter.api.MethodOrderer.OrderAnnotation"))); //$NON-NLS-1$
		addAnnotation(type, "org.junit.jupiter.api.TestMethodOrder", Map.of("value", order), cuRewrite, group); //$NON-NLS-1$ //$NON-NLS-2$
		addAnnotation(type, "org.junit.jupiter.api.TestInstance", //$NON-NLS-1$
				Map.of("value", ast.newName("org.junit.jupiter.api.TestInstance.Lifecycle.PER_METHOD")), cuRewrite, group); //$NON-NLS-1$ //$NON-NLS-2$
		addAnnotation(type, "org.junit.jupiter.api.parallel.Execution", //$NON-NLS-1$
				Map.of("value", ast.newName("org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD")), cuRewrite, group); //$NON-NLS-1$ //$NON-NLS-2$
		// This fixed adapter preserves JUnit 4's zero-based MessageFormat names and
		// evaluates the original provider once. Constructor bodies and fields stay intact.
		String member= """
				static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> %s() {
					Object source = %s();
					Iterable<?> rows = source instanceof Object[][] ? java.util.Arrays.asList((Object[][]) source) : (Iterable<?>) source;
					java.util.List<org.junit.jupiter.params.provider.Arguments> result = new java.util.ArrayList<>();
					int index = 0;
					for (Object row : rows) {
						Object[] arguments = (Object[]) row;
						String name = "[" + java.text.MessageFormat.format(%s.replace("{index}", Integer.toString(index++)), arguments) + "]";
						result.add(org.junit.jupiter.params.provider.Arguments.argumentSet(name, arguments));
					}
					return result.stream();
				}
				""".formatted(adapter, provider.resolveBinding().getDeclaringClass().getQualifiedName()
						+ "." + provider.getName().getIdentifier(), literal(ast, pattern)); //$NON-NLS-1$ //$NON-NLS-2$
		parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_CLASS_BODY_DECLARATIONS);
		parser.setSource(member.toCharArray());
		TypeDeclaration parsed= (TypeDeclaration) parser.createAST(null);
		if (parsed.getMethods().length != 1 || (parsed.getFlags() & ASTNode.MALFORMED) != 0) {
			throw failure("Cannot materialize the typed Parameterized provider adapter."); //$NON-NLS-1$
		}
		rewrite.getListRewrite(type, TypeDeclaration.BODY_DECLARATIONS_PROPERTY)
				.insertLast(ASTNode.copySubtree(ast, parsed.getMethods()[0]), group);
	}

	private static String parametersName(MethodDeclaration provider) throws CoreException {
		for (Object item : provider.modifiers()) {
			if (item instanceof Annotation annotation && annotation.resolveAnnotationBinding() != null
					&& "org.junit.runners.Parameterized.Parameters".equals(annotation.resolveTypeBinding().getQualifiedName())) { //$NON-NLS-1$
				for (IMemberValuePairBinding pair : annotation.resolveAnnotationBinding().getAllMemberValuePairs()) {
					if ("name".equals(pair.getName()) && pair.getValue() instanceof String value) { //$NON-NLS-1$
						return value;
					}
				}
			}
		}
		throw failure("The Parameterized provider name is unresolved."); //$NON-NLS-1$
	}

	private static StringLiteral literal(AST ast, String value) {
		StringLiteral result= ast.newStringLiteral();
		result.setLiteralValue(value);
		return result;
	}

	@SuppressWarnings("unchecked")
	private static void addAnnotation(BodyDeclaration target, String name, Map<String, Expression> values,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		AST ast= target.getAST();
		NormalAnnotation annotation= ast.newNormalAnnotation();
		annotation.setTypeName(ast.newName(name));
		values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
			MemberValuePair pair= ast.newMemberValuePair();
			pair.setName(ast.newSimpleName(entry.getKey()));
			pair.setValue(entry.getValue());
			annotation.values().add(pair);
		});
		cuRewrite.getASTRewrite().getListRewrite(target, target.getModifiersProperty()).insertFirst(annotation, group);
	}

	private static void removeAnnotation(BodyDeclaration target, String name,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		for (Object item : target.modifiers()) {
			if (item instanceof Annotation annotation && annotation.resolveTypeBinding() != null
					&& name.equals(annotation.resolveTypeBinding().getQualifiedName())) {
				cuRewrite.getImportRemover().registerRemovedNode(annotation);
				cuRewrite.getASTRewrite().remove(annotation, group);
			}
		}
	}

	private static String hintProgram() throws CoreException {
		String resource= "org/sandbox/jdt/internal/corext/fix/hints/junit4-parameterized-to-jupiter.sandbox-hint"; //$NON-NLS-1$
		try (InputStream stream= JUnit4ParameterizedMigration.class.getClassLoader().getResourceAsStream(resource)) {
			if (stream == null) {
				throw new IOException(resource);
			}
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, "sandbox_junit_cleanup", "Cannot load Parameterized hints", e)); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static CoreException failure(String message) {
		return new CoreException(new Status(IStatus.ERROR, "sandbox_junit_cleanup", message)); //$NON-NLS-1$
	}
}

/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import org.sandbox.jdt.container.api.ContainerLocalRewritePlan;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.ArgumentTransfer;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.EditKind;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.LocalEdit;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class ContainerLocalArgumentTransferIntegrationTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	@Test
	void retainsOneExactlyPlannedMethodArgument() throws Exception {
		ICompilationUnit unit= createUnit(source(false));
		CompilationUnit root= parse(unit);
		SourceFacts facts= facts(root);

		ContainerLocalRewriteFix.create(unit, root, plan(unit, facts))
				.createChange(null).perform(null);

		String transformed= unit.getSource();
		assertTrue(transformed.contains("List<String> values")); //$NON-NLS-1$
		assertTrue(transformed.contains("new ArrayList<>()")); //$NON-NLS-1$
		assertTrue(transformed.contains("values.add(value)")); //$NON-NLS-1$
		assertTrue(transformed.contains("consume(values)")); //$NON-NLS-1$
		assertFalse(transformed.contains("Arrays.copyOf(values")); //$NON-NLS-1$
	}

	@Test
	void rejectsAnAdditionalUnplannedMethodArgument() throws Exception {
		ICompilationUnit unit= createUnit(source(true));
		CompilationUnit root= parse(unit);
		SourceFacts facts= facts(root);

		CoreException exception= assertThrows(CoreException.class, () ->
				ContainerLocalRewriteFix.create(unit, root, plan(unit, facts)));

		assertTrue(exception.getMessage().contains("unexpected use")); //$NON-NLS-1$
	}

	@Test
	void rejectsAChangedTargetWithTheSameArgumentSourceRange() throws Exception {
		ICompilationUnit unit= createUnit(source(false));
		CompilationUnit originalRoot= parse(unit);
		ContainerLocalRewritePlan originalPlan= plan(unit, facts(originalRoot));
		unit.getBuffer().setContents(
				unit.getSource().replace("consume(values);", "another(values);")); //$NON-NLS-1$ //$NON-NLS-2$
		unit.save(null, true);

		CoreException exception= assertThrows(CoreException.class, () ->
				ContainerLocalRewriteFix.create(unit, parse(unit), originalPlan));

		assertTrue(exception.getMessage().contains("argument-transfer target changed")); //$NON-NLS-1$
	}

	private ContainerLocalRewritePlan plan(
			ICompilationUnit unit,
			SourceFacts facts) {
		return new ContainerLocalRewritePlan(
				unit.getHandleIdentifier(),
				facts.bindingKey(),
				"java.util.List", //$NON-NLS-1$
				"java.util.ArrayList", //$NON-NLS-1$
				target(),
				List.of(
						new LocalEdit(EditKind.CHANGE_LOCAL_DECLARATION, 1, 1),
						new LocalEdit(EditKind.REPLACE_EMPTY_ARRAY_INITIALIZER, 1, 1),
						new LocalEdit(EditKind.REMOVE_ARRAY_GROWTH, 2, 1),
						new LocalEdit(EditKind.REPLACE_TAIL_WRITE_WITH_ADD, 3, 1),
						new LocalEdit(
								EditKind.VERIFY_ARGUMENT_TRANSFER,
								facts.argumentStart(),
								facts.argumentLength())),
				List.of(new ArgumentTransfer(
						facts.methodJavaElementHandle(),
						0,
						facts.argumentStart(),
						facts.argumentLength())));
	}

	private static SourceFacts facts(CompilationUnit root) {
		String[] bindingKey= { null };
		String[] methodHandle= { null };
		int[] range= { -1, -1 };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(VariableDeclarationFragment fragment) {
				if ("values".equals(fragment.getName().getIdentifier())) { //$NON-NLS-1$
					IVariableBinding binding= fragment.resolveBinding();
					bindingKey[0]= binding == null
							? null : binding.getVariableDeclaration().getKey();
				}
				return true;
			}

			@Override
			public boolean visit(MethodInvocation invocation) {
				if (range[0] >= 0
						|| !"consume".equals(invocation.getName().getIdentifier()) //$NON-NLS-1$
						|| invocation.arguments().size() != 1) {
					return true;
				}
				Expression argument= (Expression) invocation.arguments().get(0);
				if (argument instanceof SimpleName name
						&& "values".equals(name.getIdentifier())) { //$NON-NLS-1$
					range[0]= name.getStartPosition();
					range[1]= name.getLength();
					IMethodBinding method= invocation.resolveMethodBinding();
					IJavaElement element= method == null
							? null : method.getMethodDeclaration().getJavaElement();
					methodHandle[0]= element == null
							? null : element.getHandleIdentifier();
				}
				return true;
			}
		});
		if (bindingKey[0] == null || methodHandle[0] == null || range[0] < 0) {
			throw new IllegalStateException("Missing planned source facts"); //$NON-NLS-1$
		}
		return new SourceFacts(
				bindingKey[0], methodHandle[0], range[0], range[1]);
	}

	private ICompilationUnit createUnit(String source) throws CoreException {
		IPackageFragment fragment= context.getSourceFolder()
				.createPackageFragment("test", false, null); //$NON-NLS-1$
		return fragment.createCompilationUnit("Sample.java", source, true, null); //$NON-NLS-1$
	}

	private CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setProject(context.getJavaProject());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		return (CompilationUnit) parser.createAST(null);
	}

	private static String source(boolean secondCall) {
		String template= """
			package test;
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					consume(values);
					__SECOND_CALL__
				}
				void consume(String[] values) {}
				void another(String[] values) {}
			}
			""";
		return template.replace(
				"__SECOND_CALL__", secondCall ? "consume(values);" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static TargetContainerContract target() {
		return new TargetContainerContract(
				ContainerShape.LIST,
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				Mutability.MUTABLE,
				NullContract.ALLOWED,
				"Use a mutable dynamic sequence."); //$NON-NLS-1$
	}

	private record SourceFacts(
			String bindingKey,
			String methodJavaElementHandle,
			int argumentStart,
			int argumentLength) {
	}
}

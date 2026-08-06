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

import java.util.ArrayList;
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
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;

import org.sandbox.jdt.container.api.ContainerParameterRewritePlan;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan.EditKind;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan.ParameterEdit;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class ContainerParameterRewriteIntegrationTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	@Test
	void convertsAReadOnlyArrayParameterToList() throws Exception {
		ICompilationUnit unit= createUnit("""
			package test;
			class Sample {
				void consume(String[] values) {
					int count = values.length;
					for (String value : values) {
						System.out.println(value + count);
					}
				}
			}
			""");
		CompilationUnit root= parse(unit);

		ContainerParameterRewriteFix.create(
				unit, root, plan(unit, root, 1, 1))
				.createChange(null).perform(null);

		String source= unit.getSource();
		assertTrue(source.contains("import java.util.List;")); //$NON-NLS-1$
		assertTrue(source.contains("void consume(List<String> values)")); //$NON-NLS-1$
		assertTrue(source.contains("values.size()")); //$NON-NLS-1$
		assertTrue(source.contains("for (String value : values)")); //$NON-NLS-1$
		assertFalse(source.contains("values.length")); //$NON-NLS-1$
	}

	@Test
	void rejectsAnUnexpectedParameterUse() throws Exception {
		ICompilationUnit unit= createUnit("""
			package test;
			class Sample {
				void consume(String[] values) {
					System.identityHashCode(values);
				}
			}
			""");
		CompilationUnit root= parse(unit);

		CoreException exception= assertThrows(CoreException.class, () ->
				ContainerParameterRewriteFix.create(
						unit, root, plan(unit, root, 0, 0)));

		assertTrue(exception.getMessage().contains("unexpected parameter use")); //$NON-NLS-1$
	}

	@Test
	void rejectsParameterCaptureEvenWhenTheNestedUseIsOnlyLength() throws Exception {
		ICompilationUnit unit= createUnit("""
			package test;
			class Sample {
				void consume(String[] values) {
					Runnable task = () -> System.out.println(values.length);
					task.run();
				}
			}
			""");
		CompilationUnit root= parse(unit);

		CoreException exception= assertThrows(CoreException.class, () ->
				ContainerParameterRewriteFix.create(
						unit, root, plan(unit, root, 1, 0)));

		assertTrue(exception.getMessage().contains("captured")); //$NON-NLS-1$
	}

	@Test
	void rejectsChangedLengthAndIterationCounts() throws Exception {
		ICompilationUnit unit= createUnit("""
			package test;
			class Sample {
				void consume(String[] values) {
					System.out.println(values.length);
					for (String value : values) {
						System.out.println(value);
					}
				}
			}
			""");
		CompilationUnit root= parse(unit);

		CoreException length= assertThrows(CoreException.class, () ->
				ContainerParameterRewriteFix.create(
						unit, root, plan(unit, root, 0, 1)));
		assertTrue(length.getMessage().contains("length occurrence count")); //$NON-NLS-1$

		CoreException iteration= assertThrows(CoreException.class, () ->
				ContainerParameterRewriteFix.create(
						unit, root, plan(unit, root, 1, 0)));
		assertTrue(iteration.getMessage().contains("encounter iteration")); //$NON-NLS-1$
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

	private static ContainerParameterRewritePlan plan(
			ICompilationUnit unit,
			CompilationUnit root,
			int lengthCount,
			int iterationCount) {
		MethodDeclaration method= method(root);
		SingleVariableDeclaration parameter=
				(SingleVariableDeclaration) method.parameters().get(0);
		IVariableBinding parameterBinding= parameter.resolveBinding();
		IMethodBinding methodBinding= method.resolveBinding();
		IJavaElement methodElement= methodBinding == null
				? null : methodBinding.getMethodDeclaration().getJavaElement();
		if (parameterBinding == null || methodElement == null) {
			throw new IllegalStateException("Missing method or parameter binding"); //$NON-NLS-1$
		}

		List<ParameterEdit> edits= new ArrayList<>();
		edits.add(new ParameterEdit(
				EditKind.CHANGE_PARAMETER_DECLARATION,
				parameter.getStartPosition(),
				parameter.getLength()));
		addEdits(edits, EditKind.REPLACE_LENGTH_WITH_SIZE, lengthCount, 30);
		addEdits(edits, EditKind.VERIFY_ENCOUNTER_ITERATION, iterationCount, 40);
		return new ContainerParameterRewritePlan(
				unit.getHandleIdentifier(),
				methodElement.getHandleIdentifier(),
				parameterBinding.getVariableDeclaration().getKey(),
				0,
				"java.util.List", //$NON-NLS-1$
				targetContract(),
				edits);
	}

	private static MethodDeclaration method(CompilationUnit root) {
		MethodDeclaration[] result= { null };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration declaration) {
				if ("consume".equals(declaration.getName().getIdentifier())) { //$NON-NLS-1$
					result[0]= declaration;
				}
				return true;
			}
		});
		if (result[0] == null) {
			throw new IllegalStateException("Missing consume method"); //$NON-NLS-1$
		}
		return result[0];
	}

	private static void addEdits(
			List<ParameterEdit> edits,
			EditKind kind,
			int count,
			int offset) {
		for (int index= 0; index < count; index++) {
			edits.add(new ParameterEdit(kind, offset + index, 1));
		}
	}

	private static TargetContainerContract targetContract() {
		return new TargetContainerContract(
				ContainerShape.LIST,
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				Mutability.MUTABLE,
				NullContract.ALLOWED,
				"Use a mutable dynamic sequence."); //$NON-NLS-1$
	}
}

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import org.sandbox.jdt.container.api.ContainerLocalRewritePlan;
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

class ContainerLocalRewriteIntegrationTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	@Test
	void appliesTheStrictLocalRewrite() throws Exception {
		ICompilationUnit unit= createUnit("""
			package test;
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					int count = values.length;
					for (String current : values) {
						System.out.println(current + count);
					}
				}
			}
			""");
		CompilationUnit root= parse(unit);

		ContainerLocalRewriteFix.create(
				unit, root, plan(unit, root, 1, 1, 1, 1))
				.createChange(null).perform(null);

		String source= unit.getSource();
		assertTrue(source.contains("List<String> values")); //$NON-NLS-1$
		assertTrue(source.contains("new ArrayList<>()")); //$NON-NLS-1$
		assertTrue(source.contains("values.add(value)")); //$NON-NLS-1$
		assertTrue(source.contains("values.size()")); //$NON-NLS-1$
		assertFalse(source.contains("Arrays.copyOf(values")); //$NON-NLS-1$
		assertFalse(source.contains("values[values.length - 1]")); //$NON-NLS-1$
	}

	@Test
	void rewritesSeveralAdjacentAppendPairs() throws Exception {
		ICompilationUnit unit= createUnit("""
			package test;
			import java.util.Arrays;
			class Sample {
				void collect(String first, String second) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = first;
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = second;
				}
			}
			""");
		CompilationUnit root= parse(unit);

		ContainerLocalRewriteFix.create(
				unit, root, plan(unit, root, 2, 2, 0, 0))
				.createChange(null).perform(null);

		String source= unit.getSource();
		assertEquals(2, occurrences(source, "values.add(")); //$NON-NLS-1$
		assertTrue(source.contains("values.add(first)")); //$NON-NLS-1$
		assertTrue(source.contains("values.add(second)")); //$NON-NLS-1$
	}

	@Test
	void rejectsAnUnexpectedBindingUse() throws Exception {
		ICompilationUnit unit= createUnit("""
			package test;
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					System.identityHashCode(values);
				}
			}
			""");
		CompilationUnit root= parse(unit);

		CoreException exception= assertThrows(CoreException.class, () ->
				ContainerLocalRewriteFix.create(
						unit, root, plan(unit, root, 1, 1, 0, 0)));

		assertTrue(exception.getMessage().contains("unexpected use")); //$NON-NLS-1$
	}

	@Test
	void rejectsChangedEditingAndIterationCounts() throws Exception {
		ICompilationUnit editingUnit= createUnit(appendSource());
		CompilationUnit editingRoot= parse(editingUnit);
		CoreException editing= assertThrows(CoreException.class, () ->
				ContainerLocalRewriteFix.create(
						editingUnit, editingRoot,
						plan(editingUnit, editingRoot, 2, 2, 0, 0)));
		assertTrue(editing.getMessage().contains("occurrence count changed")); //$NON-NLS-1$

		ICompilationUnit iterationUnit= createUnit("""
			package test;
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					for (String current : values) {
						System.out.println(current);
					}
				}
			}
			""");
		CompilationUnit iterationRoot= parse(iterationUnit);
		CoreException iteration= assertThrows(CoreException.class, () ->
				ContainerLocalRewriteFix.create(
						iterationUnit, iterationRoot,
						plan(iterationUnit, iterationRoot, 1, 1, 0, 0)));
		assertTrue(iteration.getMessage().contains("encounter iteration")); //$NON-NLS-1$
	}

	@Test
	void rejectsAnInterveningStatement() throws Exception {
		ICompilationUnit unit= createUnit("""
			package test;
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					System.out.println(value);
					values[values.length - 1] = value;
				}
			}
			""");
		CompilationUnit root= parse(unit);

		CoreException exception= assertThrows(CoreException.class, () ->
				ContainerLocalRewriteFix.create(
						unit, root, plan(unit, root, 1, 1, 0, 0)));

		assertTrue(exception.getMessage().contains("immediately followed")); //$NON-NLS-1$
	}

	@Test
	void rejectsTheWrongCompilationUnitHandle() throws Exception {
		ICompilationUnit unit= createUnit(appendSource());
		CompilationUnit root= parse(unit);
		ContainerLocalRewritePlan original= plan(unit, root, 1, 1, 0, 0);
		ContainerLocalRewritePlan wrongUnit= new ContainerLocalRewritePlan(
				"other-unit", //$NON-NLS-1$
				original.bindingKey(),
				original.targetInterfaceType(),
				original.targetImplementationType(),
				original.targetContract(),
				original.edits());

		CoreException exception= assertThrows(CoreException.class, () ->
				ContainerLocalRewriteFix.create(unit, root, wrongUnit));

		assertTrue(exception.getMessage().contains("handle changed")); //$NON-NLS-1$
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

	private static ContainerLocalRewritePlan plan(
			ICompilationUnit unit,
			CompilationUnit root,
			int growthCount,
			int appendCount,
			int lengthCount,
			int iterationCount) {
		List<LocalEdit> edits= new ArrayList<>();
		edits.add(new LocalEdit(EditKind.CHANGE_LOCAL_DECLARATION, 1, 1));
		edits.add(new LocalEdit(EditKind.REPLACE_EMPTY_ARRAY_INITIALIZER, 1, 1));
		addEdits(edits, EditKind.REMOVE_ARRAY_GROWTH, growthCount, 10);
		addEdits(edits, EditKind.REPLACE_TAIL_WRITE_WITH_ADD, appendCount, 20);
		addEdits(edits, EditKind.REPLACE_LENGTH_WITH_SIZE, lengthCount, 30);
		addEdits(edits, EditKind.VERIFY_ENCOUNTER_ITERATION, iterationCount, 40);
		return new ContainerLocalRewritePlan(
				unit.getHandleIdentifier(),
				bindingKey(root),
				"java.util.List", //$NON-NLS-1$
				"java.util.ArrayList", //$NON-NLS-1$
				targetContract(),
				edits);
	}

	private static void addEdits(
			List<LocalEdit> edits,
			EditKind kind,
			int count,
			int offset) {
		for (int index= 0; index < count; index++) {
			edits.add(new LocalEdit(kind, offset + index, 1));
		}
	}

	private static String bindingKey(CompilationUnit root) {
		String[] result= { null };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(VariableDeclarationFragment node) {
				if ("values".equals(node.getName().getIdentifier())) { //$NON-NLS-1$
					IVariableBinding binding= node.resolveBinding();
					result[0]= binding == null ? null : binding.getVariableDeclaration().getKey();
				}
				return true;
			}
		});
		if (result[0] == null) {
			throw new IllegalStateException("Missing values binding"); //$NON-NLS-1$
		}
		return result[0];
	}

	private static String appendSource() {
		return """
			package test;
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
				}
			}
			""";
	}

	private static int occurrences(String source, String token) {
		int count= 0;
		int offset= 0;
		while ((offset= source.indexOf(token, offset)) >= 0) {
			count++;
			offset+= token.length();
		}
		return count;
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

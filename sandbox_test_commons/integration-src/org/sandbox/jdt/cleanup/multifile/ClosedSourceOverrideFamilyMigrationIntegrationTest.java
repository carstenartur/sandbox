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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.sandbox.jdt.container.api.ClosedSourceParameterMigrationPlan;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.ArgumentTransfer;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.EditKind;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.LocalEdit;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan.ParameterEdit;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class ClosedSourceOverrideFamilyMigrationIntegrationTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	@Test
	void appliesAndUndoesCompleteOverrideFamilyAtomically() throws Exception {
		ICompilationUnit caller= createUnit("Caller.java", callerSource()); //$NON-NLS-1$
		ICompilationUnit contract= createUnit("Contract.java", contractSource()); //$NON-NLS-1$
		ICompilationUnit first= createUnit("First.java", firstSource()); //$NON-NLS-1$
		ICompilationUnit second= createUnit("Second.java", secondSource()); //$NON-NLS-1$
		List<ICompilationUnit> units= List.of(caller, contract, first, second);
		List<String> originals= units.stream().map(unit -> {
			try {
				return unit.getSource();
			} catch (CoreException exception) {
				throw new IllegalStateException(exception);
			}
		}).toList();

		CompilationUnit callerRoot= parse(caller);
		CompilationUnit contractRoot= parse(contract);
		CompilationUnit firstRoot= parse(first);
		CompilationUnit secondRoot= parse(second);
		CallerFacts callerFacts= callerFacts(callerRoot);
		ParameterFacts contractFacts= parameterFacts(contractRoot, false);
		ParameterFacts firstFacts= parameterFacts(firstRoot, true);
		ParameterFacts secondFacts= parameterFacts(secondRoot, true);
		ClosedSourceParameterMigrationPlan plan= plan(
				caller,
				callerFacts,
				List.of(
						parameterPlan(contract, contractFacts),
						parameterPlan(first, firstFacts),
						parameterPlan(second, secondFacts)),
				contractFacts.methodHandle());

		List<CompilationUnit> roots= List.of(
				callerRoot, contractRoot, firstRoot, secondRoot);
		CompositeChange change= new CompositeChange(
				"Migrate complete parameter override family"); //$NON-NLS-1$
		for (int index= 0; index < units.size(); index++) {
			ICleanUpFix fix= ClosedSourceParameterMigrationFix.create(
					units.get(index), roots.get(index), plan);
			assertNotNull(fix);
			change.add(fix.createChange(null));
		}
		Change undo= change.perform(new NullProgressMonitor());
		assertNotNull(undo);

		assertTrue(caller.getSource().contains("List<String> values")); //$NON-NLS-1$
		assertTrue(caller.getSource().contains("receiver.consume(values)")); //$NON-NLS-1$
		assertFalse(caller.getSource().contains("Arrays.copyOf(values")); //$NON-NLS-1$
		assertTrue(contract.getSource().contains("void consume(List<String> values)")); //$NON-NLS-1$
		assertTrue(first.getSource().contains("void consume(List<String> values)")); //$NON-NLS-1$
		assertTrue(second.getSource().contains("void consume(List<String> values)")); //$NON-NLS-1$
		assertTrue(first.getSource().contains("values.size()")); //$NON-NLS-1$
		assertTrue(second.getSource().contains("values.size()")); //$NON-NLS-1$

		undo.perform(new NullProgressMonitor());
		for (int index= 0; index < units.size(); index++) {
			assertTrue(units.get(index).getSource().equals(originals.get(index)));
		}
	}

	private static ClosedSourceParameterMigrationPlan plan(
			ICompilationUnit caller,
			CallerFacts callerFacts,
			List<ContainerParameterRewritePlan> parameterPlans,
			String callTargetMethodHandle) {
		TargetContainerContract target= target();
		ContainerLocalRewritePlan callerPlan= new ContainerLocalRewritePlan(
				caller.getHandleIdentifier(),
				callerFacts.bindingKey(),
				"java.util.List", //$NON-NLS-1$
				"java.util.ArrayList", //$NON-NLS-1$
				target,
				List.of(
						new LocalEdit(EditKind.CHANGE_LOCAL_DECLARATION, 1, 1),
						new LocalEdit(EditKind.REPLACE_EMPTY_ARRAY_INITIALIZER, 1, 1),
						new LocalEdit(EditKind.REMOVE_ARRAY_GROWTH, 2, 1),
						new LocalEdit(EditKind.REPLACE_TAIL_WRITE_WITH_ADD, 3, 1),
						new LocalEdit(
								EditKind.VERIFY_ARGUMENT_TRANSFER,
								callerFacts.argumentStart(),
								callerFacts.argumentLength())),
				List.of(new ArgumentTransfer(
						callTargetMethodHandle,
						0,
						callerFacts.argumentStart(),
						callerFacts.argumentLength())));
		return new ClosedSourceParameterMigrationPlan(
				target, callerPlan, parameterPlans);
	}

	private static ContainerParameterRewritePlan parameterPlan(
			ICompilationUnit unit,
			ParameterFacts facts) {
		List<ParameterEdit> edits= new ArrayList<>();
		edits.add(new ParameterEdit(
				ContainerParameterRewritePlan.EditKind.CHANGE_PARAMETER_DECLARATION,
				1,
				1));
		if (facts.lengthStart() >= 0) {
			edits.add(new ParameterEdit(
					ContainerParameterRewritePlan.EditKind.REPLACE_LENGTH_WITH_SIZE,
					facts.lengthStart(),
					facts.lengthLength()));
		}
		if (facts.iterationStart() >= 0) {
			edits.add(new ParameterEdit(
					ContainerParameterRewritePlan.EditKind.VERIFY_ENCOUNTER_ITERATION,
					facts.iterationStart(),
					facts.iterationLength()));
		}
		return new ContainerParameterRewritePlan(
				unit.getHandleIdentifier(),
				facts.methodHandle(),
				facts.bindingKey(),
				0,
				"java.util.List", //$NON-NLS-1$
				target(),
				edits);
	}

	private static CallerFacts callerFacts(CompilationUnit root) {
		String[] bindingKey= { null };
		int[] argumentRange= { -1, -1 };
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
				if (!"consume".equals(invocation.getName().getIdentifier()) //$NON-NLS-1$
						|| invocation.arguments().size() != 1) {
					return true;
				}
				Expression argument= (Expression) invocation.arguments().get(0);
				if (argument instanceof SimpleName name
						&& "values".equals(name.getIdentifier())) { //$NON-NLS-1$
					argumentRange[0]= name.getStartPosition();
					argumentRange[1]= name.getLength();
				}
				return true;
			}
		});
		if (bindingKey[0] == null || argumentRange[0] < 0) {
			throw new IllegalStateException("Missing caller source facts"); //$NON-NLS-1$
		}
		return new CallerFacts(
				bindingKey[0], argumentRange[0], argumentRange[1]);
	}

	private static ParameterFacts parameterFacts(
			CompilationUnit root,
			boolean requireBodyEvidence) {
		String[] methodHandle= { null };
		String[] bindingKey= { null };
		int[] lengthRange= { -1, -1 };
		int[] iterationRange= { -1, -1 };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration method) {
				if (!"consume".equals(method.getName().getIdentifier())) { //$NON-NLS-1$
					return true;
				}
				IMethodBinding binding= method.resolveBinding();
				IJavaElement element= binding == null
						? null : binding.getMethodDeclaration().getJavaElement();
				methodHandle[0]= element == null
						? null : element.getHandleIdentifier();
				SingleVariableDeclaration parameter=
						(SingleVariableDeclaration) method.parameters().get(0);
				IVariableBinding parameterBinding= parameter.resolveBinding();
				bindingKey[0]= parameterBinding == null
						? null : parameterBinding.getVariableDeclaration().getKey();
				return true;
			}

			@Override
			public boolean visit(QualifiedName name) {
				if ("length".equals(name.getName().getIdentifier()) //$NON-NLS-1$
						&& name.getQualifier() instanceof SimpleName qualifier
						&& "values".equals(qualifier.getIdentifier())) { //$NON-NLS-1$
					lengthRange[0]= name.getStartPosition();
					lengthRange[1]= name.getLength();
				}
				return true;
			}

			@Override
			public boolean visit(EnhancedForStatement statement) {
				Expression expression= statement.getExpression();
				if (expression instanceof SimpleName name
						&& "values".equals(name.getIdentifier())) { //$NON-NLS-1$
					iterationRange[0]= expression.getStartPosition();
					iterationRange[1]= expression.getLength();
				}
				return true;
			}
		});
		if (methodHandle[0] == null || bindingKey[0] == null
				|| requireBodyEvidence
						&& (lengthRange[0] < 0 || iterationRange[0] < 0)) {
			throw new IllegalStateException("Missing parameter source facts"); //$NON-NLS-1$
		}
		return new ParameterFacts(
				methodHandle[0],
				bindingKey[0],
				lengthRange[0],
				lengthRange[1],
				iterationRange[0],
				iterationRange[1]);
	}

	private ICompilationUnit createUnit(String name, String source) throws CoreException {
		IPackageFragment fragment= context.getSourceFolder()
				.createPackageFragment("test", false, null); //$NON-NLS-1$
		return fragment.createCompilationUnit(name, source, true, null);
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

	private static TargetContainerContract target() {
		return new TargetContainerContract(
				ContainerShape.LIST,
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				Mutability.MUTABLE,
				NullContract.ALLOWED,
				"Use one mutable dynamic sequence contract."); //$NON-NLS-1$
	}

	private static String callerSource() {
		return """
			package test;
			import java.util.Arrays;
			class Caller {
				void collect(Contract receiver, String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					receiver.consume(values);
				}
			}
			""";
	}

	private static String contractSource() {
		return """
			package test;
			interface Contract {
				void consume(String[] values);
			}
			""";
	}

	private static String firstSource() {
		return implementationSource("First"); //$NON-NLS-1$
	}

	private static String secondSource() {
		return implementationSource("Second"); //$NON-NLS-1$
	}

	private static String implementationSource(String typeName) {
		return """
			package test;
			class %s implements Contract {
				@Override
				public void consume(String[] values) {
					int count = values.length;
					for (String value : values) {
						System.out.println(value + count);
					}
				}
			}
			""".formatted(typeName);
	}

	private record CallerFacts(
			String bindingKey,
			int argumentStart,
			int argumentLength) {
	}

	private record ParameterFacts(
			String methodHandle,
			String bindingKey,
			int lengthStart,
			int lengthLength,
			int iterationStart,
			int iterationLength) {
	}
}

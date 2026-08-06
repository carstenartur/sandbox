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

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;

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

class ClosedSourceParameterMigrationIntegrationTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	@Test
	void appliesAndUndoesCallerAndParameterAtomically() throws Exception {
		ICompilationUnit caller= createUnit("Caller.java", callerSource()); //$NON-NLS-1$
		ICompilationUnit receiver= createUnit("Receiver.java", receiverSource()); //$NON-NLS-1$
		String originalCaller= caller.getSource();
		String originalReceiver= receiver.getSource();
		CompilationUnit callerRoot= parse(caller);
		CompilationUnit receiverRoot= parse(receiver);
		CallerFacts callerFacts= callerFacts(callerRoot);
		ParameterFacts parameterFacts= parameterFacts(receiverRoot);
		ClosedSourceParameterMigrationPlan plan= plan(
				caller, receiver, callerFacts, parameterFacts);

		ICleanUpFix callerFix= ClosedSourceParameterMigrationFix.create(
				caller, callerRoot, plan);
		ICleanUpFix receiverFix= ClosedSourceParameterMigrationFix.create(
				receiver, receiverRoot, plan);
		assertNotNull(callerFix);
		assertNotNull(receiverFix);

		CompositeChange change= new CompositeChange(
				"Migrate caller and parameter container contract"); //$NON-NLS-1$
		change.add(callerFix.createChange(null));
		change.add(receiverFix.createChange(null));
		Change undo= change.perform(null);
		assertNotNull(undo);

		String migratedCaller= caller.getSource();
		String migratedReceiver= receiver.getSource();
		assertTrue(migratedCaller.contains("List<String> values")); //$NON-NLS-1$
		assertTrue(migratedCaller.contains("new ArrayList<>()")); //$NON-NLS-1$
		assertTrue(migratedCaller.contains("values.add(value)")); //$NON-NLS-1$
		assertTrue(migratedCaller.contains("receiver.consume(values)")); //$NON-NLS-1$
		assertFalse(migratedCaller.contains("Arrays.copyOf(values")); //$NON-NLS-1$
		assertTrue(migratedReceiver.contains("void consume(List<String> values)")); //$NON-NLS-1$
		assertTrue(migratedReceiver.contains("values.size()")); //$NON-NLS-1$
		assertFalse(migratedReceiver.contains("String[] values")); //$NON-NLS-1$

		undo.perform(null);

		assertTrue(caller.getSource().contains("String[] values = new String[0]")); //$NON-NLS-1$
		assertTrue(receiver.getSource().contains("void consume(String[] values)")); //$NON-NLS-1$
		assertTrue(caller.getSource().contains("Arrays.copyOf(values")); //$NON-NLS-1$
		assertTrue(receiver.getSource().contains("values.length")); //$NON-NLS-1$
		assertTrue(caller.getSource().equals(originalCaller));
		assertTrue(receiver.getSource().equals(originalReceiver));
	}

	private static ClosedSourceParameterMigrationPlan plan(
			ICompilationUnit caller,
			ICompilationUnit receiver,
			CallerFacts callerFacts,
			ParameterFacts parameterFacts) {
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
						parameterFacts.methodHandle(),
						0,
						callerFacts.argumentStart(),
						callerFacts.argumentLength())));
		List<ParameterEdit> parameterEdits= new ArrayList<>();
		parameterEdits.add(new ParameterEdit(
				ContainerParameterRewritePlan.EditKind.CHANGE_PARAMETER_DECLARATION,
				1,
				1));
		parameterEdits.add(new ParameterEdit(
				ContainerParameterRewritePlan.EditKind.REPLACE_LENGTH_WITH_SIZE,
				parameterFacts.lengthStart(),
				parameterFacts.lengthLength()));
		parameterEdits.add(new ParameterEdit(
				ContainerParameterRewritePlan.EditKind.VERIFY_ENCOUNTER_ITERATION,
				2,
				1));
		ContainerParameterRewritePlan parameterPlan= new ContainerParameterRewritePlan(
				receiver.getHandleIdentifier(),
				parameterFacts.methodHandle(),
				parameterFacts.bindingKey(),
				0,
				"java.util.List", //$NON-NLS-1$
				target,
				parameterEdits);
		return new ClosedSourceParameterMigrationPlan(
				target, callerPlan, parameterPlan);
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

	private static ParameterFacts parameterFacts(CompilationUnit root) {
		String[] methodHandle= { null };
		String[] bindingKey= { null };
		int[] lengthRange= { -1, -1 };
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
		});
		if (methodHandle[0] == null || bindingKey[0] == null || lengthRange[0] < 0) {
			throw new IllegalStateException("Missing parameter source facts"); //$NON-NLS-1$
		}
		return new ParameterFacts(
				methodHandle[0], bindingKey[0], lengthRange[0], lengthRange[1]);
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
				void collect(Receiver receiver, String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					receiver.consume(values);
				}
			}
			""";
	}

	private static String receiverSource() {
		return """
			package test;
			class Receiver {
				void consume(String[] values) {
					int count = values.length;
					for (String value : values) {
						System.out.println(value + count);
					}
				}
			}
			""";
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
			int lengthLength) {
	}
}

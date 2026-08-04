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
package org.sandbox.jdt.container.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.ContinuationKind;
import org.sandbox.jdt.container.api.ContainerFlowContinuationPlan.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchKind;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.ResolvedSearchTarget;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.TargetKind;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class ContainerFlowContinuationDetectorTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	private final ContainerFlowContinuationDetector detector=
			new ContainerFlowContinuationDetector();

	@Test
	void discoversTransparentFieldAndSignatureContinuations() throws CoreException {
		ICompilationUnit unit= createUnit("""
			package test;
			class Sample {
				String[] field = new String[0];

				void consume(String[] input) {
					String[] alias = input;
				}

				String[] produce() {
					String[] result = field;
					return result;
				}

				void caller(String[] values) {
					consume(values);
					String[] received = produce();
				}
			}
			""");
		IType type= unit.getType("Sample"); //$NON-NLS-1$
		IField field= type.getField("field"); //$NON-NLS-1$
		IMethod consume= method(type, "consume"); //$NON-NLS-1$
		IMethod produce= method(type, "produce"); //$NON-NLS-1$

		ResolvedContainerFlowSearchPlan targets= new ResolvedContainerFlowSearchPlan(List.of(
				fieldTarget(field),
				methodTarget("parameter:consume:0", consume, //$NON-NLS-1$
						SearchKind.METHOD_OVERRIDE_FAMILY, 0),
				methodTarget("parameter:consume:0", consume, //$NON-NLS-1$
						SearchKind.METHOD_CALLERS, 0),
				methodTarget("return:produce", produce, //$NON-NLS-1$
						SearchKind.METHOD_OVERRIDE_FAMILY, -1),
				methodTarget("return:produce", produce, //$NON-NLS-1$
						SearchKind.METHOD_CALLERS, -1)));

		ContainerFlowContinuationPlan plan= detector.detect(parse(unit), targets);

		assertTrue(plan.complete());
		Set<ContinuationKind> kinds= plan.roots().stream()
				.map(ContainerFlowContinuationPlan.ContinuationRoot::kind)
				.collect(Collectors.toSet());
		assertEquals(Set.of(
				ContinuationKind.FIELD,
				ContinuationKind.PARAMETER_DECLARATION,
				ContinuationKind.CALL_ARGUMENT,
				ContinuationKind.RETURN_EXPRESSION,
				ContinuationKind.RETURN_CONSUMER), kinds);
		assertEquals(5, plan.roots().size());
		assertTrue(plan.roots().stream()
				.allMatch(root -> root.profile().identity().hasResolvedBinding()));
	}

	@Test
	void rejectsComplexTransfersAndIgnoresLambdaReturns() throws CoreException {
		ICompilationUnit unit= createUnit("""
			package test;
			import java.util.function.Supplier;
			class Sample {
				void consume(String[] input) { }

				String[] produce() {
					Supplier<String[]> nested = () -> {
						return new String[0];
					};
					return new String[0];
				}

				void caller() {
					consume(new String[0]);
					int length = produce().length;
					java.util.function.Consumer<String[]> reference = this::consume;
				}
			}
			""");
		IType type= unit.getType("Sample"); //$NON-NLS-1$
		IMethod consume= method(type, "consume"); //$NON-NLS-1$
		IMethod produce= method(type, "produce"); //$NON-NLS-1$
		ResolvedContainerFlowSearchPlan targets= new ResolvedContainerFlowSearchPlan(List.of(
				methodTarget("parameter:consume:0", consume, //$NON-NLS-1$
						SearchKind.METHOD_CALLERS, 0),
				methodTarget("return:produce", produce, //$NON-NLS-1$
						SearchKind.METHOD_OVERRIDE_FAMILY, -1),
				methodTarget("return:produce", produce, //$NON-NLS-1$
						SearchKind.METHOD_CALLERS, -1)));

		ContainerFlowContinuationPlan plan= detector.detect(parse(unit), targets);

		assertFalse(plan.complete());
		Set<DiagnosticKind> kinds= plan.diagnostics().stream()
				.map(ContainerFlowContinuationPlan.ContinuationDiagnostic::kind)
				.collect(Collectors.toSet());
		assertTrue(kinds.contains(DiagnosticKind.UNSUPPORTED_ARGUMENT));
		assertTrue(kinds.contains(DiagnosticKind.UNSUPPORTED_RETURN_EXPRESSION));
		assertTrue(kinds.contains(DiagnosticKind.UNSUPPORTED_RETURN_CONSUMER));
		assertTrue(kinds.contains(DiagnosticKind.METHOD_REFERENCE));
		long returnDiagnostics= plan.diagnostics().stream()
				.filter(diagnostic -> diagnostic.kind()
						== DiagnosticKind.UNSUPPORTED_RETURN_EXPRESSION)
				.count();
		assertEquals(1, returnDiagnostics,
				"The return inside the lambda must not be attributed to produce()."); //$NON-NLS-1$
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

	private static IMethod method(IType type, String name) throws CoreException {
		return Arrays.stream(type.getMethods())
				.filter(candidate -> candidate.getElementName().equals(name))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Missing method: " + name)); //$NON-NLS-1$
	}

	private static ResolvedSearchTarget fieldTarget(IField field) {
		return new ResolvedSearchTarget(
				"field:field", //$NON-NLS-1$
				SearchKind.FIELD_REFERENCES,
				TargetKind.FIELD,
				"field-binding", //$NON-NLS-1$
				"type-key", //$NON-NLS-1$
				field.getHandleIdentifier(),
				-1,
				"Continue field flow"); //$NON-NLS-1$
	}

	private static ResolvedSearchTarget methodTarget(
			String sourceNodeId,
			IMethod method,
			SearchKind searchKind,
			int signatureIndex) {
		return new ResolvedSearchTarget(
				sourceNodeId,
				searchKind,
				TargetKind.METHOD,
				"parameter-binding", //$NON-NLS-1$
				"method-key", //$NON-NLS-1$
				method.getHandleIdentifier(),
				signatureIndex,
				"Continue method flow"); //$NON-NLS-1$
	}
}

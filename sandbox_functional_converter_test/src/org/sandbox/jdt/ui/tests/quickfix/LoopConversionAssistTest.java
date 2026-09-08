/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.text.correction.AssistContext;
import org.eclipse.jdt.internal.ui.text.correction.ProblemLocation;
import org.eclipse.jdt.internal.ui.text.correction.proposals.FixCorrectionProposal;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.ltk.core.refactoring.Change;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix.helper.LoopConversionInspection;
import org.sandbox.jdt.internal.corext.fix.helper.LoopConversionService;
import org.sandbox.jdt.internal.corext.fix.helper.LoopTargetFormat;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.LoopConversionQuickAssistProcessor;
import org.sandbox.jdt.internal.ui.fix.LoopConversionQuickFixProcessor;
import org.sandbox.jdt.internal.ui.fix.UseFunctionalCallCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;

/** Real JDT proposals and LTK changes; no substitutes for Eclipse APIs. */
class LoopConversionAssistTest {
	@RegisterExtension
	final AssistTestContext context = new AssistTestContext();
	private ICompilationUnit workingCopy;

	@AfterEach
	void discardWorkingCopy() throws Exception {
		if (workingCopy != null) workingCopy.discardWorkingCopy();
	}

	static class AssistTestContext extends AbstractEclipseJava {
		AssistTestContext() {
			super("testresources/rtstubs_22.jar", JavaCore.VERSION_22);
		}

		void verify(ICompilationUnit unit) throws Exception {
			assertNoCompilationError(unit);
		}
	}

	private static final String SOURCE = """
			package test1;
			import java.util.*;
			class Example {
				void run(List<String> values, List<String> sink) {
					values.stream().filter(s -> !s.isEmpty()).map(String::trim).forEach(sink::add);
					values.stream().filter(s -> !s.isEmpty()).map(String::trim).forEach(sink::add);
				}
			}
			""".replace("sink::add", "s -> sink.add(s)");

	private AssistContext invocation(String source, int offset, int length) throws Exception {
		ICompilationUnit unit = context.getSourceFolder().createPackageFragment("test1", false, null)
				.createCompilationUnit("Example.java", source, true, null);
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		var invocation = new AssistContext(unit, offset, length);
		invocation.setASTRoot((CompilationUnit) parser.createAST(null));
		return invocation;
	}

	private FixCorrectionProposal proposal(AssistContext invocation, LoopTargetFormat target) throws Exception {
		return (FixCorrectionProposal) Arrays.stream(new LoopConversionQuickAssistProcessor().getAssists(invocation, new IProblemLocation[0]))
				.filter(item -> item.getDisplayString().equals(LoopConversionQuickAssistProcessor.label(target))).findFirst().orElseThrow();
	}

	@ParameterizedTest
	@ValueSource(strings = { "enhanced_for", "iterator_while" })
	void previewApplyUndoAndRepeatedAnalysisSelectOnlyOnePipeline(String targetId) throws Exception {
		var invocation = invocation(SOURCE, SOURCE.indexOf("filter"), 0);
		workingCopy = invocation.getCompilationUnit();
		workingCopy.becomeWorkingCopy(null);
		LoopTargetFormat target = LoopTargetFormat.fromId(targetId);
		assertTrue(new LoopConversionQuickAssistProcessor().hasAssists(invocation));
		var first = proposal(invocation, target);
		String preview = first.getPreviewContent();
		assertEquals(preview, proposal(invocation, target).getPreviewContent(), "Opening proposals must not reserve extra names");
		assertEquals(1, preview.split("\\.stream\\(\\)", -1).length - 1, preview);
		assertEquals(SOURCE, invocation.getCompilationUnit().getSource(), "Preview must not edit the buffer");
		var change = first.getTextChange();
		change.initializeValidationData(null);
		assertFalse(change.isValid(null).hasFatalError());
		Change undo = change.perform(null);
		try {
			assertEquals(preview, invocation.getCompilationUnit().getSource());
			context.verify(invocation.getCompilationUnit());
			assertNotNull(undo);
			undo.initializeValidationData(null);
			Change redo = undo.perform(null);
			if (redo != null) {
				redo.dispose();
			}
			assertEquals(SOURCE, invocation.getCompilationUnit().getSource());
		} finally {
			change.dispose();
			if (undo != null) {
				undo.dispose();
			}
		}
	}

	@Test
	void noAssistForUnrelatedSelectionOrUnsupportedInnerLoop() throws Exception {
		var processor = new LoopConversionQuickAssistProcessor();
		assertFalse(processor.hasAssists(invocation(SOURCE, SOURCE.indexOf("class Example"), 0)));
		assertFalse(processor.hasAssists(invocation(SOURCE, SOURCE.indexOf("values.stream"), SOURCE.lastIndexOf(';') - SOURCE.indexOf("values.stream") + 1)));
		String nested = "class Example { void run(java.util.List<String> values) { for (String s : values) { while (true) { break; } } } }";
		assertFalse(processor.hasAssists(invocation(nested, nested.indexOf("break"), 0)));
	}

	@Test
	void offersCaretInsideOrdinaryLoopBodyButRejectsCompilationErrors() throws Exception {
		String source = "class Example { void run(java.util.List<String> values) { for (String s : values) { System.out.println(s); } } }";
		assertTrue(new LoopConversionQuickAssistProcessor().hasAssists(invocation(source, source.indexOf("println"), 0)));
		String broken = source.replace("println(s)", "println(missing)");
		assertFalse(new LoopConversionQuickAssistProcessor().hasAssists(invocation(broken, broken.indexOf("println"), 0)));
	}

	@Test
	void sourceLevelAndExplicitCleanupTargetAreRespected() throws Exception {
		String source = "class Example { void run(java.util.List<String> values) { for (String s : values) { System.out.println(s); } } }";
		var invocation = invocation(source, source.indexOf("for ("), 0);
		context.getJavaProject().setOption(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_7);
		assertTrue(LoopConversionService.analyze(invocation.getASTRoot(), LoopConversionService.handlers(LoopTargetFormat.STREAM)).operations().isEmpty());
		context.getJavaProject().setOption(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_22);
		Map<String, String> values = new HashMap<>(LoopConversionQuickAssistProcessor.options(LoopTargetFormat.WHILE_LOOP));
		values.put(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP, CleanUpOptions.TRUE);
		CleanUpOptions options = new CleanUpOptions();
		values.forEach(options::setOption);
		var cleanup = new UseFunctionalCallCleanUpCore();
		cleanup.setOptions(options);
		var fix = cleanup.createFix(new CleanUpContext(invocation.getCompilationUnit(), invocation.getASTRoot()));
		assertNotNull(fix);
		var change = fix.createChange(null);
		assertTrue(change.getPreviewContent(null).contains("while ("));
		assertFalse(change.getPreviewContent(null).contains(".forEach("));
		change.dispose();
	}

	@Test
	void inspectionOptInProducesOnlyOwnFixableStyleProblems() throws Exception {
		var preferences = InstanceScope.INSTANCE.getNode(LoopConversionInspection.PLUGIN_ID);
		String previousSeverity = preferences.get(LoopConversionInspection.SEVERITY, null);
		String previousTarget = preferences.get(LoopConversionInspection.TARGET, null);
		try {
			var invocation = invocation(SOURCE, SOURCE.indexOf("filter"), 0);
			preferences.put(LoopConversionInspection.SEVERITY, "off");
			assertEquals(0, LoopConversionInspection.problems(invocation.getASTRoot()).length);
			preferences.put(LoopConversionInspection.SEVERITY, "info");
			preferences.put(LoopConversionInspection.TARGET, "enhanced_for");
			var problems = LoopConversionInspection.problems(invocation.getASTRoot());
			assertEquals(2, problems.length);
			assertTrue(problems[0].isInfo());
			assertFalse(problems[0].isError());
			var processor = new LoopConversionQuickFixProcessor();
			var problem = problems[0];
			IProblemLocation location = new ProblemLocation(problem.getSourceStart(), problem.getSourceEnd() - problem.getSourceStart() + 1,
					problem.getID(), problem.getArguments(), false, problem.getMarkerType());
			assertEquals(1, processor.getCorrections(invocation, new IProblemLocation[] { location, location }).length);
			assertEquals(0, processor.getCorrections(invocation, new IProblemLocation[] {
					new ProblemLocation(location.getOffset(), location.getLength(), problem.getID(), new String[0], false, "org.eclipse.jdt.core.problem") }).length);
			var project = context.getJavaProject().getProject();
			project.build(IncrementalProjectBuilder.FULL_BUILD, null);
			assertEquals(2, project.findMarkers(LoopConversionInspection.MARKER_TYPE, false, IResource.DEPTH_INFINITE).length);
			preferences.put(LoopConversionInspection.SEVERITY, "off");
			assertEquals(0, processor.getCorrections(invocation, new IProblemLocation[] { location }).length);
			project.build(IncrementalProjectBuilder.FULL_BUILD, null);
			assertEquals(0, project.findMarkers(LoopConversionInspection.MARKER_TYPE, false, IResource.DEPTH_INFINITE).length);
		} finally {
			if (previousSeverity == null) preferences.remove(LoopConversionInspection.SEVERITY);
			else preferences.put(LoopConversionInspection.SEVERITY, previousSeverity);
			if (previousTarget == null) preferences.remove(LoopConversionInspection.TARGET);
			else preferences.put(LoopConversionInspection.TARGET, previousTarget);
		}
	}

	@Test
	void extensionsInstantiateThroughTheRealRegistry() throws Exception {
		Map<String, String> expected = Map.of("org.eclipse.jdt.ui.quickAssistProcessors", "LoopConversionQuickAssistProcessor",
				"org.eclipse.jdt.ui.quickFixProcessors", "LoopConversionQuickFixProcessor",
				"org.eclipse.jdt.core.compilationParticipant", "LoopConversionInspection");
		for (var entry : expected.entrySet()) {
			var elements = Platform.getExtensionRegistry().getConfigurationElementsFor(entry.getKey());
			var element = Arrays.stream(elements).filter(candidate -> candidate.getContributor().getName().equals(LoopConversionInspection.PLUGIN_ID))
					.findFirst().orElseThrow();
			assertEquals(entry.getValue(), element.createExecutableExtension("class").getClass().getSimpleName());
		}
	}
}

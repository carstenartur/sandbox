/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the
 * Eclipse Public License 2.0: https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java10;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions.performAndCheck;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.internal.corext.fix.helper.ChangeBehavior;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** JavaCompareAction has an encoding cleanup in a catch removed by an earlier operation. */
class RemovedCatchEncodingTest {

	@RegisterExtension
	final EclipseJava17 context= new EclipseJava17();

	static Stream<Arguments> scenarios() {
		return Arrays.stream(ChangeBehavior.values()).flatMap(mode -> Stream.of(
				Arguments.of(mode, 0), Arguments.of(mode, 1), Arguments.of(mode, 2)));
	}

	@ParameterizedTest
	@MethodSource("scenarios") //$NON-NLS-1$
	void onlySurvivingCatchBodiesReceiveEncodingEdits(ChangeBehavior mode, int scenario) throws Exception {
		String members= switch (scenario) {
			case 0 -> """
					byte[] encode(String contents, String charset) {
						byte[] bytes;
						try {
							bytes= contents.getBytes("UTF-16"); //$NON-NLS-1$
						} catch (UnsupportedEncodingException exception) {
							bytes= contents.getBytes();
						}
						return bytes;
					}
					""";
			case 1 -> """
					byte[] encode(String contents, String charset) {
						byte[] first;
						try {
							first= contents.getBytes("UTF-16"); //$NON-NLS-1$
						} catch (UnsupportedEncodingException exception) {
							first= contents.getBytes();
						}
						try {
							return (contents + first.length).getBytes("UTF-8"); //$NON-NLS-1$
						} catch (UnsupportedEncodingException exception) {
							return contents.getBytes();
						}
					}
					""";
			case 2 -> """
					byte[] encode(String contents, String charset) {
						try {
							return contents.getBytes(charset);
						} catch (UnsupportedEncodingException exception) {
							return contents.getBytes();
						}
					}
					""";
			default -> throw new IllegalArgumentException();
		};
		String original= "package test;\nimport java.io.UnsupportedEncodingException;\npublic class Example {\n" //$NON-NLS-1$
				+ members + "}\n"; //$NON-NLS-1$
		ICompilationUnit unit= context.getSourceFolder().createPackageFragment("test", false, null) //$NON-NLS-1$
				.createCompilationUnit("Example.java", original, false, null); //$NON-NLS-1$
		CompilationUnit before= parse(unit);
		assertTrue(Arrays.stream(before.getProblems()).noneMatch(problem -> problem.isError()),
				() -> Arrays.toString(before.getProblems()));
		var fix= new UseExplicitEncodingCleanUpCore(options(mode)).createFix(new CleanUpContext(unit, before));
		assertNotNull(fix);
		var change= fix.createChange(null);
		try {
			change.initializeValidationData(null);
			var undo= performAndCheck(change, null, unit);
			assertNotNull(undo);
			try {
				CompilationUnit after= parse(unit);
				int[] counts= new int[2];
				after.accept(new ASTVisitor() {
					@Override
					public boolean visit(CatchClause clause) {
						counts[0]++;
						return true;
					}

					@Override
					public boolean visit(MethodInvocation invocation) {
						var binding= invocation.resolveMethodBinding();
						if ("getBytes".equals(invocation.getName().getIdentifier())) { //$NON-NLS-1$
							assertNotNull(binding);
							if (binding.getParameterTypes().length == 1 && "java.nio.charset.Charset" //$NON-NLS-1$
									.equals(binding.getParameterTypes()[0].getQualifiedName())) {
								counts[1]++;
							}
						}
						return true;
					}
				});
				assertEquals(scenario == 2 ? 1 : 0, counts[0], unit.getSource());
				assertEquals(scenario == 1 ? 2 : 1, counts[1], unit.getSource());
				assertNull(new UseExplicitEncodingCleanUpCore(options(mode))
						.createFix(new CleanUpContext(unit, after)), "Idempotence"); //$NON-NLS-1$
			} finally {
				var redo= undo.perform(null);
				if (redo != null) redo.dispose();
				undo.dispose();
			}
			assertEquals(original, unit.getSource(), "Undo must restore both the try and its original fallback"); //$NON-NLS-1$
		} finally {
			change.dispose();
		}
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}

	private static Map<String, String> options(ChangeBehavior mode) {
		return Map.of(MYCleanUpConstants.EXPLICITENCODING_CLEANUP, "true", switch (mode) { //$NON-NLS-1$
			case KEEP_BEHAVIOR -> MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR;
			case ENFORCE_UTF8 -> MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8;
			case ENFORCE_UTF8_AGGREGATE -> MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8;
		}, "true"); //$NON-NLS-1$
	}
}

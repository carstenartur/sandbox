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

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix.helper.ChangeBehavior;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Class-file bytes are already encoded and are not String encoding operations. */
class GetBytesReceiverBindingTest {

	@RegisterExtension
	final EclipseJava17 context= new EclipseJava17();

	@ParameterizedTest
	@EnumSource(ChangeBehavior.class)
	void leavesClassInterfaceAndNestedTypeMethodsUnchanged(ChangeBehavior mode) throws Exception {
		verify(mode, """
				static class ClassFile {
					byte[] getBytes() { return new byte[0]; }
					byte[] getBytes(String name) { return new byte[0]; }
				}
				interface IClassFile { byte[] getBytes(); }
				static class Cache { static class ClassBytes { byte[] getBytes() { return new byte[0]; } } }
				byte[] read(ClassFile file) { return file.getBytes(); }
				byte[] named(ClassFile file) { return file.getBytes("UTF-8"); } //$NON-NLS-1$
				byte[] read(IClassFile file) { return file.getBytes(); }
				byte[] read(Cache.ClassBytes file) { return file.getBytes(); }
				""", false); //$NON-NLS-1$
	}

	@ParameterizedTest
	@EnumSource(ChangeBehavior.class)
	void leavesAnUnrelatedTypeNamedStringUnchanged(ChangeBehavior mode) throws Exception {
		verify(mode, """
				static class String { byte[] getBytes() { return new byte[0]; } }
				byte[] read(String value) { return value.getBytes(); }
				""", false); //$NON-NLS-1$
	}

	@ParameterizedTest
	@EnumSource(ChangeBehavior.class)
	void stillModernizesBoundJavaStrings(ChangeBehavior mode) throws Exception {
		verify(mode, """
				byte[] read(String value) { return value.getBytes(); }
				byte[] chained(String value) { return value.trim().getBytes(); }
				byte[] named(String value) throws java.io.UnsupportedEncodingException {
					return value.getBytes("UTF-8"); //$NON-NLS-1$
				}
				""", true); //$NON-NLS-1$
	}

	private void verify(ChangeBehavior mode, String members, boolean changes) throws Exception {
		String original= "package test;\npublic class Example {\n" + members + "}\n"; //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit unit= context.getSourceFolder().createPackageFragment("test", false, null) //$NON-NLS-1$
				.createCompilationUnit("Example.java", original, false, null); //$NON-NLS-1$
		CompilationUnit root= parse(unit);
		assertTrue(Arrays.stream(root.getProblems()).noneMatch(problem -> problem.isError()),
				() -> Arrays.toString(root.getProblems()));
		Map<String, String> options= Map.of(MYCleanUpConstants.EXPLICITENCODING_CLEANUP, "true", switch (mode) { //$NON-NLS-1$
			case KEEP_BEHAVIOR -> MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR;
			case ENFORCE_UTF8 -> MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8;
			case ENFORCE_UTF8_AGGREGATE -> MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8;
		}, "true"); //$NON-NLS-1$
		var cleanup= new UseExplicitEncodingCleanUpCore(options);
		var fix= cleanup.createFix(new CleanUpContext(unit, root));
		if (!changes) {
			assertNull(fix, "Only a bound java.lang.String receiver may gain a Charset argument"); //$NON-NLS-1$
			assertEquals(original, unit.getSource());
			return;
		}
		assertNotNull(fix);
		var change= fix.createChange(null);
		try {
			change.initializeValidationData(null);
			var undo= performAndCheck(change, null, unit);
			assertNotNull(undo);
			try {
				int[] calls= new int[1];
				parse(unit).accept(new ASTVisitor() {
					@Override public boolean visit(MethodInvocation node) {
						if ("getBytes".equals(node.getName().getIdentifier())) { //$NON-NLS-1$
							var binding= node.resolveMethodBinding();
							assertNotNull(binding);
							assertEquals("java.lang.String", binding.getDeclaringClass().getQualifiedName()); //$NON-NLS-1$
							assertEquals("java.nio.charset.Charset", binding.getParameterTypes()[0].getQualifiedName()); //$NON-NLS-1$
							calls[0]++;
						}
						return true;
					}
				});
				assertEquals(3, calls[0]);
				assertNull(cleanup.createFix(new CleanUpContext(unit, parse(unit))));
			} finally {
				var redo= undo.perform(null);
				if (redo != null) redo.dispose();
				undo.dispose();
			}
			assertEquals(original, unit.getSource());
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
}

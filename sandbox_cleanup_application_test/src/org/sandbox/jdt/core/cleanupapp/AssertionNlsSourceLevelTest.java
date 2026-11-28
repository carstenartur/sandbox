/*
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 */
package org.sandbox.jdt.core.cleanupapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSElement;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSScanner;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;

/** Exercises the registered assertion cleanup without opening a native workbench. */
class AssertionNlsSourceLevelTest {
	@RegisterExtension
	final AbstractEclipseJava context= new AbstractEclipseJava("", JavaCore.VERSION_17) { //$NON-NLS-1$
		@Override
		public IClasspathEntry[] getDefaultClasspath() {
			return new IClasspathEntry[] { JavaCore.newContainerEntry(new Path("org.eclipse.jdt.launching.JRE_CONTAINER")) }; //$NON-NLS-1$
		}
	};

	@ParameterizedTest
	@CsvSource({ "false,false", "true,false", "false,true", "true,true" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	void preservesModernEscapesInTheCurrentSource(boolean tagged, boolean workingCopy) throws Exception {
		// R4_40/c922f757 AddImportTest uses \s in text blocks (first at line 1259).
		String block= "\"\"\"\nspace\\s\\\ncontinued\n\"\"\""; //$NON-NLS-1$
		String source= """
				package test;
				import static org.junit.Assert.assertEquals;
				public class Example {
					public static final String CONTENT = $block;$tag
					public void check(int actual) {
						assertEquals("right\\smessage", 1, actual);$tag
					}
				}
				""".replace("$block", block).replace("$tag", tagged ? " //$NON-NLS-1$" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		var sourceRoot= context.createClasspathForJUnit(new Path("org.eclipse.jdt.junit.JUNIT_CONTAINER/4")); //$NON-NLS-1$
		var project= context.getJavaProject();
		AbstractEclipseJava.addToClasspath(project, JavaCore.newContainerEntry(new Path("org.eclipse.jdt.junit.JUNIT_CONTAINER/5"))); //$NON-NLS-1$
		project.setOption(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL, JavaCore.WARNING);
		var projectOptions= project.getOptions(false);
		var classpath= Arrays.asList(project.getRawClasspath());
		String saved= workingCopy ? "package test; public class Example {}\n" : source; //$NON-NLS-1$
		ICompilationUnit primary= sourceRoot.createPackageFragment("test", false, null) //$NON-NLS-1$
				.createCompilationUnit("Example.java", saved, false, null); //$NON-NLS-1$
		ICompilationUnit unit= workingCopy ? primary.getWorkingCopy(null) : primary;
		try {
			if (workingCopy) unit.getBuffer().setContents(source);
			assertEquals(source, unit.getSource());
			assertEquals(JavaCore.VERSION_17, project.getOption(JavaCore.COMPILER_SOURCE, true));
			assertEquals(JavaCore.VERSION_17, project.getOption(JavaCore.COMPILER_COMPLIANCE, true));
			var diagnostics= CompilerDiagnosticAssertions.capture(unit);
			List<String> taggedBefore= taggedLiterals(unit);
			assertEquals(tagged ? 2 : 0, taggedBefore.size());
			var cleanups= JavaPlugin.getDefault().getCleanUpRegistry()
					.createCleanUps(Set.of("org.eclipse.jdt.ui.cleanup.junitcleanup")); //$NON-NLS-1$
			assertEquals(1, cleanups.length);
			var cleanup= cleanups[0];
			var options= new CleanUpOptions();
			options.setOption("cleanup.junitcleanup", CleanUpOptions.TRUE); //$NON-NLS-1$
			options.setOption("cleanup.junitcleanup_4_assert", CleanUpOptions.TRUE); //$NON-NLS-1$
			cleanup.setOptions(options);
			try {
				var status= cleanup.checkPreConditions(project, new ICompilationUnit[] { unit }, null);
				assertFalse(status.hasError(), status::toString);
				var fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
				assertNotNull(fix);
				var change= fix.createChange(null);
				try {
					var preview= new Document(unit.getSource());
					change.getEdit().copy().apply(preview);
					String generated= preview.get();
					if (!workingCopy) assertEquals(generated, change.getPreviewContent(null));
					assertEquals(source, unit.getSource(), "Preview must not mutate the current buffer"); //$NON-NLS-1$
					assertTrue(generated.contains(block), generated);
					assertTrue(generated.contains("\"right\\smessage\""), generated); //$NON-NLS-1$
					unit.getBuffer().setContents(generated);
					diagnostics.assertNoNewDiagnostics();
					assertEquals(taggedBefore, taggedLiterals(unit));
					int[] calls= { 0 };
					parse(unit).accept(new ASTVisitor() {
						@Override
						public boolean visit(MethodInvocation invocation) {
							if ("assertEquals".equals(invocation.getName().getIdentifier())) { //$NON-NLS-1$
								calls[0]++;
								var binding= invocation.resolveMethodBinding();
								assertNotNull(binding);
								assertFalse(binding.isRecovered());
								assertEquals("org.junit.jupiter.api.Assertions", binding.getDeclaringClass().getQualifiedName()); //$NON-NLS-1$
								assertEquals(3, invocation.arguments().size());
								assertEquals("1", invocation.arguments().get(0).toString()); //$NON-NLS-1$
								assertEquals("actual", invocation.arguments().get(1).toString()); //$NON-NLS-1$
								assertEquals(tagged ? "junitMessage" : "\"right\\smessage\"", invocation.arguments().get(2).toString()); //$NON-NLS-1$ //$NON-NLS-2$
							}
							return true;
						}
					});
					assertEquals(1, calls[0]);
				} finally {
					change.dispose();
					unit.getBuffer().setContents(source);
					diagnostics.assertRestored();
				}
			} finally {
				cleanup.checkPostConditions(null);
			}
			assertEquals(projectOptions, project.getOptions(false));
			assertEquals(classpath, Arrays.asList(project.getRawClasspath()));
		} finally {
			if (workingCopy) unit.discardWorkingCopy();
		}
		assertEquals(saved, primary.getSource());
	}

	private static List<String> taggedLiterals(ICompilationUnit unit) throws Exception {
		return Arrays.stream(NLSScanner.scan(unit)).flatMap(line -> Arrays.stream(line.getElements()))
				.filter(NLSElement::hasTag).map(NLSElement::getValue).sorted().toList();
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		assertTrue(Arrays.stream(root.getProblems()).noneMatch(problem -> problem.isError()),
				() -> Arrays.toString(root.getProblems()));
		return root;
	}
}

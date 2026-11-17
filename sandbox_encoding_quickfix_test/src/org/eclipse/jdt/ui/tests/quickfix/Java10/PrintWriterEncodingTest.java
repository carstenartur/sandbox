/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java10;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions.performAndCheck;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.testplugin.TestOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix.helper.ChangeBehavior;
import org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava10;

/** PrintWriter encoding changes must retain the printer type and constructor contract. */
class PrintWriterEncodingTest {
	@RegisterExtension
	final EclipseJava10 context= new EclipseJava10();
	@TempDir
	Path compileDirectory;

	@BeforeEach
	void setUp() throws Exception {
		var options= TestOptions.getDefaultOptions();
		options.put(JavaCore.COMPILER_PB_UNUSED_IMPORT, JavaCore.WARNING);
		options.put(JavaCore.COMPILER_PB_UNUSED_DECLARED_THROWN_EXCEPTION, JavaCore.WARNING);
		options.put(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL, JavaCore.WARNING);
		JavaCore.setOptions(options);
		TestOptions.initializeCodeGenerationOptions();
		JavaPlugin.getDefault().getCodeTemplateStore().load();
	}

	@ParameterizedTest
	@EnumSource(ChangeBehavior.class)
	void preservesExistingWriterEncodingAndAutoFlush(ChangeBehavior mode) throws Exception {
		verify(mode, """
				java.io.PrintWriter report(java.nio.file.Path file) throws java.io.IOException {
				    return new java.io.PrintWriter(new java.io.OutputStreamWriter(
				        java.nio.file.Files.newOutputStream(file), java.nio.charset.StandardCharsets.UTF_8));
				}
				java.io.PrintWriter wrap(java.io.Writer writer) {
				    return new java.io.PrintWriter(writer);
				}
				java.io.PrintWriter wrap(java.io.Writer writer, boolean autoFlush) {
				    return new java.io.PrintWriter(writer, autoFlush);
				}
				java.io.PrintWriter subclass(java.io.Writer writer, boolean autoFlush) {
				    return new java.io.PrintWriter(writer, autoFlush) {
				        @Override public void println(String text) { super.println(text); }
				    };
				}
				""", 0);
	}

	@ParameterizedTest
	@EnumSource(ChangeBehavior.class)
	void preservesUnrelatedTypesWithTheSameSimpleName(ChangeBehavior mode) throws Exception {
		verify(mode, """
				static class PrintWriter {
				    PrintWriter() { }
				    PrintWriter(java.io.File file) { }
				}
				PrintWriter custom(java.io.File file) { return new PrintWriter(file); }
				PrintWriter empty() { return new PrintWriter(); }
				""", 0);
	}

	@ParameterizedTest
	@EnumSource(ChangeBehavior.class)
	void preservesStreamAndAlreadyEncodedOverloads(ChangeBehavior mode) throws Exception {
		verify(mode, """
				java.io.PrintWriter stream(java.io.OutputStream out) {
				    return new java.io.PrintWriter(out);
				}
				java.io.PrintWriter stream(java.io.OutputStream out, boolean autoFlush) {
				    return new java.io.PrintWriter(out, autoFlush);
				}
				java.io.PrintWriter stream(java.io.OutputStream out, boolean autoFlush, java.nio.charset.Charset charset) {
				    return new java.io.PrintWriter(out, autoFlush, charset);
				}
				java.io.PrintWriter file(java.io.File file, java.nio.charset.Charset charset) throws java.io.IOException {
				    return new java.io.PrintWriter(file, charset);
				}
				java.io.PrintWriter path(String file, java.nio.charset.Charset charset) throws java.io.IOException {
				    return new java.io.PrintWriter(file, charset);
				}
				java.io.PrintWriter dynamic(java.io.File file, String charset)
				        throws java.io.FileNotFoundException, java.io.UnsupportedEncodingException {
				    return new java.io.PrintWriter(file, charset);
				}
				""", 0);
	}

	@ParameterizedTest
	@EnumSource(ChangeBehavior.class)
	void upgradesFilesWithoutChangingThePrinterOrFileNotFoundException(ChangeBehavior mode) throws Exception {
		String after= verify(mode, """
				java.io.PrintWriter file(java.io.File file) throws java.io.FileNotFoundException {
				    return new java.io.PrintWriter(file);
				}
				java.io.PrintWriter path(String file) throws java.io.FileNotFoundException {
				    return new java.io.PrintWriter(file);
				}
				void use(String file) {
				    try (java.io.PrintWriter writer= path(file)) { writer.println("text"); } //$NON-NLS-1$
				    catch (java.io.FileNotFoundException ex) { throw new IllegalStateException(ex); }
				}
				""", 2);
		assertTrue(after.contains("throws java.io.FileNotFoundException"), after);
		assertFalse(after.contains("throws java.io.IOException"), after);
		assertTrue(after.contains("false,"), after);
	}

	@ParameterizedTest
	@EnumSource(ChangeBehavior.class)
	void upgradesKnownEncodingNamesWithoutOverwritingTheCharset(ChangeBehavior mode) throws Exception {
		String after= verify(mode, """
				java.io.PrintWriter file(java.io.File file) throws java.io.FileNotFoundException {
				    try { return new java.io.PrintWriter(file, "ISO-8859-1"); } //$NON-NLS-1$
				    catch (java.io.UnsupportedEncodingException ex) { throw new AssertionError(ex); }
				}
				java.io.PrintWriter path(String path) throws java.io.FileNotFoundException {
				    try { return new java.io.PrintWriter(path, "UTF-16LE"); } //$NON-NLS-1$
				    catch (java.io.UnsupportedEncodingException ex) { throw new AssertionError(ex); }
				}
				""", 2);
		assertTrue(after.contains("ISO_8859_1"), after);
		assertTrue(after.contains("UTF_16LE"), after);
		assertFalse(after.contains("UnsupportedEncodingException"), after);
		assertFalse(after.contains("$NON-NLS-"), after);
	}

	@ParameterizedTest
	@EnumSource(ChangeBehavior.class)
	void upgradesKnownEncodingWithAFileExceptionContract(ChangeBehavior mode) throws Exception {
		String after= verify(mode, """
				java.io.PrintWriter file(java.io.File file) throws java.io.IOException {
				    return new java.io.PrintWriter(file, "ISO-8859-1"); //$NON-NLS-1$
				}
				java.io.PrintWriter path(String file) throws Exception {
				    return new java.io.PrintWriter(file, "UTF-16LE"); //$NON-NLS-1$
				}
				""", 2);
		assertTrue(after.contains("ISO_8859_1"), after);
		assertTrue(after.contains("UTF_16LE"), after);
		assertTrue(after.contains("throws java.io.IOException"), after);
	}

	@ParameterizedTest
	@EnumSource(ChangeBehavior.class)
	void preservesEncodingExceptionContractsAndMultiCatches(ChangeBehavior mode) throws Exception {
		verify(mode, """
				private java.io.PrintWriter narrow(java.io.File file)
				        throws java.io.FileNotFoundException, java.io.UnsupportedEncodingException {
				    return new java.io.PrintWriter(file, "UTF-8"); //$NON-NLS-1$
				}
				java.io.PrintWriter caller(java.io.File file) throws java.io.FileNotFoundException {
				    try { return narrow(file); }
				    catch (java.io.UnsupportedEncodingException ex) { throw new AssertionError(ex); }
				}
				java.io.PrintWriter multi(String file) {
				    try { return new java.io.PrintWriter(file, "UTF-8"); } //$NON-NLS-1$
				    catch (java.io.FileNotFoundException | java.io.UnsupportedEncodingException ex) {
				        throw new IllegalStateException(ex);
				    }
				}
				""", 0);
	}

	private String verify(ChangeBehavior mode, String members, int replacements) throws Exception {
		String before= "package test1;\npublic class E1 {\n" + members + "}\n";
		compile(before);
		ICompilationUnit unit= context.getSourceFolder().createPackageFragment("test1", false, null)
				.createCompilationUnit("E1.java", before, false, null);
		CharsetModernizationTest.diagnostics(unit);
		var cleanup= new UseExplicitEncodingCleanUpCore(CharsetScopeTest.options(mode));
		var fix= cleanup.createFix(new CleanUpContext(unit, CharsetModernizationTest.parse(unit)));
		if (replacements == 0) {
			if (fix != null) {
				var unexpected= fix.createChange(null);
				try { compile(unexpected.getPreviewContent(null)); }
				finally { unexpected.dispose(); }
			}
			assertNull(fix, "An existing writer or stream must keep its constructor contract");
			assertEquals(before, unit.getSource());
			return before;
		}
		assertNotNull(fix);
		var change= fix.createChange(null);
		try {
			String after= change.getPreviewContent(null);
			assertEquals(before, unit.getSource(), "Preview must preserve the source");
			compile(after);
			change.initializeValidationData(null);
			var undo= performAndCheck(change, null, unit);
			assertNotNull(undo);
			try {
				AtomicInteger count= new AtomicInteger();
				CharsetModernizationTest.parse(unit).accept(new ASTVisitor() {
					@Override public boolean visit(ClassInstanceCreation node) {
						var binding= node.resolveConstructorBinding();
						assertNotNull(binding);
						if ("java.io.PrintWriter".equals(binding.getDeclaringClass().getQualifiedName())) {
							assertEquals(List.of("java.io.OutputStream", "boolean", "java.nio.charset.Charset"),
									java.util.Arrays.stream(binding.getParameterTypes()).map(type -> type.getQualifiedName()).toList());
							count.incrementAndGet();
						}
						return true;
					}
				});
				assertEquals(replacements, count.get(), after);
				assertNull(cleanup.createFix(new CleanUpContext(unit, CharsetModernizationTest.parse(unit))), "Idempotence");
				var redo= undo.perform(null);
				if (redo != null) redo.dispose();
				assertEquals(before, unit.getSource(), "Undo must restore the source");
			} finally { undo.dispose(); }
			return after;
		} finally { change.dispose(); }
	}

	private void compile(String source) throws Exception {
		Path file= compileDirectory.resolve("E1.java");
		Files.writeString(file, source, StandardCharsets.UTF_8);
		var compiler= ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler);
		var diagnostics= new DiagnosticCollector<JavaFileObject>();
		try (var manager= compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
			assertTrue(compiler.getTask(null, manager, diagnostics,
					List.of("--release", "10", "-Xlint:all", "-Werror", "-d", compileDirectory.toString()), null,
					manager.getJavaFileObjects(file)).call(), diagnostics.getDiagnostics().toString());
			assertEquals(List.of(), diagnostics.getDiagnostics());
		}
	}
}

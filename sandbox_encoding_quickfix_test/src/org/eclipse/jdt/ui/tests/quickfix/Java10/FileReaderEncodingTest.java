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

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.internal.corext.fix.helper.ChangeBehavior;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** FileReader Charset overloads must preserve concrete types and checked exception contracts. */
class FileReaderEncodingTest {

	@RegisterExtension
	final EclipseJava17 context= new EclipseJava17();
	@TempDir
	Path output;

	static Stream<Arguments> modernLevels() {
		return cases(JavaCore.VERSION_11, JavaCore.VERSION_21);
	}

	static Stream<Arguments> legacyLevels() {
		return cases(JavaCore.VERSION_1_8, JavaCore.VERSION_10);
	}

	private static Stream<Arguments> cases(String... levels) {
		return Arrays.stream(ChangeBehavior.values()).flatMap(mode ->
				Arrays.stream(levels).map(level -> Arguments.of(mode, level)));
	}

	@ParameterizedTest
	@MethodSource("modernLevels") //$NON-NLS-1$
	void keepsFileReaderTypesAndTheCoreLineNumberReaderResourceContract(ChangeBehavior mode, String level) throws Exception {
		verify(mode, level, """
				FileReader file(java.io.File file) throws java.io.IOException { return new FileReader(file); }
				FileReader path(String file) throws java.io.IOException { return new FileReader(file); }
				String decode(String file) throws java.io.IOException {
					java.io.LineNumberReader reader= new java.io.LineNumberReader(new FileReader(file));
					String line= reader.readLine();
					reader.close();
					return line;
				}
				public static int readFrom(String file) throws java.io.IOException {
					try (FileReader reader= new FileReader(file)) { return reader.read(); }
				}
				""", true, 4, 0, true);
	}

	@ParameterizedTest
	@MethodSource("legacyLevels") //$NON-NLS-1$
	void retainsConcreteReaderTypesBeforeJava11(ChangeBehavior mode, String level) throws Exception {
		verify(mode, level, "FileReader open(String path) throws java.io.FileNotFoundException { return new FileReader(path); }", //$NON-NLS-1$
				false, 1, 0, false);
	}

	@ParameterizedTest
	@MethodSource("legacyLevels") //$NON-NLS-1$
	void keepsTheLegacyWrapperWhenTheDeclaredReaderTypeAcceptsIt(ChangeBehavior mode, String level) throws Exception {
		verify(mode, level, "java.io.Reader open(String path) throws java.io.FileNotFoundException { return new FileReader(path); }", //$NON-NLS-1$
				true, 0, 1, false);
	}

	@ParameterizedTest
	@EnumSource(ChangeBehavior.class)
	void doesNotBroadenNarrowMethodCatchOrLambdaExceptions(ChangeBehavior mode) throws Exception {
		verify(mode, JavaCore.VERSION_11, """
				FileReader narrow(String file) throws java.io.FileNotFoundException { return new FileReader(file); }
				FileReader caught(String file) {
					try { return new FileReader(file); }
					catch (java.io.FileNotFoundException exception) { return null; }
				}
				interface ReaderFactory { FileReader open() throws java.io.FileNotFoundException; }
				ReaderFactory factory(String file) throws java.io.IOException { return () -> new FileReader(file); }
				FileReader insideCatch(String file) throws java.io.FileNotFoundException {
					try { throw new java.io.IOException(); }
					catch (java.io.IOException exception) { return new FileReader(file); }
				}
				""", false, 4, 0, false);
	}

	@ParameterizedTest
	@EnumSource(ChangeBehavior.class)
	void respectsExistingBroadCatchAndFunctionalInterfaceContracts(ChangeBehavior mode) throws Exception {
		verify(mode, JavaCore.VERSION_11, """
				FileReader caught(String file) {
					try { return new FileReader(file); }
					catch (java.io.IOException exception) { return null; }
				}
				java.util.concurrent.Callable<FileReader> factory(String file) { return () -> new FileReader(file); }
				FileReader multiCatch(String file) {
					try { return new FileReader(file); }
					catch (java.io.IOException | IllegalArgumentException exception) { return null; }
				}
				""", true, 3, 0, false);
	}

	@ParameterizedTest
	@EnumSource(ChangeBehavior.class)
	void preservesExistingCharsetsAndConcreteDescriptorReaders(ChangeBehavior mode) throws Exception {
		verify(mode, JavaCore.VERSION_11, """
				FileReader file(java.io.File file, java.nio.charset.Charset charset) throws java.io.IOException {
					return new FileReader(file, charset);
				}
				FileReader path(String file, java.nio.charset.Charset charset) throws java.io.IOException {
					return new FileReader(file, charset);
				}
				FileReader descriptor(java.io.FileDescriptor descriptor) { return new FileReader(descriptor); }
				""", false, 3, 0, false);
	}

	private void verify(ChangeBehavior mode, String level, String members, boolean changed,
			int readers, int streams, boolean runRead) throws Exception {
		Map<String, String> compilerOptions= context.getJavaProject().getOptions(false);
		JavaCore.setComplianceOptions(level, compilerOptions);
		compilerOptions.put(JavaCore.COMPILER_PB_UNUSED_IMPORT, JavaCore.WARNING);
		context.getJavaProject().setOptions(compilerOptions);
		String original= "package test;\nimport java.io.FileReader;\npublic class Example {\n" + members + "}\n"; //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit unit= context.getSourceFolder().createPackageFragment("test", false, null) //$NON-NLS-1$
				.createCompilationUnit("Example.java", original, false, null); //$NON-NLS-1$
		compile(original, level);
		var cleanup= new UseExplicitEncodingCleanUpCore(options(mode));
		var fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
		if (!changed) {
			assertNull(fix, "An unsupported or already encoded constructor must retain its contract"); //$NON-NLS-1$
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
				String result= unit.getSource();
				assertConstructorTypes(parse(unit), readers, streams);
				assertNull(new UseExplicitEncodingCleanUpCore(options(mode))
						.createFix(new CleanUpContext(unit, parse(unit))), "Idempotence"); //$NON-NLS-1$
				compile(result, level);
				if (runRead) {
					Path file= output.resolve("read.txt"); //$NON-NLS-1$
					Files.writeString(file, "A", StandardCharsets.UTF_8); //$NON-NLS-1$
					try (var loader= new URLClassLoader(new java.net.URL[] { output.toUri().toURL() }, null)) {
						Object value= loader.loadClass("test.Example").getMethod("readFrom", String.class).invoke(null, file.toString()); //$NON-NLS-1$ //$NON-NLS-2$
						assertEquals(Integer.valueOf('A'), value);
					}
				}
			} finally {
				var redo= undo.perform(null);
				if (redo != null) redo.dispose();
				undo.dispose();
			}
			assertEquals(original, unit.getSource(), "Undo must restore the concrete reader declaration"); //$NON-NLS-1$
		} finally {
			change.dispose();
		}
	}

	private static void assertConstructorTypes(CompilationUnit root, int readers, int streams) {
		int[] counts= new int[2];
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(ClassInstanceCreation node) {
				var binding= node.resolveConstructorBinding();
				assertNotNull(binding);
				String type= binding.getDeclaringClass().getQualifiedName();
				if ("java.io.FileReader".equals(type) || "java.io.InputStreamReader".equals(type)) { //$NON-NLS-1$ //$NON-NLS-2$
					assertEquals("java.nio.charset.Charset", binding.getParameterTypes()[1].getQualifiedName()); //$NON-NLS-1$
					counts["java.io.FileReader".equals(type) ? 0 : 1]++; //$NON-NLS-1$
				}
				return true;
			}
		});
		assertEquals(readers, counts[0]);
		assertEquals(streams, counts[1]);
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

	private void compile(String source, String level) throws Exception {
		Path file= output.resolve("Example.java"); //$NON-NLS-1$
		Files.writeString(file, source, StandardCharsets.UTF_8);
		var compiler= ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler);
		var diagnostics= new DiagnosticCollector<JavaFileObject>();
		try (var manager= compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
			String release= JavaCore.VERSION_1_8.equals(level) ? "8" : level; //$NON-NLS-1$
			assertTrue(compiler.getTask(null, manager, diagnostics,
					List.of("--release", release, "-Xlint:all,-options", "-Werror", "-d", output.toString()), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					null, manager.getJavaFileObjects(file)).call(), () -> diagnostics.getDiagnostics().toString());
		}
	}
}

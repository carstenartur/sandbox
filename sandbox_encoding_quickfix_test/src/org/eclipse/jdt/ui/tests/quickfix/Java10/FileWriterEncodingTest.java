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

/** FileWriter's Java 11 Charset overloads preserve concrete types and append mode. */
class FileWriterEncodingTest {

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

	static Stream<Arguments> inferredLevels() {
		return cases(JavaCore.VERSION_10, JavaCore.VERSION_11);
	}

	private static Stream<Arguments> cases(String... levels) {
		return Arrays.stream(ChangeBehavior.values()).flatMap(mode ->
				Arrays.stream(levels).map(level -> Arguments.of(mode, level)));
	}

	@ParameterizedTest
	@MethodSource("modernLevels") //$NON-NLS-1$
	void keepsFileAndStringWriterTypesAndAppendSemantics(ChangeBehavior mode, String level) throws Exception {
		verify(mode, level, members("FileWriter"), true, 5, 0, true); //$NON-NLS-1$
	}

	@ParameterizedTest
	@MethodSource("legacyLevels") //$NON-NLS-1$
	void preservesConcreteTypesBeforeTheCharsetOverloadExists(ChangeBehavior mode, String level) throws Exception {
		verify(mode, level, members("FileWriter"), false, 5, 0, false); //$NON-NLS-1$
	}

	@ParameterizedTest
	@MethodSource("legacyLevels") //$NON-NLS-1$
	void preservesAppendWhenAnOlderWriterContextAcceptsTheWrapper(ChangeBehavior mode, String level) throws Exception {
		verify(mode, level, members("java.io.Writer"), true, 0, 5, true); //$NON-NLS-1$
	}

	@ParameterizedTest
	@MethodSource("inferredLevels") //$NON-NLS-1$
	void keepsTheInferredConcreteTypeAcrossTheJava11Boundary(ChangeBehavior mode, String level) throws Exception {
		verify(mode, level, """
				FileWriter open(java.io.File file) throws java.io.IOException {
					var writer= new FileWriter(file);
					writer.write('x');
					return writer;
				}
				""", JavaCore.VERSION_11.equals(level), 1, 0, false); //$NON-NLS-1$
	}

	@ParameterizedTest
	@EnumSource(ChangeBehavior.class)
	void preservesExistingCharsetsAndTheDescriptorConstructor(ChangeBehavior mode) throws Exception {
		verify(mode, JavaCore.VERSION_11, """
				FileWriter file(java.io.File file, java.nio.charset.Charset charset) throws java.io.IOException {
					return new FileWriter(file, charset);
				}
				FileWriter path(String file, java.nio.charset.Charset charset) throws java.io.IOException {
					return new FileWriter(file, charset);
				}
				FileWriter fileAppend(java.io.File file, java.nio.charset.Charset charset, boolean append) throws java.io.IOException {
					return new FileWriter(file, charset, append);
				}
				FileWriter pathAppend(String file, java.nio.charset.Charset charset, boolean append) throws java.io.IOException {
					return new FileWriter(file, charset, append);
				}
				FileWriter descriptor(java.io.FileDescriptor descriptor) { return new FileWriter(descriptor); }
				""", false, 5, 0, false); //$NON-NLS-1$
	}

	private static String members(String writerType) {
		return """
				TYPE file(java.io.File file) throws java.io.IOException { return new FileWriter(file); }
				TYPE path(String file) throws java.io.IOException { return new FileWriter(file); }
				TYPE fileAppend(java.io.File file, boolean append) throws java.io.IOException {
					return new FileWriter(file, append);
				}
				TYPE pathAppend(String file, boolean append) throws java.io.IOException {
					return new FileWriter(file, append);
				}
				public static void appendTo(String file) throws java.io.IOException {
					try (TYPE writer= new FileWriter(file, true)) { writer.write('B'); }
				}
				""".replace("TYPE", writerType); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private void verify(ChangeBehavior mode, String level, String members, boolean changed,
			int writers, int streams, boolean runAppend) throws Exception {
		Map<String, String> compilerOptions= context.getJavaProject().getOptions(false);
		JavaCore.setComplianceOptions(level, compilerOptions);
		compilerOptions.put(JavaCore.COMPILER_PB_UNUSED_IMPORT, JavaCore.WARNING);
		context.getJavaProject().setOptions(compilerOptions);
		String original= "package test;\nimport java.io.FileWriter;\npublic class Example {\n" + members + "}\n"; //$NON-NLS-1$ //$NON-NLS-2$
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
				assertConstructorTypes(parse(unit), writers, streams);
				assertNull(new UseExplicitEncodingCleanUpCore(options(mode))
						.createFix(new CleanUpContext(unit, parse(unit))), "Idempotence"); //$NON-NLS-1$
				compile(result, level);
				if (runAppend) {
					Path file= output.resolve("append.txt"); //$NON-NLS-1$
					Files.writeString(file, "A", StandardCharsets.UTF_8); //$NON-NLS-1$
					try (var loader= new URLClassLoader(new java.net.URL[] { output.toUri().toURL() }, null)) {
						loader.loadClass("test.Example").getMethod("appendTo", String.class).invoke(null, file.toString()); //$NON-NLS-1$ //$NON-NLS-2$
					}
					assertEquals("AB", Files.readString(file, StandardCharsets.UTF_8)); //$NON-NLS-1$
				}
			} finally {
				var redo= undo.perform(null);
				if (redo != null) redo.dispose();
				undo.dispose();
			}
			assertEquals(original, unit.getSource(), "Undo must restore the concrete writer declaration"); //$NON-NLS-1$
		} finally {
			change.dispose();
		}
	}

	private static void assertConstructorTypes(CompilationUnit root, int writers, int streams) {
		int[] counts= new int[2];
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(ClassInstanceCreation node) {
				var binding= node.resolveConstructorBinding();
				assertNotNull(binding);
				String type= binding.getDeclaringClass().getQualifiedName();
				if ("java.io.FileWriter".equals(type) || "java.io.OutputStreamWriter".equals(type)) { //$NON-NLS-1$ //$NON-NLS-2$
					assertEquals("java.nio.charset.Charset", binding.getParameterTypes()[1].getQualifiedName()); //$NON-NLS-1$
					counts["java.io.FileWriter".equals(type) ? 0 : 1]++; //$NON-NLS-1$
				}
				return true;
			}
		});
		assertEquals(writers, counts[0]);
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

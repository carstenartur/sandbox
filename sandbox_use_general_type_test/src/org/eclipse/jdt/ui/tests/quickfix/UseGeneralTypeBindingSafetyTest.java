/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.JavaManipulation;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.UseGeneralTypeCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava8;

/** Runs the real rewrite on complete units, including constructor declarations and callers. */
public class UseGeneralTypeBindingSafetyTest {

	@RegisterExtension
	final AbstractEclipseJava context = new EclipseJava8();
	@RegisterExtension
	final AbstractEclipseJava dependency = new EclipseJava8();

	@ParameterizedTest
	@MethodSource("restrictedTypes") //$NON-NLS-1$
	void classpathAccessRulesDoNotBecomeNewTypeReferences(String restrictedType, int accessKind) throws Exception {
		var pack = dependency.getSourceFolder().createPackageFragment("api", false, null); //$NON-NLS-1$
		var base = pack.createCompilationUnit("Base.java", """
				package api;
				public class Base<T> { public void act() { } }
				""", false, null); //$NON-NLS-1$
		var hidden = pack.createCompilationUnit("Hidden.java", """
				package api;
				public class Hidden { }
				""", false, null); //$NON-NLS-1$
		var child = pack.createCompilationUnit("Child.java", """
				package api;
				public class Child extends Base<Hidden> { }
				""", false, null); //$NON-NLS-1$
		AbstractEclipseJava.addToClasspath(context.getJavaProject(), JavaCore.newProjectEntry(
				dependency.getJavaProject().getPath(),
				new IAccessRule[] { JavaCore.newAccessRule(new Path("api/" + restrictedType), accessKind) }, //$NON-NLS-1$
				true, new IClasspathAttribute[0], false));
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_DISCOURAGED_REFERENCE, JavaCore.ERROR);
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_FORBIDDEN_REFERENCE, JavaCore.ERROR);
		var caller = create("Caller.java", """
				package test;
				import api.Child;
				import java.util.HashMap;
				public class Caller {
				    public void run(Child input) {
				        Child candidate = input;
				        candidate.act();
				        HashMap<String, Integer> values = new HashMap<>();
				        values.size();
				    }
				}
				"""); //$NON-NLS-1$
		String output = applyAndUndo(caller, base, hidden, child, caller);
		assertTrue(output.contains("Child candidate"), output); //$NON-NLS-1$
		assertFalse(output.contains("import api.Base"), output); //$NON-NLS-1$
		assertTrue(output.contains("Map<String, Integer> values"), output); //$NON-NLS-1$
	}

	static Stream<Arguments> restrictedTypes() {
		return Stream.of(Arguments.of("Base", IAccessRule.K_DISCOURAGED), //$NON-NLS-1$
				Arguments.of("Base", IAccessRule.K_NON_ACCESSIBLE), //$NON-NLS-1$
				Arguments.of("Hidden", IAccessRule.K_DISCOURAGED), //$NON-NLS-1$
				Arguments.of("Hidden", IAccessRule.K_NON_ACCESSIBLE)); //$NON-NLS-1$
	}

	@Test
	void nestedTypeArgumentsKeepTheirLexicalImportContext() throws Exception {
		// SWT's checked-in preferences use wildcard imports at one imported type.
		new ProjectScope(context.getJavaProject().getProject()).getNode(JavaUI.ID_PLUGIN)
				.putInt(PreferenceConstants.ORGIMPORTS_ONDEMANDTHRESHOLD, 1);
		assertEquals("1", JavaManipulation.getPreference(PreferenceConstants.ORGIMPORTS_ONDEMANDTHRESHOLD, context.getJavaProject())); //$NON-NLS-1$
		ICompilationUnit unit = create("Owner.java", """
				package test;
				import java.util.*;
				import java.util.List;
				class Owner {
				    private static class Entry { }
				    private static class OtherEntry { }
				    public int size() {
				        List<Entry> candidate = new ArrayList<>();
				        List<OtherEntry> other = new ArrayList<>();
				        candidate.add(new Entry());
				        other.add(new OtherEntry());
				        return candidate.size() + other.size();
				    }
				}
				"""); //$NON-NLS-1$
		String output = applyAndUndo(unit, unit);
		assertTrue(output.contains("Collection<Entry> candidate"), output); //$NON-NLS-1$
		assertTrue(output.contains("Collection<OtherEntry> other"), output); //$NON-NLS-1$
		assertFalse(output.contains("import test.Owner"), output); //$NON-NLS-1$
	}

	@Test
	void constructorContractFromAnotherCompilationUnitIsRetained() throws Exception {
		ICompilationUnit declaration = create("Consumer.java", """
				package test;
				public class Consumer {
				    public Consumer(String value) { }
				}
				"""); //$NON-NLS-1$
		ICompilationUnit caller = create("Caller.java", """
				package test;
				import java.util.HashMap;
				public class Caller {
				    public void run() {
				        String candidate = "a";
				        candidate.length();
				        new Consumer(candidate);
				        HashMap<String, Integer> values = new HashMap<>();
				        values.size();
				    }
				}
				"""); //$NON-NLS-1$
		String output = applyAndUndo(caller, declaration, caller);
		assertTrue(output.contains("String candidate"), output); //$NON-NLS-1$
		assertTrue(output.contains("Map<String, Integer> values"), output); //$NON-NLS-1$
		assertFalse(output.contains("HashMap<String, Integer> values"), output); //$NON-NLS-1$
	}

	private ICompilationUnit create(String name, String source) throws Exception {
		return context.getSourceFolder().createPackageFragment("test", false, null) //$NON-NLS-1$
				.createCompilationUnit(name, source, false, null);
	}

	@Test
	void inheritedPrivateTypeAnnotationsAreNotCopiedIntoTheCaller() throws Exception {
		ICompilationUnit declaration = context.getSourceFolder().createPackageFragment("api", false, null) //$NON-NLS-1$
				.createCompilationUnit("Types.java", """
						package api;
						public class Types {
						    @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE) private @interface Identity { }
						    public static class Base<T> { public T get() { return null; } }
						    public static class Child<T> extends Base<@Identity T> { }
						}
						""", false, null); //$NON-NLS-1$
		ICompilationUnit caller = create("Caller.java", """
				package test;
				import api.Types;
				import java.util.HashMap;
				public class Caller {
				    public void run(Types.Child<String> input) {
				        Types.Child<String> candidate = input;
				        candidate.get();
				        HashMap<String, Integer> values = new HashMap<>();
				        values.size();
				    }
				}
				"""); //$NON-NLS-1$
		String output = applyAndUndo(caller, declaration, caller);
		assertTrue(output.contains("Types.Child<String> candidate"), output); //$NON-NLS-1$
		assertFalse(output.contains("Identity"), output); //$NON-NLS-1$
		assertFalse(output.contains("HashMap<String, Integer> values"), output); //$NON-NLS-1$
	}

	private static String applyAndUndo(ICompilationUnit unit, ICompilationUnit... scope) throws Exception {
		for (ICompilationUnit checked : scope) {
			for (var problem : parse(checked).getProblems()) assertFalse(problem.isError(), problem::getMessage);
		}
		var before = CompilerDiagnosticAssertions.capture(scope);
		String original = unit.getSource();
		var monitor = new NullProgressMonitor();
		var buffers = FileBuffers.getTextFileBufferManager();
		buffers.connect(unit.getPath(), LocationKind.IFILE, monitor);
		try {
			var cleanup = new UseGeneralTypeCleanUpCore(Map.of(MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP, CleanUpOptions.TRUE));
			var fix = cleanup.createFix(new CleanUpContext(unit, parse(unit)));
			assertNotNull(fix, "The independent safe variable must still widen"); //$NON-NLS-1$
			var change = fix.createChange(monitor);
			var undo = CompilerDiagnosticAssertions.performAndCheck(change, monitor, scope);
			try {
				String result = unit.getSource();
				assertNotEquals(original, result);
				undo.perform(monitor);
				assertEquals(original, unit.getSource());
				before.assertRestored();
				return result;
			} finally {
				undo.dispose();
				change.dispose();
			}
		} finally {
			buffers.disconnect(unit.getPath(), LocationKind.IFILE, monitor);
		}
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}
}

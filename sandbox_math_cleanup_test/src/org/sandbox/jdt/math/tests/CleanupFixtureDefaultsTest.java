/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the
 * Eclipse Public License 2.0: https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CleanUpPreferenceUtil;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.ui.fix.MathematicalCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;

/** Checks the persisted shared fixture profile against the installed cleanup registry. */
class CleanupFixtureDefaultsTest {

	@RegisterExtension
	final ProfileFixture context= new ProfileFixture();

	@Test
	void disablingOptionsPreservesEveryRegisteredScalarDefault() throws Exception {
		Map<String, String> defaults= registryDefaults();
		assertTrue(defaults.values().contains(CleanUpOptions.TRUE), "Exercise enabled registry defaults"); //$NON-NLS-1$
		assertTrue(defaults.containsKey(MathCleanUpOptions.KINDS), "The installed mathematics defaults must participate"); //$NON-NLS-1$
		assertDisabledProfile(defaults);

		context.enable(MathCleanUpOptions.CLEANUP);
		context.set(MathCleanUpOptions.WORK_BUDGET, "1"); //$NON-NLS-1$
		Map<String, String> configured= CleanUpPreferenceUtil.loadOptions(InstanceScope.INSTANCE);
		assertEquals(CleanUpOptions.TRUE, configured.get(MathCleanUpOptions.CLEANUP));
		assertEquals("1", configured.get(MathCleanUpOptions.WORK_BUDGET)); //$NON-NLS-1$
		context.resetDefaults();

		assertDisabledProfile(defaults);
		assertEquals(defaults, registryDefaults(), "Disabling the fixture must not mutate registry defaults"); //$NON-NLS-1$
	}

	@Test
	void disabledMathematicsAcceptsThePersistedFixtureProfile() throws Exception {
		String source= "package test; public class Example { public int value(int input) { return input; } }"; //$NON-NLS-1$
		ICompilationUnit unit= context.getSourceFolder().createPackageFragment("test", false, null) //$NON-NLS-1$
				.createCompilationUnit("Example.java", source, false, null); //$NON-NLS-1$
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		assertTrue(Arrays.stream(root.getProblems()).noneMatch(problem -> problem.isError()),
				() -> Arrays.toString(root.getProblems()));
		var diagnostics= CompilerDiagnosticAssertions.capture(unit);
		Map<String, String> settings= CleanUpPreferenceUtil.loadOptions(InstanceScope.INSTANCE);
		assertEquals(CleanUpOptions.FALSE, settings.get(MathCleanUpOptions.CLEANUP));
		MathematicalCleanUpCore cleanup= new MathematicalCleanUpCore(settings);
		assertFalse(cleanup.getRequirements().requiresAST());
		var monitor= new NullProgressMonitor();
		var status= cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, monitor);
		assertTrue(status.isOK(), status.toString());
		assertNull(cleanup.createFix(new CleanUpContext(unit, root)));
		var postconditions= cleanup.checkPostConditions(monitor);
		assertTrue(postconditions.isOK(), postconditions.toString());
		assertEquals(source, unit.getSource());
		diagnostics.assertNoNewDiagnostics();
		assertEquals(settings, CleanUpPreferenceUtil.loadOptions(InstanceScope.INSTANCE));
	}

	private static void assertDisabledProfile(Map<String, String> defaults) throws CoreException {
		Map<String, String> actual= CleanUpPreferenceUtil.loadOptions(InstanceScope.INSTANCE);
		for (var entry : defaults.entrySet()) {
			String value= entry.getValue();
			String expected= CleanUpOptions.TRUE.equals(value) || CleanUpOptions.FALSE.equals(value)
					? CleanUpOptions.FALSE : value;
			assertEquals(expected, actual.get(entry.getKey()), entry.getKey());
		}
	}

	private static Map<String, String> registryDefaults() {
		var defaults= JavaPlugin.getDefault().getCleanUpRegistry().getDefaultOptions(CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS);
		Map<String, String> result= new LinkedHashMap<>();
		defaults.getKeys().forEach(key -> result.put(key, defaults.getValue(key)));
		return result;
	}

	private static final class ProfileFixture extends AbstractEclipseJava {
		ProfileFixture() {
			super("", JavaCore.VERSION_21); //$NON-NLS-1$
		}

		@Override
		public IClasspathEntry[] getDefaultClasspath() {
			return new IClasspathEntry[] { JavaCore.newContainerEntry(JavaRuntime.newDefaultJREContainerPath()) };
		}

		void resetDefaults() throws CoreException {
			disableAll();
		}
	}
}

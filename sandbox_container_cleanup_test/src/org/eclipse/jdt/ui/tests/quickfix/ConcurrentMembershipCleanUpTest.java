/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.CLEANUP;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class ConcurrentMembershipCleanUpTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	@Test
	void migratesOnlyAtomicMembershipAndPreservesConcurrentAdmissions(@TempDir Path output) throws Exception {
		String source= """
			package test;
			import java.util.Collections;
			import java.util.HashSet;
			import java.util.Set;
			public final class Sample {
				private final Set<String> seen = Collections.synchronizedSet(new HashSet<>());
				public boolean offer(String key) { return seen.add(java.util.Objects.requireNonNull(key)); }
				public boolean seen(String key) { return seen.contains(java.util.Objects.requireNonNull(key)); }
			}
			""";
		ICompilationUnit unit= createUnit(source);
		var cleanup= membershipCleanup();
		cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, null);
		ICleanUpFix fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
		assertNotNull(fix);
		var undo= fix.createChange(null).perform(null);
		assertNotNull(undo);
		assertTrue(unit.getSource().contains("ConcurrentHashMap.newKeySet()")); //$NON-NLS-1$
		assertFalse(Arrays.stream(parse(unit).getProblems()).anyMatch(problem -> problem.isError()));
		var file= Files.writeString(output.resolve("Sample.java"), unit.getSource()); //$NON-NLS-1$
		assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
				"-d", output.toString(), file.toString())); //$NON-NLS-1$
		try (var classes= new URLClassLoader(new URL[] { output.toUri().toURL() }, null)) {
			var type= classes.loadClass("test.Sample"); //$NON-NLS-1$
			var instance= type.getConstructor().newInstance();
			var offer= type.getMethod("offer", String.class); //$NON-NLS-1$
			var seen= type.getMethod("seen", String.class); //$NON-NLS-1$
			var start= new CyclicBarrier(8);
			try (var workers= Executors.newFixedThreadPool(8)) {
				var admitted= new ArrayList<Future<Integer>>();
				for (int i= 0; i < 8; i++) {
					admitted.add(workers.submit(() -> {
						start.await(30, TimeUnit.SECONDS);
						int count= 0;
						for (int key= 0; key < 128; key++) {
							if ((boolean) offer.invoke(instance, Integer.toString(key))) {
								count++;
							}
						}
						return count;
					}));
				}
				int count= 0;
				for (var result : admitted) {
					count+= result.get(30, TimeUnit.SECONDS);
				}
				assertEquals(128, count);
				for (int key= 0; key < 128; key++) {
					assertEquals(Boolean.TRUE, seen.invoke(instance, Integer.toString(key)));
				}
			}
		}
		cleanup.checkPostConditions(null);
		undo.perform(null);
		assertEquals(source, unit.getSource());
	}

	private static AbstractCleanUp membershipCleanup() throws CoreException {
		String id= "org.sandbox.jdt.ui.cleanup.container_contracts.concurrent_membership"; //$NON-NLS-1$
		var extensions= Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.jdt.ui.cleanUps"); //$NON-NLS-1$
		var entry= Arrays.stream(extensions).filter(e -> id.equals(e.getAttribute("id"))).findFirst(); //$NON-NLS-1$
		assertTrue(entry.isPresent(), "The opt-in concurrent membership migration must be registered"); //$NON-NLS-1$
		var cleanup= (AbstractCleanUp) entry.get().createExecutableExtension("class"); //$NON-NLS-1$
		var options= new CleanUpOptions();
		options.setOption(CLEANUP, CleanUpOptions.TRUE);
		options.setOption("cleanup.container_contracts.concurrent_membership", CleanUpOptions.TRUE); //$NON-NLS-1$
		cleanup.setOptions(options);
		return cleanup;
	}

	@Test
	void preservesLockedEscapingOrNullableMembershipAndRevalidatesWorkingCopies() throws Exception {
		String source= """
			package test;
			import java.util.Collections;
			import java.util.HashSet;
			import java.util.Set;
			public final class Sample {
				private final Set<String> seen = Collections.synchronizedSet(new HashSet<>());
				public boolean offer() { return seen.add("item"); }
			}
			""";
		ICompilationUnit unit= createUnit(source);
		for (String extra : List.of("void lock() { synchronized (seen) {} }",
				"Set<String> expose() { return seen; }", "boolean has(String key) { return seen.contains(key); }",
				"void scan() { for (String key : seen) {} }")) {
			unit.getBuffer().setContents(source);
			unit.save(null, true);
			var cleanup= membershipCleanup();
			cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, null);
			String changed= source.substring(0, source.lastIndexOf('}')) + extra + "}";
			unit.getBuffer().setContents(changed);
			unit.save(null, true);
			assertThrows(CoreException.class, () -> cleanup.createFix(new CleanUpContext(unit, parse(unit))));
			var fresh= membershipCleanup();
			fresh.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, null);
			assertNull(fresh.createFix(new CleanUpContext(unit, parse(unit))));
			assertEquals(changed, unit.getSource());
		}
	}

	private ICompilationUnit createUnit(String source) throws CoreException {
		IPackageFragment fragment= context.getSourceFolder()
				.createPackageFragment("test", false, null); //$NON-NLS-1$
		return fragment.createCompilationUnit(
				"Sample.java", source, true, null); //$NON-NLS-1$
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
}

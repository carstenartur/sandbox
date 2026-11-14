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

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class ListenerSnapshotCleanUpTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	@Test
	void preservesListenerSnapshotsDuringReentrantAndConcurrentRegistration(@TempDir Path output) throws Exception {
		String source= listenerSource();
		ICompilationUnit unit= createUnit(source);
		assertFalse(Arrays.stream(parse(unit).getProblems()).anyMatch(problem -> problem.isError()));
		var cleanup= listenerCleanup();
		cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, null);
		var fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
		assertNotNull(fix);
		var undo= fix.createChange(null).perform(null);
		assertNotNull(undo);
		String transformed= unit.getSource();
		assertTrue(transformed.contains("new CopyOnWriteArrayList<>()")); //$NON-NLS-1$
		assertFalse(transformed.contains("toArray(")); //$NON-NLS-1$
		assertFalse(transformed.contains("synchronized (listeners)")); //$NON-NLS-1$
		assertFalse(Arrays.stream(parse(unit).getProblems()).anyMatch(problem -> problem.isError()));
		Path file= Files.writeString(output.resolve("Sample.java"), transformed, StandardCharsets.UTF_8); //$NON-NLS-1$
		assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
				"-d", output.toString(), file.toString())); //$NON-NLS-1$
		try (var classes= new URLClassLoader(new URL[] { output.toUri().toURL() }, null)) {
			Class<?> type= classes.loadClass("test.Sample"); //$NON-NLS-1$
			Object instance= type.getConstructor().newInstance();
			var register= type.getMethod("register", Runnable.class); //$NON-NLS-1$
			var fire= type.getMethod("fire"); //$NON-NLS-1$
			var sequence= new CopyOnWriteArrayList<Integer>();
			var started= new CountDownLatch(1);
			var release= new CountDownLatch(1);
			var once= new AtomicBoolean();
			Runnable first= () -> {
				sequence.add(1);
				if (once.compareAndSet(false, true)) {
					try {
						register.invoke(instance, (Runnable) () -> sequence.add(2));
						started.countDown();
						assertTrue(release.await(30, TimeUnit.SECONDS));
					} catch (Exception e) {
						throw new AssertionError(e);
					}
				}
			};
			register.invoke(instance, first);
			register.invoke(instance, first);
			try (var workers= Executors.newFixedThreadPool(2)) {
				var running= workers.submit(() -> fire.invoke(instance));
				try {
					assertTrue(started.await(30, TimeUnit.SECONDS));
					workers.submit(() -> register.invoke(instance, (Runnable) () -> sequence.add(3))).get(30, TimeUnit.SECONDS);
				} finally {
					release.countDown();
				}
				running.get(30, TimeUnit.SECONDS);
			}
			assertEquals(List.of(1, 1), sequence);
			fire.invoke(instance);
			assertEquals(List.of(1, 1, 1, 1, 2, 3), sequence);
			register.invoke(instance, new Object[] { null });
			var failed= assertThrows(InvocationTargetException.class, () -> fire.invoke(instance));
			assertTrue(failed.getCause() instanceof NullPointerException);
		}
		cleanup.checkPostConditions(null);
		undo.perform(null);
		assertEquals(source, unit.getSource());
	}

	@Test
	void refusesEscapesLockChangesAndObservableSnapshotUses() throws Exception {
		String source= listenerSource();
		ICompilationUnit unit= createUnit(source);
		for (String changed : List.of(
				source.replace("snapshot = listeners.toArray", "System.out.println(listeners); snapshot = listeners.toArray"),
				source.replace("synchronized (listeners) { snapshot", "synchronized (this) { snapshot"),
				source.replace("for (Runnable listener : snapshot)", "if (snapshot.length == 0) return; for (Runnable listener : snapshot)"),
				source.replace("listeners.add(listener);", "if (!listeners.contains(listener)) listeners.add(listener);"))) {
			unit.getBuffer().setContents(source);
			unit.save(null, true);
			var planned= listenerCleanup();
			planned.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, null);
			unit.getBuffer().setContents(changed);
			unit.save(null, true);
			assertThrows(CoreException.class, () -> planned.createFix(new CleanUpContext(unit, parse(unit))));
			var fresh= listenerCleanup();
			fresh.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, null);
			assertNull(fresh.createFix(new CleanUpContext(unit, parse(unit))));
			assertEquals(changed, unit.getSource());
		}
	}

	private static String listenerSource() {
		return """
			package test;
			import java.util.ArrayList;
			import java.util.List;
			public final class Sample {
				private final List<Runnable> listeners = new ArrayList<>();
				public void register(Runnable listener) {
					synchronized (listeners) { listeners.add(listener); }
				}
				public void fire() {
					Runnable[] snapshot;
					synchronized (listeners) { snapshot = listeners.toArray(new Runnable[0]); }
					for (Runnable listener : snapshot) { listener.run(); }
				}
			}
			""";
	}

	private static AbstractCleanUp listenerCleanup() throws CoreException {
		String id= "org.sandbox.jdt.ui.cleanup.container_contracts.listener_snapshot"; //$NON-NLS-1$
		var extensions= Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.jdt.ui.cleanUps"); //$NON-NLS-1$
		var entry= Arrays.stream(extensions).filter(e -> id.equals(e.getAttribute("id"))).findFirst(); //$NON-NLS-1$
		assertTrue(entry.isPresent(), "The opt-in listener snapshot migration must be registered"); //$NON-NLS-1$
		var cleanup= (AbstractCleanUp) entry.get().createExecutableExtension("class"); //$NON-NLS-1$
		var options= new CleanUpOptions();
		options.setOption(CLEANUP, CleanUpOptions.TRUE);
		options.setOption("cleanup.container_contracts.listener_snapshot", CleanUpOptions.TRUE); //$NON-NLS-1$
		cleanup.setOptions(options);
		return cleanup;
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

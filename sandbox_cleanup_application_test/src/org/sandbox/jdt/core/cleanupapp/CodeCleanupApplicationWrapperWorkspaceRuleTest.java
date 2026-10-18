/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.core.cleanupapp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The actual cleanup read phase excludes resource jobs which can close its CU. */
class CodeCleanupApplicationWrapperWorkspaceRuleTest {

	@TempDir
	Path temporary;
	private final NullProgressMonitor monitor= new NullProgressMonitor();
	private IProject project;
	private IFile file;
	private ICompilationUnit unit;
	private Display display;
	private boolean ownsDisplay;
	private Job contender;
	private final CountDownLatch closed= new CountDownLatch(1);

	@BeforeEach
	void createJavaProject() throws Exception {
		boolean hadCurrentDisplay= Display.getCurrent() != null;
		display= Display.getDefault();
		ownsDisplay= !hadCurrentDisplay && display.getThread() == Thread.currentThread();
		project= ResourcesPlugin.getWorkspace().getRoot().getProject("CleanupRule-" + System.nanoTime()); //$NON-NLS-1$
		project.create(monitor);
		project.open(monitor);
		var description= project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, monitor);
		IFolder source= project.getFolder("src"); //$NON-NLS-1$
		source.create(true, true, monitor);
		JavaCore.create(project).setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(source.getFullPath()) },
				project.getFullPath().append("bin"), monitor); //$NON-NLS-1$
		file= source.getFile("Sample.java"); //$NON-NLS-1$
		try (var input= new ByteArrayInputStream("public class Sample{int value(){return 1;}}\n".getBytes(StandardCharsets.UTF_8))) { //$NON-NLS-1$
			file.create(input, true, monitor);
		}
		unit= JavaCore.createCompilationUnitFrom(file);
	}

	@AfterEach
	void releaseResources() throws Exception {
		if (contender != null) {
			contender.cancel();
			assertTrue(contender.join(10_000, monitor));
		}
		if (project != null && project.exists()) project.delete(true, true, monitor);
		if (ownsDisplay && !display.isDisposed()) display.dispose();
	}

	@ParameterizedTest
	@ValueSource(strings = { "apply", "check", "diff" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	void cleanupKeepsItsBufferStableUntilTheDelegateReturns(String mode) throws Exception {
		byte[] before= Files.readAllBytes(file.getLocation().toFile().toPath());
		Path patch= temporary.resolve("cleanup.patch"); //$NON-NLS-1$
		String[] arguments= arguments(mode, patch);
		IApplicationContext context= context(arguments, () -> {
			var buffer= unit.getBuffer();
			contender= closingJob();
			contender.schedule();
			assertFalse(closed.await(200, TimeUnit.MILLISECONDS), "Resource job entered the cleanup read phase"); //$NON-NLS-1$
			assertFalse(buffer.isClosed(), "Resource job closed the cleanup buffer"); //$NON-NLS-1$
			var rule= Job.getJobManager().currentRule();
			assertTrue(rule != null && rule.contains(ResourcesPlugin.getWorkspace().getRoot()),
					"Cleanup must own the conflicting workspace rule on its executing thread"); //$NON-NLS-1$
			assertSame(display, Display.getCurrent());
		});

		Object result= onDisplay(() -> new CodeCleanupApplicationWrapper().start(context));

		assertEquals(Integer.valueOf("apply".equals(mode) ? CodeCleanupApplication.EXIT_OK //$NON-NLS-1$
				: CodeCleanupApplication.EXIT_CHANGES), result);
		assertTrue(closed.await(10, TimeUnit.SECONDS), "Resource job did not run after cleanup released its rule"); //$NON-NLS-1$
		assertTrue(contender.join(10_000, monitor));
		assertTrue(contender.getResult().isOK());
		byte[] after= Files.readAllBytes(file.getLocation().toFile().toPath());
		if ("apply".equals(mode)) { //$NON-NLS-1$
			assertNotEquals(new String(before, StandardCharsets.UTF_8), new String(after, StandardCharsets.UTF_8));
		} else {
			assertArrayEquals(before, after);
		}
		assertTrue(Files.size(patch) > 0);
	}

	@Test
	void delegateFailureIsPropagatedAndReleasesTheWorkspaceRule() throws Exception {
		IllegalStateException expected= new IllegalStateException("delegate-context-failure"); //$NON-NLS-1$
		IApplicationContext context= context(arguments("apply", temporary.resolve("failure.patch")), () -> { //$NON-NLS-1$ //$NON-NLS-2$
			contender= closingJob();
			contender.schedule();
			throw expected;
		});

		IllegalStateException actual= assertThrows(IllegalStateException.class,
				() -> onDisplay(() -> new CodeCleanupApplicationWrapper().start(context)));

		assertSame(expected, actual);
		assertTrue(closed.await(10, TimeUnit.SECONDS));
	}

	@Test
	void waitingForTheWorkspaceRuleStillServicesTheOwningDisplay() throws Exception {
		CountDownLatch ownsRule= new CountDownLatch(1);
		CountDownLatch uiResponse= new CountDownLatch(1);
		AtomicBoolean serviced= new AtomicBoolean();
		contender= new WorkspaceJob("cleanup-rule-owner-needs-ui") { //$NON-NLS-1$
			@Override
			public IStatus runInWorkspace(IProgressMonitor progress) {
				ownsRule.countDown();
				try {
					serviced.set(uiResponse.await(10, TimeUnit.SECONDS));
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				}
				return Status.OK_STATUS;
			}
		};
		contender.setRule(ResourcesPlugin.getWorkspace().getRoot());
		contender.schedule();
		assertTrue(ownsRule.await(10, TimeUnit.SECONDS));
		IApplicationContext context= context(arguments("apply", temporary.resolve("ui.patch")), () -> { }); //$NON-NLS-1$ //$NON-NLS-2$

		Object result= onDisplay(() -> {
			display.timerExec(0, uiResponse::countDown);
			return new CodeCleanupApplicationWrapper().start(context);
		});

		assertEquals(Integer.valueOf(CodeCleanupApplication.EXIT_OK), result);
		assertTrue(serviced.get(), "Rule acquisition blocked the SWT event needed by its existing owner"); //$NON-NLS-1$
	}

	private WorkspaceJob closingJob() {
		WorkspaceJob job= new WorkspaceJob("cleanup-rule-closes-primary-cu") { //$NON-NLS-1$
			@Override
			public IStatus runInWorkspace(IProgressMonitor progress) throws org.eclipse.core.runtime.CoreException {
				unit.close();
				closed.countDown();
				return Status.OK_STATUS;
			}
		};
		job.setRule(ResourcesPlugin.getWorkspace().getRoot());
		return job;
	}

	private String[] arguments(String mode, Path patch) throws Exception {
		Path config= Files.writeString(temporary.resolve("cleanup.properties"), //$NON-NLS-1$
				"cleanup.format_source_code=true\n", StandardCharsets.ISO_8859_1); //$NON-NLS-1$
		return new String[] { "--import-project", project.getLocation().toOSString(), "-config", config.toString(), //$NON-NLS-1$ //$NON-NLS-2$
				"--mode", mode, "--patch", patch.toString(), file.getLocation().toOSString() }; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static IApplicationContext context(String[] arguments, CheckedRunnable beforeCleanup) {
		AtomicInteger reads= new AtomicInteger();
		return (IApplicationContext) Proxy.newProxyInstance(IApplicationContext.class.getClassLoader(),
				new Class<?>[] { IApplicationContext.class }, (proxy, method, parameters) -> {
					if ("getArguments".equals(method.getName())) { //$NON-NLS-1$
						if (reads.incrementAndGet() == 2) beforeCleanup.run();
						return Map.of(IApplicationContext.APPLICATION_ARGS, arguments.clone());
					}
					return null;
				});
	}

	private <T> T onDisplay(CheckedSupplier<T> action) throws Exception {
		AtomicReference<T> result= new AtomicReference<>();
		AtomicReference<Throwable> failure= new AtomicReference<>();
		display.syncExec(() -> {
			try { result.set(action.get()); } catch (Throwable thrown) { failure.set(thrown); }
		});
		if (failure.get() instanceof Error error) throw error;
		if (failure.get() instanceof Exception exception) throw exception;
		return result.get();
	}

	@FunctionalInterface
	private interface CheckedRunnable { void run() throws Exception; }
	@FunctionalInterface
	private interface CheckedSupplier<T> { T get() throws Exception; }
}

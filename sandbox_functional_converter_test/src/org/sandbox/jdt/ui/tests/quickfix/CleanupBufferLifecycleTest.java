/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.internal.filebuffers.AbstractFileBuffer;
import org.eclipse.core.internal.filebuffers.FileBuffersPlugin;
import org.eclipse.core.internal.filebuffers.ResourceTextFileBuffer;
import org.eclipse.core.internal.filebuffers.ResourceTextFileBufferManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.text.edits.ReplaceEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;

/** Reproduces background buffer publication using the real JDT/LTK lifecycle. */
class CleanupBufferLifecycleTest {
    private static final String ORIGINAL = "package test1; class Example { int value = 1; }";
    private static final String CHANGED = "package test1; class Example { int value = 2; }";
    private static final String NEXT = "package test1; class Example { int value = 3; }";

    @RegisterExtension final CleanupContext context = new CleanupContext();

    @Test void delayedBufferCannotReplaceSourceAfterCleanup() throws Exception {
        ICompilationUnit unit = context.createUnit(ORIGINAL);
        try (DelayedPublication publication = new DelayedPublication(unit.getPath())) {
            publication.start();
            context.cleanup(unit, CHANGED);
            assertEquals(CHANGED, fileSource(unit));
            publication.finish();
            // Opening an IDE working copy must use the same saved content, even
            // when a background opener began before the cleanup's working copy.
            unit.close();
            unit.becomeWorkingCopy(new NullProgressMonitor());
            try {
                assertEquals(CHANGED, unit.getSource());
            } finally {
                unit.discardWorkingCopy();
            }
        }
    }

    @Test void delayedBufferFromPreviousFixtureCannotReplaceNextFixtureSource() throws Exception {
        ICompilationUnit previous = context.createUnit(ORIGINAL);
        try (DelayedPublication publication = new DelayedPublication(previous.getPath())) {
            publication.start();
            context.cleanup(previous, CHANGED);
            assertEquals(CHANGED, fileSource(previous));
            context.afterEach(null);
            context.beforeEach(null);
            ICompilationUnit next = context.createUnit(NEXT);
            assertEquals(NEXT, fileSource(next));
            publication.finish();
            next.close();
            next.becomeWorkingCopy(new NullProgressMonitor());
            try {
                assertEquals(NEXT, next.getSource());
            } finally {
                next.discardWorkingCopy();
            }
        }
    }

    @Test void teardownReleasesOnlyFixtureLeaseAfterRepeatedCleanups() throws Exception {
        ICompilationUnit unit = context.createUnit(ORIGINAL);
        var manager = FileBuffers.getTextFileBufferManager();
        var monitor = new NullProgressMonitor();
        manager.connect(unit.getPath(), LocationKind.IFILE, monitor);
        var external = manager.getTextFileBuffer(unit.getPath(), LocationKind.IFILE);
        try {
            context.cleanup(unit, CHANGED);
            context.cleanup(unit, NEXT);
            assertEquals(NEXT, external.getDocument().get());
            context.afterEach(null);
            assertSame(external, manager.getTextFileBuffer(unit.getPath(), LocationKind.IFILE));
            assertFalse(external.isShared(), "All fixture and temporary cleanup connections were released");
        } finally {
            manager.disconnect(unit.getPath(), LocationKind.IFILE, monitor);
        }
        assertNull(manager.getTextFileBuffer(unit.getPath(), LocationKind.IFILE));
        context.beforeEach(null);
    }

    @Test void failedSetupDeletesItsPartiallyCreatedProject() throws Exception {
        AtomicReference<IProject> created = new AtomicReference<>();
        var failedSetup = new AbstractEclipseJava("missing-stubs.jar", JavaCore.VERSION_22) {
            @Override public IPath[] findRtJar(IPath path) throws CoreException {
                created.set(getJavaProject().getProject());
                return super.findRtJar(path);
            }
        };
        try {
            assertThrows(AssertionError.class, () -> failedSetup.beforeEach(null));
            assertNotNull(created.get());
            assertFalse(created.get().exists(), "A failed setup must release its project");
            failedSetup.afterEach(null); // Teardown after failed setup is also safe.
        } finally {
            if (created.get() != null && created.get().exists()) created.get().delete(true, null);
        }
    }

    @Test void teardownDeletesOwnedProjectAndPreservesBorrowedProject() throws Exception {
        IProject owned = context.getJavaProject().getProject();
        var borrowed = AbstractEclipseJava.createJavaProject("Borrowed_" + UUID.randomUUID(), "bin");
        try {
            context.setJavaProject(borrowed);
            context.afterEach(null);
            assertFalse(owned.exists(), "The fixture's original project was removed");
            assertTrue(borrowed.getProject().exists(), "The replacement project belongs to the caller");
        } finally {
            if (owned.exists()) owned.delete(true, null);
            if (borrowed.getProject().exists()) borrowed.getProject().delete(true, null);
            context.beforeEach(null);
        }
    }

    private static String fileSource(ICompilationUnit unit) throws Exception {
        try (var input = ((IFile) unit.getResource()).getContents()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static final class CleanupContext extends AbstractEclipseJava {
        CleanupContext() { super("testresources/rtstubs_22.jar", JavaCore.VERSION_22); }

        ICompilationUnit createUnit(String source) throws CoreException {
            return getSourceFolder().createPackageFragment("test1", false, null)
                    .createCompilationUnit("Example.java", source, true, null);
        }

        void cleanup(ICompilationUnit unit, String expected) throws CoreException {
            ICleanUp replacement = new AbstractCleanUp() {
                @Override public ICleanUpFix createFix(CleanUpContext input) {
                    return monitor -> {
                        var change = new CompilationUnitChange("replace", input.getCompilationUnit());
                        change.setEdit(new ReplaceEdit(0, expected.length(), expected));
                        return change;
                    };
                }
            };
            performRefactoring(new CleanUpRefactoring(), new ICompilationUnit[] { unit },
                    new ICleanUp[] { replacement }, null);
        }
    }

    /** Adds no editor: only pauses an ordinary background connection after load. */
    static final class DelayedPublication implements AutoCloseable {
        final Object originalManager = FileBuffers.getTextFileBufferManager();
        final Field globalManager;
        final DelayedManager manager = new DelayedManager();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final IPath path;
        final Thread reader;

        DelayedPublication(IPath path) throws Exception {
            this.path = path;
            globalManager = FileBuffersPlugin.class.getDeclaredField("fTextFileBufferManager");
            globalManager.setAccessible(true);
            globalManager.set(null, manager);
            reader = new Thread(() -> {
                try { manager.connect(path, LocationKind.IFILE, new NullProgressMonitor()); }
                catch (Throwable thrown) { failure.set(thrown); }
            }, "delayed-file-buffer-reader");
            reader.setDaemon(true);
            manager.reader = reader;
        }

        void start() throws InterruptedException {
            reader.start();
            assertTrue(manager.loaded.await(10, TimeUnit.SECONDS), "Reader loaded original snapshot");
        }

        void finish() throws InterruptedException {
            manager.publish.countDown();
            reader.join(10000);
            assertFalse(reader.isAlive(), "Reader finished");
            assertNull(failure.get());
        }

        @Override public void close() throws Exception {
            boolean interrupted = false;
            try {
                manager.publish.countDown();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (reader.isAlive()) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) break;
                    try {
                        TimeUnit.NANOSECONDS.timedJoin(reader, remaining);
                    } catch (InterruptedException interruption) {
                        interrupted = true;
                    }
                }
                assertFalse(reader.isAlive(), "The reader terminated before restoring the global manager");
                manager.disconnect(path, LocationKind.IFILE, new NullProgressMonitor());
            } finally {
                globalManager.set(null, originalManager);
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }

    static final class DelayedManager extends ResourceTextFileBufferManager {
        final CountDownLatch loaded = new CountDownLatch(1);
        final CountDownLatch publish = new CountDownLatch(1);
        Thread reader;

        @Override protected AbstractFileBuffer createTextFileBuffer(IPath location, LocationKind kind) {
            return new ResourceTextFileBuffer(this) {
                @Override public void create(IPath path, IProgressMonitor monitor) throws CoreException {
                    super.create(path, monitor);
                    if (Thread.currentThread() == reader) {
                        loaded.countDown();
                        try {
                            if (!publish.await(10, TimeUnit.SECONDS)) throw new AssertionError("Publication gate timed out");
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(interrupted);
                        }
                    }
                }
            };
        }
    }
}

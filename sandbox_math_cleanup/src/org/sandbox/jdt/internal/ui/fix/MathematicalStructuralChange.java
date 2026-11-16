/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.fix;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.NullChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.TextEdit;
import org.sandbox.jdt.internal.corext.fix.math.JavaStructuralRewritePlan;

/** Native preview and application of one already verified declaration/caller edit group. */
public final class MathematicalStructuralChange extends CompositeChange {
    private final JavaStructuralRewritePlan plan;
    private final Map<String, ICompilationUnit> units;
    private final IJavaProject project;
    private final MathematicalEnvironment.Snapshot environment;
    private final Map<CompilationUnitChange, String> preview = new HashMap<>();

    /** Recheck a verified source plan in the real workspace and expose its edits as one preview. */
    public static MathematicalStructuralChange preview(String name, Map<String, ICompilationUnit> units,
            JavaStructuralRewritePlan plan,
            IProgressMonitor monitor) throws CoreException {
        var scope = Map.copyOf(units);
        if (scope.isEmpty()) throw new IllegalArgumentException("Structural workspace scope is empty"); //$NON-NLS-1$
        return new MathematicalStructuralChange(name, plan, scope, monitor);
    }

    private MathematicalStructuralChange(String name, JavaStructuralRewritePlan plan,
            Map<String, ICompilationUnit> units, IProgressMonitor monitor) throws CoreException {
        super(name);
        this.plan = plan;
        this.units = units;
        project = units.values().iterator().next().getJavaProject();
        environment = MathematicalEnvironment.capture(project, monitor);
        var verified = plan.rebindWorkspace(units, monitor);
        for (var edit : verified.newEdits(currentSources()).entrySet()) {
            var change = change(edit.getKey(), edit.getValue());
            preview.put(change, edit.getValue().toString());
            add(change);
        }
        requireCurrentEnvironment(monitor);
    }

    @Override
    public RefactoringStatus isValid(IProgressMonitor monitor) throws CoreException {
        var status = super.isValid(monitor);
        try {
            validateGroup();
            requireCurrentEnvironment(monitor);
        } catch (IllegalArgumentException invalid) {
            status.addFatalError(invalid.getMessage());
        }
        return status;
    }

    @Override
    public Change perform(IProgressMonitor monitor) throws CoreException {
        var undo = new AtomicReference<Change>();
        ResourcesPlugin.getWorkspace().run(work -> {
            if (isValid(work).hasFatalError()) throw new CoreException(Status.error("Structural change is stale or incomplete")); //$NON-NLS-1$
            // Re-resolve the actual project and execute freshly generated edits. The preview model is mutable.
            var verified = plan.rebindWorkspace(units, work);
            var before = currentSources();
            var after = verified.apply(before);
            var steps = verified.newEdits(before).entrySet().stream().map(edit -> {
                var path = edit.getKey();
                return new NativeStep(change(path, edit.getValue()), units.get(path), before.get(path), after.get(path));
            }).toArray(Change[]::new);
            var executable = new NativeGroup(getName(), project.getProject(), steps, plan, units, false);
            requireCurrentEnvironment(work);
            try {
                undo.set(executable.execute(work, false));
            } finally {
                executable.dispose();
            }
        }, project.getProject(), IWorkspace.AVOID_UPDATE, monitor);
        return undo.get();
    }

    private static final class NativeGroup extends CompositeChange {
        private final IProject project;
        private final List<Change> children;
        private final JavaStructuralRewritePlan plan;
        private final Map<String, ICompilationUnit> units;
        private final boolean undoing;

        NativeGroup(String name, IProject project, Change[] children, JavaStructuralRewritePlan plan,
                Map<String, ICompilationUnit> units, boolean undoing) {
            super(name, children);
            this.project = project;
            this.children = List.of(children);
            this.plan = plan;
            this.units = units;
            this.undoing = undoing;
        }

        @Override protected Change createUndoChange(Change[] changes) {
            return new NativeGroup(getName(), project, changes, plan, units, !undoing);
        }

        @Override public RefactoringStatus isValid(IProgressMonitor monitor) throws CoreException {
            if (!isEnabled() || !children.equals(List.of(getChildren())) || children.stream().anyMatch(child -> !child.isEnabled()))
                return RefactoringStatus.createFatalErrorStatus("The entire structural change group is required"); //$NON-NLS-1$
            return super.isValid(monitor);
        }

        @Override public Change perform(IProgressMonitor monitor) throws CoreException {
            var inverse = new AtomicReference<Change>();
            project.getWorkspace().run(work -> inverse.set(execute(work, true)), project, IWorkspace.AVOID_UPDATE, monitor);
            return inverse.get();
        }

        private Change execute(IProgressMonitor monitor, boolean verifyScope) throws CoreException {
            if (isValid(monitor).hasFatalError())
                throw new CoreException(Status.error("Structural change failed workspace validation")); //$NON-NLS-1$
            if (verifyScope) {
                try {
                    if (undoing) plan.verifyWorkspaceUndo(units, monitor);
                    else plan.rebindWorkspace(units, monitor);
                } catch (IllegalArgumentException stale) {
                    throw new CoreException(Status.error(stale.getMessage(), stale));
                }
            }
            try {
                return super.perform(monitor);
            } catch (CoreException | RuntimeException failure) {
                if (getUndoUntilException() instanceof CompositeChange partial) {
                    try {
                        var rollback = MathematicalRollback.attempt(List.of(partial.getChildren()).reversed());
                        if (!rollback.complete())
                            throw new CoreException(Status.error("Structural rollback was incomplete " + rollback.failures(), failure)); //$NON-NLS-1$
                    } finally {
                        partial.dispose();
                    }
                }
                throw failure;
            }
        }
    }

    private static final class NativeStep extends Change {
        private final Change delegate;
        private final ICompilationUnit unit;
        private final String before;
        private final String after;
        private boolean initialized;

        NativeStep(Change delegate, ICompilationUnit unit, String before, String after) {
            this.delegate = delegate;
            this.unit = unit;
            this.before = before;
            this.after = after;
        }

        @Override public String getName() { return delegate.getName(); }
        @Override public Object getModifiedElement() { return delegate.getModifiedElement(); }
        @Override public Object[] getAffectedObjects() { return delegate.getAffectedObjects(); }
        @Override public void dispose() { delegate.dispose(); }

        @Override public void initializeValidationData(IProgressMonitor monitor) {
            if (!initialized) {
                delegate.initializeValidationData(monitor);
                initialized = true;
            }
        }

        @Override public RefactoringStatus isValid(IProgressMonitor monitor) throws CoreException {
            var buffer = FileBuffers.getTextFileBufferManager().getTextFileBuffer(unit.getPath(), LocationKind.IFILE);
            if (unit.hasUnsavedChanges() || !before.equals(unit.getSource()) || buffer != null && !matches(buffer, before))
                return RefactoringStatus.createFatalErrorStatus("Structural source has changed or is unsaved: " + unit.getPath()); //$NON-NLS-1$
            initializeValidationData(monitor);
            return delegate.isValid(monitor);
        }

        @Override public Change perform(IProgressMonitor monitor) throws CoreException {
            var manager = FileBuffers.getTextFileBufferManager();
            var path = unit.getPath();
            manager.connect(path, LocationKind.IFILE, monitor);
            try {
                var buffer = manager.getTextFileBuffer(path, LocationKind.IFILE);
                if (!matches(buffer, before) || unit.hasUnsavedChanges())
                    throw new CoreException(Status.error("Structural source has changed or is unsaved: " + path)); //$NON-NLS-1$
                long stamp = unit.getResource().getModificationStamp();
                try {
                    var inverse = delegate.perform(monitor);
                    if (inverse != null && !(inverse instanceof NullChange) && matches(buffer, after))
                        return new NativeStep(inverse, unit, after, before);
                    if (inverse != null) inverse.dispose();
                    throw new CoreException(Status.error("Structural edit did not save the verified source: " + path)); //$NON-NLS-1$
                } catch (CoreException | RuntimeException failure) {
                    try {
                        restore(buffer, stamp);
                    } catch (CoreException | RuntimeException rollback) {
                        var incomplete = new CoreException(Status.error("Structural rollback was incomplete for " + path, failure)); //$NON-NLS-1$
                        incomplete.addSuppressed(rollback);
                        throw incomplete;
                    }
                    throw failure;
                }
            } finally {
                manager.disconnect(path, LocationKind.IFILE, null);
            }
        }

        private void restore(ITextFileBuffer buffer, long stamp) throws CoreException {
            // A save or release can fail after LTK has edited the live document but before it returns an inverse.
            var repair = new CompletableFuture<Boolean>();
            Runnable restoreDocument = () -> {
                try {
                    var document = buffer.getDocument();
                    var file = unit.getResource();
                    if (!matches(buffer, before)) {
                        String current = document.get();
                        if (!(after.equals(current) || before.equals(current)) || !file.isSynchronized(IResource.DEPTH_ZERO))
                            throw new IllegalStateException("The source changed during structural rollback"); //$NON-NLS-1$
                        if (!buffer.isSynchronized() || stamp == file.getModificationStamp()) buffer.revert(null);
                    }
                    boolean needsSave = !matches(buffer, before);
                    if (needsSave) {
                        if (!after.equals(document.get()) || !buffer.isSynchronized())
                            throw new IllegalStateException("The saved source changed during structural rollback"); //$NON-NLS-1$
                        document.set(before);
                    }
                    repair.complete(needsSave);
                } catch (CoreException | RuntimeException recoveryFailure) {
                    repair.completeExceptionally(recoveryFailure);
                }
            };
            if (buffer.isSynchronizationContextRequested()) FileBuffers.getTextFileBufferManager().execute(restoreDocument);
            else restoreDocument.run();
            if (repair.join() && before.equals(buffer.getDocument().get())) buffer.commit(null, false);
            if (!matches(buffer, before)) throw new CoreException(Status.error("The original structural source was not restored")); //$NON-NLS-1$
        }

        private static boolean matches(ITextFileBuffer buffer, String text) {
            return !buffer.isDirty() && buffer.isSynchronized() && text.equals(buffer.getDocument().get());
        }
    }

    private void validateGroup() throws CoreException {
        var children = getChildren();
        if (!isEnabled() || !preview.keySet().equals(Set.of(children)))
            throw new IllegalArgumentException("Apply requires the entire verified declaration and caller change"); //$NON-NLS-1$
        for (var child : children) {
            if (!(child instanceof CompilationUnitChange unit) || !unit.isEnabled()
                    || unit.getEdit() == null || !unit.getEdit().toString().equals(preview.get(unit)))
                throw new IllegalArgumentException("A preview edit differs from the verified structural change"); //$NON-NLS-1$
        }
        plan.newEdits(currentSources());
    }

    private void requireCurrentEnvironment(IProgressMonitor monitor) {
        if (!environment.matches(project, monitor))
            throw new IllegalArgumentException("The Java binding environment has changed since structural preview"); //$NON-NLS-1$
    }

    private CompilationUnitChange change(String path, TextEdit edit) {
        var unit = units.get(path);
        var change = new CompilationUnitChange(unit.getElementName(), unit);
        change.setEdit(edit);
        return change;
    }

    private Map<String, String> currentSources() throws CoreException {
        var snapshot = new HashMap<String, String>();
        for (var entry : units.entrySet()) {
            var unit = entry.getValue();
            if (!unit.exists() || unit.hasUnsavedChanges())
                throw new CoreException(Status.error("A pinned source unit is missing or has unsaved changes " + entry.getKey())); //$NON-NLS-1$
            snapshot.put(entry.getKey(), unit.getSource());
        }
        return Collections.unmodifiableMap(snapshot);
    }
}

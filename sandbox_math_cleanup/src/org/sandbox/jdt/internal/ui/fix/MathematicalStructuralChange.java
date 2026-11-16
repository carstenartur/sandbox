/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.fix;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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
            var executable = new CompositeChange(getName());
            verified.newEdits(currentSources()).forEach((path, edit) -> executable.add(change(path, edit)));
            executable.initializeValidationData(work);
            if (executable.isValid(work).hasFatalError())
                throw new CoreException(Status.error("Verified structural edits failed workspace validation")); //$NON-NLS-1$
            requireCurrentEnvironment(work);
            try {
                undo.set(executable.perform(work));
            } catch (CoreException | RuntimeException failed) {
                var partialUndo = executable.getUndoUntilException();
                if (partialUndo != null) {
                    var rollback = MathematicalRollback.attempt(List.of(partialUndo));
                    if (!rollback.complete())
                        throw new CoreException(Status.error("Structural apply failed and rollback was incomplete " + rollback.failures(), failed)); //$NON-NLS-1$
                }
                throw failed;
            } finally {
                executable.dispose();
            }
        }, project.getProject(), IWorkspace.AVOID_UPDATE, monitor);
        return undo.get();
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

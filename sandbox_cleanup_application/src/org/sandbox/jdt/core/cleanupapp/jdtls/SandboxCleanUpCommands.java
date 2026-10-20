/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.core.cleanupapp.jdtls;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JSONUtility;
import org.eclipse.jdt.ls.core.internal.TextEditConverter;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.sandbox.jdt.cleanup.multifile.api.IMultiFileCleanUpScopeProvider;

/**
 * Optional JDT LS entry point. Reuses ICleanUp and JDT LS's edit conversion;
 * never invokes Change.perform(), a workbench, or a save action.
 */
public final class SandboxCleanUpCommands implements IDelegateCommandHandler {
    public static final String LIST = "sandbox.cleanup.list"; //$NON-NLS-1$
    public static final String PREVIEW = "sandbox.cleanup.preview"; //$NON-NLS-1$
    // Some existing cleanup helpers keep per-run state. Do not interleave runs.
    private static final Object PREVIEW_LOCK = new Object();

    public record Snapshot(String uri, Integer version, String source) {
        public Snapshot {
            if (uri == null || !"file".equalsIgnoreCase(URI.create(uri).getScheme()) //$NON-NLS-1$
                    || version == null || source == null) {
                throw new IllegalArgumentException("Each document needs a file URI, editor version, and current source."); //$NON-NLS-1$
            }
        }
    }

    public record Request(String cleanupId, Map<String, String> options, List<Snapshot> documents) {
        public Request {
            if (cleanupId == null || cleanupId.isBlank() || options == null || documents == null || documents.isEmpty()) {
                throw new IllegalArgumentException("Provide one cleanupId, its options, and at least one document snapshot."); //$NON-NLS-1$
            }
            options = Map.copyOf(options);
            documents = List.copyOf(documents);
        }
    }

    public record Preview(WorkspaceEdit edit, List<String> requiredUris, List<String> messages) {
        public Preview {
            requiredUris = List.copyOf(requiredUris);
            messages = List.copyOf(messages);
        }
    }

    @Override
    public Object executeCommand(String commandId, List<Object> arguments, IProgressMonitor monitor) throws Exception {
        IProgressMonitor progress = monitor == null ? new NullProgressMonitor() : monitor;
        cancelled(progress);
        var catalog = new SandboxCleanUpCatalog();
        if (LIST.equals(commandId)) return catalog.list();
        if (!PREVIEW.equals(commandId)) throw new IllegalArgumentException("Unknown Sandbox command: " + commandId); //$NON-NLS-1$
        if (arguments == null || arguments.size() != 1 || arguments.getFirst() == null) {
            throw new IllegalArgumentException("sandbox.cleanup.preview expects exactly one request object."); //$NON-NLS-1$
        }
        Request request = JSONUtility.toModel(arguments.getFirst(), Request.class);
        Objects.requireNonNull(request, "request"); //$NON-NLS-1$
        synchronized (PREVIEW_LOCK) {
            cancelled(progress);
            return preview(request, catalog.create(request.cleanupId(), request.options()), progress);
        }
    }

    // Package-private for lifecycle regressions; production only accepts catalogued cleanups.
    Preview preview(Request request, ICleanUp cleanup, IProgressMonitor monitor) throws CoreException {
        cancelled(monitor);
        var snapshots = new LinkedHashMap<ICompilationUnit, Snapshot>();
        IJavaProject project = null;
        for (var snapshot : request.documents()) {
            ICompilationUnit unit = JDTUtils.resolveCompilationUnit(snapshot.uri());
            if (unit == null || !unit.exists() || unit.isReadOnly() || unit.getResource() == null) {
                throw new IllegalArgumentException("Not an editable Java source in this server workspace: " + snapshot.uri()); //$NON-NLS-1$
            }
            if (project == null) project = unit.getJavaProject();
            if (!project.equals(unit.getJavaProject())) {
                throw new IllegalArgumentException("One preview must belong to one Java project; no partial cross-project migration."); //$NON-NLS-1$
            }
            if (snapshots.putIfAbsent(unit, snapshot) != null) {
                throw new IllegalArgumentException("Duplicate compilation unit: " + snapshot.uri()); //$NON-NLS-1$
            }
            unchanged(unit, snapshot);
        }

        // Preserve the existing multi-file scope capability before planning any changes.
        var scope = new LinkedHashSet<>(snapshots.keySet());
        if (cleanup instanceof IMultiFileCleanUpScopeProvider provider) {
            boolean expanded;
            do {
                cancelled(monitor);
                expanded = false;
                for (var unit : provider.expandCleanUpScope(project, List.copyOf(scope), monitor)) {
                    if (unit == null || !unit.exists() || unit.isReadOnly() || unit.getResource() == null
                            || !project.equals(unit.getJavaProject())) {
                        throw new IllegalArgumentException("Cleanup requires unavailable, read-only, or cross-project source."); //$NON-NLS-1$
                    }
                    expanded |= scope.add(unit);
                }
            } while (expanded);
        }
        var missing = new ArrayList<String>();
        for (var unit : scope) {
            if (!snapshots.containsKey(unit)) missing.add(unit.getResource().getLocationURI().toASCIIString());
        }
        if (!missing.isEmpty()) {
            return result(List.of(), missing, List.of("Open the related documents and retry with their versions and source; nothing was changed.")); //$NON-NLS-1$
        }

        var edits = new ArrayList<Either<TextDocumentEdit, ResourceOperation>>();
        var status = new RefactoringStatus();
        Throwable failure = null;
        try {
            status.merge(cleanup.checkPreConditions(project, scope.toArray(ICompilationUnit[]::new), monitor));
            requireValid(status);
            var requirements = cleanup.getRequirements();
            if (requirements.requiresChangedRegions()) {
                throw new IllegalArgumentException("This cleanup requires changed-region information, not a whole-file preview."); //$NON-NLS-1$
            }
            var compilerOptions = new LinkedHashMap<>(project.getOptions(true));
            if (requirements.getCompilerOptions() != null) compilerOptions.putAll(requirements.getCompilerOptions());
            for (var unit : scope) {
                cancelled(monitor);
                Snapshot snapshot = snapshots.get(unit);
                unchanged(unit, snapshot);
                CompilationUnit ast = null;
                if (requirements.requiresAST()) {
                    var parser = ASTParser.newParser(AST.getJLSLatest());
                    parser.setSource(unit);
                    parser.setResolveBindings(true);
                    parser.setCompilerOptions(compilerOptions);
                    ast = (CompilationUnit) parser.createAST(monitor);
                }
                var fix = cleanup.createFix(new CleanUpContext(unit, ast));
                if (fix == null) continue;
                var change = fix.createChange(monitor);
                if (change == null) continue;
                try {
                    if (!unit.equals(change.getCompilationUnit())) {
                        throw new IllegalArgumentException("Cleanup returned a change outside its compilation unit."); //$NON-NLS-1$
                    }
                    change.initializeValidationData(monitor);
                    status.merge(change.isValid(monitor));
                    requireValid(status);
                    if (change.getEdit() == null) continue;
                    var edit = new TextEditConverter(unit, change.getEdit().copy()).convertToTextDocumentEdit(snapshot.version());
                    edit.getTextDocument().setUri(snapshot.uri());
                    if (!edit.getEdits().isEmpty()) edits.add(Either.forLeft(edit));
                } finally {
                    change.dispose();
                }
            }
        } catch (CoreException | RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            // Release plans even when the client cancels or a fix fails.
            try {
                status.merge(cleanup.checkPostConditions(new NullProgressMonitor()));
            } catch (CoreException | RuntimeException e) {
                if (failure != null) failure.addSuppressed(e);
                else throw e;
            }
        }
        requireValid(status);
        cancelled(monitor);
        for (var entry : snapshots.entrySet()) unchanged(entry.getKey(), entry.getValue());
        var messages = new ArrayList<String>();
        for (var entry : status.getEntries()) messages.add(entry.getMessage());
        if (edits.isEmpty()) messages.add("No changes proposed; the existing cleanup options and semantic guards still apply."); //$NON-NLS-1$
        return result(edits, List.of(), messages);
    }

    private static Preview result(List<Either<TextDocumentEdit, ResourceOperation>> edits,
            List<String> requiredUris, List<String> messages) {
        var edit = new WorkspaceEdit();
        edit.setDocumentChanges(edits);
        return new Preview(edit, requiredUris, messages);
    }

    private static void unchanged(ICompilationUnit unit, Snapshot snapshot) throws CoreException {
        if (!snapshot.source().equals(unit.getSource())) {
            throw new IllegalArgumentException("Document changed or is not synchronized with JDT LS; request a fresh preview: " + snapshot.uri()); //$NON-NLS-1$
        }
    }

    private static void requireValid(RefactoringStatus status) {
        if (status.hasError()) throw new IllegalArgumentException(status.getMessageMatchingSeverity(status.getSeverity()));
    }

    private static void cancelled(IProgressMonitor monitor) {
        if (monitor.isCanceled()) throw new OperationCanceledException();
    }
}

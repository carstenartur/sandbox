package org.sandbox.jdt.core.cleanupapp;

/*-
 * #%L
 * Sandbox cleanup application
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jface.operation.ModalContext;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

public class CodeCleanupApplicationWrapper implements IApplication {

	private static final String ARG_IMPORT_PROJECT= "--import-project"; //$NON-NLS-1$

	private record ImportRequest(File projectDirectory, String[] applicationArguments) {
		ImportRequest {
			applicationArguments= applicationArguments.clone();
		}

		@Override
		public String[] applicationArguments() {
			return applicationArguments.clone();
		}
	}

	private static final class ForwardingApplicationContext implements IApplicationContext {
		private final IApplicationContext delegate;
		private final String[] applicationArguments;
		private final IApplication owner;

		ForwardingApplicationContext(IApplicationContext delegate, String[] applicationArguments, IApplication owner) {
			this.delegate= delegate;
			this.applicationArguments= applicationArguments.clone();
			this.owner= owner;
		}

		@Override
		@SuppressWarnings({ "rawtypes", "unchecked" })
		public Map getArguments() {
			Map forwarded= new HashMap(delegate.getArguments());
			forwarded.put(APPLICATION_ARGS, applicationArguments.clone());
			return forwarded;
		}

		@Override
		public void applicationRunning() {
			delegate.applicationRunning();
		}

		@Override
		public String getBrandingApplication() {
			return delegate.getBrandingApplication();
		}

		@Override
		public Bundle getBrandingBundle() {
			return delegate.getBrandingBundle();
		}

		@Override
		public String getBrandingDescription() {
			return delegate.getBrandingDescription();
		}

		@Override
		public String getBrandingId() {
			return delegate.getBrandingId();
		}

		@Override
		public String getBrandingName() {
			return delegate.getBrandingName();
		}

		@Override
		public String getBrandingProperty(String key) {
			return delegate.getBrandingProperty(key);
		}

		@Override
		public void setResult(Object result, IApplication application) {
			delegate.setResult(result, owner);
		}
	}

	/**
	 * Deals with the messages in the properties file (cut n' pasted from a generated class).
	 */
	private static final class Messages extends NLS {
		private static final String BUNDLE_NAME= "org.sandbox.jdt.core.cleanupapp.messages"; //$NON-NLS-1$

		public static String CommandLineUsage;
		public static String WorkspaceRequired;

		static {
			NLS.initializeMessages(BUNDLE_NAME, Messages.class);
		}

		public static String bind(final String message) {
			return bind(message, null);
		}

		public static String bind(final String message, final Object binding) {
			return bind(message, new Object[] { binding });
		}

		public static String bind(final String message, final Object binding1, final Object binding2) {
			return bind(message, new Object[] { binding1, binding2 });
		}

		public static String bind(final String message, final Object[] bindings) {
			return MessageFormat.format(message, bindings);
		}
	}

	private final CodeCleanupApplication delegate= new CodeCleanupApplication();

	/** Transfers only a completed acquisition, never one whose UI caller has left. */
	private static final class WorkspaceRuleHandoff implements AutoCloseable {
		private final IJobManager manager;
		private final ISchedulingRule rule;
		private final Thread uiThread;
		private final NullProgressMonitor monitor= new NullProgressMonitor();
		private boolean abandoned;
		private boolean owned;

		WorkspaceRuleHandoff(IJobManager manager, ISchedulingRule rule, Thread uiThread) {
			this.manager= manager;
			this.rule= rule;
			this.uiThread= uiThread;
		}

		void acquire(IProgressMonitor progress) {
			// ModalContext may legally execute inline even with fork=true.
			if (Thread.currentThread() == uiThread) {
				throw new IllegalStateException("Workspace rule acquisition requires a separate modal thread."); //$NON-NLS-1$
			}
			boolean transferred= false;
			try {
				manager.beginRule(rule, progress);
				synchronized (this) {
					if (abandoned || progress.isCanceled() || Thread.currentThread().isInterrupted()) {
						throw new OperationCanceledException();
					}
					manager.transferRule(rule, uiThread);
					owned= true;
					transferred= true;
				}
			} finally {
				// beginRule must be balanced even when acquisition is canceled.
				if (!transferred) manager.endRule(rule);
			}
		}

		@Override
		public synchronized void close() {
			abandoned= true;
			monitor.setCanceled(true);
			if (owned) {
				owned= false;
				manager.endRule(rule);
			}
		}
	}

	@Override
	public Object start(final IApplicationContext context) throws NoClassDefFoundError, Exception {
		final String[] arguments= (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		final ImportRequest request;
		try {
			request= parseImportRequest(arguments);
		} catch (IllegalArgumentException e) {
			return fail(e.getMessage());
		}
		final List<String> args= Arrays.asList(request.applicationArguments());
		if (args.isEmpty() || args.contains("-help") || args.contains("--help")) { //$NON-NLS-1$ //$NON-NLS-2$
			System.out.println(Messages.bind(Messages.CommandLineUsage));
			return IApplication.EXIT_OK;
		}
		try {
			ResourcesPlugin.getWorkspace();
		} catch (NoClassDefFoundError noClassError) {
			if (noClassError.getCause() instanceof ClassNotFoundException classNotFoundException
					&& classNotFoundException.getException() instanceof BundleException bundleException
					&& bundleException.getCause() instanceof IllegalStateException) {
				return fail(Messages.bind(Messages.WorkspaceRequired));
			}
			throw noClassError;
		}

		return request.projectDirectory() == null ? startCleanup(context, request) : startOnDisplay(context, request);
	}

	private Object startOnDisplay(IApplicationContext context, ImportRequest request) throws Exception {
		if (Thread.currentThread().isInterrupted()) {
			throw new InterruptedException("Interrupted before starting workspace cleanup."); //$NON-NLS-1$
		}
		Display current= Display.getCurrent();
		Display display= current == null ? Display.getDefault() : current;
		boolean ownsDisplay= current == null && display.getThread() == Thread.currentThread();
		try {
			if (display.getThread() != Thread.currentThread() && Job.getJobManager().currentRule() != null) {
				throw new IllegalStateException("Cannot dispatch cleanup while the calling thread owns a scheduling rule."); //$NON-NLS-1$
			}
			AtomicReference<Object> result= new AtomicReference<>();
			AtomicReference<Throwable> failure= new AtomicReference<>();
			display.syncExec(() -> {
				try {
					result.set(startWithWorkspaceRule(context, request, display));
				} catch (Exception | Error thrown) {
					failure.set(thrown);
				}
			});
			if (failure.get() != null) return rethrow(failure.get());
			return result.get();
		} finally {
			if (ownsDisplay && !display.isDisposed()) display.dispose();
		}
	}

	/**
	 * Keep import deltas synchronous and exclude conflicting resource jobs for the
	 * complete cleanup read/change lifetime. Waiting runs off the SWT thread while
	 * ModalContext services events needed by a job that already owns the rule.
	 */
	private Object startWithWorkspaceRule(IApplicationContext context, ImportRequest request, Display display) throws Exception {
		IJobManager manager= Job.getJobManager();
		ISchedulingRule root= ResourcesPlugin.getWorkspace().getRoot();
		ISchedulingRule current= manager.currentRule();
		if (current != null) {
			if (!current.contains(root)) {
				throw new IllegalStateException("The current SWT scheduling rule does not contain the workspace root."); //$NON-NLS-1$
			}
			try {
				manager.beginRule(root, new NullProgressMonitor());
				return startCleanup(context, request);
			} finally {
				manager.endRule(root);
			}
		}
		try (WorkspaceRuleHandoff handoff= new WorkspaceRuleHandoff(manager, root, Thread.currentThread())) {
			try {
				ModalContext.run(handoff::acquire, true, handoff.monitor, display);
			} catch (InvocationTargetException failure) {
				return rethrow(failure.getCause());
			}
			return startCleanup(context, request);
		}
	}

	private static Object rethrow(Throwable failure) throws Exception {
		if (failure instanceof Exception exception) throw exception;
		if (failure instanceof Error error) throw error;
		throw new IllegalStateException(failure);
	}

	private Object startCleanup(IApplicationContext context, ImportRequest request) throws Exception {
		if (request.projectDirectory() != null) {
			try {
				importProject(request.projectDirectory());
			} catch (CoreException | IOException e) {
				return fail(e.getMessage());
			}
		}

		Object result= delegate.start(new ForwardingApplicationContext(context, request.applicationArguments(), this));
		return delegate.hasErrors() ? Integer.valueOf(CodeCleanupApplication.EXIT_ERROR) : result;
	}

	private static Integer fail(String message) {
		System.err.println(failureMessage(message));
		System.out.println(Messages.bind(Messages.CommandLineUsage));
		return Integer.valueOf(CodeCleanupApplication.EXIT_ERROR);
	}

	static String failureMessage(String message) {
		return message == null || message.isBlank()
				? "Cleanup application failed without a diagnostic message." //$NON-NLS-1$
				: message;
	}

	private static ImportRequest parseImportRequest(String[] arguments) {
		if (arguments == null) {
			throw new IllegalArgumentException("Application arguments are unavailable."); //$NON-NLS-1$
		}
		List<String> forwarded= new ArrayList<>(arguments.length);
		File projectDirectory= null;
		for (int index= 0; index < arguments.length; index++) {
			String argument= arguments[index];
			if (!ARG_IMPORT_PROJECT.equals(argument)) {
				forwarded.add(argument);
				continue;
			}
			if (projectDirectory != null) {
				throw new IllegalArgumentException("The --import-project option may only be specified once."); //$NON-NLS-1$
			}
			if (++index >= arguments.length) {
				throw new IllegalArgumentException("The --import-project option requires a project directory."); //$NON-NLS-1$
			}
			projectDirectory= new File(arguments[index]);
		}
		return new ImportRequest(projectDirectory, forwarded.toArray(String[]::new));
	}

	private static void importProject(File projectDirectory) throws CoreException, IOException {
		File canonicalDirectory= projectDirectory.getCanonicalFile();
		File projectDescriptionFile= new File(canonicalDirectory, ".project"); //$NON-NLS-1$
		if (!canonicalDirectory.isDirectory() || !projectDescriptionFile.isFile()) {
			throw new IOException("The imported project directory must contain a .project file: " //$NON-NLS-1$
					+ canonicalDirectory);
		}

		IWorkspace workspace= ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root= workspace.getRoot();
		IPath projectLocation= Path.fromOSString(canonicalDirectory.getAbsolutePath());
		IProjectDescription description= workspace.loadProjectDescription(
				Path.fromOSString(projectDescriptionFile.getAbsolutePath()));
		IProject project= root.getProject(description.getName());
		NullProgressMonitor monitor= new NullProgressMonitor();
		if (!project.exists()) {
			IPath workspaceLocation= root.getLocation();
			IPath defaultLocation= workspaceLocation == null ? null : workspaceLocation.append(description.getName());
			description.setLocation(projectLocation.equals(defaultLocation) ? null : projectLocation);
			project.create(description, monitor);
		} else if (project.getLocation() != null && !projectLocation.equals(project.getLocation())) {
			throw new IOException("Workspace project already exists at another location: " + description.getName()); //$NON-NLS-1$
		}
		if (!project.isOpen()) {
			project.open(monitor);
		}
		project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
		if (project.hasNature(JavaCore.NATURE_ID)) {
			// A background job can own a pending link while waiting for our root rule.
			// Materialize this project's mapped links directly before parsing.
			var externalFolders= JavaModelManager.getExternalManager();
			for (var entry : JavaCore.create(project).getResolvedClasspath(true)) {
				var folder= externalFolders.getFolder(entry.getPath());
				if (folder != null && !folder.exists()) {
					externalFolders.createLinkFolder(entry.getPath(), false, monitor);
					externalFolders.removePendingFolder(entry.getPath());
				}
			}
		}
	}

	@Override
	public void stop() {
		delegate.stop();
	}
}

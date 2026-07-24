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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.osgi.util.NLS;
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

		/**
		 * Bind the given message's substitution locations with the given string values.
		 *
		 * @param message
		 *            the message to be manipulated
		 * @return the manipulated String
		 */
		public static String bind(final String message) {
			return bind(message, null);
		}

		/**
		 * Bind the given message's substitution locations with the given string values.
		 *
		 * @param message
		 *            the message to be manipulated
		 * @param binding
		 *            the object to be inserted into the message
		 * @return the manipulated String
		 */
		public static String bind(final String message, final Object binding) {
			return bind(message, new Object[] { binding });
		}

		/**
		 * Bind the given message's substitution locations with the given string values.
		 *
		 * @param message
		 *            the message to be manipulated
		 * @param binding1
		 *            An object to be inserted into the message
		 * @param binding2
		 *            A second object to be inserted into the message
		 * @return the manipulated String
		 */
		public static String bind(final String message, final Object binding1, final Object binding2) {
			return bind(message, new Object[] { binding1, binding2 });
		}

		/**
		 * Bind the given message's substitution locations with the given string values.
		 *
		 * @param message
		 *            the message to be manipulated
		 * @param bindings
		 *            An array of objects to be inserted into the message
		 * @return the manipulated String
		 */
		public static String bind(final String message, final Object[] bindings) {
			return MessageFormat.format(message, bindings);
		}
	}

	private final CodeCleanupApplication delegate= new CodeCleanupApplication();

	@Override
	public Object start(final IApplicationContext context) throws NoClassDefFoundError, Exception {
		final String[] arguments= (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		final ImportRequest request;
		try {
			request= parseImportRequest(arguments);
		} catch (IllegalArgumentException e) {
			System.err.println(e.getMessage());
			System.out.println(Messages.bind(Messages.CommandLineUsage));
			return Integer.valueOf(CodeCleanupApplication.EXIT_ERROR);
		}
		final List<String> args= Arrays.asList(request.applicationArguments());
		if (args.isEmpty() || args.contains("-help") || args.contains("--help")) { //$NON-NLS-1$ //$NON-NLS-2$
			System.out.println(Messages.bind(Messages.CommandLineUsage));
			return IApplication.EXIT_OK;
		}
		try {
			// Try to see if the workspace is available. If it is not available we'll
			// get a NoClassDefFoundError wrapping a ClassNotFoundException which
			// has a BundleException in it, whose cause was the original
			// IllegalStateException raised by
			// org.eclipse.core.internal.runtime.DataArea.assertLocationInitialized()
			ResourcesPlugin.getWorkspace();
		} catch (NoClassDefFoundError noClassError) {
			if (noClassError.getCause() instanceof ClassNotFoundException classNotFoundException
					&& classNotFoundException.getException() instanceof BundleException bundleException
					&& bundleException.getCause() instanceof IllegalStateException) {
				System.err.println(Messages.bind(Messages.WorkspaceRequired));
				System.out.println(Messages.bind(Messages.CommandLineUsage));
				return Integer.valueOf(CodeCleanupApplication.EXIT_ERROR);
			}
			throw noClassError;
		}

		if (request.projectDirectory() != null) {
			importProject(request.projectDirectory());
		}

		return delegate.start(new ForwardingApplicationContext(context, request.applicationArguments(), this));
	}

	private static ImportRequest parseImportRequest(String[] arguments) {
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
	}

	@Override
	public void stop() {
		delegate.stop();
	}
}

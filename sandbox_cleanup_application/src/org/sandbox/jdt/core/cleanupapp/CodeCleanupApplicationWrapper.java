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


import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.BundleException;

public class CodeCleanupApplicationWrapper implements IApplication {

	/**
	 * Deals with the messages in the properties file (cut n' pasted from a generated class).
	 */
	private final static class Messages extends NLS {
		private static final String BUNDLE_NAME = "org.eclipse.jdt.core.formatterapp.messages";//$NON-NLS-1$

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
		private static String bind(final String message, final Object binding) {
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
	@Override
	public Object start(final IApplicationContext context) throws NoClassDefFoundError, Exception {
		final String[] arguments = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		final List<String> args = Arrays.asList(arguments);
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
			if (noClassError.getCause() instanceof ClassNotFoundException) {
				final ClassNotFoundException classNotFoundException = (ClassNotFoundException) noClassError.getCause();
				if (classNotFoundException.getException() instanceof BundleException) {
					final BundleException bundleException = (BundleException) classNotFoundException.getException();
					if (bundleException.getCause() instanceof IllegalStateException) {
						System.err.println(Messages.bind(Messages.WorkspaceRequired));
						System.out.println(Messages.bind(Messages.CommandLineUsage));
						return 1;
					}
				}
			}
			throw noClassError;
		}

		// Workspace is available, so launch the  Code Cleanup Application
				return new CodeCleanupApplication().start(context);
//		return null;
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub

	}

}

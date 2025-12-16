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


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.util.Util;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.osgi.util.NLS;

public class CodeCleanupApplication implements IApplication {
	private static final File[] FILES = new File[0];

	private static final String ARG_CONFIG = "-config"; //$NON-NLS-1$

	private static final String ARG_HELP = "-help"; //$NON-NLS-1$

	private static final String ARG_QUIET = "-quiet"; //$NON-NLS-1$

	private static final String ARG_VERBOSE = "-verbose"; //$NON-NLS-1$

	private String configName;

	private Map<String, String> options = null;

	private static final String PDE_LAUNCH = "-pdelaunch"; //$NON-NLS-1$

	private boolean quiet = false;

	private boolean verbose = false;

	private static final  int INITIALSIZE = 1;

	private static final int DEFAULT_MODE = 0;

	private static final int CONFIG_MODE = 1;


	/**
	 * Deals with the messages in the properties file (cut n' pasted from a generated class).
	 */
	private final static class Messages extends NLS {
		private static final String BUNDLE_NAME = "org.eclipse.jdt.core.formatterapp.messages";//$NON-NLS-1$

		public static final String CommandLineCleaning = null;

		public static final String CleanupProblem = null;

		public static final String CaughtException = null;

		public static final String ExceptionSkip = null;

		public static final String CommandLineErrorFile = null;

		public static final String CommandLineErrorFileTryFullPath = null;

		public static final String CommandLineErrorConfig = null;

		public static final String CommandLineErrorNoConfigFile = null;

		public static final String CommandLineErrorQuietVerbose = null;

		public static final String CommandLineErrorFileDir = null;

		public static final String ConfigFileNotFoundErrorTryFullPath = null;

		public static final String ConfigFileReadingError = null;

		public static final String CommandLineConfigFile = null;

		public static final String CommandLineStart = null;

		public static final String CommandLineDone = null;

		public static final String FileOutsideWorkspace = null;

		public static final String CleanupFatalError = null;

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
			return bind(message, (Object[]) null);
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
	/**
	 * Clean up the given Java source file.
	 */
	private void cleanFile(final File file) {
		try {
			// Verbose output
			if (this.verbose) {
				System.out.println(Messages.bind(Messages.CommandLineCleaning, file.getAbsolutePath()));
			}

			// Convert file to workspace IFile
			IPath filePath = Path.fromOSString(file.getAbsolutePath());
			IFile iFile = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(filePath);
			
			if (iFile == null || !iFile.exists()) {
				if (!this.quiet) {
					System.err.println(Messages.bind(Messages.FileOutsideWorkspace, file.getAbsolutePath()));
				}
				return;
			}

			// Get the compilation unit
			ICompilationUnit cu = JavaCore.createCompilationUnitFrom(iFile);
			if (cu == null) {
				if (!this.quiet) {
					System.err.println(Messages.bind(Messages.CleanupProblem, file.getAbsolutePath()));
				}
				return;
			}

			// Create and configure the cleanup refactoring
			CleanUpRefactoring refactoring = new CleanUpRefactoring();
			refactoring.addCompilationUnit(cu);
			
			// Get all registered cleanups and configure with options if provided
			ICleanUp[] cleanUps = JavaPlugin.getDefault().getCleanUpRegistry().createCleanUps();
			if (this.options != null && cleanUps.length > 0) {
				// Create CleanUpOptions from the provided options map
				CleanUpOptions cleanUpOptions = new CleanUpOptions();
				for (Map.Entry<String, String> entry : this.options.entrySet()) {
					cleanUpOptions.setOption(entry.getKey(), entry.getValue());
				}
				// Set options on each cleanup
				for (ICleanUp cleanUp : cleanUps) {
					cleanUp.setOptions(cleanUpOptions);
					refactoring.addCleanUp(cleanUp);
				}
			} else {
				// Use default options from profile
				refactoring.setUseOptionsFromProfile(true);
				for (ICleanUp cleanUp : cleanUps) {
					refactoring.addCleanUp(cleanUp);
				}
			}

			// Check conditions
			RefactoringStatus status = refactoring.checkAllConditions(new NullProgressMonitor());
			if (status.hasFatalError()) {
				if (!this.quiet) {
					System.err.println(Messages.bind(Messages.CleanupFatalError, file.getAbsolutePath(), status.getMessageMatchingSeverity(RefactoringStatus.FATAL)));
				}
				return;
			}

			// Create and perform the change
			Change change = refactoring.createChange(new NullProgressMonitor());
			if (change != null) {
				change.perform(new NullProgressMonitor());
				cu.save(new NullProgressMonitor(), true);
				iFile.refreshLocal(1, new NullProgressMonitor());
			}

		} catch (CoreException e) {
			final String errorMessage = Messages.bind(Messages.CaughtException, "CoreException", e.getLocalizedMessage()); //$NON-NLS-1$
			Util.log(e, errorMessage);
			System.err.println(Messages.bind(Messages.ExceptionSkip, errorMessage));
		} catch (Exception e) {
			final String errorMessage = Messages.bind(Messages.CaughtException, e.getClass().getSimpleName(), e.getLocalizedMessage());
			Util.log(e, errorMessage);
			System.err.println(Messages.bind(Messages.ExceptionSkip, errorMessage));
		}
	}

	private File[] processCommandLine(final String[] argsArray) {

		int index = 0;
		final int argCount = argsArray.length;

		int mode = DEFAULT_MODE;
		
		int fileCounter = 0;

		File[] filesToCleanup = new File[INITIALSIZE];

		loop: while (index < argCount) {
			final String currentArg = argsArray[index++];

			switch(mode) {
				default:
					break;
				case DEFAULT_MODE :
					if (PDE_LAUNCH.equals(currentArg)) {
						continue loop;
					}
					if (ARG_HELP.equals(currentArg)) {
						displayHelp();
						return FILES;
					}
					if (ARG_VERBOSE.equals(currentArg)) {
						this.verbose = true;
						continue loop;
					}
					if (ARG_QUIET.equals(currentArg)) {
						this.quiet = true;
						continue loop;
					}
					if (ARG_CONFIG.equals(currentArg)) {
						mode = CONFIG_MODE;
						continue loop;
					}
					// the current arg should be a file or a directory name
					final File file = new File(currentArg);
					if (file.exists()) {
						if (filesToCleanup.length == fileCounter) {
							System.arraycopy(filesToCleanup, 0, filesToCleanup = new File[fileCounter * 2], 0, fileCounter);
						}
						filesToCleanup[fileCounter++] = file;
					} else {
						String canonicalPath;
						try {
							canonicalPath = file.getCanonicalPath();
						} catch(IOException e2) {
							canonicalPath = file.getAbsolutePath();
						}
						final String errorMsg = file.isAbsolute()?
										  Messages.bind(Messages.CommandLineErrorFile, canonicalPath):
										  Messages.bind(Messages.CommandLineErrorFileTryFullPath, canonicalPath);
						displayHelp(errorMsg);
						return FILES;
					}
					break;
				case CONFIG_MODE :
					this.configName = currentArg;
					this.options = readConfig(currentArg);
					if (this.options == null) {
						displayHelp(Messages.bind(Messages.CommandLineErrorConfig, currentArg));
						return FILES;
					}
					mode = DEFAULT_MODE;
					continue loop;
			}
		}

		if (mode == CONFIG_MODE || this.options == null) {
			displayHelp(Messages.bind(Messages.CommandLineErrorNoConfigFile));
			return null;
		}
		if (this.quiet && this.verbose) {
			displayHelp(
				Messages.bind(
					Messages.CommandLineErrorQuietVerbose,
					new String[] { ARG_QUIET, ARG_VERBOSE }
				));
			return null;
		}
		if (fileCounter == 0) {
			displayHelp(Messages.bind(Messages.CommandLineErrorFileDir));
			return null;
		}
		if (filesToCleanup.length != fileCounter) {
			System.arraycopy(filesToCleanup, 0, filesToCleanup = new File[fileCounter], 0, fileCounter);
		}
		return filesToCleanup;
	}

	/**
	 * Return a Java Properties file representing the options that are in the
	 * specified configuration file.
	 */
	private static Map<String, String> readConfig(final String filename) {
		final File configFile = new File(filename);
		try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(configFile));){
			final Properties formatterOptions = new Properties();
			formatterOptions.load(stream);
			// Convert Properties to Map<String, String>
			Map<String, String> options = new HashMap<>();
			for (String key : formatterOptions.stringPropertyNames()) {
				options.put(key, formatterOptions.getProperty(key));
			}
			return options;
		} catch (IOException e) {
			String canonicalPath = null;
			try {
				canonicalPath = configFile.getCanonicalPath();
			} catch(IOException e2) {
				canonicalPath = configFile.getAbsolutePath();
			}
			final String errorMessage;
			if (!configFile.exists() && !configFile.isAbsolute()) {
				errorMessage = Messages.bind(Messages.ConfigFileNotFoundErrorTryFullPath, new Object[] {
					canonicalPath,
					System.getProperty("user.dir") //$NON-NLS-1$
				});

			} else {
				errorMessage = Messages.bind(Messages.ConfigFileReadingError, canonicalPath);
			}
			Util.log(e, errorMessage);
			System.err.println(errorMessage);
		}
		return null;
	}

	/**
	 * Runs the Java code cleanup application
	 */
	@Override
	public Object start(final IApplicationContext context) throws Exception {
		final File[] filesToCleanup = processCommandLine((String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS));

		if (filesToCleanup == null) {
			return IApplication.EXIT_OK;
		}

		if (!this.quiet) {
			if (this.configName != null) {
				System.out.println(Messages.bind(Messages.CommandLineConfigFile, this.configName));
			}
			System.out.println(Messages.bind(Messages.CommandLineStart));
		}

		// clean up the list of files and/or directories
		for (final File file : filesToCleanup) {
			if (file.isDirectory()) {
				cleanDirTree(file);
			} else if (Util.isJavaLikeFileName(file.getPath())) {
				cleanFile(file);
			}
		}
		if (!this.quiet) {
			System.out.println(Messages.bind(Messages.CommandLineDone));
		}

		return IApplication.EXIT_OK;
	}
	@Override
	public void stop() {
		// do nothing
	}

	/**
	 * Display the command line usage message.
	 */
	private static void displayHelp() {
		System.out.println(Messages.bind(Messages.CommandLineUsage));
	}

	private static void displayHelp(final String message) {
		System.err.println(message);
		System.out.println();
		displayHelp();
	}
	/**
	 * Recursively clean up the Java source code that is contained in the
	 * directory rooted at dir.
	 */
	private void cleanDirTree(final File dir) {

		final File[] files = dir.listFiles();
		if (files == null) {
			return;
		}

		for (final File file : files) {
			if (file.isDirectory()) {
				cleanDirTree(file);
			} else if (Util.isJavaLikeFileName(file.getPath())) {
				cleanFile(file);
			}
		}
	}
}

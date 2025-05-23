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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.internal.compiler.env.IModule;
import org.eclipse.jdt.internal.core.util.Util;
import org.eclipse.jdt.internal.formatter.DefaultCodeFormatter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.osgi.util.NLS;
import org.eclipse.text.edits.TextEdit;

public class CodeCleanupApplication implements IApplication {
	private static final File[] FILES = new File[0];

	private static final String ARG_CONFIG = "-config"; //$NON-NLS-1$

	private static final String ARG_HELP = "-help"; //$NON-NLS-1$

	private static final String ARG_QUIET = "-quiet"; //$NON-NLS-1$

	private static final String ARG_VERBOSE = "-verbose"; //$NON-NLS-1$

	private String configName;

	private Map<?, ?> options = null;

	private static final String PDE_LAUNCH = "-pdelaunch"; //$NON-NLS-1$

	public static final int M_FORMAT_NEW = Integer.valueOf(0).intValue();

	public static final int M_FORMAT_EXISTING = Integer.valueOf(1).intValue();

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

		public static final String CommandLineFormatting = null;

		public static final String FormatProblem = null;

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
		public static String bind(String message) {
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
		public static String bind(String message, Object binding) {
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
		public static String bind(String message, Object binding1, Object binding2) {
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
		public static String bind(String message, Object[] bindings) {
			return MessageFormat.format(message, bindings);
		}
	}
	/**
	 * Format the given Java source file.
	 */
	private void formatFile(File file, CodeFormatter codeFormatter) {
		IDocument doc = new Document();
		try {
			// read the file
			if (this.verbose) {
				System.out.println(Messages.bind(Messages.CommandLineFormatting, file.getAbsolutePath()));
			}
			String contents = new String(org.eclipse.jdt.internal.compiler.util.Util.getFileCharContent(file, null));
			// format the file (the meat and potatoes)
			doc.set(contents);
			int kind = (file.getName().equals(IModule.MODULE_INFO_JAVA)? CodeFormatter.K_MODULE_INFO
					: CodeFormatter.K_COMPILATION_UNIT) | CodeFormatter.F_INCLUDE_COMMENTS;
			TextEdit edit = codeFormatter.format(kind, contents, 0, contents.length(), 0, null);
			if (edit != null) {
				edit.apply(doc);
			} else {
				System.err.println(Messages.bind(Messages.FormatProblem, file.getAbsolutePath()));
				return;
			}

			// write the file
			try (BufferedWriter out = new BufferedWriter(new FileWriter(file,StandardCharsets.UTF_8));){
				out.write(doc.get());
				out.flush();
			}
		} catch (IOException e) {
			String errorMessage = Messages.bind(Messages.CaughtException, "IOException", e.getLocalizedMessage()); //$NON-NLS-1$
			Util.log(e, errorMessage);
			System.err.println(Messages.bind(Messages.ExceptionSkip ,errorMessage));
		} catch (BadLocationException e) {
			String errorMessage = Messages.bind(Messages.CaughtException, "BadLocationException", e.getLocalizedMessage()); //$NON-NLS-1$
			Util.log(e, errorMessage);
			System.err.println(Messages.bind(Messages.ExceptionSkip ,errorMessage));
		}
	}

	private File[] processCommandLine(String[] argsArray) {

		int index = 0;
		final int argCount = argsArray.length;

		int mode = DEFAULT_MODE;
		
		int fileCounter = 0;

		File[] filesToFormat = new File[INITIALSIZE];

		loop: while (index < argCount) {
			String currentArg = argsArray[index++];

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
					File file = new File(currentArg);
					if (file.exists()) {
						if (filesToFormat.length == fileCounter) {
							System.arraycopy(filesToFormat, 0, filesToFormat = new File[fileCounter * 2], 0, fileCounter);
						}
						filesToFormat[fileCounter++] = file;
					} else {
						String canonicalPath;
						try {
							canonicalPath = file.getCanonicalPath();
						} catch(IOException e2) {
							canonicalPath = file.getAbsolutePath();
						}
						String errorMsg = file.isAbsolute()?
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
		if (filesToFormat.length != fileCounter) {
			System.arraycopy(filesToFormat, 0, filesToFormat = new File[fileCounter], 0, fileCounter);
		}
		return filesToFormat;
	}

	/**
	 * Return a Java Properties file representing the options that are in the
	 * specified configuration file.
	 */
	private static Properties readConfig(String filename) {
		File configFile = new File(filename);
		try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(configFile));){
			final Properties formatterOptions = new Properties();
			formatterOptions.load(stream);
			return formatterOptions;
		} catch (IOException e) {
			String canonicalPath = null;
			try {
				canonicalPath = configFile.getCanonicalPath();
			} catch(IOException e2) {
				canonicalPath = configFile.getAbsolutePath();
			}
			String errorMessage;
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
	 * Runs the Java code formatter application
	 */
	@Override
	public Object start(IApplicationContext context) throws Exception {
		File[] filesToFormat = processCommandLine((String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS));

		if (filesToFormat == null) {
			return IApplication.EXIT_OK;
		}

		if (!this.quiet) {
			if (this.configName != null) {
				System.out.println(Messages.bind(Messages.CommandLineConfigFile, this.configName));
			}
			System.out.println(Messages.bind(Messages.CommandLineStart));
		}

		final CodeFormatter codeFormatter = createCodeFormatter(this.options,
				M_FORMAT_EXISTING);
		// format the list of files and/or directories
		for (final File file : filesToFormat) {
			if (file.isDirectory()) {
				formatDirTree(file, codeFormatter);
			} else if (Util.isJavaLikeFileName(file.getPath())) {
				formatFile(file, codeFormatter);
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

	private static void displayHelp(String message) {
		System.err.println(message);
		System.out.println();
		displayHelp();
	}
	/**
	 * Recursively format the Java source code that is contained in the
	 * directory rooted at dir.
	 */
	private void formatDirTree(File dir, CodeFormatter codeFormatter) {

		File[] files = dir.listFiles();
		if (files == null) {
			return;
		}

		for (File file : files) {
			if (file.isDirectory()) {
				formatDirTree(file, codeFormatter);
			} else if (Util.isJavaLikeFileName(file.getPath())) {
				formatFile(file, codeFormatter);
			}
		}
	}

	private static CodeFormatter createCodeFormatter(Map options, int mode) {
		if (options == null) {
			options = JavaCore.getOptions();
		}
		Map currentOptions = new HashMap(options);
		if (mode == M_FORMAT_NEW) {
			// disable the option for not formatting comments starting on first column
			currentOptions.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_FORMAT_LINE_COMMENT_STARTING_ON_FIRST_COLUMN, DefaultCodeFormatterConstants.TRUE);
			// disable the option for not indenting comments starting on first column
			currentOptions.put(DefaultCodeFormatterConstants.FORMATTER_NEVER_INDENT_BLOCK_COMMENTS_ON_FIRST_COLUMN, DefaultCodeFormatterConstants.FALSE);
			currentOptions.put(DefaultCodeFormatterConstants.FORMATTER_NEVER_INDENT_LINE_COMMENTS_ON_FIRST_COLUMN, DefaultCodeFormatterConstants.FALSE);
		}
		String formatterId = (String) options.get(JavaCore.JAVA_FORMATTER);
		if (formatterId != null) {
			IExtensionPoint extension = Platform.getExtensionRegistry().getExtensionPoint(JavaCore.PLUGIN_ID,
					JavaCore.JAVA_FORMATTER_EXTENSION_POINT_ID);
			if (extension != null) {
				for (IExtension extension2 : extension.getExtensions()) {
					for (IConfigurationElement configElement : extension2.getConfigurationElements()) {
						String initializerID = configElement.getAttribute("id"); //$NON-NLS-1$
						if (initializerID != null && initializerID.equals(formatterId)) {
							try {
								Object execExt = configElement.createExecutableExtension("class"); //$NON-NLS-1$
								if (execExt instanceof CodeFormatter formatter) {
									formatter.setOptions(currentOptions);
									return formatter;
								}
							} catch (CoreException e) {
								Util.log(e.getStatus());
								break;
							}
						}
					}
				}
			}
			Util.log(IStatus.WARNING,
					"Unable to instantiate formatter extension '" + formatterId + "', returning built-in formatter."); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return new DefaultCodeFormatter(currentOptions);
	}
}

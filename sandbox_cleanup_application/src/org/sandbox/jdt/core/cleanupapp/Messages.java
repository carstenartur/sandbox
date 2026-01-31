package org.sandbox.jdt.core.cleanupapp;

import java.text.MessageFormat;

import org.eclipse.osgi.util.NLS;

/**
 * Deals with the messages in the properties file (cut n' pasted from a generated class).
 */
final class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.sandbox.jdt.core.cleanupapp.messages";//$NON-NLS-1$

	public static String CommandLineCleaning;

	public static String CleanupProblem;

	public static String CaughtException;

	public static String ExceptionSkip;

	public static String CommandLineErrorFile;

	public static String CommandLineErrorFileTryFullPath;

	public static String CommandLineErrorConfig;

	public static String CommandLineErrorNoConfigFile;

	public static String CommandLineErrorQuietVerbose;

	public static String CommandLineErrorFileDir;

	public static String ConfigFileNotFoundErrorTryFullPath;

	public static String ConfigFileReadingError;

	public static String CommandLineConfigFile;

	public static String CommandLineStart;

	public static String CommandLineDone;

	public static String FileOutsideWorkspace;

	public static String CleanupFatalError;

	public static String CommandLineUsage;
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
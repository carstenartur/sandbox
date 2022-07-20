package org.sandbox.jdt.internal.ui.fix;

import org.eclipse.osgi.util.NLS;

public class MultiFixMessages {
	private static final String BUNDLE_NAME= "org.sandbox.jdt.internal.ui.fix.MultiFixMessages"; //$NON-NLS-1$

	public static String PlatformStatusCleanUp_description;
	public static String PlatformStatusCleanUpFix_refactor;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, MultiFixMessages.class);
	}
}

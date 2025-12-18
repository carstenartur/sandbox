package org.sandbox.jdt.internal.ui.search;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.sandbox.jdt.internal.ui.search.messages"; //$NON-NLS-1$
	public static String OpenUpdateSearchPageAction_0;
	public static String OpenUpdateSearchPageAction_PageNotAvailable;
	public static String OpenUpdateSearchPageAction_WindowNotAvailable;
	public static String UpdateNeededSearchPage_ClassListLoadFailed;
	public static String UpdateNeededSearchPage_ClassListNotFound;
	public static String UpdateNeededSearchPage_NoProjectsFound;
	public static String UpdateNeededSearchPage_WorkspaceNotAvailable;
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}

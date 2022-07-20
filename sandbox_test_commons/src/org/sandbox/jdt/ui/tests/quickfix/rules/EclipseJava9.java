package org.sandbox.jdt.ui.tests.quickfix.rules;

import org.eclipse.jdt.core.JavaCore;

public class EclipseJava9 extends AbstractEclipseJava {
	private static final String TESTRESOURCES_RTSTUBS_9_JAR= "testresources/rtstubs9.jar";

	public EclipseJava9() {
		super(TESTRESOURCES_RTSTUBS_9_JAR, JavaCore.VERSION_9);
	}
}

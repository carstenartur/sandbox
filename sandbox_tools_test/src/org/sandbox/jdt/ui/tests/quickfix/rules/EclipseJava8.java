package org.sandbox.jdt.ui.tests.quickfix.rules;

import org.eclipse.jdt.core.JavaCore;

public class EclipseJava8 extends AbstractEclipseJava {
	private static final String TESTRESOURCES_RTSTUBS18_JAR = "testresources/rtstubs18.jar";
	
	public EclipseJava8() {
		super(TESTRESOURCES_RTSTUBS18_JAR,JavaCore.VERSION_1_8);
	}
}

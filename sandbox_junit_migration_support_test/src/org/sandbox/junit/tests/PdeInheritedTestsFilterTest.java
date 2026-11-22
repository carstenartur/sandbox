/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.junit.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.eclipse.jdt.internal.junit5.runner.JUnit5TestLoader;
import org.eclipse.pde.internal.junit.runtime.SPIBundleClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.PostDiscoveryFilter;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.sandbox.junit.jupiter.IgnoreInheritedTests;

/** Runs the actual PDE SPI class loader and JDT 5 discovery across separate Equinox bundles. */
public class PdeInheritedTestsFilterTest {

	public static class Base {
		@Test public void inheritedJupiter() {}
		@org.junit.Test public void inheritedVintage() {}
	}

	@IgnoreInheritedTests
	public static class Migrated extends Base {
		@Test public void localJupiter() {}
		@org.junit.Test public void localVintage() {}
	}

	@Test
	public void discoversTheTestBundleProviderAndCountsOnlyDirectTests() throws ClassNotFoundException {
		Bundle consumer= FrameworkUtil.getBundle(Migrated.class);
		Bundle support= FrameworkUtil.getBundle(IgnoreInheritedTests.class);
		Bundle jdt= FrameworkUtil.getBundle(JUnit5TestLoader.class);
		assertNotSame(consumer, support);
		assertNotNull(consumer.getEntry("META-INF/services/" + PostDiscoveryFilter.class.getName())); //$NON-NLS-1$
		// Choose the engines wired to the JDT 5 runtime even when JUnit 6 is also installed.
		Bundle jupiter= FrameworkUtil.getBundle(jdt.loadClass("org.junit.jupiter.engine.JupiterTestEngine")); //$NON-NLS-1$
		Bundle vintage= FrameworkUtil.getBundle(jdt.loadClass("org.junit.vintage.engine.VintageTestEngine")); //$NON-NLS-1$
		var loader= new SPIBundleClassLoader(List.of(jupiter, vintage, consumer, jdt), 5);
		Thread thread= Thread.currentThread();
		ClassLoader previous= thread.getContextClassLoader();
		try {
			thread.setContextClassLoader(loader);
			// Discovery initializes PDE's SPI mapping through the actual Platform service call stack.
			var tests= new JUnit5TestLoader().loadTests(new Class<?>[] { Migrated.class, Base.class },
					null, null, null, new String[2][], null, null);
			assertEquals(2, tests[0].countTestCases());
			assertEquals(2, tests[1].countTestCases());
			assertSame(support, FrameworkUtil.getBundle(consumer.loadClass("org.sandbox.junit.jupiter.InheritedTestsFilter"))); //$NON-NLS-1$
		} finally {
			thread.setContextClassLoader(previous);
		}
	}
}

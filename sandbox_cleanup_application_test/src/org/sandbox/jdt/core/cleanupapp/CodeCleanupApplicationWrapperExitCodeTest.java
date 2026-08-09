/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.core.cleanupapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.equinox.app.IApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Bundle;

/** Public-process contract tests for {@link CodeCleanupApplicationWrapper}. */
class CodeCleanupApplicationWrapperExitCodeTest {

	@TempDir
	Path temporaryDirectory;

	private PrintStream originalOut;
	private PrintStream originalErr;
	private ByteArrayOutputStream stdout;
	private ByteArrayOutputStream stderr;

	@BeforeEach
	void captureOutput() {
		originalOut= System.out;
		originalErr= System.err;
		stdout= new ByteArrayOutputStream();
		stderr= new ByteArrayOutputStream();
		System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
		System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
	}

	@AfterEach
	void restoreOutput() {
		System.setOut(originalOut);
		System.setErr(originalErr);
	}

	@Test
	void helpReturnsSuccess() throws Exception {
		Object result= run("--help"); //$NON-NLS-1$

		assertEquals(Integer.valueOf(CodeCleanupApplication.EXIT_OK), result);
		assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("Usage:")); //$NON-NLS-1$
	}

	@Test
	void missingConfigurationReturnsError() throws Exception {
		Path source= Files.writeString(temporaryDirectory.resolve("MissingConfig.java"), //$NON-NLS-1$
				"class MissingConfig {}", StandardCharsets.UTF_8); //$NON-NLS-1$

		Object result= run("--mode", "check", source.toString()); //$NON-NLS-1$ //$NON-NLS-2$

		assertEquals(Integer.valueOf(CodeCleanupApplication.EXIT_ERROR), result);
		assertTrue(stderr.toString(StandardCharsets.UTF_8).toLowerCase().contains("config")); //$NON-NLS-1$
	}

	@Test
	void malformedProjectImportReturnsError() throws Exception {
		Object result= run("--import-project"); //$NON-NLS-1$

		assertEquals(Integer.valueOf(CodeCleanupApplication.EXIT_ERROR), result);
		assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("requires a project directory")); //$NON-NLS-1$
	}

	@Test
	void processingFailureReturnsErrorEvenInQuietMode() throws Exception {
		Path configuration= Files.writeString(temporaryDirectory.resolve("cleanup.properties"), //$NON-NLS-1$
				"cleanup.format_source_code=true\n", StandardCharsets.ISO_8859_1); //$NON-NLS-1$
		Path source= Files.writeString(temporaryDirectory.resolve("OutsideWorkspace.java"), //$NON-NLS-1$
				"class OutsideWorkspace {}", StandardCharsets.UTF_8); //$NON-NLS-1$

		Object result= run("--quiet", "--mode", "check", "--config", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				configuration.toString(), source.toString());

		assertEquals(Integer.valueOf(CodeCleanupApplication.EXIT_ERROR), result);
		assertTrue(stderr.toString(StandardCharsets.UTF_8).toLowerCase().contains("workspace")); //$NON-NLS-1$
	}

	private static Object run(String... arguments) throws Exception {
		return new CodeCleanupApplicationWrapper().start(new TestApplicationContext(arguments));
	}

	private static final class TestApplicationContext implements IApplicationContext {
		private final String[] arguments;

		TestApplicationContext(String[] arguments) {
			this.arguments= arguments.clone();
		}

		@Override
		@SuppressWarnings({ "rawtypes", "unchecked" })
		public Map getArguments() {
			Map result= new HashMap();
			result.put(APPLICATION_ARGS, arguments.clone());
			return result;
		}

		@Override
		public void applicationRunning() {
			// Nothing to report in the test harness.
		}

		@Override
		public String getBrandingApplication() {
			return null;
		}

		@Override
		public Bundle getBrandingBundle() {
			return null;
		}

		@Override
		public String getBrandingDescription() {
			return null;
		}

		@Override
		public String getBrandingId() {
			return null;
		}

		@Override
		public String getBrandingName() {
			return null;
		}

		@Override
		public String getBrandingProperty(String key) {
			return null;
		}

		@Override
		public void setResult(Object result, org.eclipse.equinox.app.IApplication application) {
			// The wrapper's return value is asserted directly.
		}
	}
}

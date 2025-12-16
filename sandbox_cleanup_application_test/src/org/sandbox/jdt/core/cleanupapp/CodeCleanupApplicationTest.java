package org.sandbox.jdt.core.cleanupapp;

/*******************************************************************************
 * Copyright (c) 2024 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Bundle;

/**
 * Test class for CodeCleanupApplication command-line argument parsing and behavior.
 * 
 * These tests verify:
 * - Help flag returns EXIT_OK
 * - Missing config file error handling
 * - Quiet vs verbose conflict detection
 * - Config mode handling
 * - File validation
 * - Integration tests via start() method validating public API behavior
 */
public class CodeCleanupApplicationTest {

	private CodeCleanupApplication app;
	private PrintStream originalOut;
	private PrintStream originalErr;
	private ByteArrayOutputStream outContent;
	private ByteArrayOutputStream errContent;

	@TempDir
	File tempDir;

	@BeforeEach
	public void setUp() {
		app = new CodeCleanupApplication();
		
		// Capture stdout and stderr
		originalOut = System.out;
		originalErr = System.err;
		outContent = new ByteArrayOutputStream();
		errContent = new ByteArrayOutputStream();
		System.setOut(new PrintStream(outContent));
		System.setErr(new PrintStream(errContent));
	}

	@AfterEach
	public void tearDown() {
		// Restore stdout and stderr
		System.setOut(originalOut);
		System.setErr(originalErr);
	}

	/**
	 * Test that -help flag returns empty file array (EXIT_OK behavior)
	 */
	@Test
	public void testHelpFlag() throws Exception {
		String[] args = { "-help" };
		File[] result = processCommandLine(args);

		// -help should return empty array (FILES constant)
		assertNotNull(result);
		assertEquals(0, result.length);
		
		// Help text should be printed to stdout
		String output = outContent.toString();
		assertTrue(output.contains("Usage:"), "Help output should contain usage information");
	}

	/**
	 * Test that missing config file produces error
	 */
	@Test
	public void testMissingConfigFile() throws Exception {
		String[] args = { "-config" };
		File[] result = processCommandLine(args);

		// Should return null on error
		assertNull(result);
		
		// Error message should be printed
		String output = outContent.toString();
		assertTrue(output.contains("configuration file") || output.contains("config"), 
				"Output should mention configuration file error");
	}

	/**
	 * Test that config file that doesn't exist produces error
	 */
	@Test
	public void testNonExistentConfigFile() throws Exception {
		String[] args = { "-config", "/nonexistent/config.properties", "dummy.java" };
		File[] result = processCommandLine(args);

		// Should return empty array due to config error
		assertNotNull(result);
		assertEquals(0, result.length);
		
		// Error message should be printed to stderr
		String errorOutput = errContent.toString();
		assertTrue(errorOutput.contains("config") || errorOutput.contains("Configuration"),
				"Error output should mention config file problem");
	}

	/**
	 * Test that -quiet and -verbose together produces error
	 */
	@Test
	public void testQuietVerboseConflict() throws Exception {
		File configFile = createTempConfigFile();
		File javaFile = createTempJavaFile("Test.java");
		
		String[] args = { "-config", configFile.getAbsolutePath(), "-quiet", "-verbose", javaFile.getAbsolutePath() };
		File[] result = processCommandLine(args);

		// Should return null on error
		assertNull(result);
		
		// Error message should be printed
		String output = outContent.toString();
		assertTrue(output.contains("quiet") || output.contains("verbose"),
				"Output should mention quiet/verbose conflict");
	}

	/**
	 * Test config mode handling - valid config file
	 */
	@Test
	public void testValidConfigMode() throws Exception {
		File configFile = createTempConfigFile();
		File javaFile = createTempJavaFile("Test.java");
		
		String[] args = { "-config", configFile.getAbsolutePath(), javaFile.getAbsolutePath() };
		File[] result = processCommandLine(args);

		// Should succeed with valid config
		assertNotNull(result);
		assertEquals(1, result.length);
		assertEquals(javaFile.getAbsolutePath(), result[0].getAbsolutePath());
		
		// Check that options were loaded
		Map<String, String> options = getOptions();
		assertNotNull(options, "Options should be loaded from config file");
		assertTrue(options.containsKey("cleanup.format_source_code"), 
				"Options should contain cleanup settings");
	}

	/**
	 * Test that non-existent file produces error
	 */
	@Test
	public void testNonExistentFile() throws Exception {
		File configFile = createTempConfigFile();
		
		String[] args = { "-config", configFile.getAbsolutePath(), "/nonexistent/file.java" };
		File[] result = processCommandLine(args);

		// Should return empty array
		assertNotNull(result);
		assertEquals(0, result.length);
		
		// Error message should be printed to stderr
		String errorOutput = errContent.toString();
		assertTrue(errorOutput.contains("doesn't exist") || errorOutput.contains("File"),
				"Error output should mention file doesn't exist");
	}

	/**
	 * Test that no files specified produces error
	 */
	@Test
	public void testNoFilesSpecified() throws Exception {
		File configFile = createTempConfigFile();
		
		String[] args = { "-config", configFile.getAbsolutePath() };
		File[] result = processCommandLine(args);

		// Should return null on error
		assertNull(result);
		
		// Error message should be printed
		String output = outContent.toString();
		assertTrue(output.contains("files") || output.contains("directories"),
				"Output should mention no files/directories specified");
	}

	/**
	 * Test verbose flag sets verbose mode
	 */
	@Test
	public void testVerboseFlag() throws Exception {
		File configFile = createTempConfigFile();
		File javaFile = createTempJavaFile("Test.java");
		
		String[] args = { "-config", configFile.getAbsolutePath(), "-verbose", javaFile.getAbsolutePath() };
		File[] result = processCommandLine(args);

		// Should succeed
		assertNotNull(result);
		assertEquals(1, result.length);
		
		// Check that verbose flag was set
		boolean verbose = getVerbose();
		assertTrue(verbose, "Verbose flag should be set");
	}

	/**
	 * Test quiet flag sets quiet mode
	 */
	@Test
	public void testQuietFlag() throws Exception {
		File configFile = createTempConfigFile();
		File javaFile = createTempJavaFile("Test.java");
		
		String[] args = { "-config", configFile.getAbsolutePath(), "-quiet", javaFile.getAbsolutePath() };
		File[] result = processCommandLine(args);

		// Should succeed
		assertNotNull(result);
		assertEquals(1, result.length);
		
		// Check that quiet flag was set
		boolean quiet = getQuiet();
		assertTrue(quiet, "Quiet flag should be set");
	}

	/**
	 * Test multiple files can be specified
	 */
	@Test
	public void testMultipleFiles() throws Exception {
		File configFile = createTempConfigFile();
		File javaFile1 = createTempJavaFile("Test1.java");
		File javaFile2 = createTempJavaFile("Test2.java");
		
		String[] args = { 
			"-config", configFile.getAbsolutePath(), 
			javaFile1.getAbsolutePath(), 
			javaFile2.getAbsolutePath() 
		};
		File[] result = processCommandLine(args);

		// Should succeed with both files
		assertNotNull(result);
		assertEquals(2, result.length);
	}

	/**
	 * Test that directory can be specified
	 */
	@Test
	public void testDirectory() throws Exception {
		File configFile = createTempConfigFile();
		
		String[] args = { "-config", configFile.getAbsolutePath(), tempDir.getAbsolutePath() };
		File[] result = processCommandLine(args);

		// Should succeed with directory
		assertNotNull(result);
		assertEquals(1, result.length);
		assertTrue(result[0].isDirectory(), "Result should be a directory");
	}

	/**
	 * Integration test: Verify verbose mode produces output
	 */
	@Test
	public void testStartWithVerboseMode() throws Exception {
		File configFile = createTempConfigFile();
		File javaFile = createTempJavaFile("VerboseTest.java");
		
		IApplicationContext context = new MockApplicationContext(
			new String[] { "-config", configFile.getAbsolutePath(), "-verbose", javaFile.getAbsolutePath() }
		);
		Object result = app.start(context);
		
		assertEquals(IApplication.EXIT_OK, result);
		
		// In verbose mode, should see configuration file message
		String output = outContent.toString();
		assertTrue(output.contains("configuration file") || output.contains("Starting cleanup"), 
				"Verbose mode should produce progress output");
	}

	/**
	 * Integration test: Verify quiet mode suppresses output
	 */
	@Test
	public void testStartWithQuietMode() throws Exception {
		File configFile = createTempConfigFile();
		File javaFile = createTempJavaFile("QuietTest.java");
		
		IApplicationContext context = new MockApplicationContext(
			new String[] { "-config", configFile.getAbsolutePath(), "-quiet", javaFile.getAbsolutePath() }
		);
		Object result = app.start(context);
		
		assertEquals(IApplication.EXIT_OK, result);
		
		// In quiet mode, stdout should be minimal (no "Starting cleanup" messages)
		String output = outContent.toString();
		assertTrue(output.isEmpty() || !output.contains("Starting cleanup"), 
				"Quiet mode should suppress normal output");
	}

	/**
	 * Integration test: Verify error handling for missing config
	 */
	@Test
	public void testStartWithMissingConfig() throws Exception {
		IApplicationContext context = new MockApplicationContext(
			new String[] { "-config", "/nonexistent/config.properties", "dummy.java" }
		);
		Object result = app.start(context);
		
		assertEquals(IApplication.EXIT_OK, result);
		
		// Should see error message about config file
		String errorOutput = errContent.toString();
		assertTrue(errorOutput.contains("config") || errorOutput.contains("Configuration"), 
				"Should report configuration file error");
	}

	/**
	 * Integration test: Verify conflict between quiet and verbose flags
	 */
	@Test
	public void testStartWithQuietVerboseConflict() throws Exception {
		File configFile = createTempConfigFile();
		File javaFile = createTempJavaFile("ConflictTest.java");
		
		IApplicationContext context = new MockApplicationContext(
			new String[] { "-config", configFile.getAbsolutePath(), "-quiet", "-verbose", javaFile.getAbsolutePath() }
		);
		Object result = app.start(context);
		
		assertEquals(IApplication.EXIT_OK, result);
		
		// Should see error message about the conflict
		String output = outContent.toString();
		assertTrue(output.contains("quiet") || output.contains("verbose"), 
				"Should report quiet/verbose conflict");
	}

	// Helper methods using reflection to access private methods and fields

	private File[] processCommandLine(String[] args) throws Exception {
		Method method = CodeCleanupApplication.class.getDeclaredMethod("processCommandLine", String[].class);
		method.setAccessible(true);
		return (File[]) method.invoke(app, (Object) args);
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> getOptions() throws Exception {
		Field field = CodeCleanupApplication.class.getDeclaredField("options");
		field.setAccessible(true);
		Map<String, String> options = (Map<String, String>) field.get(app);
		return options;
	}

	private boolean getVerbose() throws Exception {
		Field field = CodeCleanupApplication.class.getDeclaredField("verbose");
		field.setAccessible(true);
		return field.getBoolean(app);
	}

	private boolean getQuiet() throws Exception {
		Field field = CodeCleanupApplication.class.getDeclaredField("quiet");
		field.setAccessible(true);
		return field.getBoolean(app);
	}

	private File createTempConfigFile() throws IOException {
		File configFile = new File(tempDir, "cleanup-config.properties");
		try (java.io.FileOutputStream fos = new java.io.FileOutputStream(configFile)) {
			Properties props = new Properties();
			props.setProperty("cleanup.format_source_code", "true");
			props.setProperty("cleanup.organize_imports", "true");
			props.setProperty("cleanup.remove_unused_imports", "true");
			props.store(fos, "Test cleanup configuration");
		}
		return configFile;
	}

	private File createTempJavaFile(String filename) throws IOException {
		File javaFile = new File(tempDir, filename);
		try (OutputStreamWriter writer = new OutputStreamWriter(
				new FileOutputStream(javaFile), StandardCharsets.UTF_8)) {
			writer.write("public class " + filename.replace(".java", "") + " {\n");
			writer.write("    public static void main(String[] args) {\n");
			writer.write("        System.out.println(\"Hello\");\n");
			writer.write("    }\n");
			writer.write("}\n");
		}
		return javaFile;
	}

	/**
	 * Integration test: Test start() method returns EXIT_OK for -help
	 */
	@Test
	public void testStartWithHelp() throws Exception {
		IApplicationContext context = new MockApplicationContext(new String[] { "-help" });
		Object result = app.start(context);
		
		assertEquals(IApplication.EXIT_OK, result);
		
		String output = outContent.toString();
		assertTrue(output.contains("Usage:"), "Help should be displayed");
	}

	/**
	 * Mock IApplicationContext for testing
	 */
	private static class MockApplicationContext implements IApplicationContext {
		private final String[] args;

		public MockApplicationContext(String[] args) {
			this.args = args;
		}

		@Override
		public Map<String, Object> getArguments() {
			Map<String, Object> arguments = new HashMap<>();
			arguments.put(IApplicationContext.APPLICATION_ARGS, args);
			return arguments;
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
		public void applicationRunning() {
			// No-op
		}

		@Override
		public void setResult(Object result, IApplication application) {
			// No-op
		}
	}
}

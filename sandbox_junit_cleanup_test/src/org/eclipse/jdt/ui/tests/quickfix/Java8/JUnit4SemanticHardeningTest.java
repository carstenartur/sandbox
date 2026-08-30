/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Regression coverage derived from real Eclipse JDT JUnit 4 rule shapes. */
public class JUnit4SemanticHardeningTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void testNameUsesTheJavaMethodNameAndKeepsTheFieldIdentity() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("MethodNameTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.TestName;

				public class MethodNameTest {
					@Rule
					public final TestName tn = new TestName();

					@Test
					public void actualJavaMethodName() {
						System.out.println(tn.getMethodName());
					}
				}
				""", false, null);

		enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME);

		context.assertRefactoringResultAsExpectedNormalizingWhitespace(new ICompilationUnit[] { unit },
				new String[] { """
						package test;
						import org.junit.jupiter.api.BeforeEach;
						import org.junit.jupiter.api.Test;
						import org.junit.jupiter.api.TestInfo;

						public class MethodNameTest {
							public String tn;

							@BeforeEach
							void initializeTnFromTestInfo(TestInfo testInfo) {
								this.tn = testInfo.getTestMethod().orElseThrow().getName();
							}

							@Test
							public void actualJavaMethodName() {
								System.out.println(tn);
							}
						}
						""" }, null);
	}

	@Test
	public void unsupportedTestNameUseLeavesTheCompleteRuleUnchanged() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("UnsupportedTestName.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.TestName;

				public class UnsupportedTestName {
					@Rule
					public TestName name = new TestName();

					@Test
					public void testRuleObjectUse() {
						consume(name);
					}

					private void consume(Object value) {
					}
				}
				""", false, null);

		enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	@Test
	public void expectedExceptionPreservesSubstringMessageSemantics() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("ExpectedMessageTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.ExpectedException;

				public class ExpectedMessageTest {
					@Rule
					public ExpectedException thrown = ExpectedException.none();

					@Test
					public void testSubstring() {
						thrown.expect(IllegalArgumentException.class);
						thrown.expectMessage("needle");
						throw new IllegalArgumentException("prefix needle suffix");
					}
				}
				""", false, null);

		enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXPECTEDEXCEPTION);

		context.assertRefactoringResultAsExpectedNormalizingWhitespace(new ICompilationUnit[] { unit },
				new String[] { """
						package test;
						import static org.junit.jupiter.api.Assertions.assertThrows;
						import static org.junit.jupiter.api.Assertions.assertTrue;

						import org.junit.jupiter.api.Test;

						public class ExpectedMessageTest {
							@Test
							public void testSubstring() {
								IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
									throw new IllegalArgumentException("prefix needle suffix");
								});
								assertTrue(exception.getMessage() != null && exception.getMessage().contains("needle"));
							}
						}
						""" }, null);
	}

	@Test
	public void expectedExceptionMatcherOverloadIsRejectedAtomically() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("ExpectedMatcherTest.java", //$NON-NLS-1$
				"""
				package test;
				import static org.hamcrest.CoreMatchers.containsString;

				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.ExpectedException;

				public class ExpectedMatcherTest {
					@Rule
					public ExpectedException thrown = ExpectedException.none();

					@Test
					public void testMatcher() {
						thrown.expect(IllegalArgumentException.class);
						thrown.expectMessage(containsString("needle"));
						throw new IllegalArgumentException("needle");
					}
				}
				""", false, null);

		enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXPECTEDEXCEPTION);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	@Test
	public void temporaryFolderUsesStrictNioEquivalents() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("TemporaryFolderTest.java", //$NON-NLS-1$
				"""
				package test;
				import java.io.File;
				import java.io.IOException;

				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.TemporaryFolder;

				public class TemporaryFolderTest {
					@Rule
					public final TemporaryFolder folder = new TemporaryFolder();

					@Test
					public void testFiles() throws IOException {
						File randomFile = folder.newFile();
						File namedFile = folder.newFile("named.txt");
						File randomFolder = folder.newFolder();
						File namedFolder = folder.newFolder("named");
						File root = folder.getRoot();
					}
				}
				""", false, null);

		enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER);

		context.assertRefactoringResultAsExpectedNormalizingWhitespace(new ICompilationUnit[] { unit },
				new String[] { """
						package test;
						import java.io.File;
						import java.io.IOException;
						import java.nio.file.Files;
						import java.nio.file.Path;

						import org.junit.jupiter.api.Test;
						import org.junit.jupiter.api.io.TempDir;

						public class TemporaryFolderTest {
							@TempDir
							public Path folder;

							@Test
							public void testFiles() throws IOException {
								File randomFile = Files.createTempFile(folder, "junit", null).toFile();
								File namedFile = Files.createFile(folder.resolve("named.txt")).toFile();
								File randomFolder = Files.createTempDirectory(folder, "junit").toFile();
								File namedFolder = Files.createDirectory(folder.resolve("named")).toFile();
								File root = folder.toFile();
							}
						}
						""" }, null);
	}

	@Test
	public void multiSegmentTemporaryFolderIsRejectedAtomically() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("NestedTemporaryFolderTest.java", //$NON-NLS-1$
				"""
				package test;
				import java.io.IOException;

				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.TemporaryFolder;

				public class NestedTemporaryFolderTest {
					@Rule
					public TemporaryFolder folder = new TemporaryFolder();

					@Test
					public void testNestedFolder() throws IOException {
						folder.newFolder("parent", "child");
					}
				}
				""", false, null);

		enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	@Test
	public void externalResourceHierarchyPreservesCheckedAndSuperLifecycleSemantics() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("JUnitSourceSetup.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.rules.ExternalResource;

				public class JUnitSourceSetup extends ExternalResource {
					@Override
					public void before() throws Throwable {
						checkedOperation();
					}

					@Override
					public void after() {
					}

					private void checkedOperation() throws Exception {
					}
				}
				""", false, null);
		ICompilationUnit derived= pack.createCompilationUnit("LeakTestSetup.java", //$NON-NLS-1$
				"""
				package test;

				public class LeakTestSetup extends JUnitSourceSetup {
					@Override
					public void before() throws Throwable {
						super.before();
					}

					@Override
					public void after() {
						super.after();
					}
				}
				""", false, null);
		ICompilationUnit test= pack.createCompilationUnit("UsingTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Rule;

				public class UsingTest {
					@Rule
					public LeakTestSetup setup = new LeakTestSetup();
				}
				""", false, null);

		enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE);

		context.assertRefactoringResultAsExpectedNormalizingWhitespace(
				new ICompilationUnit[] { base, derived, test }, new String[] {
						"""
						package test;
						import org.junit.jupiter.api.extension.AfterEachCallback;
						import org.junit.jupiter.api.extension.BeforeEachCallback;
						import org.junit.jupiter.api.extension.ExtensionContext;

						public class JUnitSourceSetup implements BeforeEachCallback, AfterEachCallback {
							@Override
							public void beforeEach(ExtensionContext context) throws Exception {
								checkedOperation();
							}

							@Override
							public void afterEach(ExtensionContext context) {
							}

							private void checkedOperation() throws Exception {
							}
						}
						""",
						"""
						package test;

						import org.junit.jupiter.api.extension.ExtensionContext;

						public class LeakTestSetup extends JUnitSourceSetup {
							@Override
							public void beforeEach(ExtensionContext context) throws Exception {
								super.beforeEach(context);
							}

							@Override
							public void afterEach(ExtensionContext context) {
								super.afterEach(context);
							}
						}
						""",
						"""
						package test;
						import org.junit.jupiter.api.extension.RegisterExtension;
						import org.junit.jupiter.api.parallel.Isolated;

						@Isolated
						public class UsingTest {
							@RegisterExtension
							public LeakTestSetup setup = new LeakTestSetup();
						}
						""" }, null);
	}

	@Test
	public void explicitParameterizedRunnerValueIsEquivalent() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("ExplicitParameterizedTest.java", //$NON-NLS-1$
				"""
				package test;
				import java.util.Arrays;
				import java.util.Collection;

				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Parameterized;
				import org.junit.runners.Parameterized.Parameters;

				@RunWith(value = Parameterized.class)
				public class ExplicitParameterizedTest {
					private int value;

					public ExplicitParameterizedTest(int value) {
						this.value = value;
					}

					@Parameters
					public static Collection<Object[]> data() {
						return Arrays.asList(new Object[][] { { 1 }, { 2 } });
					}

					@Test
					public void testValue() {
						System.out.println(value);
					}
				}
				""", false, null);

		enable(MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED);

		context.assertRefactoringResultAsExpectedNormalizingWhitespace(new ICompilationUnit[] { unit },
				new String[] { """
						package test;
						import java.util.stream.Stream;

						import org.junit.jupiter.params.ParameterizedTest;
						import org.junit.jupiter.params.provider.Arguments;
						import org.junit.jupiter.params.provider.MethodSource;

						public class ExplicitParameterizedTest {
							@ParameterizedTest
							@MethodSource("data")
							public void testValue(int value) {
								System.out.println(value);
							}

							static Stream<Arguments> data() {
								return Stream.of(Arguments.of(1), Arguments.of(2));
							}
						}
						""" }, null);
	}

	private void enable(String... options) throws CoreException {
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		for (String option : options) {
			context.enable(option);
		}
	}
}

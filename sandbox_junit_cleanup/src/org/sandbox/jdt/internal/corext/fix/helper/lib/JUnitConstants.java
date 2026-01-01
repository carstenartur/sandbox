/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.jdt.internal.corext.fix.helper.lib;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Central repository for JUnit-related constants used during migration from JUnit 3/4 to JUnit 5.
 * Contains annotation names, method names, fully qualified class references, and assertion method sets.
 */
public final class JUnitConstants {

	// Private constructor to prevent instantiation
	private JUnitConstants() {
		throw new UnsupportedOperationException("Utility class");
	}

	// ===== Annotation Simple Names =====

	public static final String ANNOTATION_REGISTER_EXTENSION = "RegisterExtension";
	public static final String ANNOTATION_EXTEND_WITH = "ExtendWith";
	public static final String ANNOTATION_AFTER_EACH = "AfterEach";
	public static final String ANNOTATION_BEFORE_EACH = "BeforeEach";
	public static final String ANNOTATION_AFTER_ALL = "AfterAll";
	public static final String ANNOTATION_BEFORE_ALL = "BeforeAll";
	public static final String ANNOTATION_DISABLED = "Disabled";
	public static final String ANNOTATION_TEST = "Test";
	public static final String ANNOTATION_TIMEOUT = "Timeout";
	public static final String ANNOTATION_SELECT_CLASSES = "SelectClasses";
	public static final String ANNOTATION_SUITE = "Suite";
	public static final String MOCKITO_EXTENSION = "MockitoExtension";
	public static final String SPRING_EXTENSION = "SpringExtension";

	// ===== Method Names =====

	public static final String METHOD_AFTER_EACH = "afterEach";
	public static final String METHOD_BEFORE_EACH = "beforeEach";
	public static final String METHOD_AFTER_ALL = "afterAll";
	public static final String METHOD_BEFORE_ALL = "beforeAll";
	public static final String METHOD_AFTER = "after";
	public static final String METHOD_BEFORE = "before";

	// ===== Internal Class/Interface Names =====

	public static final String ASSERTIONS = "Assertions";
	public static final String ASSUMPTIONS = "Assumptions";
	public static final String TEST_NAME = "testName";
	public static final String VARIABLE_NAME_CONTEXT = "context";
	public static final String EXTENSION_CONTEXT = "ExtensionContext";

	// ===== JUnit 4 Fully Qualified References =====

	public static final String ORG_JUNIT_AFTER = "org.junit.After";
	public static final String ORG_JUNIT_BEFORE = "org.junit.Before";
	public static final String ORG_JUNIT_AFTERCLASS = "org.junit.AfterClass";
	public static final String ORG_JUNIT_BEFORECLASS = "org.junit.BeforeClass";
	public static final String ORG_JUNIT_RULE = "org.junit.Rule";
	public static final String ORG_JUNIT_CLASS_RULE = "org.junit.ClassRule";
	public static final String ORG_JUNIT_RULES_TEMPORARY_FOLDER = "org.junit.rules.TemporaryFolder";
	public static final String ORG_JUNIT_RULES_TEST_NAME = "org.junit.rules.TestName";
	public static final String ORG_JUNIT_RULES_EXTERNAL_RESOURCE = "org.junit.rules.ExternalResource";
	public static final String ORG_JUNIT_RULES_EXPECTED_EXCEPTION = "org.junit.rules.ExpectedException";
	public static final String ORG_JUNIT_RUNWITH = "org.junit.runner.RunWith";
	public static final String ORG_JUNIT_SUITE = "org.junit.runners.Suite";
	public static final String ORG_JUNIT_SUITE_SUITECLASSES = "org.junit.runners.Suite.SuiteClasses";
	public static final String ORG_JUNIT_TEST = "org.junit.Test";
	public static final String ORG_JUNIT_IGNORE = "org.junit.Ignore";
	public static final String ORG_JUNIT_ASSERT = "org.junit.Assert";
	public static final String ORG_JUNIT_ASSUME = "org.junit.Assume";
	public static final String ORG_JUNIT_FIX_METHOD_ORDER = "org.junit.FixMethodOrder";

	// ===== JUnit 4 Runner Fully Qualified References =====

	public static final String ORG_MOCKITO_JUNIT_MOCKITO_JUNIT_RUNNER = "org.mockito.junit.MockitoJUnitRunner";
	public static final String ORG_MOCKITO_RUNNERS_MOCKITO_JUNIT_RUNNER = "org.mockito.runners.MockitoJUnitRunner";
	public static final String ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT4_SPRING_RUNNER = "org.springframework.test.context.junit4.SpringRunner";
	public static final String ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT4_SPRING_JUNIT4_CLASS_RUNNER = "org.springframework.test.context.junit4.SpringJUnit4ClassRunner";
	public static final String ORG_JUNIT_RUNNERS_PARAMETERIZED = "org.junit.runners.Parameterized";
	public static final String ORG_JUNIT_RUNNERS_PARAMETERIZED_PARAMETERS = "org.junit.runners.Parameterized.Parameters";

	// ===== JUnit 5 / Jupiter Fully Qualified References =====

	public static final String ORG_JUNIT_JUPITER_API_AFTER_EACH = "org.junit.jupiter.api.AfterEach";
	public static final String ORG_JUNIT_JUPITER_API_AFTER_ALL = "org.junit.jupiter.api.AfterAll";
	public static final String ORG_JUNIT_JUPITER_API_BEFORE_ALL = "org.junit.jupiter.api.BeforeAll";
	public static final String ORG_JUNIT_JUPITER_API_BEFORE_EACH = "org.junit.jupiter.api.BeforeEach";
	public static final String ORG_JUNIT_JUPITER_API_ASSERTIONS = "org.junit.jupiter.api.Assertions";
	public static final String ORG_JUNIT_JUPITER_DISABLED = "org.junit.jupiter.api.Disabled";
	public static final String ORG_JUNIT_JUPITER_API_IO_TEMP_DIR = "org.junit.jupiter.api.io.TempDir";
	public static final String ORG_JUNIT_JUPITER_API_TEST_INFO = "org.junit.jupiter.api.TestInfo";
	public static final String ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT = "org.junit.jupiter.api.extension.ExtensionContext";
	public static final String ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION = "org.junit.jupiter.api.extension.RegisterExtension";
	public static final String ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK = "org.junit.jupiter.api.extension.BeforeEachCallback";
	public static final String ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK = "org.junit.jupiter.api.extension.AfterEachCallback";
	public static final String ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_ALL_CALLBACK = "org.junit.jupiter.api.extension.BeforeAllCallback";
	public static final String ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_ALL_CALLBACK = "org.junit.jupiter.api.extension.AfterAllCallback";
	public static final String ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";
	public static final String ORG_JUNIT_JUPITER_TEST = "org.junit.jupiter.api.Test";
	public static final String ORG_JUNIT_JUPITER_API_TIMEOUT = "org.junit.jupiter.api.Timeout";
	public static final String ORG_JUNIT_JUPITER_API_ASSUMPTIONS = "org.junit.jupiter.api.Assumptions";
	
	// ===== JUnit 5 Parameterized Fully Qualified References =====
	
	public static final String ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST = "org.junit.jupiter.params.ParameterizedTest";
	public static final String ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE = "org.junit.jupiter.params.provider.MethodSource";
	public static final String ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS = "org.junit.jupiter.params.provider.Arguments";
	public static final String ANNOTATION_PARAMETERIZED_TEST = "ParameterizedTest";
	public static final String ANNOTATION_METHOD_SOURCE = "MethodSource";

	// ===== JUnit 5 Extension Fully Qualified References =====

	public static final String ORG_MOCKITO_JUNIT_JUPITER_MOCKITO_EXTENSION = "org.mockito.junit.jupiter.MockitoExtension";
	public static final String ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT_JUPITER_SPRING_EXTENSION = "org.springframework.test.context.junit.jupiter.SpringExtension";

	// ===== JUnit Platform Fully Qualified References =====

	public static final String ORG_JUNIT_JUPITER_SUITE = "org.junit.platform.suite.api.Suite";
	public static final String ORG_JUNIT_PLATFORM_SUITE_API_SELECT_CLASSES = "org.junit.platform.suite.api.SelectClasses";

	// ===== Callback Names =====

	public static final String AFTER_ALL_CALLBACK = "AfterAllCallback";
	public static final String BEFORE_ALL_CALLBACK = "BeforeAllCallback";
	public static final String AFTER_EACH_CALLBACK = "AfterEachCallback";
	public static final String BEFORE_EACH_CALLBACK = "BeforeEachCallback";

	// ===== Assertion Method Sets =====

	public static final Set<String> TWOPARAM_ASSERTIONS = Set.of("assertEquals", "assertNotEquals", "assertArrayEquals",
			"assertSame", "assertNotSame", "assertThat");
	public static final Set<String> ONEPARAM_ASSERTIONS = Set.of("assertTrue", "assertFalse", "assertNull", "assertNotNull");
	public static final Set<String> NOPARAM_ASSERTIONS = Set.of("fail");
	public static final Set<String> ALL_ASSERTION_METHODS = Stream
			.of(TWOPARAM_ASSERTIONS, ONEPARAM_ASSERTIONS, NOPARAM_ASSERTIONS)
			.flatMap(Set::stream)
			.collect(Collectors.toSet());
}

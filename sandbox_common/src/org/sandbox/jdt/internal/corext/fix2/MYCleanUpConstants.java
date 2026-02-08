package org.sandbox.jdt.internal.corext.fix2;

/*-
 * #%L
 * Sandbox common
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */


/**
 * @author chammer
 *
 */
public class MYCleanUpConstants {

	/**
	 *
	 */
	public static final String EXPLICITENCODING_CLEANUP= "cleanup.explicit_encoding"; //$NON-NLS-1$

	/**
	 * Don't change behavior - just replace or insert to make use of platform encoding visible in the code.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 *
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 *
	 */
	public static final String EXPLICITENCODING_KEEP_BEHAVIOR= "cleanup.explicit_encoding_keep_behavior"; //$NON-NLS-1$

	/**
	 * Set all uses of platform encoding explicitly to UTF-8 - This changes behavior of the resulting code!
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 *
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 *
	 */
	public static final String EXPLICITENCODING_INSERT_UTF8= "cleanup.explicit_encoding_insert_utf8"; //$NON-NLS-1$

	/**
	 * Set all uses of platform encoding explicitly to UTF-8 - This changes behavior of the resulting code!
	 * At the same time try to have a single constant per project for this encoding that is referenced whenever
	 * code is changed to use this charset. That way later it is easy to change the default.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 *
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 *
	 */
	public static final String EXPLICITENCODING_AGGREGATE_TO_UTF8= "cleanup.explicit_encoding_aggregate_to_utf8"; //$NON-NLS-1$

	/**
	 *
	 */
	public static final String SIMPLIFY_STATUS_CLEANUP= "cleanup.simplify_status_creation"; //$NON-NLS-1$

	/**
	 *
	 */
	public static final String XML_CLEANUP= "cleanup.xmlcleanup"; //$NON-NLS-1$
	
	/**
	 * Enable indentation for XML cleanup. When disabled (default), indent="no" is used
	 * to minimize file size. When enabled, minimal indentation is applied.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 *
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 */
	public static final String XML_CLEANUP_INDENT= "cleanup.xmlcleanup_indent"; //$NON-NLS-1$
	
	/**
	 *
	 */
	public static final String JUNIT_CLEANUP= "cleanup.junitcleanup"; //$NON-NLS-1$
	/**
	 *
	 */
	public static final String JUNIT3_CLEANUP= "cleanup.junit3cleanup"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_ASSERT= "cleanup.junitcleanup_4_assert"; //$NON-NLS-1$
	/**
	 * Optimization of assertTrue/assertFalse to more specific assertions
	 */
	public static final String JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION= "cleanup.junitcleanup_4_assert_optimization"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_ASSUME= "cleanup.junitcleanup_4_assume"; //$NON-NLS-1$
	/**
	 * Optimization of assumeTrue/assumeFalse to more specific assumptions
	 */
	public static final String JUNIT_CLEANUP_4_ASSUME_OPTIMIZATION= "cleanup.junitcleanup_4_assume_optimization"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_SUITE= "cleanup.junitcleanup_4_suite"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_BEFORE= "cleanup.junitcleanup_4_before"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_AFTER= "cleanup.junitcleanup_4_after"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_BEFORECLASS= "cleanup.junitcleanup_4_beforeclass"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_AFTERCLASS= "cleanup.junitcleanup_4_afterclass"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_IGNORE= "cleanup.junitcleanup_4_ignore"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_CATEGORY= "cleanup.junitcleanup_4_category"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_FIX_METHOD_ORDER= "cleanup.junitcleanup_4_fix_method_order"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_TEST= "cleanup.junitcleanup_4_test"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_3_TEST= "cleanup.junitcleanup_3_test"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_RULETEMPORARYFOLDER= "cleanup.junitcleanup_4_ruletemporaryfolder"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_RULETESTNAME= "cleanup.junitcleanup_4_ruletestname"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_EXTERNALRESOURCE= "cleanup.junitcleanup_4_externalresource"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE= "cleanup.junitcleanup_4_ruleexternalresource"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_RUNWITH= "cleanup.junitcleanup_4_runwith"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_TEST_TIMEOUT= "cleanup.junitcleanup_4_test_timeout"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_TEST_EXPECTED= "cleanup.junitcleanup_4_test_expected"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_PARAMETERIZED= "cleanup.junitcleanup_4_parameterized"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_RULETIMEOUT= "cleanup.junitcleanup_4_ruletimeout"; //$NON-NLS-1$
	/**
	 * Migrate JUnit 4 @Rule ErrorCollector to JUnit 5 assertAll() pattern
	 */
	public static final String JUNIT_CLEANUP_4_RULEERRORCOLLECTOR= "cleanup.junitcleanup_4_ruleerrorcollector"; //$NON-NLS-1$
	/**
	 * Find and fix "lost" JUnit 3 tests that were not properly migrated
	 * (methods starting with "test" but missing @Test annotation)
	 */
	public static final String JUNIT_CLEANUP_4_LOST_TESTS= "cleanup.junitcleanup_4_lost_tests"; //$NON-NLS-1$
	/**
	 * Migrate JUnit 4 ThrowingRunnable to JUnit 5 Executable.
	 * <p>
	 * Transforms:
	 * <ul>
	 * <li>org.junit.function.ThrowingRunnable → org.junit.jupiter.api.function.Executable</li>
	 * <li>ThrowingRunnable.run() → Executable.execute()</li>
	 * </ul>
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 * @see <a href="https://github.com/eclipse-platform/eclipse.platform/issues/903">Eclipse Platform Issue #903</a>
	 */
	public static final String JUNIT_CLEANUP_4_THROWINGRUNNABLE= "cleanup.junitcleanup_4_throwingrunnable"; //$NON-NLS-1$
	/**
	 *
	 */
	public static final String JFACE_CLEANUP= "cleanup.jfacecleanup"; //$NON-NLS-1$

	/**
	 *
	 */
	public static final String USEFUNCTIONALLOOP_CLEANUP= "cleanup.functionalloop"; //$NON-NLS-1$

	/**
	 * NEU: V2 Konstante für parallele Implementierung (ULR-basiert)
	 * 
	 * @see <a href="https://github.com/carstenartur/sandbox/issues/450">Issue #450</a>
	 */
	public static final String USEFUNCTIONALLOOP_CLEANUP_V2= "cleanup.use_functional_loop_v2"; //$NON-NLS-1$

	/**
	 * Target format for loop conversions: Stream (default)
	 */
	public static final String USEFUNCTIONALLOOP_FORMAT_STREAM= "cleanup.functionalloop.format.stream"; //$NON-NLS-1$

	/**
	 * Target format for loop conversions: For-loop
	 */
	public static final String USEFUNCTIONALLOOP_FORMAT_FOR= "cleanup.functionalloop.format.for"; //$NON-NLS-1$

	/**
	 * Target format for loop conversions: While-loop
	 */
	public static final String USEFUNCTIONALLOOP_FORMAT_WHILE= "cleanup.functionalloop.format.while"; //$NON-NLS-1$

	// Bidirectional Loop Conversion Constants (Phase 9)
	
	/**
	 * Master switch for bidirectional loop conversions.
	 * When enabled, allows converting between different loop representations
	 * (Stream, Enhanced-for, Iterator-while) based on target format and source selections.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 */
	public static final String LOOP_CONVERSION_ENABLED = "cleanup.loop_conversion.enabled"; //$NON-NLS-1$

	/**
	 * Target format for bidirectional loop conversions.
	 * Specifies the desired output format for all enabled source formats.
	 * <p>
	 * Possible values: "stream", "enhanced_for", "iterator_while"
	 * <p>
	 * Default: "stream"
	 */
	public static final String LOOP_CONVERSION_TARGET_FORMAT = "cleanup.loop_conversion.target_format"; //$NON-NLS-1$

	/**
	 * Enable conversion FROM enhanced for-loops.
	 * When enabled, enhanced for-loops will be converted to the target format.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 */
	public static final String LOOP_CONVERSION_FROM_ENHANCED_FOR = "cleanup.loop_conversion.from.enhanced_for"; //$NON-NLS-1$

	/**
	 * Enable conversion FROM iterator while-loops.
	 * When enabled, iterator-based while-loops will be converted to the target format.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 */
	public static final String LOOP_CONVERSION_FROM_ITERATOR_WHILE = "cleanup.loop_conversion.from.iterator_while"; //$NON-NLS-1$

	/**
	 * Enable conversion FROM stream expressions.
	 * When enabled, stream forEach expressions will be converted to the target format.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 */
	public static final String LOOP_CONVERSION_FROM_STREAM = "cleanup.loop_conversion.from.stream"; //$NON-NLS-1$

	/**
	 * Enable conversion FROM classic index-based for-loops (experimental).
	 * When enabled, classic for-loops will be converted to the target format.
	 * <p>
	 * Note: This is an experimental feature for future implementation.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 */
	public static final String LOOP_CONVERSION_FROM_CLASSIC_FOR = "cleanup.loop_conversion.from.classic_for"; //$NON-NLS-1$

	/**
	 *
	 */
	public static final String METHOD_REUSE_CLEANUP= "cleanup.method_reuse"; //$NON-NLS-1$

	/**
	 * Enable detection of inline code sequences that can be replaced with method calls.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 *
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 */
	public static final String METHOD_REUSE_INLINE_SEQUENCES= "cleanup.method_reuse_inline_sequences"; //$NON-NLS-1$

	/**
	 * Enable string simplification cleanup using TriggerPattern hints.
	 * <p>
	 * Simplifies patterns like {@code "" + x} and {@code x + ""} to {@code String.valueOf(x)}.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 *
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 * @since 1.2.2
	 */
	public static final String TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP= "cleanup.string_simplification"; //$NON-NLS-1$

	/**
	 * Convert integer constants used in if-else chains to enum with switch statement.
	 * Improves type safety and code maintainability.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 *
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 */
	public static final String INT_TO_ENUM_CLEANUP= "cleanup.int_to_enum"; //$NON-NLS-1$
}

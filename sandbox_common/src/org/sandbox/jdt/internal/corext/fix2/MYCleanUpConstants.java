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
	 * Migrate JUnit 4 @Rule ExpectedException to JUnit 5 assertThrows()
	 */
	public static final String JUNIT_CLEANUP_4_RULEEXPECTEDEXCEPTION= "cleanup.junitcleanup_4_ruleexpectedexception"; //$NON-NLS-1$
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
	 * Enable threading cleanup using TriggerPattern hints.
	 * <p>
	 * Currently detects threading anti-patterns where {@code Thread.run()} is called
	 * directly instead of {@code Thread.start()}.
	 * <p>
	 * Inspired by NetBeans' threading hints (Tiny.java).
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 *
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 * @since 1.2.5
	 */
	public static final String TRIGGERPATTERN_THREADING_CLEANUP= "cleanup.threading"; //$NON-NLS-1$

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

	/**
	 * Replace out-of-range shift amounts with the effective masked value.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 *
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 * @since 1.2.5
	 */
	public static final String SHIFT_OUT_OF_RANGE_CLEANUP= "cleanup.shift_out_of_range"; //$NON-NLS-1$

	/**
	 * Widen variable declaration types to more general supertypes/interfaces based on usage.
	 * Analyzes variable usage and changes the declared type to the most general type
	 * (highest in hierarchy) that still declares all used methods/fields.
	 * Only affects local variables, not fields or method parameters.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 *
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 * @since 1.2.6
	 */
	public static final String USE_GENERAL_TYPE_CLEANUP= "cleanup.use_general_type"; //$NON-NLS-1$

	/**
	 * Apply transformation rules from registered {@code .sandbox-hint} DSL files.
	 * <p>
	 * When enabled, all registered {@code .sandbox-hint} pattern libraries (collections,
	 * modernize-java11, performance, etc.) are applied as cleanup operations. This enables
	 * declarative code transformations without writing Java code.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 *
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 * @since 1.3.5
	 */
	public static final String HINTFILE_CLEANUP= "cleanup.hintfile"; //$NON-NLS-1$

	/**
	 * Enable the {@code collections.sandbox-hint} pattern library.
	 * <p>
	 * When enabled, Collection API improvement rules are applied
	 * (e.g., {@code list.size() == 0} → {@code list.isEmpty()}).
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.6
	 */
	public static final String HINTFILE_BUNDLE_COLLECTIONS= "cleanup.hintfile.bundle.collections"; //$NON-NLS-1$

	/**
	 * Enable the {@code performance.sandbox-hint} pattern library.
	 * <p>
	 * When enabled, performance optimization rules are applied.
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.6
	 */
	public static final String HINTFILE_BUNDLE_PERFORMANCE= "cleanup.hintfile.bundle.performance"; //$NON-NLS-1$

	/**
	 * Enable the {@code modernize-java9.sandbox-hint} pattern library.
	 * <p>
	 * When enabled, Java 9+ API modernization rules are applied.
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.6
	 */
	public static final String HINTFILE_BUNDLE_MODERNIZE_JAVA9= "cleanup.hintfile.bundle.modernize-java9"; //$NON-NLS-1$

	/**
	 * Enable the {@code modernize-java11.sandbox-hint} pattern library.
	 * <p>
	 * When enabled, Java 11+ API modernization rules are applied.
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.6
	 */
	public static final String HINTFILE_BUNDLE_MODERNIZE_JAVA11= "cleanup.hintfile.bundle.modernize-java11"; //$NON-NLS-1$

	/**
	 * Bundle ID for the collections hint file, matching the registry key
	 * derived from {@code collections.sandbox-hint}.
	 * @since 1.3.6
	 */
	public static final String HINTFILE_BUNDLE_ID_COLLECTIONS= "collections"; //$NON-NLS-1$

	/**
	 * Bundle ID for the performance hint file, matching the registry key
	 * derived from {@code performance.sandbox-hint}.
	 * @since 1.3.6
	 */
	public static final String HINTFILE_BUNDLE_ID_PERFORMANCE= "performance"; //$NON-NLS-1$

	/**
	 * Bundle ID for the modernize-java9 hint file, matching the registry key
	 * derived from {@code modernize-java9.sandbox-hint}.
	 * @since 1.3.6
	 */
	public static final String HINTFILE_BUNDLE_ID_MODERNIZE_JAVA9= "modernize-java9"; //$NON-NLS-1$

	/**
	 * Bundle ID for the modernize-java11 hint file, matching the registry key
	 * derived from {@code modernize-java11.sandbox-hint}.
	 * @since 1.3.6
	 */
	public static final String HINTFILE_BUNDLE_ID_MODERNIZE_JAVA11= "modernize-java11"; //$NON-NLS-1$

	/**
	 * Enables or disables the encoding hint file bundle.
	 * <p>
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * Controls whether StandardCharsets migration rules are applied.
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.7
	 */
	public static final String HINTFILE_BUNDLE_ENCODING= "cleanup.hintfile.bundle.encoding"; //$NON-NLS-1$

	/**
	 * Enables or disables the junit5 hint file bundle.
	 * <p>
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * Controls whether JUnit 4 to JUnit 5 migration rules are applied.
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.7
	 */
	public static final String HINTFILE_BUNDLE_JUNIT5= "cleanup.hintfile.bundle.junit5"; //$NON-NLS-1$

	/**
	 * Bundle ID for the encoding hint file, matching the registry key
	 * derived from {@code encoding.sandbox-hint}.
	 * @since 1.3.7
	 */
	public static final String HINTFILE_BUNDLE_ID_ENCODING= "encoding"; //$NON-NLS-1$

	/**
	 * Bundle ID for the junit5 hint file, matching the registry key
	 * derived from {@code junit5.sandbox-hint}.
	 * @since 1.3.7
	 */
	public static final String HINTFILE_BUNDLE_ID_JUNIT5= "junit5"; //$NON-NLS-1$

	/**
	 * Enable the {@code stream-performance.sandbox-hint} pattern library.
	 * <p>
	 * When enabled, Stream API performance optimization rules are applied.
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_STREAM_PERFORMANCE= "cleanup.hintfile.bundle.stream-performance"; //$NON-NLS-1$

	/**
	 * Bundle ID for the stream-performance hint file.
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_ID_STREAM_PERFORMANCE= "stream-performance"; //$NON-NLS-1$

	/**
	 * Enable the {@code io-performance.sandbox-hint} pattern library.
	 * <p>
	 * When enabled, I/O stream performance rules are applied (e.g., double-buffering detection).
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_IO_PERFORMANCE= "cleanup.hintfile.bundle.io-performance"; //$NON-NLS-1$

	/**
	 * Bundle ID for the io-performance hint file.
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_ID_IO_PERFORMANCE= "io-performance"; //$NON-NLS-1$

	/**
	 * Enable the {@code collection-performance.sandbox-hint} pattern library.
	 * <p>
	 * When enabled, collection performance optimization rules are applied.
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_COLLECTION_PERFORMANCE= "cleanup.hintfile.bundle.collection-performance"; //$NON-NLS-1$

	/**
	 * Bundle ID for the collection-performance hint file.
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_ID_COLLECTION_PERFORMANCE= "collection-performance"; //$NON-NLS-1$

	/**
	 * Enable the {@code number-compare.sandbox-hint} pattern library.
	 * <p>
	 * When enabled, Number.compare() optimization rules are applied.
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_NUMBER_COMPARE= "cleanup.hintfile.bundle.number-compare"; //$NON-NLS-1$

	/**
	 * Bundle ID for the number-compare hint file.
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_ID_NUMBER_COMPARE= "number-compare"; //$NON-NLS-1$

	/**
	 * Enable the {@code string-equals.sandbox-hint} pattern library.
	 * <p>
	 * When enabled, String equality anti-pattern detection rules are applied.
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_STRING_EQUALS= "cleanup.hintfile.bundle.string-equals"; //$NON-NLS-1$

	/**
	 * Bundle ID for the string-equals hint file.
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_ID_STRING_EQUALS= "string-equals"; //$NON-NLS-1$

	/**
	 * Enable the {@code string-isblank.sandbox-hint} pattern library.
	 * <p>
	 * When enabled, String.isBlank() modernization rules are applied (Java 11+).
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_STRING_ISBLANK= "cleanup.hintfile.bundle.string-isblank"; //$NON-NLS-1$

	/**
	 * Bundle ID for the string-isblank hint file.
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_ID_STRING_ISBLANK= "string-isblank"; //$NON-NLS-1$

	/**
	 * Enable the {@code arrays.sandbox-hint} pattern library.
	 * <p>
	 * When enabled, array utility modernization rules are applied.
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_ARRAYS= "cleanup.hintfile.bundle.arrays"; //$NON-NLS-1$

	/**
	 * Bundle ID for the arrays hint file.
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_ID_ARRAYS= "arrays"; //$NON-NLS-1$

	/**
	 * Enable the {@code collection-toarray.sandbox-hint} pattern library.
	 * <p>
	 * When enabled, Collection.toArray() modernization rules are applied.
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_COLLECTION_TOARRAY= "cleanup.hintfile.bundle.collection-toarray"; //$NON-NLS-1$

	/**
	 * Bundle ID for the collection-toarray hint file.
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_ID_COLLECTION_TOARRAY= "collection-toarray"; //$NON-NLS-1$

	/**
	 * Enable the {@code probable-bugs.sandbox-hint} pattern library.
	 * <p>
	 * When enabled, probable bug detection rules are applied.
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_PROBABLE_BUGS= "cleanup.hintfile.bundle.probable-bugs"; //$NON-NLS-1$

	/**
	 * Bundle ID for the probable-bugs hint file.
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_ID_PROBABLE_BUGS= "probable-bugs"; //$NON-NLS-1$

	/**
	 * Enable the {@code misc.sandbox-hint} pattern library.
	 * <p>
	 * When enabled, miscellaneous code improvement rules are applied.
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_MISC= "cleanup.hintfile.bundle.misc"; //$NON-NLS-1$

	/**
	 * Bundle ID for the misc hint file.
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_ID_MISC= "misc"; //$NON-NLS-1$

	/**
	 * Enable the {@code deprecations.sandbox-hint} pattern library.
	 * <p>
	 * When enabled, deprecated API replacement rules are applied.
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_DEPRECATIONS= "cleanup.hintfile.bundle.deprecations"; //$NON-NLS-1$

	/**
	 * Bundle ID for the deprecations hint file.
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_ID_DEPRECATIONS= "deprecations"; //$NON-NLS-1$

	/**
	 * Enable the {@code classfile-api.sandbox-hint} pattern library.
	 * <p>
	 * When enabled, Java ClassFile API and reflection modernization rules are applied.
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_CLASSFILE_API= "cleanup.hintfile.bundle.classfile-api"; //$NON-NLS-1$

	/**
	 * Bundle ID for the classfile-api hint file.
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_ID_CLASSFILE_API= "classfile-api"; //$NON-NLS-1$

	/**
	 * Enable the {@code serialization.sandbox-hint} pattern library.
	 * <p>
	 * When enabled, serialization best practice rules are applied.
	 * Only effective when {@link #HINTFILE_CLEANUP} is also enabled.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @see #HINTFILE_CLEANUP
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_SERIALIZATION= "cleanup.hintfile.bundle.serialization"; //$NON-NLS-1$

	/**
	 * Bundle ID for the serialization hint file.
	 * @since 1.3.8
	 */
	public static final String HINTFILE_BUNDLE_ID_SERIALIZATION= "serialization"; //$NON-NLS-1$

	// --- Phase 3: Java-coded Cleanups for Complex Analysis ---

	/**
	 * Detect wrong string comparison using {@code ==} or {@code !=} with string literals.
	 * <p>
	 * Replaces {@code str == "literal"} with {@code "literal".equals(str)} and
	 * {@code str != "literal"} with {@code !"literal".equals(str)}.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @since 1.3.9
	 */
	public static final String WRONG_STRING_COMPARISON_CLEANUP= "cleanup.wrong_string_comparison"; //$NON-NLS-1$

	/**
	 * Detect {@code ex.printStackTrace()} calls and suggest using a logger.
	 * <p>
	 * This is a hint-only cleanup that marks {@code printStackTrace()} calls
	 * but does not automatically replace them (the logger type varies per project).
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @since 1.3.9
	 */
	public static final String PRINT_STACKTRACE_CLEANUP= "cleanup.print_stacktrace"; //$NON-NLS-1$

	/**
	 * Detect {@code System.out.println()} and {@code System.err.println()} calls
	 * and suggest using a logger.
	 * <p>
	 * This is a hint-only cleanup that marks System.out/err usage
	 * but does not automatically replace them.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @since 1.3.9
	 */
	public static final String SYSTEM_OUT_CLEANUP= "cleanup.system_out"; //$NON-NLS-1$

	/**
	 * Warn on usage of obsolete collection types ({@code Vector}, {@code Hashtable}, {@code Stack}).
	 * <p>
	 * These legacy synchronized collections should typically be replaced with
	 * {@code ArrayList}, {@code HashMap}, and {@code ArrayDeque} respectively.
	 * This is a hint-only cleanup.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @since 1.3.9
	 */
	public static final String OBSOLETE_COLLECTION_CLEANUP= "cleanup.obsolete_collection"; //$NON-NLS-1$

	/**
	 * Detect classes that override {@code equals()} without overriding {@code hashCode()}.
	 * <p>
	 * This violates the general contract of {@code Object.hashCode()} and can
	 * cause issues when objects are used in hash-based collections.
	 * This is a hint-only cleanup.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @since 1.3.9
	 */
	public static final String MISSING_HASHCODE_CLEANUP= "cleanup.missing_hashcode"; //$NON-NLS-1$

	/**
	 * Warn on overridable method calls in constructors.
	 * <p>
	 * Calling non-final, non-private, non-static methods from a constructor
	 * is dangerous because the subclass may not be fully initialized yet.
	 * This is a hint-only cleanup.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 *
	 * @since 1.3.9
	 */
	public static final String OVERRIDABLE_IN_CONSTRUCTOR_CLEANUP= "cleanup.overridable_in_constructor"; //$NON-NLS-1$
}

/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

/** Explicit test/QA profiles; never changes workspace or project preferences. */
public final class StrictCompilerOptions {
    private static final List<String> WARNINGS = List.of(
            JavaCore.COMPILER_PB_UNNECESSARY_TYPE_CHECK,
            JavaCore.COMPILER_PB_AUTOBOXING,
            JavaCore.COMPILER_PB_UNCHECKED_TYPE_OPERATION,
            JavaCore.COMPILER_PB_RAW_TYPE_REFERENCE,
            JavaCore.COMPILER_PB_REDUNDANT_TYPE_ARGUMENTS,
            JavaCore.COMPILER_PB_UNUSED_TYPE_ARGUMENTS_FOR_METHOD_INVOCATION,
            JavaCore.COMPILER_PB_DEPRECATION,
            JavaCore.COMPILER_PB_TERMINAL_DEPRECATION,
            JavaCore.COMPILER_PB_STATIC_ACCESS_RECEIVER,
            JavaCore.COMPILER_PB_INDIRECT_STATIC_ACCESS,
            JavaCore.COMPILER_PB_LOCAL_VARIABLE_HIDING,
            JavaCore.COMPILER_PB_FIELD_HIDING,
            JavaCore.COMPILER_PB_TYPE_PARAMETER_HIDING,
            JavaCore.COMPILER_PB_UNUSED_LOCAL,
            JavaCore.COMPILER_PB_UNUSED_PRIVATE_MEMBER,
            JavaCore.COMPILER_PB_UNUSED_IMPORT,
            JavaCore.COMPILER_PB_UNUSED_TYPE_PARAMETER,
            JavaCore.COMPILER_PB_UNUSED_LABEL,
            JavaCore.COMPILER_PB_UNUSED_OBJECT_ALLOCATION,
            JavaCore.COMPILER_PB_NO_EFFECT_ASSIGNMENT,
            JavaCore.COMPILER_PB_DEAD_CODE,
            JavaCore.COMPILER_PB_EMPTY_STATEMENT,
            JavaCore.COMPILER_PB_POSSIBLE_ACCIDENTAL_BOOLEAN_ASSIGNMENT,
            JavaCore.COMPILER_PB_HIDDEN_CATCH_BLOCK,
            JavaCore.COMPILER_PB_FINALLY_BLOCK_NOT_COMPLETING,
            JavaCore.COMPILER_PB_FALLTHROUGH_CASE,
            JavaCore.COMPILER_PB_INCOMPLETE_ENUM_SWITCH,
            JavaCore.COMPILER_PB_SWITCH_MISSING_DEFAULT_CASE,
            JavaCore.COMPILER_PB_NULL_REFERENCE,
            JavaCore.COMPILER_PB_POTENTIAL_NULL_REFERENCE,
            JavaCore.COMPILER_PB_REDUNDANT_NULL_CHECK,
            JavaCore.COMPILER_PB_NULL_SPECIFICATION_VIOLATION,
            JavaCore.COMPILER_PB_NULL_ANNOTATION_INFERENCE_CONFLICT,
            JavaCore.COMPILER_PB_NULL_UNCHECKED_CONVERSION,
            JavaCore.COMPILER_PB_REDUNDANT_NULL_ANNOTATION,
            JavaCore.COMPILER_PB_NONNULL_PARAMETER_ANNOTATION_DROPPED,
            JavaCore.COMPILER_PB_PESSIMISTIC_NULL_ANALYSIS_FOR_FREE_TYPE_VARIABLES,
            JavaCore.COMPILER_PB_UNCLOSED_CLOSEABLE,
            JavaCore.COMPILER_PB_POTENTIALLY_UNCLOSED_CLOSEABLE,
            JavaCore.COMPILER_PB_EXPLICITLY_CLOSED_AUTOCLOSEABLE,
            JavaCore.COMPILER_PB_RECOMMENDED_RESOURCE_MANAGEMENT,
            JavaCore.COMPILER_PB_INCOMPATIBLE_OWNING_CONTRACT,
            JavaCore.COMPILER_PB_UNLIKELY_COLLECTION_METHOD_ARGUMENT_TYPE,
            JavaCore.COMPILER_PB_UNLIKELY_EQUALS_ARGUMENT_TYPE,
            JavaCore.COMPILER_PB_CHAR_ARRAY_IN_STRING_CONCATENATION);

    private StrictCompilerOptions() { }

    /** A fresh common profile for complete input and generated compilation units. */
    public static Map<String, String> options(int targetJava) {
        var options = strengthen(Map.of());
        JavaCore.setComplianceOptions(targetJava == 8 ? JavaCore.VERSION_1_8 : Integer.toString(targetJava), options);
        return options;
    }

    /** Includes contextual diagnostics such as NLS, unused parameters and Javadoc. */
    public static Map<String, String> allWarnings(int targetJava) {
        var options = options(targetJava);
        options.replaceAll((key, value) -> key.startsWith(JavaCore.PLUGIN_ID + ".compiler.") //$NON-NLS-1$
                && (JavaCore.IGNORE.equals(value) || JavaCore.INFO.equals(value)) ? JavaCore.WARNING : value);
        for (String key : List.of(JavaCore.COMPILER_DOC_COMMENT_SUPPORT,
                JavaCore.COMPILER_PB_UNUSED_PARAMETER_WHEN_IMPLEMENTING_ABSTRACT,
                JavaCore.COMPILER_PB_UNUSED_PARAMETER_WHEN_OVERRIDING_CONCRETE,
                JavaCore.COMPILER_PB_UNUSED_DECLARED_THROWN_EXCEPTION_WHEN_OVERRIDING,
                JavaCore.COMPILER_PB_DEPRECATION_IN_DEPRECATED_CODE,
                JavaCore.COMPILER_PB_DEPRECATION_WHEN_OVERRIDING_DEPRECATED_METHOD,
                JavaCore.COMPILER_PB_DEAD_CODE_IN_TRIVIAL_IF_STATEMENT,
                JavaCore.COMPILER_PB_SYNTACTIC_NULL_ANALYSIS_FOR_FIELDS)) {
            options.put(key, JavaCore.ENABLED);
        }
        return options;
    }

    /** Returns a fresh profile without lowering inherited or explicitly supplied errors. */
    public static Map<String, String> strengthen(Map<String, String> supplied) {
        // Outside the Eclipse preferences runtime JavaCore exposes only a few defaults.
        var defaults = new HashMap<>(new CompilerOptions().getMap());
        JavaCore.getDefaultOptions().forEach((key, value) -> {
            if (!JavaCore.ERROR.equals(defaults.get(key))) defaults.put(key, value);
        });
        var options = new HashMap<>(defaults);
        options.putAll(supplied);
        defaults.forEach((key, value) -> {
            if (JavaCore.ERROR.equals(value)) options.put(key, value);
        });
        for (String key : WARNINGS) {
            if (!JavaCore.ERROR.equals(options.get(key))) options.put(key, JavaCore.WARNING);
        }
        // These analyses have switches independent of their individual diagnostic severities.
        options.put(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS, JavaCore.ENABLED);
        options.put(JavaCore.COMPILER_ANNOTATION_RESOURCE_ANALYSIS, JavaCore.ENABLED);
        return options;
    }
}

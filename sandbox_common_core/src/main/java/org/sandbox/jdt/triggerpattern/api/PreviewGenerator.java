/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Generates before/after preview text from source and replacement patterns.
 * 
 * <p>Automatically substitutes placeholders with example values to produce
 * human-readable preview text. Eliminates the need for manually writing
 * preview code for each transformation rule.</p>
 * 
 * <h2>Example</h2>
 * <pre>
 * Source pattern:  "new String($bytes, \"UTF-8\")"
 * Replacement:    "new String($bytes, java.nio.charset.StandardCharsets.UTF_8)"
 * 
 * Generated preview:
 *   Before: new String(bytes, "UTF-8")
 *   After:  new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
 * </pre>
 * 
 * @since 1.3.2
 */
public final class PreviewGenerator {
	
	/**
	 * Default example values for placeholders by naming convention.
	 */
	private static final Map<String, String> DEFAULT_EXAMPLES = new HashMap<>();
	
	static {
		DEFAULT_EXAMPLES.put("$x", "x"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$y", "y"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$z", "z"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$a", "a"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$b", "b"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$var", "value"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$obj", "obj"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$name", "name"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$type", "MyType"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$T", "String"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$path", "path"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$bytes", "bytes"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$enc", "\"UTF-8\""); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$encoding", "\"UTF-8\""); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$charset", "charset"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$src", "source"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$msg", "message"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$message", "message"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$expected", "expected"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$actual", "actual"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$cond", "condition"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$result", "result"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$list", "list"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$map", "map"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$key", "key"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$value", "value"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$stream", "stream"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$length", "length"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$len", "len"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$size", "size"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$copy", "copy"); //$NON-NLS-1$ //$NON-NLS-2$
		DEFAULT_EXAMPLES.put("$srcPos", "0"); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	private PreviewGenerator() {
		// Utility class
	}
	
	/**
	 * Generates a preview for a transformation rule.
	 * 
	 * @param rule the transformation rule
	 * @return the generated preview
	 */
	public static Preview generatePreview(TransformationRule rule) {
		String sourceText = rule.sourcePattern().getValue();
		
		// Collect all placeholders from the source pattern
		Map<String, String> examples = collectPlaceholderExamples(sourceText);
		
		String before = substitutePlaceholders(sourceText, examples);
		
		String after = null;
		if (!rule.isHintOnly() && !rule.alternatives().isEmpty()) {
			String firstReplacement = rule.alternatives().get(0).replacementPattern();
			after = substitutePlaceholders(firstReplacement, examples);
		}
		
		return new Preview(before, after, rule.getDescription());
	}
	
	/**
	 * Generates a preview from a source pattern and replacement pattern.
	 * 
	 * @param sourcePattern the source pattern text
	 * @param replacementPattern the replacement pattern text (may be {@code null} for hint-only)
	 * @return the generated preview
	 */
	public static Preview generatePreview(String sourcePattern, String replacementPattern) {
		Map<String, String> examples = collectPlaceholderExamples(sourcePattern);
		
		String before = substitutePlaceholders(sourcePattern, examples);
		String after = replacementPattern != null 
				? substitutePlaceholders(replacementPattern, examples) 
				: null;
		
		return new Preview(before, after, null);
	}
	
	/**
	 * Collects placeholder names from a pattern and assigns example values.
	 * 
	 * @param patternText the pattern text
	 * @return map of placeholder name to example value
	 */
	static Map<String, String> collectPlaceholderExamples(String patternText) {
		Map<String, String> examples = new HashMap<>();
		
		java.util.regex.Pattern phPattern = java.util.regex.Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)\\$?"); //$NON-NLS-1$
		Matcher matcher = phPattern.matcher(patternText);
		
		int varCounter = 0;
		while (matcher.find()) {
			String fullPlaceholder = matcher.group(0);
			if (!examples.containsKey(fullPlaceholder)) {
				String example = DEFAULT_EXAMPLES.get(fullPlaceholder);
				if (example == null) {
					// Generate a meaningful name from the placeholder
					String name = matcher.group(1);
					if (fullPlaceholder.endsWith("$")) { //$NON-NLS-1$
						// Variadic placeholder: use "arg1, arg2"
						example = "arg" + (++varCounter) + ", arg" + (++varCounter); //$NON-NLS-1$ //$NON-NLS-2$
					} else {
						example = name;
					}
				}
				examples.put(fullPlaceholder, example);
			}
		}
		
		return examples;
	}
	
	/**
	 * Substitutes placeholders in a pattern with example values.
	 * 
	 * @param patternText the pattern text with placeholders
	 * @param examples the placeholder-to-value mapping
	 * @return the substituted text
	 */
	static String substitutePlaceholders(String patternText, Map<String, String> examples) {
		String result = patternText;
		// Sort by length descending to replace longer placeholders first (e.g., $args$ before $a)
		List<Map.Entry<String, String>> sorted = new java.util.ArrayList<>(examples.entrySet());
		sorted.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
		
		for (Map.Entry<String, String> entry : sorted) {
			result = result.replace(entry.getKey(), entry.getValue());
		}
		return result;
	}
	
	/**
	 * Represents a before/after preview for a transformation rule.
	 * 
	 * @param before the code before transformation
	 * @param after the code after transformation (null for hint-only rules)
	 * @param description optional description of the transformation
	 */
	public record Preview(String before, String after, String description) {
		
		/**
		 * Returns {@code true} if this preview has an "after" transformation.
		 * 
		 * @return {@code true} if not hint-only
		 */
		public boolean hasTransformation() {
			return after != null;
		}
		
		/**
		 * Formats the preview as a human-readable string.
		 * 
		 * @return formatted preview text
		 */
		public String format() {
			StringBuilder sb = new StringBuilder();
			if (description != null) {
				sb.append("// ").append(description).append('\n'); //$NON-NLS-1$
			}
			sb.append("Before: ").append(before); //$NON-NLS-1$
			if (after != null) {
				sb.append('\n');
				sb.append("After:  ").append(after); //$NON-NLS-1$
			}
			return sb.toString();
		}
	}
}

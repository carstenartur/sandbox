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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.sandbox.jdt.triggerpattern.api.Hint;
import org.sandbox.jdt.triggerpattern.api.HintContext;
import org.sandbox.jdt.triggerpattern.api.HintKind;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.Severity;
import org.sandbox.jdt.triggerpattern.api.TriggerPattern;
import org.sandbox.jdt.triggerpattern.api.TriggerPatterns;
import org.sandbox.jdt.triggerpattern.api.TriggerTreeKind;

/**
 * Registry for trigger pattern hints.
 * 
 * <p>This class discovers and loads hint providers from:
 * <ul>
 *   <li>Extension point contributions</li>
 *   <li>Annotated methods in registered provider classes</li>
 * </ul>
 * </p>
 * 
 * @since 1.2.2
 */
public class HintRegistry {
	
	private static final String EXTENSION_POINT_ID = "org.sandbox.jdt.triggerpattern.hints"; //$NON-NLS-1$
	
	private List<HintDescriptor> hints;
	private boolean initialized = false;
	
	/**
	 * Represents a registered hint with its pattern and invocation information.
	 */
	public static class HintDescriptor {
		private final Pattern pattern;
		private final String displayName;
		private final String description;
		private final boolean enabledByDefault;
		private final Severity severity;
		private final String id;
		private final String category;
		private final String[] suppressWarnings;
		private final HintKind hintKind;
		private final String minSourceVersion;
		private final Integer[] treeKinds;  // For @TriggerTreeKind
		private final Class<?> providerClass;
		private final Method method;
		
		/**
		 * Full constructor with all hint attributes.
		 * 
		 * @param pattern the pattern to match (may be null for @TriggerTreeKind)
		 * @param displayName hint display name
		 * @param description hint description
		 * @param enabledByDefault whether enabled by default
		 * @param severity severity level
		 * @param id hint identifier
		 * @param category hint category
		 * @param suppressWarnings suppress warnings keys
		 * @param hintKind inspection or action
		 * @param minSourceVersion minimum Java version
		 * @param treeKinds AST node types to match (for @TriggerTreeKind)
		 * @param providerClass the provider class
		 * @param method the hint method
		 */
		public HintDescriptor(Pattern pattern, String displayName, String description,
				boolean enabledByDefault, Severity severity, String id, String category,
				String[] suppressWarnings, HintKind hintKind, String minSourceVersion,
				Integer[] treeKinds, Class<?> providerClass, Method method) {
			this.pattern = pattern;
			this.displayName = displayName;
			this.description = description;
			this.enabledByDefault = enabledByDefault;
			this.severity = severity;
			this.id = id;
			this.category = category;
			this.suppressWarnings = suppressWarnings;
			this.hintKind = hintKind;
			this.minSourceVersion = minSourceVersion;
			this.treeKinds = treeKinds;
			this.providerClass = providerClass;
			this.method = method;
		}
		
		/**
		 * Legacy constructor for backward compatibility.
		 * 
		 * @deprecated Use the full constructor instead
		 */
		@Deprecated
		public HintDescriptor(Pattern pattern, String displayName, String description,
				boolean enabledByDefault, String severity, Class<?> providerClass, Method method) {
			this(pattern, displayName, description, enabledByDefault,
				parseSeverity(severity), "", "", new String[0], //$NON-NLS-1$ //$NON-NLS-2$
				HintKind.INSPECTION, "", null, providerClass, method); //$NON-NLS-1$
		}
		
		private static Severity parseSeverity(String severityStr) {
			if (severityStr == null) {
				return Severity.INFO;
			}
			try {
				return Severity.valueOf(severityStr.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				// Map common string values to enum
				return switch (severityStr.toLowerCase(Locale.ROOT)) {
					case "error" -> Severity.ERROR; //$NON-NLS-1$
					case "warning" -> Severity.WARNING; //$NON-NLS-1$
					case "hint" -> Severity.HINT; //$NON-NLS-1$
					default -> Severity.INFO;
				};
			}
		}
		
		/**
		 * @return the hint pattern (may be null for tree kind hints)
		 */
		public Pattern getPattern() {
			return pattern;
		}
		
		/**
		 * @return the display name
		 */
		public String getDisplayName() {
			return displayName;
		}
		
		/**
		 * @return the description
		 */
		public String getDescription() {
			return description;
		}
		
		/**
		 * @return whether enabled by default
		 */
		public boolean isEnabledByDefault() {
			return enabledByDefault;
		}
		
		/**
		 * @return the severity level
		 */
		public Severity getSeverity() {
			return severity;
		}
		
		/**
		 * @return the severity as string (for backward compatibility)
		 * @deprecated Use {@link #getSeverity()} instead
		 */
		@Deprecated
		public String getSeverityString() {
			return severity.name().toLowerCase(Locale.ROOT);
		}
		
		/**
		 * @return the hint ID
		 */
		public String getId() {
			return id;
		}
		
		/**
		 * @return the category
		 */
		public String getCategory() {
			return category;
		}
		
		/**
		 * @return suppress warnings keys
		 */
		public String[] getSuppressWarnings() {
			return suppressWarnings;
		}
		
		/**
		 * @return the hint kind
		 */
		public HintKind getHintKind() {
			return hintKind;
		}
		
		/**
		 * @return minimum source version
		 */
		public String getMinSourceVersion() {
			return minSourceVersion;
		}
		
		/**
		 * @return tree kinds for @TriggerTreeKind hints (null for pattern hints)
		 */
		public Integer[] getTreeKinds() {
			return treeKinds;
		}
		
		/**
		 * Invokes the hint method with the given context.
		 * 
		 * @param context the hint context
		 * @return the result of the hint method (typically a completion proposal or list of proposals)
		 * @throws Exception if invocation fails
		 */
		public Object invoke(HintContext context) throws Exception {
			if (!Modifier.isStatic(method.getModifiers())) {
				throw new IllegalStateException("Hint method must be static: " + method); //$NON-NLS-1$
			}
			return method.invoke(null, context);
		}
	}
	
	/**
	 * Returns all registered hints.
	 * Lazily initializes the registry on first call.
	 * 
	 * @return list of hint descriptors
	 */
	public synchronized List<HintDescriptor> getHints() {
		if (!initialized) {
			loadHints();
			initialized = true;
		}
		return new ArrayList<>(hints);
	}
	
	/**
	 * Loads hints from extension points and annotations.
	 */
	private synchronized void loadHints() {
		List<HintDescriptor> loadedHints = new ArrayList<>();
		
		IExtensionRegistry registry = Platform.getExtensionRegistry();
		if (registry == null) {
			hints = loadedHints;
			return;
		}
		
		IConfigurationElement[] elements = registry.getConfigurationElementsFor(EXTENSION_POINT_ID);
		
		for (IConfigurationElement element : elements) {
			try {
				if ("hintProvider".equals(element.getName())) { //$NON-NLS-1$
					loadFromProvider(element, loadedHints);
				} else if ("pattern".equals(element.getName())) { //$NON-NLS-1$
					loadDeclarativePattern(element, loadedHints);
				}
			} catch (Exception e) {
				// Log error but continue with other hints
				ILog log = Platform.getLog(HintRegistry.class);
				log.log(Status.error("Error loading hint from extension point", e)); //$NON-NLS-1$
			}
		}
		
		hints = loadedHints;
	}
	
	/**
	 * Loads hints from a provider class that contains @TriggerPattern annotated methods.
	 */
	private void loadFromProvider(IConfigurationElement element, List<HintDescriptor> loadedHints) throws Exception {
		String className = element.getAttribute("class"); //$NON-NLS-1$
		if (className == null) {
			return;
		}
		
		// Load the class from the contributing bundle
		Bundle bundle = Platform.getBundle(element.getContributor().getName());
		if (bundle == null) {
			return;
		}
		
		Class<?> providerClass = bundle.loadClass(className);
		
		// Find all methods with hint annotations
		for (Method method : providerClass.getDeclaredMethods()) {
			// Check for @TriggerPattern
			TriggerPattern triggerPattern = method.getAnnotation(TriggerPattern.class);
			if (triggerPattern != null) {
				loadTriggerPatternHint(method, triggerPattern, providerClass, loadedHints);
			}
			
			// Check for @TriggerPatterns (container)
			TriggerPatterns triggerPatterns = method.getAnnotation(TriggerPatterns.class);
			if (triggerPatterns != null) {
				for (TriggerPattern tp : triggerPatterns.value()) {
					loadTriggerPatternHint(method, tp, providerClass, loadedHints);
				}
			}
			
			// Check for @TriggerTreeKind
			TriggerTreeKind treeKind = method.getAnnotation(TriggerTreeKind.class);
			if (treeKind != null) {
				loadTreeKindHint(method, treeKind, providerClass, loadedHints);
			}
		}
	}
	
	/**
	 * Loads a single @TriggerPattern hint.
	 */
	private void loadTriggerPatternHint(Method method, TriggerPattern triggerPattern,
			Class<?> providerClass, List<HintDescriptor> loadedHints) throws Exception {
		validateHintMethod(method);
		
		Pattern pattern = new Pattern(
			triggerPattern.value(),
			triggerPattern.kind(),
			triggerPattern.id().isEmpty() ? null : triggerPattern.id(),
			null
		);
		
		HintDescriptor descriptor = createHintDescriptor(pattern, null, method, providerClass);
		loadedHints.add(descriptor);
	}
	
	/**
	 * Loads a @TriggerTreeKind hint.
	 */
	private void loadTreeKindHint(Method method, TriggerTreeKind treeKind,
			Class<?> providerClass, List<HintDescriptor> loadedHints) throws Exception {
		validateHintMethod(method);
		
		// Convert int[] to Integer[]
		int[] kinds = treeKind.value();
		Integer[] treeKinds = new Integer[kinds.length];
		for (int i = 0; i < kinds.length; i++) {
			treeKinds[i] = kinds[i];
		}
		
		HintDescriptor descriptor = createHintDescriptor(null, treeKinds, method, providerClass);
		loadedHints.add(descriptor);
	}
	
	/**
	 * Creates a HintDescriptor from a method and its @Hint annotation.
	 */
	private HintDescriptor createHintDescriptor(Pattern pattern, Integer[] treeKinds,
			Method method, Class<?> providerClass) {
		// Check for @Hint annotation
		Hint hintAnnotation = method.getAnnotation(Hint.class);
		
		String displayName = hintAnnotation != null ? hintAnnotation.displayName() : ""; //$NON-NLS-1$
		String description = hintAnnotation != null ? hintAnnotation.description() : ""; //$NON-NLS-1$
		boolean enabledByDefault = hintAnnotation == null || hintAnnotation.enabledByDefault();
		Severity severity = hintAnnotation != null ? hintAnnotation.severity() : Severity.INFO;
		String id = hintAnnotation != null ? hintAnnotation.id() : ""; //$NON-NLS-1$
		String category = hintAnnotation != null ? hintAnnotation.category() : ""; //$NON-NLS-1$
		String[] suppressWarnings = hintAnnotation != null ? hintAnnotation.suppressWarnings() : new String[0];
		HintKind hintKind = hintAnnotation != null ? hintAnnotation.hintKind() : HintKind.INSPECTION;
		String minSourceVersion = hintAnnotation != null ? hintAnnotation.minSourceVersion() : ""; //$NON-NLS-1$
		
		return new HintDescriptor(pattern, displayName, description, enabledByDefault,
			severity, id, category, suppressWarnings, hintKind, minSourceVersion,
			treeKinds, providerClass, method);
	}
	
	/**
	 * Loads a declaratively defined pattern hint.
	 */
	private void loadDeclarativePattern(IConfigurationElement element, List<HintDescriptor> loadedHints) throws Exception {
		String id = element.getAttribute("id"); //$NON-NLS-1$
		String value = element.getAttribute("value"); //$NON-NLS-1$
		String kindStr = element.getAttribute("kind"); //$NON-NLS-1$
		String displayName = element.getAttribute("displayName"); //$NON-NLS-1$
		String className = element.getAttribute("class"); //$NON-NLS-1$
		String methodName = element.getAttribute("method"); //$NON-NLS-1$
		
		if (value == null || kindStr == null || className == null || methodName == null) {
			return;
		}
		
		PatternKind kind = PatternKind.valueOf(kindStr);
		Pattern pattern = new Pattern(value, kind, id, displayName);
		
		// Load the class and method
		Bundle bundle = Platform.getBundle(element.getContributor().getName());
		if (bundle == null) {
			return;
		}
		
		Class<?> providerClass = bundle.loadClass(className);
		Method method = providerClass.getMethod(methodName, HintContext.class);
		validateHintMethod(method);
		
		HintDescriptor descriptor = new HintDescriptor(
			pattern, displayName != null ? displayName : "", "", true, //$NON-NLS-1$ //$NON-NLS-2$
			Severity.INFO, "", "", new String[0], //$NON-NLS-1$ //$NON-NLS-2$
			HintKind.INSPECTION, "", null, providerClass, method //$NON-NLS-1$
		);
		loadedHints.add(descriptor);
	}
	
	/**
	 * Validates that a method can be used as a hint method.
	 */
	private void validateHintMethod(Method method) {
		if (!Modifier.isPublic(method.getModifiers())) {
			throw new IllegalArgumentException("Hint method must be public: " + method); //$NON-NLS-1$
		}
		if (!Modifier.isStatic(method.getModifiers())) {
			throw new IllegalArgumentException("Hint method must be static: " + method); //$NON-NLS-1$
		}
		Class<?>[] paramTypes = method.getParameterTypes();
		if (paramTypes.length != 1 || !HintContext.class.isAssignableFrom(paramTypes[0])) {
			throw new IllegalArgumentException(
				"Hint method must have exactly one parameter of type HintContext: " + method //$NON-NLS-1$
			);
		}
	}
}

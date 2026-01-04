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

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.sandbox.jdt.triggerpattern.api.Hint;
import org.sandbox.jdt.triggerpattern.api.HintContext;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPattern;

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
	
	private static final String EXTENSION_POINT_ID = "org.sandbox.jdt.triggerpattern.hints";
	
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
		private final String severity;
		private final Class<?> providerClass;
		private final Method method;
		
		public HintDescriptor(Pattern pattern, String displayName, String description,
				boolean enabledByDefault, String severity, Class<?> providerClass, Method method) {
			this.pattern = pattern;
			this.displayName = displayName;
			this.description = description;
			this.enabledByDefault = enabledByDefault;
			this.severity = severity;
			this.providerClass = providerClass;
			this.method = method;
		}
		
		public Pattern getPattern() {
			return pattern;
		}
		
		public String getDisplayName() {
			return displayName;
		}
		
		public String getDescription() {
			return description;
		}
		
		public boolean isEnabledByDefault() {
			return enabledByDefault;
		}
		
		public String getSeverity() {
			return severity;
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
				throw new IllegalStateException("Hint method must be static: " + method);
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
	private void loadHints() {
		hints = new ArrayList<>();
		
		IExtensionRegistry registry = Platform.getExtensionRegistry();
		if (registry == null) {
			return;
		}
		
		IConfigurationElement[] elements = registry.getConfigurationElementsFor(EXTENSION_POINT_ID);
		
		for (IConfigurationElement element : elements) {
			try {
				if ("hintProvider".equals(element.getName())) {
					loadFromProvider(element);
				} else if ("pattern".equals(element.getName())) {
					loadDeclarativePattern(element);
				}
			} catch (Exception e) {
				// Log error but continue with other hints
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Loads hints from a provider class that contains @TriggerPattern annotated methods.
	 */
	private void loadFromProvider(IConfigurationElement element) throws Exception {
		String className = element.getAttribute("class");
		if (className == null) {
			return;
		}
		
		// Load the class from the contributing bundle
		Bundle bundle = Platform.getBundle(element.getContributor().getName());
		if (bundle == null) {
			return;
		}
		
		Class<?> providerClass = bundle.loadClass(className);
		
		// Find all methods annotated with @TriggerPattern
		for (Method method : providerClass.getDeclaredMethods()) {
			TriggerPattern triggerPattern = method.getAnnotation(TriggerPattern.class);
			if (triggerPattern != null) {
				validateHintMethod(method);
				
				Pattern pattern = new Pattern(
					triggerPattern.value(),
					triggerPattern.kind(),
					triggerPattern.id().isEmpty() ? null : triggerPattern.id(),
					null
				);
				
				// Check for @Hint annotation
				Hint hintAnnotation = method.getAnnotation(Hint.class);
				String displayName = hintAnnotation != null ? hintAnnotation.displayName() : ""; //$NON-NLS-1$
				String description = hintAnnotation != null ? hintAnnotation.description() : ""; //$NON-NLS-1$
				boolean enabledByDefault = hintAnnotation == null || hintAnnotation.enabledByDefault();
				String severity = hintAnnotation != null ? hintAnnotation.severity() : "info"; //$NON-NLS-1$
				
				HintDescriptor descriptor = new HintDescriptor(
					pattern, displayName, description, enabledByDefault, severity, providerClass, method
				);
				hints.add(descriptor);
			}
		}
	}
	
	/**
	 * Loads a declaratively defined pattern hint.
	 */
	private void loadDeclarativePattern(IConfigurationElement element) throws Exception {
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
			pattern, displayName != null ? displayName : "", "", true, "info", providerClass, method //$NON-NLS-1$ //$NON-NLS-2$
		);
		hints.add(descriptor);
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

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
package org.sandbox.jdt.triggerpattern.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;

/**
 * Registry for guard functions with built-in guards.
 * 
 * <p>The registry is a singleton that manages guard functions by name. It comes
 * pre-loaded with built-in guards for common checks such as type testing,
 * modifier inspection, source version comparison, and annotation checking.</p>
 * 
 * <h2>Built-in Guards</h2>
 * <table>
 *   <caption>Available built-in guard functions</caption>
 *   <tr><th>Name</th><th>Description</th></tr>
 *   <tr><td>{@code instanceof}</td><td>Checks if a binding's type matches a given type name</td></tr>
 *   <tr><td>{@code matchesAny}</td><td>Returns {@code true} if a placeholder's text matches any of the given literals, or if bound and no literals given</td></tr>
 *   <tr><td>{@code matchesNone}</td><td>Returns {@code true} if a placeholder's text matches none of the given literals, or if unbound and no literals given</td></tr>
 *   <tr><td>{@code referencedIn}</td><td>Checks if a variable is referenced within another expression</td></tr>
 *   <tr><td>{@code elementKindMatches}</td><td>Checks if a binding is of a specific element kind (FIELD, METHOD, LOCAL_VARIABLE, PARAMETER, TYPE)</td></tr>
 *   <tr><td>{@code hasNoSideEffect}</td><td>Checks if an expression has no side effects</td></tr>
 *   <tr><td>{@code sourceVersionGE}</td><td>Checks if the source version is greater than or equal to a given version</td></tr>
 *   <tr><td>{@code sourceVersionLE}</td><td>Checks if the source version is less than or equal to a given version</td></tr>
 *   <tr><td>{@code sourceVersionBetween}</td><td>Checks if the source version is within a given range</td></tr>
 *   <tr><td>{@code isStatic}</td><td>Checks if a binding has the static modifier</td></tr>
 *   <tr><td>{@code isFinal}</td><td>Checks if a binding has the final modifier</td></tr>
 *   <tr><td>{@code hasAnnotation}</td><td>Checks if a binding has a specific annotation</td></tr>
 *   <tr><td>{@code isDeprecated}</td><td>Checks if a binding is deprecated</td></tr>
 *   <tr><td>{@code contains}</td><td>Checks if a text pattern occurs in the enclosing method body</td></tr>
 *   <tr><td>{@code notContains}</td><td>Checks if a text pattern does NOT occur in the enclosing method body</td></tr>
 * </table>
 * 
 * @since 1.3.2
 */
public final class GuardRegistry {
	
	private static final String GUARDS_EXTENSION_POINT_ID = "org.sandbox.jdt.triggerpattern.guards"; //$NON-NLS-1$
	
	private static final GuardRegistry INSTANCE = new GuardRegistry();
	
	private final Map<String, GuardFunction> guards = new ConcurrentHashMap<>();
	
	private GuardRegistry() {
		BuiltInGuards.registerAll(guards);
		// Register this registry as the resolver for GuardExpression evaluation
		org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder.setResolver(this::get);
	}
	
	/**
	 * Returns the singleton instance.
	 * 
	 * @return the guard registry instance
	 */
	public static GuardRegistry getInstance() {
		return INSTANCE;
	}
	
	/**
	 * Registers a guard function with the given name.
	 * 
	 * @param name the guard function name
	 * @param fn the guard function
	 */
	public void register(String name, GuardFunction fn) {
		guards.put(name, fn);
	}
	
	/**
	 * Returns the guard function registered under the given name.
	 * 
	 * @param name the guard function name
	 * @return the guard function, or {@code null} if not found
	 */
	public GuardFunction get(String name) {
		return guards.get(name);
	}
	
	/**
	 * Returns the names of all registered guard functions.
	 * 
	 * @return unmodifiable set of guard function names
	 * @since 1.3.6
	 */
	public Set<String> getRegisteredNames() {
		return java.util.Collections.unmodifiableSet(guards.keySet());
	}
	
	/**
	 * Loads custom guard functions from the Eclipse extension registry.
	 * 
	 * <p>This method queries the {@code org.sandbox.jdt.triggerpattern.guards}
	 * extension point for contributed guard function implementations. Each
	 * contribution specifies a name and a class implementing
	 * {@link GuardFunction}.</p>
	 * 
	 * <p>Guards loaded from extensions are registered in addition to the
	 * built-in guards. If a contributed guard has the same name as a built-in
	 * guard, it overrides the built-in.</p>
	 * 
	 * @return list of successfully loaded guard names
	 * @since 1.3.6
	 */
	public List<String> loadExtensions() {
		List<String> loaded = new ArrayList<>();
		
		IExtensionRegistry registry = Platform.getExtensionRegistry();
		if (registry == null) {
			return loaded;
		}
		
		IConfigurationElement[] elements = registry.getConfigurationElementsFor(GUARDS_EXTENSION_POINT_ID);
		
		for (IConfigurationElement element : elements) {
			if (!"guard".equals(element.getName())) { //$NON-NLS-1$
				continue;
			}
			String name = element.getAttribute("name"); //$NON-NLS-1$
			if (name == null || name.isEmpty()) {
				continue;
			}
			try {
				Bundle bundle = Platform.getBundle(element.getContributor().getName());
				if (bundle == null) {
					continue;
				}
				String className = element.getAttribute("class"); //$NON-NLS-1$
				if (className == null) {
					continue;
				}
				Class<?> guardClass = bundle.loadClass(className);
				Object instance = guardClass.getDeclaredConstructor().newInstance();
				if (instance instanceof GuardFunction guardFunction) {
					register(name, guardFunction);
					loaded.add(name);
				}
			} catch (Exception e) {
				ILog log = Platform.getLog(GuardRegistry.class);
				log.log(Status.warning(
						"Failed to load guard function from extension: " + name, e)); //$NON-NLS-1$
			}
		}
		
		return loaded;
	}
	
}

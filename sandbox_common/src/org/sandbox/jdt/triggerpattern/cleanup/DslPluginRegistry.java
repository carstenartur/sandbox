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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.cleanup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for {@link AbstractPatternCleanupPlugin} instances, grouped by bundle ID.
 *
 * <p>Plugins register themselves (or are registered on their behalf) by calling
 * {@link #register(AbstractPatternCleanupPlugin)}.  Consumers such as
 * {@code HintFileCleanUpCore} can then retrieve all plugins for a given bundle via
 * {@link #getPluginsForBundle(String)}.</p>
 *
 * <p>Bundle IDs are derived from the plugin's {@code cleanupId} through
 * {@link AbstractPatternCleanupPlugin#getBundleId()}.  For example, a plugin
 * whose {@code cleanupId} is {@code "cleanup.encoding.charset.forname.utf8"} will
 * be associated with the bundle {@code "encoding"}.</p>
 *
 * @since 1.3.5
 */
public final class DslPluginRegistry {

	/** All registered plugins keyed by their cleanup ID (preserves registration order). */
	private static final Map<String, AbstractPatternCleanupPlugin<?>> BY_CLEANUP_ID = new LinkedHashMap<>();

	/** Plugins grouped by bundle ID. */
	private static final Map<String, List<AbstractPatternCleanupPlugin<?>>> BY_BUNDLE_ID = new LinkedHashMap<>();

	private DslPluginRegistry() {
		// utility class
	}

	/**
	 * Registers a plugin with the registry.
	 *
	 * <p>A plugin is only added once; a second registration whose
	 * {@link AbstractPatternCleanupPlugin#getCleanupId()} matches an already-registered
	 * plugin is silently ignored.</p>
	 *
	 * @param plugin the plugin to register; must not be {@code null}
	 */
	public static synchronized void register(AbstractPatternCleanupPlugin<?> plugin) {
		String id = plugin.getCleanupId();
		if (BY_CLEANUP_ID.containsKey(id)) {
			return; // already registered
		}
		BY_CLEANUP_ID.put(id, plugin);
		String bundleId = plugin.getBundleId();
		BY_BUNDLE_ID.computeIfAbsent(bundleId, k -> new ArrayList<>()).add(plugin);
	}

	/**
	 * Returns all registered plugins.
	 *
	 * @return an unmodifiable snapshot of all registered plugins (never {@code null})
	 */
	public static synchronized List<AbstractPatternCleanupPlugin<?>> getAllPlugins() {
		return Collections.unmodifiableList(new ArrayList<>(BY_CLEANUP_ID.values()));
	}

	/**
	 * Returns all registered plugins whose bundle ID matches {@code bundleId}.
	 *
	 * @param bundleId the bundle identifier (e.g., {@code "encoding"})
	 * @return an unmodifiable list of matching plugins (never {@code null})
	 */
	public static synchronized List<AbstractPatternCleanupPlugin<?>> getPluginsForBundle(String bundleId) {
		List<AbstractPatternCleanupPlugin<?>> plugins = BY_BUNDLE_ID.get(bundleId);
		if (plugins == null) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(new ArrayList<>(plugins));
	}
}

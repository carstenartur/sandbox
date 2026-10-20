/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.core.cleanupapp.jdtls;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jdt.ui.cleanup.ICleanUpOptionsInitializer;

/** Reads the existing registrations; it does not maintain a second cleanup registry. */
final class SandboxCleanUpCatalog {
    private static final String EXTENSION = "org.eclipse.jdt.ui.cleanUps"; //$NON-NLS-1$
    private static final Set<String> JAVA_CLEANUPS = Set.of(
            "sandbox_common", "sandbox_triggerpattern", "sandbox_encoding_quickfix", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            "sandbox_functional_converter", "sandbox_platform_helper", "sandbox_jface_cleanup", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            "sandbox_junit_cleanup", "sandbox_method_reuse", "sandbox_int_to_enum", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            "sandbox_math_cleanup", "sandbox_container_cleanup", "sandbox_use_general_type"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    record Entry(String id, String bundle, Map<String, String> defaults, boolean available, String reason) {
        Entry { defaults = Map.copyOf(defaults); }
    }

    List<Entry> list() throws CoreException {
        var result = new ArrayList<Entry>();
        for (var element : registrations()) result.add(describe(element));
        result.sort(Comparator.comparing(Entry::id));
        return List.copyOf(result);
    }

    ICleanUp create(String id, Map<String, String> overrides) throws CoreException {
        var matches = registrations().stream().filter(e -> id.equals(e.getAttribute("id"))).toList(); //$NON-NLS-1$
        if (matches.size() != 1) throw new IllegalArgumentException("Unknown or ambiguous Sandbox cleanup: " + id); //$NON-NLS-1$
        var element = matches.getFirst();
        var entry = describe(element);
        if (!entry.available()) throw new IllegalArgumentException(entry.reason());
        var cleanup = (ICleanUp) element.createExecutableExtension("class"); //$NON-NLS-1$
        var options = new CleanUpOptions();
        entry.defaults().forEach(options::setOption);
        overrides.forEach(options::setOption);
        cleanup.setOptions(options);
        return cleanup;
    }

    private static List<IConfigurationElement> registrations() {
        var result = new ArrayList<IConfigurationElement>();
        for (var element : Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION)) {
            String bundle = element.getContributor().getName();
            if ("cleanUp".equals(element.getName()) && bundle.startsWith("sandbox_") //$NON-NLS-1$ //$NON-NLS-2$
                    && !"sandbox_tools".equals(bundle)) result.add(element); //$NON-NLS-1$
        }
        return result;
    }

    private static Entry describe(IConfigurationElement element) throws CoreException {
        String bundle = element.getContributor().getName();
        String id = element.getAttribute("id"); //$NON-NLS-1$
        if (!JAVA_CLEANUPS.contains(bundle)) {
            String reason = "sandbox_xml_cleanup".equals(bundle) //$NON-NLS-1$
                    ? "XML cleanup writes resources during fix creation and cannot be used as a read-only Java preview." //$NON-NLS-1$
                    : "This contribution has not been qualified for read-only Java cleanup previews."; //$NON-NLS-1$
            return new Entry(id, bundle, Map.of(), false, reason);
        }
        var defaults = new CleanUpOptions();
        for (var initializer : Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION)) {
            if (bundle.equals(initializer.getContributor().getName())
                    && "cleanUpOptionsInitializer".equals(initializer.getName()) //$NON-NLS-1$
                    && "cleanUp".equals(initializer.getAttribute("cleanUpKind"))) { //$NON-NLS-1$ //$NON-NLS-2$
                ((ICleanUpOptionsInitializer) initializer.createExecutableExtension("class")).setDefaultOptions(defaults); //$NON-NLS-1$
            }
        }
        var values = new LinkedHashMap<String, String>();
        for (String key : defaults.getKeys()) values.put(key, defaults.getValue(key));
        return new Entry(id, bundle, values, true, ""); //$NON-NLS-1$
    }
}

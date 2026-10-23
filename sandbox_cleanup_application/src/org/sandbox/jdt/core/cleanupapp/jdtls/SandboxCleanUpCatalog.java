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
import org.eclipse.jdt.internal.ui.fix.MapCleanUpOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jdt.ui.cleanup.ICleanUpOptionsInitializer;

/** Reads the existing registrations; it does not maintain a second cleanup registry. */
final class SandboxCleanUpCatalog {
    private static final String EXTENSION = "org.eclipse.jdt.ui.cleanUps"; //$NON-NLS-1$
    // Qualification is per registration, never inherited by a new contribution
    // merely because it is installed in an already-known bundle.
    private record Contribution(String bundle, String id, String implementation) {}

    private static final Set<Contribution> READ_ONLY_CLEANUPS = Set.of(
            new Contribution("sandbox_container_cleanup", "org.sandbox.jdt.ui.cleanup.container_contracts", //$NON-NLS-1$ //$NON-NLS-2$
                    "org.sandbox.jdt.container.cleanup.internal.ui.fix.ContainerCleanUp"), //$NON-NLS-1$
            new Contribution("sandbox_container_cleanup", "org.sandbox.jdt.ui.cleanup.container_contracts.closed_source_parameter_migration", //$NON-NLS-1$ //$NON-NLS-2$
                    "org.sandbox.jdt.container.cleanup.internal.ui.fix.CoordinatedContainerCleanUp"), //$NON-NLS-1$
            new Contribution("sandbox_encoding_quickfix", "org.eclipse.jdt.ui.cleanup.explicitencoding", //$NON-NLS-1$ //$NON-NLS-2$
                    "org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUp"), //$NON-NLS-1$
            new Contribution("sandbox_functional_converter", "org.eclipse.jdt.ui.cleanup.functionalcleanup", //$NON-NLS-1$ //$NON-NLS-2$
                    "org.sandbox.jdt.internal.ui.fix.UseFunctionalCallCleanUp"), //$NON-NLS-1$
            new Contribution("sandbox_int_to_enum", "org.eclipse.jdt.ui.cleanup.int_to_enum", //$NON-NLS-1$ //$NON-NLS-2$
                    "org.sandbox.jdt.internal.ui.fix.IntToEnumCleanUp"), //$NON-NLS-1$
            new Contribution("sandbox_jface_cleanup", "org.eclipse.jdt.ui.cleanup.jfacecleanup", //$NON-NLS-1$ //$NON-NLS-2$
                    "org.sandbox.jdt.internal.ui.fix.JFaceCleanUp"), //$NON-NLS-1$
            new Contribution("sandbox_junit_cleanup", "org.eclipse.jdt.ui.cleanup.junitcleanup", //$NON-NLS-1$ //$NON-NLS-2$
                    "org.sandbox.jdt.internal.ui.fix.JUnitCleanUp"), //$NON-NLS-1$
            new Contribution("sandbox_math_cleanup", "org.sandbox.jdt.ui.cleanup.mathematics", //$NON-NLS-1$ //$NON-NLS-2$
                    "org.sandbox.jdt.internal.ui.fix.MathematicalCleanUp"), //$NON-NLS-1$
            new Contribution("sandbox_method_reuse", "org.eclipse.jdt.ui.cleanup.methodreuse", //$NON-NLS-1$ //$NON-NLS-2$
                    "org.sandbox.jdt.internal.ui.fix.MethodReuseCleanUp"), //$NON-NLS-1$
            new Contribution("sandbox_platform_helper", "org.eclipse.jdt.ui.cleanup.simplifystatus", //$NON-NLS-1$ //$NON-NLS-2$
                    "org.sandbox.jdt.internal.ui.fix.SimplifyPlatformStatusCleanUp"), //$NON-NLS-1$
            new Contribution("sandbox_triggerpattern", "org.eclipse.jdt.ui.cleanup.stringsimplification", //$NON-NLS-1$ //$NON-NLS-2$
                    "org.sandbox.jdt.internal.ui.fix.StringSimplificationCleanUp"), //$NON-NLS-1$
            new Contribution("sandbox_triggerpattern", "org.eclipse.jdt.ui.cleanup.threading", //$NON-NLS-1$ //$NON-NLS-2$
                    "org.sandbox.jdt.internal.ui.fix.ThreadingCleanUp"), //$NON-NLS-1$
            new Contribution("sandbox_triggerpattern", "org.eclipse.jdt.ui.cleanup.shift_out_of_range", //$NON-NLS-1$ //$NON-NLS-2$
                    "org.sandbox.jdt.internal.ui.fix.ShiftOutOfRangeCleanUp"), //$NON-NLS-1$
            new Contribution("sandbox_use_general_type", "org.eclipse.jdt.ui.cleanup.use_general_type", //$NON-NLS-1$ //$NON-NLS-2$
                    "org.sandbox.jdt.internal.ui.fix.UseGeneralTypeCleanUp") //$NON-NLS-1$
            );

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
        var options = new LinkedHashMap<>(entry.defaults());
        options.putAll(overrides);
        // Existing Eclipse cleanups also consume numeric/profile values through
        // MapCleanUpOptions; boolean-only adapters silently lose those settings.
        cleanup.setOptions(new MapCleanUpOptions(options));
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
        String implementation = element.getAttribute("class"); //$NON-NLS-1$
        if (!READ_ONLY_CLEANUPS.contains(new Contribution(bundle, id, implementation))) {
            String reason = "sandbox_xml_cleanup".equals(bundle) //$NON-NLS-1$
                    ? "XML cleanup writes resources during fix creation and cannot be used as a read-only Java preview." //$NON-NLS-1$
                    : "sandbox_triggerpattern".equals(bundle) //$NON-NLS-1$
                            ? "This TriggerPattern contribution is not qualified for a marker-free preview; marker-writing implementations must not run during preview." //$NON-NLS-1$
                            : "This contribution identity has not been qualified for read-only Java cleanup previews."; //$NON-NLS-1$
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

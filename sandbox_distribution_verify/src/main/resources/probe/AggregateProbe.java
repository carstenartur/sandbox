/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.distribution.probe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.FrameworkWiring;

/** Test instrumentation only: never included in either published repository. */
public final class AggregateProbe implements IApplication {
    @Override
    public Object start(IApplicationContext context) throws Exception {
        String[] arguments = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
        if (arguments.length != 2) throw new IllegalArgumentException("Expected inventory and result paths");
        List<String> expected = Files.readAllLines(Path.of(arguments[0]));
        Set<String> ids = new LinkedHashSet<>();
        List<Bundle> bundles = new ArrayList<>();
        for (String line : expected) {
            String[] fields = line.split("\t", -1);
            if ("bundle".equals(fields[0])) {
                Bundle bundle = Platform.getBundle(fields[1]);
                require(bundle != null, "Missing bundle " + fields[1]);
                require(ids.add(fields[1]), "Duplicate expected bundle " + fields[1]);
                bundles.add(bundle);
            }
        }
        require(!bundles.isEmpty(), "Empty bundle inventory");
        var system = Platform.getBundle("org.eclipse.osgi");
        require(system.adapt(FrameworkWiring.class).resolveBundles(bundles), "Cannot resolve the expected bundles");
        Set<String> actual = new LinkedHashSet<>();
        int cleanups = 0;
        int tocs = 0;
        for (IConfigurationElement element : Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.jdt.ui.cleanUps")) {
            String contributor = element.getContributor().getName();
            if (ids.contains(contributor) && "cleanUp".equals(element.getName())) {
                String key = "cleanup\t" + contributor + '\t' + element.getAttribute("id");
                require(actual.add(key), "Duplicate cleanup " + key);
                require(element.createExecutableExtension("class") instanceof ICleanUp, "Invalid cleanup " + key);
                cleanups++;
            }
        }
        for (IConfigurationElement element : Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.help.toc")) {
            String contributor = element.getContributor().getName();
            if (ids.contains(contributor) && "toc".equals(element.getName())) {
                String file = element.getAttribute("file");
                String key = "toc\t" + contributor + '\t' + file;
                require(actual.add(key), "Duplicate Help TOC " + key);
                require(file != null && Platform.getBundle(contributor).getEntry(file) != null, "Missing Help content " + key);
                try (var stream = Platform.getBundle(contributor).getEntry(file).openStream()) {
                    require(stream.read() != -1, "Empty Help content " + key);
                }
                tocs++;
            }
        }
        Set<String> registrations = new LinkedHashSet<>(expected);
        registrations.removeIf(line -> line.startsWith("bundle\t"));
        require(actual.equals(registrations), "Runtime registrations differ; expected " + registrations + ", actual " + actual);
        require(cleanups > 0 && tocs > 0, "Empty runtime coverage");
        Files.writeString(Path.of(arguments[1]), "{\"status\":\"PASS\",\"bundles\":" + bundles.size()
                + ",\"cleanups\":" + cleanups + ",\"helpTocs\":" + tocs + "}\n");
        return EXIT_OK;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    @Override
    public void stop() { }
}

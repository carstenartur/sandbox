/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.distribution.probe;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;

import static org.sandbox.distribution.probe.AggregateProbe.escape;
import static org.sandbox.distribution.probe.AggregateProbe.require;

/** Runs inside the selected installation, using the JDT runner's real providers. */
final class SupportProbe {
    private static final String BSN = "org.sandbox.junit.migration.support";
    private static final String EXT = "org.junit.jupiter.api.extension.";
    private static final String FN = "org.junit.jupiter.api.function.";
    private static final String FILTER = "org.junit.platform.launcher.PostDiscoveryFilter";

    static String observe(boolean historical) throws Exception {
        Bundle system = Platform.getBundle("org.eclipse.osgi");
        var matches = Arrays.stream(system.getBundleContext().getBundles())
                .filter(b -> BSN.equals(b.getSymbolicName()) && b.getState() != Bundle.UNINSTALLED).toList();
        require(matches.size() == 1, "Expected one installed support bundle, found " + matches);
        Bundle support = matches.getFirst();
        Bundle runner = Platform.getBundle("org.eclipse.jdt.junit5.runtime");
        var resolver = system.adapt(FrameworkWiring.class);
        require(runner != null && resolver.resolveBundles(List.of(support, runner)), "Cannot resolve support/JDT");
        var fragments = Arrays.stream(system.getBundleContext().getBundles())
                .filter(b -> "sandbox_junit_cleanup_jdt5".equals(b.getSymbolicName()) && b.getState() != Bundle.UNINSTALLED).toList();
        require(fragments.size() == (historical ? 0 : 1), "Unexpected installed JDT 5 bindings " + fragments);
        Bundle binding = fragments.isEmpty() ? null : fragments.getFirst();
        boolean legacySix = historical && support.loadClass(EXT + "Extension") != runner.loadClass(EXT + "Extension");
        if (historical) {
            if (legacySix) runner = Platform.getBundle("org.eclipse.jdt.junit6.runtime");
            require(runner != null && resolver.resolveBundles(List.of(runner)), "Cannot resolve historical runner");
        } else {
            require(binding != null && resolver.resolveBundles(List.of(binding)), "Missing installed JDT 5 fragment");
            var fragment = binding.adapt(BundleWiring.class);
            require(fragment.isCurrent() && fragment.getRequiredWires(HostNamespace.HOST_NAMESPACE).stream()
                    .map(w -> w.getProviderWiring().getBundle()).toList().equals(List.of(support)), "JDT 5 fragment attached to another host");
        }
        var obsolete = resolver.getRemovalPendingBundles().stream().filter(b -> BSN.equals(b.getSymbolicName())
                || "sandbox_junit_cleanup_jdt5".equals(b.getSymbolicName())).toList();
        require(obsolete.isEmpty(), "Non-current support or fragment revisions still in use: " + obsolete);
        BundleWiring wiring = support.adapt(BundleWiring.class);
        require(wiring.isCurrent(), "Obsolete support wiring " + support);
        List<String> wires = new ArrayList<>();
        for (var type : List.of(EXT + "Extension", FN + "ThrowingConsumer", FILTER, "org.junit.platform.engine.TestDescriptor",
                "org.junit.platform.engine.support.descriptor.MethodSource")) {
            Class<?> actual = support.loadClass(type);
            String pkg = actual.getPackageName();
            var required = wiring.getRequiredWires(PackageNamespace.PACKAGE_NAMESPACE).stream()
                    .filter(w -> pkg.equals(w.getCapability().getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE))).toList();
            require(!required.isEmpty(), "Missing support wire for " + pkg);
            var wire = required.getFirst();
            Bundle provider = wire.getProviderWiring().getBundle();
            String expected = pkg.startsWith("org.junit.jupiter.") ? "junit-jupiter-api"
                    : type.equals(FILTER) ? "junit-platform-launcher" : "junit-platform-engine";
            require(required.stream().allMatch(w -> w.getProviderWiring().equals(wire.getProviderWiring())
                    && w.getCapability().getAttributes().equals(wire.getCapability().getAttributes()))
                    && expected.equals(provider.getSymbolicName()) && wire.getProviderWiring().isCurrent()
                    && provider.equals(FrameworkUtil.getBundle(actual)) && actual == runner.loadClass(type),
                    "Support/JDT class spaces differ for " + type + ": support " + provider + ", JDT " + FrameworkUtil.getBundle(runner.loadClass(type)));
            if (!historical) {
                require(wiring.getRequiredWires(BundleNamespace.BUNDLE_NAMESPACE).stream()
                        .anyMatch(w -> provider.equals(w.getProviderWiring().getBundle())),
                        "Missing JDT 5 provider bundle wire for " + pkg + ": " + provider);
            }
            Version exported = (Version) wire.getCapability().getAttributes().get(PackageNamespace.CAPABILITY_VERSION_ATTRIBUTE);
            int major = legacySix ? 6 : pkg.startsWith("org.junit.jupiter.") ? 5 : 1;
            int minimumMinor = legacySix ? 0 : 14;
            require(provider.getVersion().getMajor() == major && provider.getVersion().getMinor() >= minimumMinor
                    && exported.getMajor() == major && exported.getMinor() >= minimumMinor, "Unexpected runner provider for " + pkg + ": " + provider + " exports " + exported);
            wires.add("\"" + pkg + "=" + provider.getSymbolicName() + "/" + provider.getVersion() + "@" + exported + "\"");
        }
        Class<?> factories = defined(support, "ExternalResourceExtension");
        Class<?> filter = defined(support, "InheritedTestsFilter");
        defined(support, "IgnoreInheritedTests");
        require(runner.loadClass(FILTER).isInstance(filter.getConstructor().newInstance()), "Filter does not implement runner SPI");
        try (var in = support.getEntry("META-INF/services/" + FILTER).openStream()) {
            require(new String(in.readAllBytes(), StandardCharsets.UTF_8).trim().equals(filter.getName()), "Wrong packaged SPI");
        }
        List<String> apis = new ArrayList<>();
        for (String name : List.of("perTest", "perClass")) {
            Class<?> executable = runner.loadClass(FN + "Executable");
            Class<?> before = runner.loadClass(FN + "ThrowingConsumer");
            invokeFactory(runner, factories.getMethod(name, executable, Runnable.class), noOp(executable), (Runnable) () -> {});
            apis.add("\"" + name + ".original\"");
            Method supplier;
            try {
                supplier = factories.getMethod(name, Supplier.class, before, Consumer.class);
            } catch (NoSuchMethodException absent) {
                if (!historical) throw absent;
                apis.add("\"" + name + ".supplier-absent\"");
                continue;
            }
            require(!historical, "Historical bundle exposes " + supplier);
            invokeFactory(runner, supplier, (Supplier<Object>) Object::new, noOp(before), (Consumer<Object>) value -> {});
            apis.add("\"" + name + ".supplier\"");
        }
        String fragmentEvidence = binding == null ? "" : ",\"fragmentVersion\":\"" + binding.getVersion()
                + "\",\"fragmentPath\":\"" + escape(FileLocator.getBundleFileLocation(binding).orElseThrow().toPath().toRealPath().toString()) + "\"";
        String path = FileLocator.getBundleFileLocation(support).orElseThrow().toPath().toRealPath().toString();
        return "{\"bsn\":\"" + BSN + "\",\"version\":\"" + support.getVersion() + "\",\"bundleId\":" + support.getBundleId()
                + fragmentEvidence + ",\"nonCurrentRevisionsInUse\":0,\"runnerBsn\":\"" + runner.getSymbolicName() + "\",\"path\":\"" + escape(path) + "\",\"jdtRunner\":\"" + runner.getVersion() + "\",\"providers\":[" + String.join(",", wires)
                + "],\"factories\":[" + String.join(",", apis) + "],\"filter\":\"" + filter.getName() + "\"}";
    }

    private static Class<?> defined(Bundle support, String name) throws Exception {
        Class<?> type = support.loadClass("org.sandbox.junit.jupiter." + name);
        require(support.equals(FrameworkUtil.getBundle(type)), "Wrong defining bundle for " + name);
        return type;
    }

    private static Object noOp(Class<?> type) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> null);
    }

    private static void invokeFactory(Bundle runner, Method factory, Object... callbacks) throws Exception {
        require(Modifier.isPublic(factory.getModifiers()) && Modifier.isStatic(factory.getModifiers())
                && factory.getReturnType() == runner.loadClass(EXT + "Extension"), "Wrong factory signature " + factory);
        Object extension = factory.invoke(null, callbacks);
        String boundary = "perTest".equals(factory.getName()) ? "Each" : "All";
        for (String hook : List.of("Before", "After")) {
            require(runner.loadClass(EXT + hook + boundary + "Callback").isInstance(extension), "Wrong callback from " + factory);
        }
    }

    private SupportProbe() {}
}

/*
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 */
package org.apache.maven.surefire.junitplatform;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.maven.plugin.surefire.StartupReportConfiguration;
import org.apache.maven.plugin.surefire.extensions.SurefireConsoleOutputReporter;
import org.apache.maven.plugin.surefire.extensions.SurefireStatelessReporter;
import org.apache.maven.plugin.surefire.extensions.SurefireStatelessTestsetInfoReporter;
import org.apache.maven.plugin.surefire.log.api.ConsoleLogger;
import org.apache.maven.plugin.surefire.report.DefaultReporterFactory;
import org.apache.maven.surefire.api.report.ReportEntry;
import org.apache.maven.surefire.api.report.ReporterFactoryOptions;
import org.apache.maven.surefire.api.report.RunMode;
import org.apache.maven.surefire.api.report.Stoppable;
import org.apache.maven.surefire.api.report.TestOutputReportEntry;
import org.apache.maven.surefire.api.report.TestReportListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/** Runs the real 3.5.6 adapter and reporter, not a model of their behaviour. */
class ReportingProbeTest {
    private static int invocations;

    @ParameterizedClass(name = "compliance={0}")
    @MethodSource("levels")
    public static class CompilerFixture {
        final int compliance;
        CompilerFixture(int compliance) { this.compliance = compliance; }
        static IntStream levels() { return IntStream.range(0, invocations); }
        @Test void first() { assertTrue(compliance >= 0); }
        @Test void second() { assertTrue(compliance >= 0); }
        @Test void third() { assertTrue(compliance >= 0); }
    }

    @Suite @SelectClasses(CompilerFixture.class)
    public static class InnerSuite { }

    @Suite @SelectClasses(InnerSuite.class)
    public static class OuterSuite { }

    @Test void directParameterizedClassMustWriteResultsOnce() throws Exception {
        assertLinear(run("direct-32", CompilerFixture.class, 32));
    }

    @Test void nestedSuiteMustWriteResultsOnce() throws Exception {
        assertLinear(run("suite-32", OuterSuite.class, 32));
    }

    private static void assertLinear(Observation result) {
        assertEquals(96, result.tests(), "All actual executions must be present in XML");
        assertTrue(result.reportCompletions() <= 2,
                "REPEATED_REPORT_COMPLETION: " + result.reportCompletions());
        assertTrue(result.serializedBytes() <= result.finalBytes() * 2,
                "QUADRATIC_XML_REWRITE: serialized=" + result.serializedBytes()
                + ", final=" + result.finalBytes());
    }

    @SuppressWarnings("unchecked")
    private static Observation run(String name, Class<?> selection, int count) throws Exception {
        invocations = count;
        Path directory = Path.of(System.getProperty("probe.output"), name);
        Files.createDirectories(directory);
        // Reject stale result files; every measurement must start from an empty directory.
        try (var existing = Files.list(directory)) {
            assertEquals(0, existing.count(), "Run clean before repeating a measurement");
        }
        ConsoleLogger logger = (ConsoleLogger) Proxy.newProxyInstance(
                ConsoleLogger.class.getClassLoader(), new Class<?>[]{ConsoleLogger.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
        StartupReportConfiguration config = new StartupReportConfiguration(
                true, false, "PLAIN", false, directory.toFile(), false, null,
                directory.resolve("TESTHASH").toFile(), false, 0, null, "UTF-8",
                false, true, true, false, new SurefireStatelessReporter(),
                new SurefireConsoleOutputReporter(), new SurefireStatelessTestsetInfoReporter(),
                new ReporterFactoryOptions());
        DefaultReporterFactory factory = new DefaultReporterFactory(config, logger);
        TestReportListener<TestOutputReportEntry> delegate = factory.createReporter();
        long[] counters = new long[2];
        TestReportListener<TestOutputReportEntry> measured =
                (TestReportListener<TestOutputReportEntry>) Proxy.newProxyInstance(
                        TestReportListener.class.getClassLoader(), new Class<?>[]{TestReportListener.class},
                        (proxy, method, args) -> {
                            Object result;
                            try {
                                result = method.invoke(delegate, args);
                            } catch (InvocationTargetException e) {
                                throw e.getCause();
                            }
                            if (method.getName().equals("testSetCompleted")) {
                                counters[0]++;
                                ReportEntry entry = (ReportEntry) args[0];
                                Path xml = directory.resolve("TEST-" + entry.getSourceName() + ".xml");
                                assertTrue(Files.isRegularFile(xml), "Missing real XML report: " + xml);
                                // The reporter truncates and rewrites this file on completion.
                                // Count its whole resulting size, not the change in final size.
                                counters[1] += Files.size(xml);
                            }
                            return result;
                        });
        RunListenerAdapter adapter = new RunListenerAdapter(measured, Stoppable.NOOP);
        adapter.setRunMode(RunMode.NORMAL_RUN);
        SummaryGeneratingListener summary = new SummaryGeneratingListener();
        long start = System.nanoTime();
        try {
            LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectClass(selection))
                    .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
                    .build(), adapter, summary);
        } finally {
            factory.close();
        }
        long nanos = System.nanoTime() - start;
        assertEquals(count * 3L, summary.getSummary().getTestsSucceededCount());
        assertEquals(0, summary.getSummary().getTotalFailureCount());
        long bytes = 0;
        List<String> inventory = new ArrayList<>();
        DocumentBuilderFactory parser = DocumentBuilderFactory.newInstance();
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try (var reports = Files.list(directory)) {
            for (Path file : reports.filter(p -> p.getFileName().toString().endsWith(".xml")).sorted().toList()) {
                bytes += Files.size(file);
                var cases = parser.newDocumentBuilder().parse(file.toFile()).getElementsByTagName("testcase");
                for (int i = 0; i < cases.getLength(); i++) {
                    var element = (org.w3c.dom.Element) cases.item(i);
                    inventory.add(element.getAttribute("classname") + "#" + element.getAttribute("name"));
                }
            }
        }
        inventory.sort(String::compareTo);
        Files.write(directory.resolve("inventory.txt"), inventory);
        Observation observation = new Observation(counters[0], counters[1], bytes, inventory.size());
        Properties evidence = new Properties();
        evidence.setProperty("selectedClass", selection.getName());
        evidence.setProperty("invocations", Integer.toString(count));
        evidence.setProperty("reportCompletions", Long.toString(counters[0]));
        evidence.setProperty("serializedBytes", Long.toString(counters[1]));
        evidence.setProperty("finalBytes", Long.toString(bytes));
        evidence.setProperty("testcases", Integer.toString(inventory.size()));
        evidence.setProperty("elapsedNanosIncludingInstrumentation", Long.toString(nanos));
        evidence.setProperty("adapterLocation", RunListenerAdapter.class.getProtectionDomain().getCodeSource().getLocation().toString());
        try (var output = Files.newOutputStream(directory.resolve("measurement.properties"))) {
            evidence.store(output, "Actual adapter/reporter measurement; not a JDT performance claim");
        }
        System.out.println(name + " " + observation);
        return observation;
    }

    private record Observation(long reportCompletions, long serializedBytes, long finalBytes, long tests) { }
}

/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.apache.maven.surefire.junitplatform;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.apache.maven.plugin.surefire.report.StatelessXmlReporter;
import org.apache.maven.surefire.api.report.ReportEntry;
import org.apache.maven.surefire.api.report.ReporterFactoryOptions;
import org.apache.maven.surefire.api.report.RunMode;
import org.apache.maven.surefire.api.report.Stoppable;
import org.apache.maven.surefire.api.report.TestOutputReportEntry;
import org.apache.maven.surefire.api.report.TestReportListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/** Real adapter, engine and reporter. The proxy only measures delegated calls. */
@Execution(ExecutionMode.SAME_THREAD)
class ReportingProbeTest {
    private static int invocations;
    @ParameterizedClass(name = "compliance={0}") @MethodSource("levels")
    public static class CompilerFixture {
        final int compliance;
        CompilerFixture(int compliance) { this.compliance = compliance; }
        static IntStream levels() { return IntStream.range(0, invocations); }
        @Test void first() { assertTrue(compliance >= 0); }
        @Test void second() { assertTrue(compliance >= 0); }
        @Test void third() { assertTrue(compliance >= 0); }
    }
    @Suite @SelectClasses(CompilerFixture.class) public static class InnerSuite { }
    @Suite @SelectClasses(InnerSuite.class) public static class OuterSuite { }

    @ParameterizedClass(name = "compliance={0}") @MethodSource("levels")
    public static class OutcomeFixture {
        OutcomeFixture(int compliance) { }
        static IntStream levels() { return IntStream.range(0, invocations); }
        @Test void passes() { }
        @Test void fails() { fail("expected assertion failure"); }
        @Test void errors() { throw new IllegalStateException("expected test error"); }
        @Test void aborts() { Assumptions.assumeTrue(false, "expected assumption"); }
        @Test @Disabled("expected disabled method") void disabled() { fail("must not execute"); }
    }
    public static class GoodControl { @Test void control() { } }
    public static class BrokenBeforeAll {
        @BeforeAll static void setup() { throw new IllegalStateException("expected before-all error"); }
        @Test void unreachable() { fail("must not execute"); }
    }
    public static class BrokenAfterAll {
        @Test void control() { }
        @AfterAll static void cleanup() { throw new IllegalStateException("expected after-all error"); }
    }
    @Disabled("expected disabled class")
    public static class DisabledContainer {
        @Test void first() { fail("must not execute"); }
        @Test void second() { fail("must not execute"); }
    }
    @Suite @SelectClasses(OutcomeFixture.class) public static class OutcomeSuite { }
    @Suite @SelectClasses({GoodControl.class, BrokenBeforeAll.class}) public static class BeforeAllSuite { }
    @Suite @SelectClasses({GoodControl.class, BrokenAfterAll.class}) public static class AfterAllSuite { }
    @Suite @SelectClasses({CompilerFixture.class, DisabledContainer.class}) public static class DisabledSuite { }

    @Test void allOutcomeStatesRemainVisible() throws Exception {
        run("outcomes", 2, new Expected(2, 4, 2, 2, 0, 10, 2, 2, 4), OutcomeSuite.class);
    }
    @Test void nestedBeforeAllFailureRemainsVisible() throws Exception {
        run("before-all", 1, new Expected(1, 0, 0, 0, 1, 2, 0, 1, 0), BeforeAllSuite.class);
    }
    @Test void nestedAfterAllFailureRemainsVisible() throws Exception {
        run("after-all", 1, new Expected(2, 0, 0, 0, 1, 3, 0, 1, 0), AfterAllSuite.class);
    }
    @Test void disabledClassMustNotFlushItsRunningOwner() throws Exception {
        Observation result = run("disabled-class", 4, new Expected(12, 0, 0, 2, 0, 14, 0, 0, 2), DisabledSuite.class);
        if (!Boolean.getBoolean("probe.expectBaseline")) assertEquals(1, result.reportCompletions());
    }
    @Test void directParameterizedClassMustWriteResultsOnce() throws Exception {
        assertReporting(run("direct-32", CompilerFixture.class, 32));
    }
    @Test void nestedSuiteMustWriteResultsOnce() throws Exception {
        assertReporting(run("suite-32", OuterSuite.class, 32));
    }
    @Test void doublingInvocationsMustNotQuadrupleSerializedData() throws Exception {
        Observation small = run("scale-32", OuterSuite.class, 32);
        Observation large = run("scale-64", OuterSuite.class, 64);
        assertEquals(96, small.tests());
        assertEquals(192, large.tests());
        if (Boolean.getBoolean("probe.expectBaseline")) {
            assertTrue(large.serializedBytes() > small.serializedBytes() * 2,
                    "The known baseline amplification must actually be reproduced");
        } else {
            assertEquals(1, large.reportCompletions());
            assertTrue(large.serializedBytes() <= small.serializedBytes() * 2 + 1024,
                    "Doubling the workload must grow the final report approximately linearly");
        }
    }
    private static void assertReporting(Observation result) {
        assertEquals(96, result.tests(), "All actual executions must be present in XML");
        if (Boolean.getBoolean("probe.expectBaseline")) {
            assertTrue(result.reportCompletions() > 2, "Missing baseline reproduction");
            assertTrue(result.serializedBytes() > result.finalBytes() * 2, "Missing baseline rewrite amplification");
        } else {
            assertEquals(1, result.reportCompletions(), "REPEATED_REPORT_COMPLETION");
            assertEquals(result.finalBytes(), result.serializedBytes(), "QUADRATIC_XML_REWRITE");
        }
    }
    private static Observation run(String name, Class<?> selection, int count) throws Exception {
        return run(name, count, new Expected(count * 3L, 0, 0, 0, 0, count * 3L, 0, 0, 0), selection);
    }
    @SuppressWarnings("unchecked")
    private static Observation run(String name, int count, Expected expected, Class<?>... selections) throws Exception {
        invocations = count;
        Path directory = Path.of(System.getProperty("probe.output"), name);
        Files.createDirectories(directory);
        try (var existing = Files.list(directory)) {
            assertEquals(0, existing.count(), "Run clean or use a fresh evidence directory");
        }
        boolean baseline = Boolean.getBoolean("probe.expectBaseline");
        String adapterLocation = RunListenerAdapter.class.getProtectionDomain().getCodeSource().getLocation().toString();
        String reporterLocation = StatelessXmlReporter.class.getProtectionDomain().getCodeSource().getLocation().toString();
        assertEquals(baseline, adapterLocation.endsWith("surefire-junit-platform-3.5.6.jar"), "Wrong adapter: " + adapterLocation);
        assertEquals(baseline, reporterLocation.endsWith("maven-surefire-common-3.5.6.jar"), "Wrong reporter: " + reporterLocation);
        ConsoleLogger logger = (ConsoleLogger) Proxy.newProxyInstance(ConsoleLogger.class.getClassLoader(),
                new Class<?>[]{ConsoleLogger.class}, (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
        StartupReportConfiguration config = new StartupReportConfiguration(true, false, "PLAIN", false,
                directory.toFile(), false, null, directory.resolve("TESTHASH").toFile(), false, 0,
                "https://maven.apache.org/surefire/maven-surefire-plugin/xsd/surefire-test-report.xsd", "UTF-8",
                false, true, true, false, new SurefireStatelessReporter(), new SurefireConsoleOutputReporter(),
                new SurefireStatelessTestsetInfoReporter(), new ReporterFactoryOptions());
        DefaultReporterFactory factory = new DefaultReporterFactory(config, logger);
        TestReportListener<TestOutputReportEntry> delegate = factory.createTestReportListener();
        long[] counters = new long[2];
        TestReportListener<TestOutputReportEntry> measured = (TestReportListener<TestOutputReportEntry>) Proxy.newProxyInstance(
                TestReportListener.class.getClassLoader(), new Class<?>[]{TestReportListener.class}, (proxy, method, args) -> {
                    Object result;
                    try { result = method.invoke(delegate, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                    if (method.getName().equals("testSetCompleted")) {
                        counters[0]++;
                        ReportEntry entry = (ReportEntry) args[0];
                        Path xml = directory.resolve("TEST-" + entry.getSourceName() + ".xml");
                        assertTrue(Files.isRegularFile(xml), "Missing XML report: " + xml);
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
                    .selectors(Arrays.stream(selections).map(c -> selectClass(c)).toList())
                    .configurationParameter("junit.jupiter.execution.parallel.enabled", "false").build(), adapter, summary);
        } finally { factory.close(); }
        long nanos = System.nanoTime() - start;
        assertEquals(expected.successes(), summary.getSummary().getTestsSucceededCount(), "successful executions");
        assertEquals(expected.failures(), summary.getSummary().getTestsFailedCount(), "failed executions");
        assertEquals(expected.aborted(), summary.getSummary().getTestsAbortedCount(), "aborted executions");
        assertEquals(expected.skipped(), summary.getSummary().getTestsSkippedCount(), "skipped executions");
        assertEquals(expected.containerFailures(), summary.getSummary().getContainersFailedCount(), "failed containers");
        long bytes = 0;
        List<String> inventory = new ArrayList<>();
        long[] states = new long[3];
        long[] headers = new long[4];
        DocumentBuilderFactory parser = DocumentBuilderFactory.newInstance();
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try (var reports = Files.list(directory)) {
            for (Path file : reports.filter(p -> p.getFileName().toString().endsWith(".xml")).sorted().toList()) {
                bytes += Files.size(file);
                var document = parser.newDocumentBuilder().parse(file.toFile());
                var suite = document.getDocumentElement();
                String[] attributes = {"tests", "failures", "errors", "skipped"};
                for (int h = 0; h < attributes.length; h++) headers[h] += Long.parseLong(suite.getAttribute(attributes[h]));
                var cases = document.getElementsByTagName("testcase");
                for (int i = 0; i < cases.getLength(); i++) {
                    var element = (org.w3c.dom.Element) cases.item(i);
                    String state = "PASS";
                    String[] tags = {"failure", "error", "skipped"};
                    for (int k = 0; k < tags.length; k++) {
                        if (element.getElementsByTagName(tags[k]).getLength() != 0) { states[k]++; state = tags[k]; }
                    }
                    inventory.add(element.getAttribute("classname") + "#" + element.getAttribute("name") + "|" + state);
                }
            }
        }
        inventory.sort(String::compareTo);
        Files.write(directory.resolve("inventory.txt"), inventory);
        Observation observation = new Observation(counters[0], counters[1], bytes, inventory.size());
        Properties evidence = new Properties();
        evidence.setProperty("selectedClass", Arrays.toString(selections));
        evidence.setProperty("invocations", Integer.toString(count));
        evidence.setProperty("reportCompletions", Long.toString(counters[0]));
        evidence.setProperty("serializedBytes", Long.toString(counters[1]));
        evidence.setProperty("finalBytes", Long.toString(bytes));
        evidence.setProperty("testcases", Integer.toString(inventory.size()));
        evidence.setProperty("xmlSuiteTotals", Arrays.toString(headers));
        evidence.setProperty("xmlOutcomeCounts", Arrays.toString(states));
        evidence.setProperty("elapsedNanosIncludingInstrumentation", Long.toString(nanos));
        evidence.setProperty("adapterLocation", adapterLocation);
        evidence.setProperty("reporterLocation", reporterLocation);
        try (var output = Files.newOutputStream(directory.resolve("measurement.properties"))) {
            evidence.store(output, "Actual adapter/reporter measurement; not a JDT performance claim");
        }
        assertEquals(expected.xmlCases(), inventory.size(), "XML test case multiplicity");
        assertArrayEquals(new long[]{expected.xmlFailures(), expected.xmlErrors(), expected.xmlSkipped()}, states, "XML outcomes");
        if (!baseline) assertArrayEquals(new long[]{expected.xmlCases(), expected.xmlFailures(), expected.xmlErrors(), expected.xmlSkipped()}, headers, "XML suite totals");
        System.out.println(name + " " + observation);
        return observation;
    }
    private record Expected(long successes, long failures, long aborted, long skipped, long containerFailures, long xmlCases, long xmlFailures, long xmlErrors, long xmlSkipped) { }
    private record Observation(long reportCompletions, long serializedBytes, long finalBytes, long tests) { }
}

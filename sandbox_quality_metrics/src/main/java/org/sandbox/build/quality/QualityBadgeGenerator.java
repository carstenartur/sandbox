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
package org.sandbox.build.quality;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Generates commit-bound Shields endpoint JSON and a compact test index from
 * Maven/JUnit and aggregate JaCoCo XML evidence.
 */
public final class QualityBadgeGenerator {

	private static final String DEFAULT_COVERAGE_REPORT =
			"sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml"; //$NON-NLS-1$
	private static final String INSTRUCTION = "INSTRUCTION"; //$NON-NLS-1$

	private QualityBadgeGenerator() {
	}

	/** Exact totals collected from the executed JUnit XML reports. */
	public record TestTotals(long tests, long failures, long errors, long skipped, long reportFiles) {
		public long executed() {
			return tests - skipped;
		}

		public long passed() {
			return executed() - failures - errors;
		}
	}

	/** Aggregate JaCoCo counter selected from the report root. */
	public record CoverageTotals(long covered, long missed, double percent, String metric) {
	}

	/** Result returned after successful generation. */
	public record Result(TestTotals tests, CoverageTotals coverage) {
	}

	/**
	 * Generates the badge JSON, machine-readable summary and test index.
	 *
	 * @param root repository root containing Maven target directories
	 * @param output output directory to populate
	 * @param coverageReport aggregate JaCoCo XML, absolute or relative to root
	 * @param commit source commit represented by the evidence
	 * @param generatedAt generation timestamp
	 * @return collected totals
	 * @throws IOException if evidence is absent, malformed or inconsistent
	 */
	public static Result generate(Path root, Path output, Path coverageReport, String commit,
			String generatedAt) throws IOException {
		Path normalizedRoot = root.toAbsolutePath().normalize();
		Path normalizedOutput = output.toAbsolutePath().normalize();
		Path normalizedCoverage = coverageReport.isAbsolute()
				? coverageReport.normalize()
				: normalizedRoot.resolve(coverageReport).normalize();
		String sourceCommit = requireText(commit, "commit"); //$NON-NLS-1$
		String timestamp = requireText(generatedAt, "generatedAt"); //$NON-NLS-1$

		TestTotals tests = collectTests(normalizedRoot);
		CoverageTotals coverage = collectCoverage(normalizedCoverage, INSTRUCTION);
		write(normalizedOutput.resolve("badges/tests.json"), testBadgeJson(tests)); //$NON-NLS-1$
		write(normalizedOutput.resolve("badges/coverage.json"), coverageBadgeJson(coverage)); //$NON-NLS-1$
		write(normalizedOutput.resolve("quality-summary.json"), //$NON-NLS-1$
				summaryJson(sourceCommit, timestamp, tests, coverage));
		write(normalizedOutput.resolve("tests/index.html"), //$NON-NLS-1$
				testIndexHtml(normalizedRoot, sourceCommit, timestamp, tests, coverage));
		return new Result(tests, coverage);
	}

	static TestTotals collectTests(Path root) throws IOException {
		List<Path> reports = junitReportFiles(root);
		if (reports.isEmpty()) {
			throw new IOException("No JUnit XML reports found below " + root); //$NON-NLS-1$
		}
		long tests = 0;
		long failures = 0;
		long errors = 0;
		long skipped = 0;
		for (Path report : reports) {
			TestTotals current = parseJUnitReport(report);
			tests = Math.addExact(tests, current.tests());
			failures = Math.addExact(failures, current.failures());
			errors = Math.addExact(errors, current.errors());
			skipped = Math.addExact(skipped, current.skipped());
		}
		TestTotals totals = new TestTotals(tests, failures, errors, skipped, reports.size());
		validateTotals(totals, root.toString());
		return totals;
	}

	static CoverageTotals collectCoverage(Path report, String metric) throws IOException {
		if (!Files.isRegularFile(report)) {
			throw new IOException("Aggregate JaCoCo report is missing: " + report); //$NON-NLS-1$
		}
		Document document = parseXml(report);
		Element root = document.getDocumentElement();
		String requested = requireText(metric, "metric").toUpperCase(Locale.ROOT); //$NON-NLS-1$
		List<Element> counters = directChildren(root, "counter").stream() //$NON-NLS-1$
				.filter(counter -> requested.equals(counter.getAttribute("type"))) //$NON-NLS-1$
				.toList();
		if (counters.size() != 1) {
			throw new IOException("Expected exactly one aggregate " + requested //$NON-NLS-1$
					+ " counter in " + report + ", found " + counters.size()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		Element counter = counters.get(0);
		long covered = requiredLong(counter, "covered", report); //$NON-NLS-1$
		long missed = requiredLong(counter, "missed", report); //$NON-NLS-1$
		long total = Math.addExact(covered, missed);
		if (total <= 0) {
			throw new IOException("Aggregate " + requested + " counter contains no evidence in " + report); //$NON-NLS-1$ //$NON-NLS-2$
		}
		double percent = Math.round(covered * 10000.0 / total) / 100.0;
		return new CoverageTotals(covered, missed, percent, requested);
	}

	static List<ModuleReport> moduleReports(Path root) throws IOException {
		try (Stream<Path> paths = Files.walk(root)) {
			return paths.filter(Files::isRegularFile)
					.filter(path -> "surefire-report.html".equals(fileName(path))) //$NON-NLS-1$
					.filter(path -> unixPath(root.relativize(path))
							.endsWith("/target/site/surefire-report.html")) //$NON-NLS-1$
					.map(path -> toModuleReport(root, path))
					.sorted(Comparator.comparing(ModuleReport::module))
					.toList();
		}
	}

	record ModuleReport(String module, String href) {
	}

	private static List<Path> junitReportFiles(Path root) throws IOException {
		try (Stream<Path> paths = Files.walk(root)) {
			return paths.filter(Files::isRegularFile)
					.filter(path -> fileName(path).startsWith("TEST-")) //$NON-NLS-1$
					.filter(path -> fileName(path).endsWith(".xml")) //$NON-NLS-1$
					.filter(path -> {
						String relative = unixPath(root.relativize(path));
						return relative.contains("/target/surefire-reports/") //$NON-NLS-1$
								|| relative.contains("/target/failsafe-reports/"); //$NON-NLS-1$
					})
					.sorted(Comparator.comparing(path -> unixPath(root.relativize(path))))
					.toList();
		}
	}

	private static TestTotals parseJUnitReport(Path report) throws IOException {
		Document document = parseXml(report);
		Element root = document.getDocumentElement();
		String rootName = localName(root);
		if (!"testsuite".equals(rootName) && !"testsuites".equals(rootName)) { //$NON-NLS-1$ //$NON-NLS-2$
			throw new IOException("Unsupported JUnit root element '" + rootName + "' in " + report); //$NON-NLS-1$ //$NON-NLS-2$
		}

		List<Element> suites = descendants(root, "testsuite"); //$NON-NLS-1$
		if ("testsuite".equals(rootName)) { //$NON-NLS-1$
			suites.add(0, root);
		}
		if (suites.isEmpty()) {
			throw new IOException("No testsuite data in " + report); //$NON-NLS-1$
		}
		for (Element suite : suites) {
			validateSuiteSummary(suite, report);
		}

		List<Element> testCases = descendants(root, "testcase"); //$NON-NLS-1$
		if (testCases.isEmpty()) {
			throw new IOException("JUnit report contains no testcase elements: " + report); //$NON-NLS-1$
		}
		if ("testsuites".equals(rootName) && root.hasAttribute("tests")) { //$NON-NLS-1$ //$NON-NLS-2$
			long declared = requiredLong(root, "tests", report); //$NON-NLS-1$
			if (declared != testCases.size()) {
				throw new IOException("JUnit aggregate declares " + declared + " tests but contains " //$NON-NLS-1$ //$NON-NLS-2$
						+ testCases.size() + " testcases in " + report); //$NON-NLS-1$
			}
		}

		long failures = 0;
		long errors = 0;
		long skipped = 0;
		for (Element testCase : testCases) {
			boolean failed = hasDirectChild(testCase, "failure"); //$NON-NLS-1$
			boolean errored = hasDirectChild(testCase, "error"); //$NON-NLS-1$
			boolean ignored = hasDirectChild(testCase, "skipped"); //$NON-NLS-1$
			int outcomes = (failed ? 1 : 0) + (errored ? 1 : 0) + (ignored ? 1 : 0);
			if (outcomes > 1) {
				throw new IOException("JUnit testcase has conflicting outcomes in " + report); //$NON-NLS-1$
			}
			failures += failed ? 1 : 0;
			errors += errored ? 1 : 0;
			skipped += ignored ? 1 : 0;
		}
		TestTotals totals = new TestTotals(testCases.size(), failures, errors, skipped, 1);
		validateTotals(totals, report.toString());
		return totals;
	}

	private static void validateSuiteSummary(Element suite, Path report) throws IOException {
		List<Element> testCases = descendants(suite, "testcase"); //$NON-NLS-1$
		long declared = requiredLong(suite, "tests", report); //$NON-NLS-1$
		if (declared != testCases.size()) {
			throw new IOException("JUnit suite declares " + declared + " tests but contains " //$NON-NLS-1$ //$NON-NLS-2$
					+ testCases.size() + " testcases in " + report); //$NON-NLS-1$
		}
		validateOptionalCount(suite, "failures", testCases, "failure", report); //$NON-NLS-1$ //$NON-NLS-2$
		validateOptionalCount(suite, "errors", testCases, "error", report); //$NON-NLS-1$ //$NON-NLS-2$
		validateOptionalCount(suite, "skipped", testCases, "skipped", report); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void validateOptionalCount(Element suite, String attribute,
			List<Element> testCases, String outcome, Path report) throws IOException {
		if (!suite.hasAttribute(attribute)) {
			return;
		}
		long declared = requiredLong(suite, attribute, report);
		long actual = testCases.stream().filter(testCase -> hasDirectChild(testCase, outcome)).count();
		if (declared != actual) {
			throw new IOException("JUnit suite declares " + declared + " " + attribute //$NON-NLS-1$ //$NON-NLS-2$
					+ " but contains " + actual + " in " + report); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static void validateTotals(TestTotals totals, String source) throws IOException {
		if (totals.tests() <= 0 || totals.failures() < 0 || totals.errors() < 0
				|| totals.skipped() < 0 || totals.executed() < 0 || totals.passed() < 0) {
			throw new IOException("Inconsistent JUnit totals in " + source + ": " + totals); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static Document parseXml(Path source) throws IOException {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false); //$NON-NLS-1$
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); //$NON-NLS-1$
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); //$NON-NLS-1$
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); //$NON-NLS-1$
			return factory.newDocumentBuilder().parse(source.toFile());
		} catch (ParserConfigurationException | SAXException | IllegalArgumentException exception) {
			throw new IOException("Cannot parse XML evidence " + source + ": " //$NON-NLS-1$ //$NON-NLS-2$
					+ exception.getMessage(), exception);
		}
	}

	private static long requiredLong(Element element, String attribute, Path source) throws IOException {
		if (!element.hasAttribute(attribute)) {
			throw new IOException("Missing required '" + attribute + "' attribute on " //$NON-NLS-1$ //$NON-NLS-2$
					+ localName(element) + " in " + source); //$NON-NLS-1$
		}
		String raw = element.getAttribute(attribute).strip();
		try {
			long value = Long.parseLong(raw);
			if (value < 0) {
				throw new NumberFormatException("negative"); //$NON-NLS-1$
			}
			return value;
		} catch (NumberFormatException exception) {
			throw new IOException("Invalid " + attribute + "='" + raw + "' on " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					+ localName(element) + " in " + source, exception); //$NON-NLS-1$
		}
	}

	private static List<Element> descendants(Element parent, String name) {
		List<Element> result = new ArrayList<>();
		collectDescendants(parent, name, result);
		return result;
	}

	private static void collectDescendants(Element parent, String name, List<Element> result) {
		NodeList children = parent.getChildNodes();
		for (int index = 0; index < children.getLength(); index++) {
			Node child = children.item(index);
			if (child instanceof Element element) {
				if (name.equals(localName(element))) {
					result.add(element);
				}
				collectDescendants(element, name, result);
			}
		}
	}

	private static List<Element> directChildren(Element parent, String name) {
		List<Element> result = new ArrayList<>();
		NodeList children = parent.getChildNodes();
		for (int index = 0; index < children.getLength(); index++) {
			Node child = children.item(index);
			if (child instanceof Element element && name.equals(localName(element))) {
				result.add(element);
			}
		}
		return result;
	}

	private static boolean hasDirectChild(Element parent, String name) {
		return !directChildren(parent, name).isEmpty();
	}

	private static String localName(Element element) {
		String local = element.getLocalName();
		return local == null ? element.getTagName() : local;
	}

	private static ModuleReport toModuleReport(Path root, Path report) {
		Path site = requireParent(report, "report site"); //$NON-NLS-1$
		Path target = requireParent(site, "module target"); //$NON-NLS-1$
		Path moduleDirectory = requireParent(target, "module directory"); //$NON-NLS-1$
		String module = unixPath(root.relativize(moduleDirectory));
		return new ModuleReport(module, module + "/surefire-report.html"); //$NON-NLS-1$
	}

	private static Path requireParent(Path path, String description) {
		Path parent = path.getParent();
		if (parent == null) {
			throw new IllegalArgumentException("Cannot resolve " + description + " for " + path); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return parent;
	}

	private static String testBadgeJson(TestTotals totals) {
		long failing = totals.failures() + totals.errors();
		String message = failing == 0
				? totals.tests() + ", " + totals.skipped() + " skipped" //$NON-NLS-1$ //$NON-NLS-2$
				: totals.tests() + ", " + failing + " failing, " + totals.skipped() + " skipped"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return badgeJson("tests", message, failing == 0 ? "brightgreen" : "red"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static String coverageBadgeJson(CoverageTotals totals) {
		return badgeJson("coverage", String.format(Locale.ROOT, "%.1f%%", totals.percent()), //$NON-NLS-1$ //$NON-NLS-2$
				coverageColor(totals.percent()));
	}

	private static String badgeJson(String label, String message, String color) {
		StringBuilder json = new StringBuilder();
		appendLine(json, "{"); //$NON-NLS-1$
		appendLine(json, "  \"schemaVersion\": 1,"); //$NON-NLS-1$
		appendLine(json, "  \"label\": \"" + jsonEscape(label) + "\","); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(json, "  \"message\": \"" + jsonEscape(message) + "\","); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(json, "  \"color\": \"" + jsonEscape(color) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(json, "}"); //$NON-NLS-1$
		return json.toString();
	}

	private static String summaryJson(String commit, String generatedAt, TestTotals tests,
			CoverageTotals coverage) {
		StringBuilder json = new StringBuilder();
		appendLine(json, "{"); //$NON-NLS-1$
		appendLine(json, "  \"schemaVersion\": 1,"); //$NON-NLS-1$
		appendLine(json, "  \"sourceCommit\": \"" + jsonEscape(commit) + "\","); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(json, "  \"generatedAt\": \"" + jsonEscape(generatedAt) + "\","); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(json, "  \"tests\": {"); //$NON-NLS-1$
		appendLine(json, "    \"tests\": " + tests.tests() + ","); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(json, "    \"executed\": " + tests.executed() + ","); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(json, "    \"passed\": " + tests.passed() + ","); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(json, "    \"failures\": " + tests.failures() + ","); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(json, "    \"errors\": " + tests.errors() + ","); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(json, "    \"skipped\": " + tests.skipped() + ","); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(json, "    \"reportFiles\": " + tests.reportFiles()); //$NON-NLS-1$
		appendLine(json, "  },"); //$NON-NLS-1$
		appendLine(json, "  \"coverage\": {"); //$NON-NLS-1$
		appendLine(json, "    \"metric\": \"" + jsonEscape(coverage.metric()) + "\","); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(json, "    \"covered\": " + coverage.covered() + ","); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(json, "    \"missed\": " + coverage.missed() + ","); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(json, "    \"percent\": "
				+ String.format(Locale.ROOT, "%.2f", coverage.percent())); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(json, "  }"); //$NON-NLS-1$
		appendLine(json, "}"); //$NON-NLS-1$
		return json.toString();
	}

	private static String testIndexHtml(Path root, String commit, String generatedAt,
			TestTotals tests, CoverageTotals coverage) throws IOException {
		List<ModuleReport> reports = moduleReports(root);
		if (reports.isEmpty()) {
			throw new IOException("No Maven Site module reports were generated below " + root); //$NON-NLS-1$
		}
		StringBuilder links = new StringBuilder();
		for (ModuleReport report : reports) {
			links.append("<li><a href=\"") //$NON-NLS-1$
					.append(htmlEscape(report.href()))
					.append("\">") //$NON-NLS-1$
					.append(htmlEscape(report.module()))
					.append("</a></li>\n"); //$NON-NLS-1$
		}

		StringBuilder html = new StringBuilder();
		appendLine(html, "<!doctype html>"); //$NON-NLS-1$
		appendLine(html, "<html lang=\"en\">"); //$NON-NLS-1$
		appendLine(html, "<head>"); //$NON-NLS-1$
		appendLine(html, "<meta charset=\"utf-8\">"); //$NON-NLS-1$
		appendLine(html, "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"); //$NON-NLS-1$
		appendLine(html, "<title>Sandbox verified test results</title>"); //$NON-NLS-1$
		appendLine(html, "<style>"); //$NON-NLS-1$
		appendLine(html,
				"body { font: 16px/1.5 system-ui, sans-serif; max-width: 72rem; margin: 2rem auto; padding: 0 1rem; }"); //$NON-NLS-1$
		appendLine(html, "table { border-collapse: collapse; margin: 1rem 0 1.5rem; }"); //$NON-NLS-1$
		appendLine(html, "th, td { border: 1px solid #bbb; padding: .45rem .7rem; text-align: right; }"); //$NON-NLS-1$
		appendLine(html, "th:first-child, td:first-child { text-align: left; }"); //$NON-NLS-1$
		appendLine(html, "code { overflow-wrap: anywhere; }"); //$NON-NLS-1$
		appendLine(html, "</style>"); //$NON-NLS-1$
		appendLine(html, "</head>"); //$NON-NLS-1$
		appendLine(html, "<body>"); //$NON-NLS-1$
		appendLine(html, "<h1>Sandbox verified test results</h1>"); //$NON-NLS-1$
		appendLine(html, "<p>Source commit: <code>" + htmlEscape(commit) + "</code></p>"); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(html, "<p>Generated: " + htmlEscape(generatedAt) + "</p>"); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(html, "<table>"); //$NON-NLS-1$
		appendLine(html, "<thead><tr><th>Metric</th><th>Value</th></tr></thead>"); //$NON-NLS-1$
		appendLine(html, "<tbody>"); //$NON-NLS-1$
		appendLine(html, "<tr><td>Registered tests</td><td>" + tests.tests() + "</td></tr>"); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(html, "<tr><td>Executed</td><td>" + tests.executed() + "</td></tr>"); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(html, "<tr><td>Passed</td><td>" + tests.passed() + "</td></tr>"); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(html, "<tr><td>Skipped</td><td>" + tests.skipped() + "</td></tr>"); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(html, "<tr><td>Failures</td><td>" + tests.failures() + "</td></tr>"); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(html, "<tr><td>Errors</td><td>" + tests.errors() + "</td></tr>"); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(html, "<tr><td>Instruction coverage</td><td>"
				+ String.format(Locale.ROOT, "%.2f%%", coverage.percent()) + "</td></tr>"); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(html, "<tr><td>JUnit report files</td><td>" + tests.reportFiles() + "</td></tr>"); //$NON-NLS-1$ //$NON-NLS-2$
		appendLine(html, "</tbody>"); //$NON-NLS-1$
		appendLine(html, "</table>"); //$NON-NLS-1$
		appendLine(html,
				"<p><a href=\"../coverage/\">Aggregate JaCoCo report</a> · <a href=\"../quality-summary.json\">Machine-readable summary</a></p>"); //$NON-NLS-1$
		appendLine(html, "<h2>Module reports</h2>"); //$NON-NLS-1$
		appendLine(html, "<ul>"); //$NON-NLS-1$
		html.append(links);
		appendLine(html, "</ul>"); //$NON-NLS-1$
		appendLine(html, "</body>"); //$NON-NLS-1$
		appendLine(html, "</html>"); //$NON-NLS-1$
		return html.toString();
	}

	private static void appendLine(StringBuilder target, String line) {
		target.append(line).append('\n');
	}

	private static String coverageColor(double percent) {
		if (percent >= 80.0) {
			return "brightgreen"; //$NON-NLS-1$
		}
		if (percent >= 60.0) {
			return "yellow"; //$NON-NLS-1$
		}
		if (percent >= 40.0) {
			return "orange"; //$NON-NLS-1$
		}
		return "red"; //$NON-NLS-1$
	}

	private static void write(Path path, String content) throws IOException {
		Path parent = path.getParent();
		if (parent == null) {
			throw new IOException("Output path has no parent: " + path); //$NON-NLS-1$
		}
		Files.createDirectories(parent);
		Files.writeString(path, content, StandardCharsets.UTF_8);
	}

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank"); //$NON-NLS-1$
		}
		return value.strip();
	}

	private static String fileName(Path path) {
		Path name = path.getFileName();
		return name == null ? "" : name.toString(); //$NON-NLS-1$
	}

	private static String unixPath(Path path) {
		return path.toString().replace(path.getFileSystem().getSeparator(), "/"); //$NON-NLS-1$
	}

	private static String jsonEscape(String value) {
		StringBuilder result = new StringBuilder(value.length() + 16);
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			switch (character) {
				case '\\' -> result.append("\\\\"); //$NON-NLS-1$
				case '"' -> result.append("\\\""); //$NON-NLS-1$
				case '\b' -> result.append("\\b"); //$NON-NLS-1$
				case '\f' -> result.append("\\f"); //$NON-NLS-1$
				case '\n' -> result.append("\\n"); //$NON-NLS-1$
				case '\r' -> result.append("\\r"); //$NON-NLS-1$
				case '\t' -> result.append("\\t"); //$NON-NLS-1$
				default -> {
					if (character < 0x20) {
						result.append(String.format(Locale.ROOT, "\\u%04x", Integer.valueOf(character))); //$NON-NLS-1$
					} else {
						result.append(character);
					}
				}
			}
		}
		return result.toString();
	}

	private static String htmlEscape(String value) {
		return value.replace("&", "&amp;") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("<", "&lt;") //$NON-NLS-1$ //$NON-NLS-2$
				.replace(">", "&gt;") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\"", "&quot;") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("'", "&#39;"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static Map<String, String> parseArguments(String[] arguments) {
		Map<String, String> values = new LinkedHashMap<>();
		for (int index = 0; index < arguments.length; index++) {
			String option = arguments[index];
			if (!option.startsWith("--") || index + 1 >= arguments.length) { //$NON-NLS-1$
				throw new IllegalArgumentException("Expected --name value arguments, got '" + option + "'"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			String value = arguments[++index];
			if (value.startsWith("--")) { //$NON-NLS-1$
				throw new IllegalArgumentException("Missing value for " + option); //$NON-NLS-1$
			}
			if (values.put(option, value) != null) {
				throw new IllegalArgumentException("Duplicate option " + option); //$NON-NLS-1$
			}
		}
		return values;
	}

	public static void main(String[] arguments) {
		try {
			Map<String, String> options = parseArguments(arguments);
			Path root = Path.of(options.getOrDefault("--root", ".")); //$NON-NLS-1$ //$NON-NLS-2$
			Path output = Path.of(options.getOrDefault("--output", "target/quality-site")); //$NON-NLS-1$ //$NON-NLS-2$
			Path coverage = Path.of(options.getOrDefault("--coverage-report", DEFAULT_COVERAGE_REPORT)); //$NON-NLS-1$
			String commit = options.getOrDefault("--commit", "local"); //$NON-NLS-1$ //$NON-NLS-2$
			String generatedAt = options.getOrDefault("--generated-at", Instant.now().toString()); //$NON-NLS-1$
			Result result = generate(root, output, coverage, commit, generatedAt);
			System.out.println("Generated quality badges: tests=" + result.tests().tests() //$NON-NLS-1$
					+ ", skipped=" + result.tests().skipped() //$NON-NLS-1$
					+ ", coverage=" + String.format(Locale.ROOT, "%.2f%%", result.coverage().percent())); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (IOException | IllegalArgumentException | ArithmeticException exception) {
			System.err.println("Quality badge generation failed: " + exception.getMessage()); //$NON-NLS-1$
			System.exit(1);
		}
	}
}

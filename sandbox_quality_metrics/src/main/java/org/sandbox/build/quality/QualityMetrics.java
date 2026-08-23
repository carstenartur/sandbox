package org.sandbox.build.quality;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Generates commit-bound Shields endpoint data from the JUnit and aggregate
 * JaCoCo XML produced by the authoritative Maven build.
 */
public final class QualityMetrics {

	private static final String TEST_REPORT_PREFIX= "TEST-"; //$NON-NLS-1$
	private static final String XML_SUFFIX= ".xml"; //$NON-NLS-1$

	private QualityMetrics() {
	}

	/** Exact executed-test totals collected from individual testcase elements. */
	public record TestTotals(long total, long passed, long skipped, long failures, long errors, int reportFiles) {
		public TestTotals {
			if (total < 0 || passed < 0 || skipped < 0 || failures < 0 || errors < 0 || reportFiles < 0) {
				throw new IllegalArgumentException("Test totals must not be negative"); //$NON-NLS-1$
			}
			if (passed + skipped + failures + errors != total) {
				throw new IllegalArgumentException("Inconsistent test totals"); //$NON-NLS-1$
			}
		}
	}

	/** Aggregate JaCoCo line counter. */
	public record CoverageTotals(long covered, long missed) {
		public CoverageTotals {
			if (covered < 0 || missed < 0 || covered + missed == 0) {
				throw new IllegalArgumentException("Coverage counters must describe at least one line"); //$NON-NLS-1$
			}
		}

		public BigDecimal percent() {
			return BigDecimal.valueOf(covered)
					.multiply(BigDecimal.valueOf(100))
					.divide(BigDecimal.valueOf(covered + missed), 1, RoundingMode.HALF_UP);
		}
	}

	public static void main(String[] args) throws Exception {
		Arguments arguments= Arguments.parse(args);
		TestTotals tests= collectTests(arguments.reportsRoot());
		CoverageTotals coverage= collectCoverage(arguments.jacocoXml());
		writeSite(arguments.output(), arguments.commit(), tests, coverage);
		System.out.printf(Locale.ROOT,
				"Verified %d tests (%d skipped) and %s%% line coverage for %s%n", //$NON-NLS-1$
				tests.total(), tests.skipped(), coverage.percent().toPlainString(), arguments.commit());
	}

	public static TestTotals collectTests(Path repositoryRoot) throws IOException, ParserConfigurationException, SAXException {
		List<Path> reports;
		try (Stream<Path> files= Files.walk(repositoryRoot)) {
			reports= files.filter(Files::isRegularFile)
					.filter(QualityMetrics::isJUnitXml)
					.sorted()
					.toList();
		}
		if (reports.isEmpty()) {
			throw new IllegalStateException("No JUnit XML reports found below " + repositoryRoot); //$NON-NLS-1$
		}

		long total= 0;
		long passed= 0;
		long skipped= 0;
		long failures= 0;
		long errors= 0;
		Set<Path> canonicalReports= new HashSet<>();
		for (Path report : reports) {
			Path canonical= report.toRealPath();
			if (!canonicalReports.add(canonical)) {
				continue;
			}
			Document document= parseXml(report);
			NodeList cases= document.getElementsByTagName("testcase"); //$NON-NLS-1$
			for (int index= 0; index < cases.getLength(); index++) {
				Element testCase= (Element) cases.item(index);
				total++;
				if (hasDirectChild(testCase, "skipped")) { //$NON-NLS-1$
					skipped++;
				} else if (hasDirectChild(testCase, "failure")) { //$NON-NLS-1$
					failures++;
				} else if (hasDirectChild(testCase, "error")) { //$NON-NLS-1$
					errors++;
				} else {
					passed++;
				}
			}
		}
		if (total == 0) {
			throw new IllegalStateException("JUnit XML reports contain no testcase elements"); //$NON-NLS-1$
		}
		return new TestTotals(total, passed, skipped, failures, errors, canonicalReports.size());
	}

	public static CoverageTotals collectCoverage(Path jacocoXml)
			throws IOException, ParserConfigurationException, SAXException {
		if (!Files.isRegularFile(jacocoXml)) {
			throw new IllegalStateException("Aggregate JaCoCo XML is missing: " + jacocoXml); //$NON-NLS-1$
		}
		Document document= parseXml(jacocoXml);
		Element report= document.getDocumentElement();
		for (Node child= report.getFirstChild(); child != null; child= child.getNextSibling()) {
			if (child instanceof Element counter && "counter".equals(counter.getTagName()) //$NON-NLS-1$
					&& "LINE".equals(counter.getAttribute("type"))) { //$NON-NLS-1$ //$NON-NLS-2$
				long covered= parseCounter(counter, "covered"); //$NON-NLS-1$
				long missed= parseCounter(counter, "missed"); //$NON-NLS-1$
				return new CoverageTotals(covered, missed);
			}
		}
		throw new IllegalStateException("Aggregate JaCoCo report has no root LINE counter: " + jacocoXml); //$NON-NLS-1$
	}

	public static void writeSite(Path output, String commit, TestTotals tests, CoverageTotals coverage) throws IOException {
		if (commit == null || commit.isBlank()) {
			throw new IllegalArgumentException("A verified commit identifier is required"); //$NON-NLS-1$
		}
		Path badges= output.resolve("badges"); //$NON-NLS-1$
		Path testsDirectory= output.resolve("tests"); //$NON-NLS-1$
		Files.createDirectories(badges);
		Files.createDirectories(testsDirectory);

		String testColor= tests.failures() == 0 && tests.errors() == 0 ? "brightgreen" : "red"; //$NON-NLS-1$ //$NON-NLS-2$
		String testMessage= tests.total() + " tests, " + tests.skipped() + " skipped"; //$NON-NLS-1$ //$NON-NLS-2$
		writeJson(badges.resolve("tests.json"), badge("tests", testMessage, testColor)); //$NON-NLS-1$ //$NON-NLS-2$
		writeJson(badges.resolve("skipped.json"), badge("skipped", Long.toString(tests.skipped()), //$NON-NLS-1$ //$NON-NLS-2$
				tests.skipped() == 0 ? "brightgreen" : "blue")); //$NON-NLS-1$ //$NON-NLS-2$
		writeJson(badges.resolve("coverage.json"), //$NON-NLS-1$
				badge("line coverage", coverage.percent().toPlainString() + "%", coverageColor(coverage.percent()))); //$NON-NLS-1$ //$NON-NLS-2$

		String summary= "{\n" //$NON-NLS-1$
				+ "  \"schemaVersion\": 1,\n" //$NON-NLS-1$
				+ "  \"verifiedCommit\": \"" + json(commit) + "\",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  \"generatedAt\": \"" + Instant.now() + "\",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  \"tests\": {\n" //$NON-NLS-1$
				+ "    \"total\": " + tests.total() + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    \"passed\": " + tests.passed() + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    \"skipped\": " + tests.skipped() + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    \"failures\": " + tests.failures() + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    \"errors\": " + tests.errors() + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    \"reportFiles\": " + tests.reportFiles() + "\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  },\n" //$NON-NLS-1$
				+ "  \"coverage\": {\n" //$NON-NLS-1$
				+ "    \"coveredLines\": " + coverage.covered() + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    \"missedLines\": " + coverage.missed() + ",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "    \"linePercent\": " + coverage.percent().toPlainString() + "\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  }\n" //$NON-NLS-1$
				+ "}\n"; //$NON-NLS-1$
		writeJson(output.resolve("quality-summary.json"), summary); //$NON-NLS-1$
		Files.writeString(testsDirectory.resolve("index.html"), htmlReport(commit, tests, coverage), StandardCharsets.UTF_8); //$NON-NLS-1$
		Files.writeString(output.resolve(".nojekyll"), "", StandardCharsets.UTF_8); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static boolean isJUnitXml(Path path) {
		String fileName= path.getFileName().toString();
		if (!fileName.startsWith(TEST_REPORT_PREFIX) || !fileName.endsWith(XML_SUFFIX)) {
			return false;
		}
		List<String> parts= new ArrayList<>();
		path.forEach(part -> parts.add(part.toString()));
		for (int index= 0; index + 1 < parts.size(); index++) {
			if ("target".equals(parts.get(index)) //$NON-NLS-1$
					&& ("surefire-reports".equals(parts.get(index + 1)) //$NON-NLS-1$
							|| "failsafe-reports".equals(parts.get(index + 1)))) { //$NON-NLS-1$
				return true;
			}
		}
		return false;
	}

	private static boolean hasDirectChild(Element element, String tagName) {
		for (Node child= element.getFirstChild(); child != null; child= child.getNextSibling()) {
			if (child instanceof Element childElement && tagName.equals(childElement.getTagName())) {
				return true;
			}
		}
		return false;
	}

	private static long parseCounter(Element counter, String name) {
		try {
			return Long.parseLong(counter.getAttribute(name));
		} catch (NumberFormatException exception) {
			throw new IllegalStateException("Invalid JaCoCo " + name + " counter", exception); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static Document parseXml(Path path) throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory factory= DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false); //$NON-NLS-1$
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); //$NON-NLS-1$
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		DocumentBuilder builder= factory.newDocumentBuilder();
		return builder.parse(path.toFile());
	}

	private static String badge(String label, String message, String color) {
		return "{\n" //$NON-NLS-1$
				+ "  \"schemaVersion\": 1,\n" //$NON-NLS-1$
				+ "  \"label\": \"" + json(label) + "\",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  \"message\": \"" + json(message) + "\",\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "  \"color\": \"" + json(color) + "\"\n" //$NON-NLS-1$ //$NON-NLS-2$
				+ "}\n"; //$NON-NLS-1$
	}

	private static String coverageColor(BigDecimal percent) {
		if (percent.compareTo(BigDecimal.valueOf(80)) >= 0) {
			return "brightgreen"; //$NON-NLS-1$
		}
		if (percent.compareTo(BigDecimal.valueOf(60)) >= 0) {
			return "yellow"; //$NON-NLS-1$
		}
		if (percent.compareTo(BigDecimal.valueOf(40)) >= 0) {
			return "orange"; //$NON-NLS-1$
		}
		return "red"; //$NON-NLS-1$
	}

	private static void writeJson(Path path, String content) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, content, StandardCharsets.UTF_8);
	}

	private static String json(String value) {
		StringWriter writer= new StringWriter();
		value.codePoints().forEach(codePoint -> {
			switch (codePoint) {
				case '\\' -> writer.write("\\\\"); //$NON-NLS-1$
				case '"' -> writer.write("\\\""); //$NON-NLS-1$
				case '\n' -> writer.write("\\n"); //$NON-NLS-1$
				case '\r' -> writer.write("\\r"); //$NON-NLS-1$
				case '\t' -> writer.write("\\t"); //$NON-NLS-1$
				default -> writer.write(Character.toChars(codePoint), 0, Character.charCount(codePoint));
			}
		});
		return writer.toString();
	}

	private static String htmlReport(String commit, TestTotals tests, CoverageTotals coverage) {
		return """
				<!doctype html>
				<html lang="en">
				<head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
				<title>Sandbox verified quality metrics</title>
				<style>body{font:16px/1.5 system-ui,sans-serif;max-width:60rem;margin:2rem auto;padding:0 1rem}table{border-collapse:collapse}th,td{border:1px solid #bbb;padding:.45rem .7rem;text-align:right}th:first-child,td:first-child{text-align:left}</style></head>
				<body><h1>Sandbox verified quality metrics</h1>
				<p>Verified commit: <code>%s</code></p>
				<table><tbody>
				<tr><th>Total tests</th><td>%d</td></tr>
				<tr><th>Passed</th><td>%d</td></tr>
				<tr><th>Skipped</th><td>%d</td></tr>
				<tr><th>Failures</th><td>%d</td></tr>
				<tr><th>Errors</th><td>%d</td></tr>
				<tr><th>JUnit XML reports</th><td>%d</td></tr>
				<tr><th>Covered lines</th><td>%d</td></tr>
				<tr><th>Missed lines</th><td>%d</td></tr>
				<tr><th>Line coverage</th><td>%s%%</td></tr>
				</tbody></table>
				<p><a href="../quality-summary.json">Machine-readable summary</a> · <a href="../coverage/">JaCoCo report</a></p>
				</body></html>
				""".formatted(escapeHtml(commit), tests.total(), tests.passed(), tests.skipped(), tests.failures(), //$NON-NLS-1$
						tests.errors(), tests.reportFiles(), coverage.covered(), coverage.missed(), coverage.percent());
	}

	private static String escapeHtml(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
	}

	private record Arguments(Path reportsRoot, Path jacocoXml, Path output, String commit) {
		static Arguments parse(String[] args) {
			Path reportsRoot= Path.of("."); //$NON-NLS-1$
			Path jacocoXml= Path.of("sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml"); //$NON-NLS-1$
			Path output= Path.of("target/quality-site"); //$NON-NLS-1$
			String commit= null;
			for (int index= 0; index < args.length; index++) {
				String option= args[index];
				if (index + 1 >= args.length) {
					throw new IllegalArgumentException("Missing value for " + option); //$NON-NLS-1$
				}
				String value= args[++index];
				switch (option) {
					case "--reports-root" -> reportsRoot= Path.of(value); //$NON-NLS-1$
					case "--jacoco" -> jacocoXml= Path.of(value); //$NON-NLS-1$
					case "--output" -> output= Path.of(value); //$NON-NLS-1$
					case "--commit" -> commit= value; //$NON-NLS-1$
					default -> throw new IllegalArgumentException("Unknown option: " + option); //$NON-NLS-1$
				}
			}
			if (commit == null || commit.isBlank()) {
				throw new IllegalArgumentException("--commit is required"); //$NON-NLS-1$
			}
			return new Arguments(reportsRoot, jacocoXml, output, commit);
		}
	}
}

package org.sandbox.build.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXParseException;

class QualityMetricsTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void countsIndividualTestCasesAcrossSurefireAndFailsafeReports() throws Exception {
		write("module-a/target/surefire-reports/TEST-a.xml", """ //$NON-NLS-1$
				<testsuite name="a" tests="3" failures="1" errors="0" skipped="1">
				  <testcase classname="A" name="passes"/>
				  <testcase classname="A" name="skips"><skipped/></testcase>
				  <testcase classname="A" name="fails"><failure message="boom"/></testcase>
				</testsuite>
				""");
		write("module-b/target/failsafe-reports/TEST-b.xml", """ //$NON-NLS-1$
				<testsuites>
				  <testsuite name="b" tests="2" failures="0" errors="1" skipped="0">
				    <testcase classname="B" name="passes"/>
				    <testcase classname="B" name="errors"><error message="broken"/></testcase>
				  </testsuite>
				</testsuites>
				""");
		write("module-c/target/site/TEST-not-a-runtime-report.xml", """ //$NON-NLS-1$
				<testsuite><testcase classname="Ignored" name="ignored"/></testsuite>
				""");

		QualityMetrics.TestTotals totals= QualityMetrics.collectTests(temporaryDirectory);

		assertEquals(5, totals.total());
		assertEquals(2, totals.passed());
		assertEquals(1, totals.skipped());
		assertEquals(1, totals.failures());
		assertEquals(1, totals.errors());
		assertEquals(2, totals.reportFiles());
	}

	@Test
	void readsOnlyTheAggregateRootLineCounter() throws Exception {
		Path report= write("jacoco.xml", """ //$NON-NLS-1$
				<report name="aggregate">
				  <package name="demo"><counter type="LINE" missed="99" covered="1"/></package>
				  <counter type="INSTRUCTION" missed="500" covered="500"/>
				  <counter type="LINE" missed="25" covered="75"/>
				</report>
				""");

		QualityMetrics.CoverageTotals totals= QualityMetrics.collectCoverage(report);

		assertEquals(75, totals.covered());
		assertEquals(25, totals.missed());
		assertEquals("75.0", totals.percent().toPlainString()); //$NON-NLS-1$
	}

	@Test
	void writesExactBadgeAndMachineReadableValues() throws Exception {
		Path output= temporaryDirectory.resolve("site"); //$NON-NLS-1$
		QualityMetrics.writeSite(output, "abc123", //$NON-NLS-1$
				new QualityMetrics.TestTotals(10, 7, 2, 1, 0, 3),
				new QualityMetrics.CoverageTotals(872, 128));

		String testsBadge= Files.readString(output.resolve("badges/tests.json")); //$NON-NLS-1$
		String skippedBadge= Files.readString(output.resolve("badges/skipped.json")); //$NON-NLS-1$
		String coverageBadge= Files.readString(output.resolve("badges/coverage.json")); //$NON-NLS-1$
		String summary= Files.readString(output.resolve("quality-summary.json")); //$NON-NLS-1$

		assertTrue(testsBadge.contains("\"message\": \"10 tests, 2 skipped\"")); //$NON-NLS-1$
		assertTrue(skippedBadge.contains("\"message\": \"2\"")); //$NON-NLS-1$
		assertTrue(coverageBadge.contains("\"message\": \"87.2%\"")); //$NON-NLS-1$
		assertTrue(summary.contains("\"verifiedCommit\": \"abc123\"")); //$NON-NLS-1$
		assertTrue(summary.contains("\"linePercent\": 87.2")); //$NON-NLS-1$
	}

	@Test
	void rejectsExternalEntityDeclarations() throws Exception {
		Path report= write("module/target/surefire-reports/TEST-unsafe.xml", """ //$NON-NLS-1$
				<!DOCTYPE testsuite [<!ENTITY external SYSTEM "file:///etc/passwd">]>
				<testsuite><testcase classname="A" name="&external;"/></testsuite>
				""");

		assertThrows(SAXParseException.class, () -> QualityMetrics.collectTests(temporaryDirectory));
		assertTrue(Files.isRegularFile(report));
	}

	@Test
	void rejectsMissingEvidenceInsteadOfPublishingZeroes() {
		assertThrows(IllegalStateException.class, () -> QualityMetrics.collectTests(temporaryDirectory));
		assertThrows(IllegalStateException.class,
				() -> QualityMetrics.collectCoverage(temporaryDirectory.resolve("missing-jacoco.xml"))); //$NON-NLS-1$
	}

	private Path write(String relativePath, String content) throws Exception {
		Path path= temporaryDirectory.resolve(relativePath);
		Files.createDirectories(path.getParent());
		Files.writeString(path, content, StandardCharsets.UTF_8);
		return path;
	}
}

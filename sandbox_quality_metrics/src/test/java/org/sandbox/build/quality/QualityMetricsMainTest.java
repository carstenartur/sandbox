package org.sandbox.build.quality;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the same command boundary used by the publication workflow. */
class QualityMetricsMainTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void commandCreatesAllPublishedEndpointsAndProvenance() throws Exception {
		Path reports= temporaryDirectory.resolve("reactor"); //$NON-NLS-1$
		Path report= reports.resolve("module/target/surefire-reports/TEST-command.xml"); //$NON-NLS-1$
		Files.createDirectories(report.getParent());
		Files.writeString(report, """
				<testsuite name="command" tests="2" failures="0" errors="0" skipped="1">
				  <testcase classname="CommandTest" name="passes"/>
				  <testcase classname="CommandTest" name="skips"><skipped/></testcase>
				</testsuite>
				""", StandardCharsets.UTF_8);

		Path jacoco= temporaryDirectory.resolve("jacoco.xml"); //$NON-NLS-1$
		Files.writeString(jacoco, """
				<report name="aggregate">
				  <counter type="LINE" missed="1" covered="3"/>
				</report>
				""", StandardCharsets.UTF_8);
		Path output= temporaryDirectory.resolve("site"); //$NON-NLS-1$

		QualityMetrics.main(new String[] {
				"--reports-root", reports.toString(), //$NON-NLS-1$
				"--jacoco", jacoco.toString(), //$NON-NLS-1$
				"--output", output.toString(), //$NON-NLS-1$
				"--commit", "0123456789abcdef" //$NON-NLS-1$ //$NON-NLS-2$
		});

		assertTrue(Files.readString(output.resolve("badges/tests.json")) //$NON-NLS-1$
				.contains("\"message\": \"2 tests, 1 skipped\"")); //$NON-NLS-1$
		assertTrue(Files.readString(output.resolve("badges/skipped.json")) //$NON-NLS-1$
				.contains("\"message\": \"1\"")); //$NON-NLS-1$
		assertTrue(Files.readString(output.resolve("badges/coverage.json")) //$NON-NLS-1$
				.contains("\"message\": \"75.0%\"")); //$NON-NLS-1$
		assertTrue(Files.readString(output.resolve("quality-summary.json")) //$NON-NLS-1$
				.contains("\"verifiedCommit\": \"0123456789abcdef\"")); //$NON-NLS-1$
		assertTrue(Files.isRegularFile(output.resolve("tests/index.html"))); //$NON-NLS-1$
		assertTrue(Files.isRegularFile(output.resolve(".nojekyll"))); //$NON-NLS-1$
	}
}

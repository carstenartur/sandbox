package org.sandbox.build.quality;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ensures a failing build can never publish a green test endpoint. */
class QualityMetricsFailureBadgeTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void failuresAndErrorsProduceARedTestBadge() throws Exception {
		QualityMetrics.writeSite(temporaryDirectory, "failed-build", //$NON-NLS-1$
				new QualityMetrics.TestTotals(4, 1, 1, 1, 1, 2),
				new QualityMetrics.CoverageTotals(5, 5));

		String badge= Files.readString(temporaryDirectory.resolve("badges/tests.json")); //$NON-NLS-1$
		assertTrue(badge.contains("\"message\": \"4 tests, 1 skipped\"")); //$NON-NLS-1$
		assertTrue(badge.contains("\"color\": \"red\"")); //$NON-NLS-1$
	}
}

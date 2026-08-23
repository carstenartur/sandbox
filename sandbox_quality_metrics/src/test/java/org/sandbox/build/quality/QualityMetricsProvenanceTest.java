package org.sandbox.build.quality;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Guards the machine-readable provenance consumed from the published site. */
class QualityMetricsProvenanceTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void escapesCommitIdentityAsValidJsonData() throws Exception {
		QualityMetrics.writeSite(temporaryDirectory, "ref\"with\\characters", //$NON-NLS-1$
				new QualityMetrics.TestTotals(1, 1, 0, 0, 0, 1),
				new QualityMetrics.CoverageTotals(1, 1));

		String summary= Files.readString(temporaryDirectory.resolve("quality-summary.json")); //$NON-NLS-1$
		assertTrue(summary.contains("\"verifiedCommit\": \"ref\\\"with\\\\characters\"")); //$NON-NLS-1$
	}
}

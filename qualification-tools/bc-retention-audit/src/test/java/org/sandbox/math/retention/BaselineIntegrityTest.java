package org.sandbox.math.retention;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Exercises the archived real source/evidence format without claiming any current-source qualification. */
@EnabledIfSystemProperty(named="retention.baselineRoot", matches=".+")
class BaselineIntegrityTest {
    @Test void readsEveryStrictAndMathematicalBaselineRegionFromVerifiedArchiveSources() throws Exception {
        Path baseline=Path.of(System.getProperty("retention.baselineRoot"));
        RetentionAudit.verifyBaseline(Path.of(System.getProperty("retention.baselineZip")),
                "a37d6bf5ab2962184b051c806d6a7382e040816d0c4224547f127f8aa1f7e1ed",baseline);
        Path archive=Path.of(System.getProperty("retention.sources"));
        var strict=RetentionAudit.readIndex(baseline.resolve("bc-strict"),archive,false);
        var mathematical=RetentionAudit.readIndex(baseline.resolve("bc-mathematical"),archive,false);
        assertEquals(146,strict.size()); assertEquals(149,mathematical.size());
        assertEquals(146,RetentionAudit.compare(strict,strict).path("retained").asInt());
        assertEquals(149,RetentionAudit.compare(mathematical,mathematical).path("retained").asInt());
    }
}

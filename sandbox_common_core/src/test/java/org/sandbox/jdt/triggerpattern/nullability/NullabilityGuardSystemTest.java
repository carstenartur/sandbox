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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.nullability;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Nullability Guard and Confidence Scorer system.
 */
@DisplayName("Nullability Guard System Tests")
class NullabilityGuardSystemTest {

	// ---- NullStatus tests ----

	@Nested
	@DisplayName("NullStatus enum")
	class NullStatusTest {
		@Test
		void testAllValuesExist() {
			assertEquals(4, NullStatus.values().length);
			assertNotNull(NullStatus.NON_NULL);
			assertNotNull(NullStatus.NULLABLE);
			assertNotNull(NullStatus.POTENTIALLY_NULLABLE);
			assertNotNull(NullStatus.UNKNOWN);
		}
	}

	// ---- MatchSeverity tests ----

	@Nested
	@DisplayName("MatchSeverity enum")
	class MatchSeverityTest {
		@Test
		void testAllValuesExist() {
			assertEquals(5, MatchSeverity.values().length);
			assertNotNull(MatchSeverity.IGNORE);
			assertNotNull(MatchSeverity.INFO);
			assertNotNull(MatchSeverity.QUICKASSIST);
			assertNotNull(MatchSeverity.CLEANUP);
			assertNotNull(MatchSeverity.WARNING);
		}

		@Test
		void testSeverityOrdering() {
			// Verify ordinal order matches severity level
			assertTrue(MatchSeverity.IGNORE.ordinal() < MatchSeverity.INFO.ordinal());
			assertTrue(MatchSeverity.INFO.ordinal() < MatchSeverity.QUICKASSIST.ordinal());
			assertTrue(MatchSeverity.QUICKASSIST.ordinal() < MatchSeverity.CLEANUP.ordinal());
			assertTrue(MatchSeverity.CLEANUP.ordinal() < MatchSeverity.WARNING.ordinal());
		}
	}

	// ---- MatchScore tests ----

	@Nested
	@DisplayName("MatchScore record")
	class MatchScoreTest {
		@Test
		void testValidScore() {
			MatchScore score = new MatchScore(5, NullStatus.UNKNOWN, MatchSeverity.QUICKASSIST, "test reason");
			assertEquals(5, score.trivialChange());
			assertEquals(NullStatus.UNKNOWN, score.nullStatus());
			assertEquals(MatchSeverity.QUICKASSIST, score.severity());
			assertEquals("test reason", score.reason());
			assertTrue(score.evidence().isEmpty());
		}

		@Test
		void testScoreWithEvidence() {
			List<String> evidence = List.of("line 10: value != null", "line 20: value == null");
			MatchScore score = new MatchScore(9, NullStatus.NULLABLE, MatchSeverity.WARNING, "nullable", evidence);
			assertEquals(2, score.evidence().size());
			assertEquals("line 10: value != null", score.evidence().get(0));
		}

		@Test
		void testScoreBoundaries() {
			// Min boundary
			MatchScore min = new MatchScore(0, NullStatus.NON_NULL, MatchSeverity.IGNORE, "trivial");
			assertEquals(0, min.trivialChange());

			// Max boundary
			MatchScore max = new MatchScore(10, NullStatus.NULLABLE, MatchSeverity.WARNING, "critical");
			assertEquals(10, max.trivialChange());
		}

		@Test
		void testInvalidScoreThrows() {
			assertThrows(IllegalArgumentException.class,
					() -> new MatchScore(-1, NullStatus.UNKNOWN, MatchSeverity.INFO, "negative"));
			assertThrows(IllegalArgumentException.class,
					() -> new MatchScore(11, NullStatus.UNKNOWN, MatchSeverity.INFO, "too high"));
		}

		@Test
		void testNullParametersThrow() {
			assertThrows(NullPointerException.class,
					() -> new MatchScore(5, null, MatchSeverity.INFO, "test"));
			assertThrows(NullPointerException.class,
					() -> new MatchScore(5, NullStatus.UNKNOWN, null, "test"));
			assertThrows(NullPointerException.class,
					() -> new MatchScore(5, NullStatus.UNKNOWN, MatchSeverity.INFO, null));
		}

		@Test
		void testEvidenceIsImmutable() {
			List<String> evidence = List.of("evidence1");
			MatchScore score = new MatchScore(5, NullStatus.UNKNOWN, MatchSeverity.INFO, "test", evidence);
			assertThrows(UnsupportedOperationException.class, () -> score.evidence().add("new"));
		}
	}

	// ---- NullabilityGuard tests ----

	@Nested
	@DisplayName("NullabilityGuard")
	class NullabilityGuardTest {

		@Test
		void testWhitelistLoaded() {
			NullabilityGuard guard = new NullabilityGuard();
			Map<String, String> types = guard.getNonNullTypes();
			assertFalse(types.isEmpty());
			assertTrue(types.containsKey("java.lang.StringBuilder"));
			assertTrue(types.containsKey("java.lang.String"));
			assertTrue(types.containsKey("java.time.LocalDate"));
			assertTrue(types.containsKey("java.nio.file.Path"));
		}

		@Test
		void testCustomWhitelist() {
			Map<String, String> custom = Map.of("com.example.MyClass", "custom_reason");
			NullabilityGuard guard = new NullabilityGuard(custom);
			assertEquals(1, guard.getNonNullTypes().size());
			assertTrue(guard.getNonNullTypes().containsKey("com.example.MyClass"));
		}

		@Test
		void testAnalyzeNullExpression() {
			NullabilityGuard guard = new NullabilityGuard();
			NullabilityResult result = guard.analyze(null);
			assertEquals(NullStatus.UNKNOWN, result.status());
		}
	}

	// ---- ConfidenceScorer tests ----

	@Nested
	@DisplayName("ConfidenceScorer")
	class ConfidenceScorerTest {

		private final ConfidenceScorer scorer = new ConfidenceScorer();

		@Test
		void testScoreNonNullIgnore() {
			NullabilityResult result = new NullabilityResult(NullStatus.NON_NULL,
					"'this' reference is always non-null", List.of());
			MatchScore score = scorer.score(result);
			assertEquals(0, score.trivialChange());
			assertEquals(MatchSeverity.IGNORE, score.severity());
			assertEquals(NullStatus.NON_NULL, score.nullStatus());
		}

		@Test
		void testScoreNonNullStructuralChild() {
			NullabilityResult result = new NullabilityResult(NullStatus.NON_NULL,
					"getter on AST node type returns structural child", List.of());
			MatchScore score = scorer.score(result);
			assertEquals(1, score.trivialChange());
			assertEquals(MatchSeverity.INFO, score.severity());
		}

		@Test
		void testScoreNullableAfterUsage() {
			NullabilityResult result = new NullabilityResult(NullStatus.NULLABLE,
					"null-check found after usage (SpotBugs-style)", List.of("line 15: value != null"));
			MatchScore score = scorer.score(result);
			assertEquals(10, score.trivialChange());
			assertEquals(MatchSeverity.WARNING, score.severity());
		}

		@Test
		void testScoreNullableAnnotation() {
			NullabilityResult result = new NullabilityResult(NullStatus.NULLABLE,
					"@Nullable annotation found", List.of());
			MatchScore score = scorer.score(result);
			assertEquals(10, score.trivialChange());
			assertEquals(MatchSeverity.CLEANUP, score.severity());
		}

		@Test
		void testScoreNullableMapGet() {
			NullabilityResult result = new NullabilityResult(NullStatus.NULLABLE,
					"variable may be result of Map.get() which can return null", List.of());
			MatchScore score = scorer.score(result);
			assertEquals(8, score.trivialChange());
			assertEquals(MatchSeverity.CLEANUP, score.severity());
		}

		@Test
		void testScorePotentiallyNullable() {
			NullabilityResult result = new NullabilityResult(NullStatus.POTENTIALLY_NULLABLE,
					"null-check found in same method", List.of());
			MatchScore score = scorer.score(result);
			assertEquals(5, score.trivialChange());
			assertEquals(MatchSeverity.QUICKASSIST, score.severity());
		}

		@Test
		void testScoreUnknownDefault() {
			NullabilityResult result = new NullabilityResult(NullStatus.UNKNOWN,
					"nullability could not be determined", List.of());
			MatchScore score = scorer.score(result);
			assertEquals(3, score.trivialChange());
			assertEquals(MatchSeverity.QUICKASSIST, score.severity());
		}

		@Test
		void testScoreUnknownParameter() {
			NullabilityResult result = new NullabilityResult(NullStatus.UNKNOWN,
					"parameter without null-guard", List.of());
			MatchScore score = scorer.score(result);
			assertEquals(3, score.trivialChange());
			assertEquals(MatchSeverity.QUICKASSIST, score.severity());
		}

		@Test
		void testScoreUnknownField() {
			NullabilityResult result = new NullabilityResult(NullStatus.UNKNOWN,
					"field without null-check", List.of());
			MatchScore score = scorer.score(result);
			assertEquals(5, score.trivialChange());
			assertEquals(MatchSeverity.QUICKASSIST, score.severity());
		}

		@Test
		void testScoreUnknownGetter() {
			NullabilityResult result = new NullabilityResult(NullStatus.UNKNOWN,
					"getter on unknown type", List.of());
			MatchScore score = scorer.score(result);
			assertEquals(4, score.trivialChange());
			assertEquals(MatchSeverity.QUICKASSIST, score.severity());
		}
	}

	// ---- ReportFilter tests ----

	@Nested
	@DisplayName("ReportFilter")
	class ReportFilterTest {

		@Test
		void testDefaultFilterPassesAll() {
			ReportFilter filter = new ReportFilter();
			List<ScoredMatchEntry> entries = createSampleEntries();
			List<ScoredMatchEntry> filtered = filter.apply(entries);
			assertEquals(entries.size(), filtered.size());
		}

		@Test
		void testMinTrivialChange() {
			ReportFilter filter = new ReportFilter().withMinTrivialChange(5);
			List<ScoredMatchEntry> entries = createSampleEntries();
			List<ScoredMatchEntry> filtered = filter.apply(entries);
			assertTrue(filtered.stream().allMatch(e -> e.score().trivialChange() >= 5));
		}

		@Test
		void testSeverityFilter() {
			ReportFilter filter = new ReportFilter()
					.withSeverities(java.util.EnumSet.of(MatchSeverity.WARNING, MatchSeverity.CLEANUP));
			List<ScoredMatchEntry> entries = createSampleEntries();
			List<ScoredMatchEntry> filtered = filter.apply(entries);
			assertTrue(filtered.stream().allMatch(
					e -> e.score().severity() == MatchSeverity.WARNING
							|| e.score().severity() == MatchSeverity.CLEANUP));
		}

		@Test
		void testExcludeNonNull() {
			ReportFilter filter = new ReportFilter().withExcludeNonNull(true);
			List<ScoredMatchEntry> entries = createSampleEntries();
			List<ScoredMatchEntry> filtered = filter.apply(entries);
			assertTrue(filtered.stream().noneMatch(e -> e.score().nullStatus() == NullStatus.NON_NULL));
		}

		@Test
		void testCombinedFilters() {
			ReportFilter filter = new ReportFilter()
					.withMinTrivialChange(5)
					.withExcludeNonNull(true)
					.withSeverities(java.util.EnumSet.of(MatchSeverity.WARNING));
			List<ScoredMatchEntry> entries = createSampleEntries();
			List<ScoredMatchEntry> filtered = filter.apply(entries);
			for (ScoredMatchEntry entry : filtered) {
				assertTrue(entry.score().trivialChange() >= 5);
				assertNotEquals(NullStatus.NON_NULL, entry.score().nullStatus());
				assertEquals(MatchSeverity.WARNING, entry.score().severity());
			}
		}

		@Test
		void testMinTrivialChangeValidation() {
			assertThrows(IllegalArgumentException.class,
					() -> new ReportFilter().withMinTrivialChange(-1));
			assertThrows(IllegalArgumentException.class,
					() -> new ReportFilter().withMinTrivialChange(11));
		}

		@Test
		void testSeveritiesValidation() {
			assertThrows(IllegalArgumentException.class,
					() -> new ReportFilter().withSeverities(null));
			assertThrows(IllegalArgumentException.class,
					() -> new ReportFilter().withSeverities(java.util.EnumSet.noneOf(MatchSeverity.class)));
		}

		private List<ScoredMatchEntry> createSampleEntries() {
			return List.of(
					new ScoredMatchEntry("repo", "rule1", "File1.java", 10, "sb.toString()", null,
							new MatchScore(0, NullStatus.NON_NULL, MatchSeverity.IGNORE, "StringBuilder")),
					new ScoredMatchEntry("repo", "rule1", "File2.java", 20, "value.toString()", "String.valueOf(value)",
							new MatchScore(9, NullStatus.NULLABLE, MatchSeverity.WARNING, "null-check after usage")),
					new ScoredMatchEntry("repo", "rule1", "File3.java", 30, "param.toString()", null,
							new MatchScore(3, NullStatus.UNKNOWN, MatchSeverity.QUICKASSIST, "parameter")),
					new ScoredMatchEntry("repo", "rule1", "File4.java", 40, "map.get(key).toString()",
							"String.valueOf(map.get(key))",
							new MatchScore(8, NullStatus.NULLABLE, MatchSeverity.CLEANUP, "Map.get")),
					new ScoredMatchEntry("repo", "rule1", "File5.java", 50, "node.getType().toString()", null,
							new MatchScore(1, NullStatus.NON_NULL, MatchSeverity.INFO, "structural child"))
			);
		}
	}

	// ---- ScoredMatchEntry tests ----

	@Nested
	@DisplayName("ScoredMatchEntry record")
	class ScoredMatchEntryTest {
		@Test
		void testRecordFields() {
			MatchScore score = new MatchScore(5, NullStatus.UNKNOWN, MatchSeverity.QUICKASSIST, "test");
			ScoredMatchEntry entry = new ScoredMatchEntry("repo", "rule", "File.java", 42,
					"value.toString()", "String.valueOf(value)", score);
			assertEquals("repo", entry.repository());
			assertEquals("rule", entry.rule());
			assertEquals("File.java", entry.file());
			assertEquals(42, entry.line());
			assertEquals("value.toString()", entry.matchedCode());
			assertEquals("String.valueOf(value)", entry.suggestedReplacement());
			assertEquals(score, entry.score());
		}
	}

	// ---- ScoredMarkdownReporter tests ----

	@Nested
	@DisplayName("ScoredMarkdownReporter")
	class ScoredMarkdownReporterTest {

		@Test
		void testGenerateReport() {
			ScoredMarkdownReporter reporter = new ScoredMarkdownReporter();
			List<ScoredMatchEntry> entries = List.of(
					new ScoredMatchEntry("sandbox", "toString", "File1.java", 10, "sb.toString()", null,
							new MatchScore(0, NullStatus.NON_NULL, MatchSeverity.IGNORE, "toString_never_null")),
					new ScoredMatchEntry("sandbox", "toString", "File2.java", 20, "value.toString()",
							"String.valueOf(value)",
							new MatchScore(10, NullStatus.NULLABLE, MatchSeverity.WARNING,
									"null-check after usage", List.of("line 30: value != null")))
			);

			String report = reporter.generate(entries);
			assertNotNull(report);
			assertTrue(report.contains("Refactoring Mining Report"));
			assertTrue(report.contains("Warnings"));
			assertTrue(report.contains("Ignored"));
			assertTrue(report.contains("File2.java:20"));
		}

		@Test
		void testEmptyReport() {
			ScoredMarkdownReporter reporter = new ScoredMarkdownReporter();
			String report = reporter.generate(List.of());
			assertNotNull(report);
			assertTrue(report.contains("Refactoring Mining Report"));
			assertTrue(report.contains("Summary"));
		}

		@Test
		void testNullEntriesThrows() {
			ScoredMarkdownReporter reporter = new ScoredMarkdownReporter();
			assertThrows(NullPointerException.class, () -> reporter.generate(null));
		}
	}

	// ---- ScoredJsonReporter tests ----

	@Nested
	@DisplayName("ScoredJsonReporter")
	class ScoredJsonReporterTest {

		@Test
		void testGenerateJson() {
			ScoredJsonReporter reporter = new ScoredJsonReporter();
			List<ScoredMatchEntry> entries = List.of(
					new ScoredMatchEntry("sandbox", "toString", "File.java", 42, "value.toString()",
							"String.valueOf(value)",
							new MatchScore(9, NullStatus.NULLABLE, MatchSeverity.WARNING,
									"null-check found", List.of("line 58: value != null")))
			);

			String json = reporter.generate(entries);
			assertNotNull(json);
			assertTrue(json.contains("\"trivialChange\": 9"));
			assertTrue(json.contains("\"nullStatus\": \"NULLABLE\""));
			assertTrue(json.contains("\"severity\": \"WARNING\""));
			assertTrue(json.contains("\"evidence\""));
			assertTrue(json.contains("line 58: value != null"));
		}

		@Test
		void testGenerateJsonWithoutReplacement() {
			ScoredJsonReporter reporter = new ScoredJsonReporter();
			List<ScoredMatchEntry> entries = List.of(
					new ScoredMatchEntry("sandbox", "toString", "File.java", 10, "sb.toString()", null,
							new MatchScore(0, NullStatus.NON_NULL, MatchSeverity.IGNORE, "trivial"))
			);

			String json = reporter.generate(entries);
			assertFalse(json.contains("suggestedReplacement"));
		}

		@Test
		void testEmptyJson() {
			ScoredJsonReporter reporter = new ScoredJsonReporter();
			String json = reporter.generate(List.of());
			assertEquals("[\n]\n", json);
		}

		@Test
		void testNullEntriesThrows() {
			ScoredJsonReporter reporter = new ScoredJsonReporter();
			assertThrows(NullPointerException.class, () -> reporter.generate(null));
		}
	}

	// ---- NullabilityResult tests ----

	@Nested
	@DisplayName("NullabilityResult record")
	class NullabilityResultTest {
		@Test
		void testResultFields() {
			List<String> evidence = List.of("evidence1");
			NullabilityResult result = new NullabilityResult(NullStatus.NULLABLE, "nullable", evidence);
			assertEquals(NullStatus.NULLABLE, result.status());
			assertEquals("nullable", result.reason());
			assertEquals(1, result.evidence().size());
		}

		@Test
		void testUnknownConstant() {
			assertNotNull(NullabilityResult.UNKNOWN);
			assertEquals(NullStatus.UNKNOWN, NullabilityResult.UNKNOWN.status());
		}
	}
}

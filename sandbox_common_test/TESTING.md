# sandbox_common_test

> **Navigation**: [Main README](../README.md) | [sandbox_common Architecture](../sandbox_common/ARCHITECTURE.md) | [sandbox_common_core README](../sandbox_common_core/README.md)

This module contains tests that require `sandbox_common` (OSGi/Eclipse Platform classes
that cannot be used without the full Eclipse target platform). Tests that only depend on
`sandbox_common_core` classes have been moved to `sandbox_common_core/src/test/java/`.

## What's here (requires sandbox_common)

- **Mining/Git tests**: AstDiffAnalyzerTest, AsyncCommitAnalyzerTest, CommandLineGitProviderTest,
  CommitAnalysisResultTest, ConfidenceCalculatorTest, DiffHunkRefinerTest, ImportDiffAnalyzerTest,
  InferredRuleValidatorTest, PlaceholderGeneralizerTest, RuleGrouperTest, RuleInferenceEngineTest
- **Registry tests**: BundledLibrariesTest, ExtensionPointLoadingTest, GuardExpressionTest,
  HintFileCleanUpBridgeTest, HintFileRegistryInferredTest, PatternCompositionTest,
  Phase6FeaturesTest, WorkspaceHintFileTest
- **HintContext/corext tests**: NetBeansParityTest, ASTProcessorTest, AdvancedVisitorPatternsTest,
  ClassInstanceCreationVisitorTest, ExpressionHelperTest, ReferenceHolderTest, VisitorTest

## What moved to sandbox_common_core

The following tests now live in `sandbox_common_core/src/test/java/` and run without
Eclipse target platform:

- HelperVisitor tests: HelperVisitorFluentApiTest, NodeMatcherTest, StatementContextTest,
  HelperVisitorBuilderTest, BasicVisitorUsageTest, VisitorApiDocumentationTest,
  AstProcessorBuilderTest, MatcherTest, LibStandardNamesTest, VisitorConfigDataTest
- TriggerPattern tests: TriggerPatternCleanupTest, TriggerPatternEngineTest,
  PatternParserTest, PlaceholderMatcherTest, MultiPlaceholderTest, VariadicPlaceholderTest,
  NewPatternKindsTest, StatementSequenceTest, HintFileParserTest, PatternIndexTest,
  BatchTransformationProcessorTest, DryRunReporterTest, TransformationReporterTest,
  TransformationRuleTest

## Running Tests

```bash
# Run only core tests (fast, no target platform needed)
mvn test -pl sandbox_common_core

# Run sandbox_common_test (needs sandbox_common built first)
mvn install -pl sandbox_target,sandbox_common -DskipTests
mvn test -pl sandbox_common_test
```

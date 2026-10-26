/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.sdk.optimization.CancellationToken;
import de.regelsuche.sdk.optimization.CheckedPolicy;
import de.regelsuche.sdk.optimization.ComputationOptimizer;
import de.regelsuche.sdk.optimization.ComputationExplanations;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.OptimizationRequest;
import de.regelsuche.sdk.optimization.OptimizationResult;
import de.regelsuche.sdk.optimization.SafetyProfile;
import de.regelsuche.sdk.optimization.VerificationEvidence;
import de.regelsuche.sdk.optimization.OptimizationResult.BudgetExceeded;
import de.regelsuche.sdk.optimization.OptimizationResult.Candidate;
import de.regelsuche.sdk.optimization.OptimizationResult.CostAssessment;
import de.regelsuche.sdk.optimization.OptimizationResult.NoImprovement;
import de.regelsuche.sdk.optimization.RuntimeObligations.GuardKind;
import de.regelsuche.sdk.optimization.VerificationResult.Verified;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

public final class MathematicalAnalysis {
   private MathematicalAnalysis() {
   }

   public static MathematicalAnalysis.Analysis analyze(CompilationUnit ast, String source, MathCleanUpOptions options, IProgressMonitor progress, int selectionOffset, int selectionLength) {
      return analyze(ast, source, options, progress, selectionOffset, selectionLength, null);
   }

   public static MathematicalAnalysis.Analysis analyze(
      CompilationUnit ast, String source, MathCleanUpOptions options, IProgressMonitor progress, int selectionOffset, int selectionLength, MathematicalAnalysis.SourceEnvironment environment
   ) {
      Objects.requireNonNull(ast);
      Objects.requireNonNull(source);
      Objects.requireNonNull(options);
      try (var context = JavaSourceContext.open(ast, source, environment, progress, options)) {
         Analysis result = analyzeWithin(ast, source, options, progress, selectionOffset, selectionLength, environment, context);
         var dependencies = context.checkpoint();
         if (!dependencies.current()) {
            var diagnostics = new ArrayList<>(result.diagnostics());
            diagnostics.add(new Diagnostic("SOURCE_DEPENDENCY_CHANGED", "Referenced source changed during analysis", 0, 0, Stage.SOURCE)); //$NON-NLS-1$ //$NON-NLS-2$
            return new Analysis(List.of(), diagnostics, result.sourceDigest(), result.compilerOptions(),
                  List.of(), List.of(), dependencies);
         }
         return new Analysis(result.replacements(), result.diagnostics(), result.sourceDigest(), result.compilerOptions(),
               result.evidence(), result.documentation(), dependencies);
      }
   }

   private static Analysis analyzeWithin(CompilationUnit ast, String source, MathCleanUpOptions options,
         IProgressMonitor progress, int selectionOffset, int selectionLength, SourceEnvironment environment, JavaSourceContext sourceContext) {
      IProgressMonitor monitor = progress == null ? new NullProgressMonitor() : progress;
      Map<String, String> compilerOptions = ast.getJavaElement() == null ? Map.of() : ast.getJavaElement().getJavaProject().getOptions(true);
      String sourceDigest = digest(source);
      ArrayList<Replacement> replacements = new ArrayList<>();
      ArrayList<Diagnostic> diagnostics = new ArrayList<>();
      ArrayList<VerifiedRegion> evidence = new ArrayList<>();
      final HashSet<String> generatedNames = new HashSet<>();
      if (!options.enabled()) {
         return new MathematicalAnalysis.Analysis(replacements, diagnostics, sourceDigest, compilerOptions);
      }

      if (isCancelled(monitor)) {
         return cancelled(sourceDigest, compilerOptions);
      }

      if (!matchesAstSource(ast, source, compilerOptions, options.targetJava())) {
         diagnostics.add(new MathematicalAnalysis.Diagnostic("STALE_AST_SOURCE", "Resolved AST does not match the current source snapshot", 0, 0));
         return new MathematicalAnalysis.Analysis(replacements, diagnostics, sourceDigest, compilerOptions);
      }

      if (!options.exclusions().isEmpty()) {
         if (ast.getJavaElement() == null) {
            diagnostics.add(new MathematicalAnalysis.Diagnostic("EXCLUSION_REQUIRES_FILE_CONTEXT", "Exclusions require the actual project file path", 0, 0));
            return new MathematicalAnalysis.Analysis(replacements, diagnostics, sourceDigest, compilerOptions);
         }

         Path path = Path.of(ast.getJavaElement().getPath().toPortableString());

         for (String exclusion : options.exclusions()) {
            if (FileSystems.getDefault().getPathMatcher("glob:" + exclusion).matches(path)) {
               diagnostics.add(new MathematicalAnalysis.Diagnostic("EXCLUDED_FILE", "File excluded by mathematics profile", 0, 0));
               return new MathematicalAnalysis.Analysis(replacements, diagnostics, sourceDigest, compilerOptions);
            }
         }
      }

      var extractor = new JavaComputationExtractor();
      JavaComputationExtractor.Extraction extraction = selectionOffset < 0
            ? extractor.extractRuntimeWithoutStores(ast, source, options) : extractor.extractRuntime(ast, source, options);

      for (JavaComputationExtractor.Diagnostic diagnostic : extraction.diagnostics()) {
         diagnostics.add(new MathematicalAnalysis.Diagnostic(diagnostic.code(), diagnostic.message(), diagnostic.offset(), diagnostic.length(), Stage.SOURCE));
      }

      monitor.beginTask("Verify mathematics regions", extraction.regions().size());
      ComputationOptimizer optimizer = new ComputationOptimizer();
      JavaComputationEmitter emitter = new JavaComputationEmitter();
      MathematicalJavadoc documentation = new MathematicalJavadoc();
      CancellationToken cancellation = () -> isCancelled(monitor);
      // New singleton assignments must not dilute the established regions' work shares.
      // Explicit selections still receive the whole available file budget immediately.
      var deferredAssignments = new ArrayList<JavaComputationRegion>();
      var primaryRegions = new ArrayList<JavaComputationRegion>();
      for (var region : extraction.regions()) {
         if (selectionOffset < 0 && incomingIntegralAssignment(region)) deferredAssignments.add(region);
         else primaryRegions.add(region);
      }
      var connections = selectionOffset >= 0
            ? new JavaComputationSlices.Discovery(primaryRegions, Map.of(), List.of(), 0)
            : JavaComputationSlices.discover(ast, primaryRegions, options, cancellation);
      var pinnedOffsets = connections.slices().values().stream().flatMap(slice -> slice.pinned().stream())
            .map(ASTNode::getStartPosition).collect(Collectors.toSet());
      diagnostics.removeIf(diagnostic -> diagnostic.code().equals("SIDE_EFFECT_BOUNDARY") && pinnedOffsets.contains(diagnostic.offset())); //$NON-NLS-1$
      connections.slices().values().forEach(slice -> slice.pinned().forEach(node -> diagnostics.add(new Diagnostic(
            "PINNED_SOURCE_EFFECT", "Call retained in its original order; only total local arithmetic crosses it", //$NON-NLS-1$ //$NON-NLS-2$
            node.getStartPosition(), node.getLength(), Stage.SOURCE))));
      diagnostics.addAll(connections.diagnostics());
      long remainingWork = options.workBudget() - connections.work() - sourceContext.takeWork();
      List<JavaComputationRegion> pending = new ArrayList<>(connections.regions());
      List<JavaComputationRegion> fragments = null;
      List<JavaComputationRegion> statements = null;
      List<JavaComputationRegion> scalarAlternatives = null;
      // Only retries receive a minimum fair share; primary region weighting is unchanged.
      var scalarRetries = java.util.Collections.newSetFromMap(
            new java.util.IdentityHashMap<JavaComputationRegion, Boolean>());
      boolean storesScheduled = selectionOffset >= 0;

      try {
         for (int regionIndex = 0; regionIndex < pending.size() || !deferredAssignments.isEmpty() || !storesScheduled; regionIndex++) {
            if (regionIndex == pending.size()) {
               if (!deferredAssignments.isEmpty()) {
                  pending.addAll(deferredAssignments);
                  deferredAssignments.clear();
               } else if (!storesScheduled && remainingWork > 0) {
                  storesScheduled = true;
                  // New store regions only use the remainder; they must never overlap accepted edits.
                  pending.addAll(extractor.extractRuntime(ast, source, options).regions().stream()
                        .filter(part -> part.outputs().stream().anyMatch(JavaComputationRegion.OutputBinding::arrayStore))
                        .filter(part -> replacements.stream().noneMatch(other -> overlaps(part.start(), part.length(), other.offset(), other.length())))
                        .toList());
               }
               if (regionIndex == pending.size()) break;
            }
            remainingWork -= sourceContext.takeWork();
            JavaComputationRegion region = pending.get(regionIndex);
            boolean scalarRetry = scalarRetries.remove(region);
            var slice = connections.slices().get(region);
            if (isCancelled(monitor)) {
               return cancelled(sourceDigest, compilerOptions);
            }

            if (selectionOffset < 0 || selectionOffset < region.start() + region.length() && (long)selectionOffset + Math.max(1, selectionLength) > region.start()) {
               if (remainingWork <= 0L) {
                  diagnostics.add(diagnostic("BUDGET_EXCEEDED", "Analysis budget exhausted", region));
                  break;
               }

               // Larger source traces need more search, while every later region retains a share.
               long regionWork = selectionOffset >= 0 ? remainingWork : regionAllowance(pending, regionIndex, remainingWork);
               // A small fallback still has fixed preparation, proof and replay costs.
               // Reserve at least an equal share of the actual remainder, never a new budget.
               if (selectionOffset < 0 && scalarRetry)
                  regionWork = Math.max(regionWork, remainingWork / (pending.size() - regionIndex));
               boolean mathematicalIntegral = false;
               boolean canTryMathematical = options.mathematicalOptIn()
                     && region.outputs().stream().noneMatch(JavaComputationRegion.OutputBinding::arrayStore)
                     && IntegralMathematicalDomain.supports(region, cancellation);
               boolean mathematicalBigInteger = options.mathematicalOptIn()
                     && region.trace().occurrences().stream().anyMatch(o -> o.evaluatedKind() == NumericKind.BIG_INTEGER);
               boolean canRetryBigInteger = mathematicalBigInteger && region.outputs().size() + region.internalBindings().size() > 1;
               // Alternate domains share the allocation equally. Keep a quarter for smaller
               // BigInteger requests: an Unsupported result has no counter and costs its full allowance.
               long ordinaryWork = canTryMathematical || slice != null ? Math.max(1L, regionWork / 2)
                     : canRetryBigInteger ? Math.max(1L, regionWork - Math.max(1L, regionWork / 4)) : regionWork;
               OptimizationRequest request = new OptimizationRequest(
                  region.plan(),
                  region.trace(),
                  options.kinds(),
                  "java25-numeric/v1",
                  region.assumptions(),
                  options.safety(),
                  options.goal(),
                  options.optimizationBudget(ordinaryWork),
                  options.safety() == SafetyProfile.CHECKED_THROW ? CheckedPolicy.EXPLICIT_DEFAULT : CheckedPolicy.NONE
               );

               boolean retryArrayScalars = false;
               int replacementsBefore = replacements.size();
               Stage failureStage = Stage.SEARCH_OR_PROOF;
               try {
                  OptimizationResult result = optimizer.optimize(request, cancellation);
                  // Keep ordinary Java sharing improvements. A different overflow contract is
                  // only needed after that search fails, and both attempts share one work budget.
                  long ordinarySpent = consumedWork(result, ordinaryWork);
                  if (canTryMathematical && (result instanceof NoImprovement || result instanceof OptimizationResult.Inconclusive)
                        && ordinarySpent < regionWork) {
                     remainingWork -= ordinarySpent;
                     mathematicalIntegral = true;
                     request = new OptimizationRequest(region.plan(), region.trace(), options.kinds(), "java25-numeric/v1",
                           region.assumptions(), SafetyProfile.CHECKED_THROW, OptimizationGoal.READABILITY,
                           options.optimizationBudget(regionWork - ordinarySpent), CheckedPolicy.EXPLICIT_DEFAULT);
                     result = optimizer.optimize(request, cancellation);
                  }
                  if (!(result instanceof Candidate candidate)) {
                     boolean preparationLimit = result instanceof OptimizationResult.Unsupported unsupported
                           && unsupported.toString().contains("plan preparation work bound exceeded");
                     long attemptSpent = preparationLimit
                           ? failedPreparationWork(request.budget().maximumWork(), remainingWork)
                           : consumedWork(result, request.budget().maximumWork());
                     if (preparationLimit && slice == null) {
                        if (fragments == null) fragments = new JavaComputationExtractor().extractRuntimeFragments(ast, source, options).regions();
                        var smaller = fragments.stream().filter(part -> part.start() >= region.start()
                              && part.start() + part.length() <= region.start() + region.length() && part.length() < region.length()).toList();
                        if (!smaller.isEmpty()) {
                           // A later trace preparation can fail after earlier request work.
                           // Reserve that allowance plus the unreported preparation before retrying.
                           remainingWork -= attemptSpent;
                           scheduleRetries(pending, regionIndex + 1, smaller, deferredAssignments, selectionOffset < 0);
                           diagnostics.add(diagnostic("REGION_PREPARATION_SPLIT", "Preparation limit reached; retrying " + smaller.size()
                                 + " smaller source regions within the remaining file budget", region));
                           continue;
                        }
                     }
                     if (mathematicalBigInteger && (result instanceof NoImprovement || result instanceof OptimizationResult.Inconclusive
                           || result instanceof OptimizationResult.Unsupported || result instanceof BudgetExceeded) && remainingWork > attemptSpent) {
                        if (fragments == null) fragments = new JavaComputationExtractor().extractRuntimeFragments(ast, source, options).regions();
                        var windows = fragments.stream().filter(part -> part.start() >= region.start()
                              && part.start() + part.length() <= region.start() + region.length() && part.length() < region.length()).toList();
                        if (windows.stream().anyMatch(part -> part.outputs().size() + part.internalBindings().size() > 1)) {
                           remainingWork -= attemptSpent;
                           scheduleRetries(pending, regionIndex + 1, windows, deferredAssignments, selectionOffset < 0);
                           diagnostics.add(diagnostic("MODULAR_WINDOW_RETRY", //$NON-NLS-1$
                                 "Retrying connected modular windows within the remaining file budget", region)); //$NON-NLS-1$
                           continue;
                        }
                        if (statements == null) statements = new JavaComputationExtractor().extractRuntimeStatements(ast, source, options).regions();
                        var smaller = statements.stream().filter(part -> part.start() >= region.start()
                              && part.start() + part.length() <= region.start() + region.length() && part.length() < region.length()).toList();
                        if (!smaller.isEmpty()) {
                           // Unsupported carries no work counter, so charge the full failed request allocation.
                           remainingWork -= attemptSpent;
                           scheduleRetries(pending, regionIndex + 1, smaller, deferredAssignments, selectionOffset < 0);
                           diagnostics.add(diagnostic("MODULAR_STATEMENT_RETRY", "Retrying supported statements within the remaining file budget", region));
                           continue;
                        }
                     }
                     diagnostics.add(diagnostic(result.getClass().getSimpleName().toUpperCase(Locale.ROOT), result.toString(), region));
                     if (result instanceof NoImprovement noImprovement) {
                        remainingWork -= noImprovement.work();
                     }
                     if (result instanceof OptimizationResult.Inconclusive || result instanceof OptimizationResult.Unsupported)
                        remainingWork -= attemptSpent;

                     if (result instanceof BudgetExceeded exceeded) {
                        // Charge this attempt, including any reported overshoot. A preceding
                        // ordinary attempt was already charged before the retry.
                        remainingWork -= Math.max(request.budget().maximumWork(), exceeded.work());
                     }
                     retryArrayScalars = result instanceof NoImprovement || result instanceof OptimizationResult.Inconclusive
                           || result instanceof BudgetExceeded;
                     continue;
                  }

                  remainingWork -= candidate.work();
                  failureStage = Stage.PROOF;
                  var explained = ComputationExplanations.describe(request, candidate, cancellation);
                  remainingWork -= explained.explanation().map(ComputationExplanations.Explanation::presentationWork).orElse(0L);
                  if (!(explained.verification() instanceof Verified) || explained.explanation().isEmpty()) {
                     diagnostics.add(diagnostic("REVERIFICATION_FAILED", explained.verification().toString(), region));
                     continue;
                  }
                  retryArrayScalars = true;

                  String contract = mathematicalIntegral ? IntegralMathematicalDomain.verify(region, candidate.plan(), cancellation)
                        : mathematicalBigInteger ? "BigInteger numerical values are preserved; result reference identity may change. "
                              + "Where receivers are unproved, the optimized branch requires exact BigInteger instances "
                              + "with bitLength() below 4096 and positive guarded moduli or exponents; signed bases are allowed. "
                              + "Otherwise the original calculation executes. "
                              + "No extension of the BigInteger value range or constant-time execution is claimed." : null;

                  if (options.goal() == OptimizationGoal.LOWER_ESTIMATED_RUNTIME && !mathematicalIntegral
                        && !candidate.cost().estimatedRuntimeImprovement()) {
                     diagnostics.add(diagnostic("NO_RUNTIME_IMPROVEMENT", "Candidate improves storage or ordering only, not estimated runtime", region));
                     continue;
                  }
                  if (mathematicalIntegral && options.goal() == OptimizationGoal.LOWER_ESTIMATED_RUNTIME
                        && candidate.cost().candidateCost().operationWork() >= candidate.cost().sourceCost().operationWork()) {
                     diagnostics.add(diagnostic("NO_RUNTIME_IMPROVEMENT", "Mathematical candidate does not reduce operation work", region));
                     continue;
                  }

                  failureStage = Stage.EMISSION;
                  HashSet<String> reserved = new HashSet<>(region.reservedNames());
                  reserved.addAll(generatedNames);
                  if (region.outputs().stream().anyMatch(JavaComputationRegion.OutputBinding::arrayStore)
                        && candidate.obligations().guard() != GuardKind.NONE)
                     throw new IllegalArgumentException("ARRAY_STORE_GUARD_UNSUPPORTED"); //$NON-NLS-1$
                  JavaComputationEmitter.Emission plain = emitter.emit(candidate.prepared(), region.inputNames(), reserved, options.targetJava());
                  if (mathematicalBigInteger) plain = JavaOriginalExpressions.restore(ast, source, region, plain);
                  failureStage = Stage.PROOF;
                  JavaEmissionVerifier.verify(request, candidate, plain, region, options, cancellation);
                  failureStage = Stage.EMISSION;
                  MathExplanation explanationText = MathExplanation.render(explained.explanation().orElseThrow(),
                     request, candidate, region, plain, options, contract, cancellation);
                  String replacement;
                  if (mathematicalIntegral) {
                     replacement = indentGenerated(plain.statements(), source, region.start()) + rewriteValues(ast, source, region, plain.outputValues(), compilerOptions);
                  } else if (options.safety() == SafetyProfile.CHECKED_THROW) {
                     JavaComputationEmitter.Emission checked = emitter.emitChecked(request, candidate, region.inputNames(), reserved, options.targetJava());
                     replacement = indentGenerated(checked.statements(), source, region.start()) + rewriteValues(ast, source, region, checked.outputValues(), compilerOptions);
                  } else if (!region.receiverGuards().isEmpty()) {
                     if (candidate.obligations().guard() != GuardKind.NONE) {
                        throw new IllegalArgumentException("MIXED_RECEIVER_NUMERIC_GUARD_UNSUPPORTED");
                     }

                     if (options.goal() == OptimizationGoal.LOWER_ESTIMATED_RUNTIME && (!mathematicalBigInteger
                           || candidate.cost().sourceScore() - candidate.cost().candidateScore() <= 70L * region.receiverGuards().size())) {
                        throw new IllegalArgumentException("RECEIVER_GUARD_RUNTIME_COST_UNQUALIFIED");
                     }

                     String receiverGuard = region.receiverGuards()
                        .stream()
                        .sorted()
                        .map(receiver -> "(" + receiver + " != null && " + receiver + ".getClass() == java.math.BigInteger.class && " + receiver + ".bitLength() < 4096"
                              + (mathematicalBigInteger && region.assumptions().stream().anyMatch(a -> a.kind() == de.regelsuche.sdk.optimization.SemanticAssumption.Kind.POSITIVE
                                    && receiver.equals(region.inputNames().get(a.subject()))) ? " && " + receiver + ".signum() > 0" : "") + ")")
                        .collect(Collectors.joining(" && "));
                     replacement = guardedSource(ast, source, region, "", receiverGuard, plain);
                  } else if (candidate.obligations().guard() != GuardKind.NONE) {
                     JavaComputationEmitter.GuardEmission guarded = emitter.emitGuard(request, candidate, region.inputNames(), reserved, options.targetJava());
                     replacement = guardedSource(ast, source, region, guarded.statements(), guarded.guardJava(), guarded.replacement());
                  } else {
                     replacement = indentGenerated(plain.statements(), source, region.start()) + rewriteValues(ast, source, region, plain.outputValues(), compilerOptions);
                  }

                  replacement = indentGenerated(JavaRegionSourceRewriter.arrayCaptures(ast, source, region), source, region.start()) + replacement;
                  // Explicit checked/fallback profiles require their policy control flow.
                  if (options.goal() == OptimizationGoal.READABILITY && options.safety() == SafetyProfile.PRESERVE_JAVA) {
                     var patch = MathCandidateReview.assess(source.substring(region.start(), region.start() + region.length()),
                           replacement, candidate.cost().sourceScore(), candidate.cost().candidateScore(),
                           candidate.cost().estimatedRuntimeImprovement(), mathematicalBigInteger || mathematicalIntegral,
                           region.receiverGuards().size(), options.targetJava());
                     if (patch.addedBranches() > 0 || patch.cautions().contains("LARGE_REWRITE")) { //$NON-NLS-1$
                        diagnostics.add(diagnostic("PATCH_READABILITY_COST", //$NON-NLS-1$
                              "Readability selection retains the original instead of adding control flow or a large rewrite", region)); //$NON-NLS-1$
                        continue;
                     }
                  }
                  if (options.explanations() == MathCleanUpOptions.ExplanationMode.ALL
                        || options.explanations() == MathCleanUpOptions.ExplanationMode.NONTRIVIAL && explanationText.nontrivial()) {
                     replacement = indentGenerated(explanationText.sourceComment(), source, region.start()) + replacement;
                  }
                  JavaSourceRewritePlan sourcePlan = null;
                  if (slice != null) {
                     String prefix = indentGenerated(plain.statements(), source, region.start());
                     if (options.explanations() == MathCleanUpOptions.ExplanationMode.ALL
                           || options.explanations() == MathCleanUpOptions.ExplanationMode.NONTRIVIAL && explanationText.nontrivial())
                        prefix = indentGenerated(explanationText.sourceComment(), source, region.start()) + prefix;
                     String unitName = ast.getJavaElement() != null ? ast.getJavaElement().getPath().toPortableString()
                           : environment != null ? environment.unitName() : "source@" + sourceDigest; //$NON-NLS-1$
                     sourcePlan = JavaSourceRewritePlan.capture(ast, source, unitName, region, slice.pinned(),
                           JavaRegionSourceRewriter.edits(ast, source, region, plain.outputValues(), compilerOptions), prefix);
                     if (!replacement.equals(sourcePlan.replacement(source)))
                        throw new IllegalArgumentException("SOURCE_PLAN_REPLACEMENT_DIFFERS"); //$NON-NLS-1$
                  }
                  checkGeneratedSource(
                     ast,
                     source.substring(0, region.start()) + replacement + source.substring(region.start() + region.length()),
                     compilerOptions,
                     options.targetJava(),
                     (IProgressMonitor)monitor,
                     environment
                  );
                  if (isCancelled(monitor)) {
                     return cancelled(sourceDigest, compilerOptions);
                  }

                  failureStage = Stage.COST;
                  if (incomingIntegralAssignment(region) && options.goal() == OptimizationGoal.LOWER_ESTIMATED_RUNTIME
                        && RuntimeCalculationPolicy.arithmeticOperations(replacement)
                              >= RuntimeCalculationPolicy.arithmeticOperations(source.substring(region.start(), region.start() + region.length()))) {
                     diagnostics.add(diagnostic("NO_RUNTIME_IMPROVEMENT", //$NON-NLS-1$
                           "Incoming assignment does not reduce source arithmetic; helper expansion or cast sharing alone is insufficient", region)); //$NON-NLS-1$
                     continue;
                  }
                  CostAssessment cost = candidate.cost();
                  if (mathematicalIntegral) {
                     long workWeight = options.goal() == OptimizationGoal.LOWER_ESTIMATED_RUNTIME ? 10 : 1;
                     long storageWeight = options.goal() == OptimizationGoal.READABILITY ? 0
                           : options.goal() == OptimizationGoal.LOWER_ALLOCATION ? 10 : 1;
                     long sourceWork = Math.max(region.trace().occurrences().size(), cost.sourceCost().operationWork());
                     cost = new CostAssessment(cost.sourceCost().weighted(workWeight, storageWeight, storageWeight, 1)
                           + (sourceWork - cost.sourceCost().operationWork()) * workWeight,
                           cost.candidateCost().weighted(workWeight, storageWeight, storageWeight, 1),
                           0, 0, cost.sourceCost(), cost.candidateCost(), cost.candidateCost().operationWork() < sourceWork);
                  }
                  if (!region.receiverGuards().isEmpty()) {
                     long receiverCheckCost = (mathematicalBigInteger ? 7L : 5L) * region.receiverGuards().size();
                     long weightedChecks = receiverCheckCost * (options.goal() == OptimizationGoal.LOWER_ESTIMATED_RUNTIME ? 10 : 1);
                     cost = new CostAssessment(
                        cost.sourceScore(),
                        cost.candidateScore() + weightedChecks,
                        cost.checkWork() + receiverCheckCost,
                        region.trace().occurrences().size(),
                        cost.sourceCost(),
                        cost.candidateCost(),
                        mathematicalBigInteger && cost.estimatedRuntimeImprovement() && cost.sourceScore() > cost.candidateScore() + weightedChecks
                     );
                  }
                  if (mathematicalBigInteger && cost.candidateScore() >= cost.sourceScore()) {
                     diagnostics.add(diagnostic("GUARD_COST_EXCEEDS_BENEFIT", "Receiver checks erase the estimated improvement", region));
                     continue;
                  }
                  long arithmeticWork = cost.candidateCost().operationWork();
                  cost = JavaArrayCaptureCost.assess(region, plain, cost, options.goal());
                  if (!region.arrayReads().isEmpty() && options.goal() != OptimizationGoal.READABILITY
                        && (cost.candidateScore() >= cost.sourceScore()
                              || options.goal() == OptimizationGoal.LOWER_ESTIMATED_RUNTIME && !cost.estimatedRuntimeImprovement())) {
                     diagnostics.add(diagnostic("ARRAY_CAPTURE_COST_EXCEEDS_BENEFIT", //$NON-NLS-1$
                           "Capture stores, references and retained values erase the estimated benefit", region)); //$NON-NLS-1$
                     continue;
                  }

                  failureStage = Stage.EMISSION;
                  ComputationExplanation explanation = ComputationExplanation.from(region, explained.explanation().orElseThrow());
                  String description = "Mathematics "
                     + (contract == null ? options.safety() : "MATHEMATICAL")
                     + "; estimated operation work "
                     + cost.sourceCost().operationWork()
                     + " -> "
                     + cost.candidateCost().operationWork()
                     + "; check work "
                     + cost.checkWork()
                     + "; proof "
                     + candidate.evidence().checkerRevision()
                     + "; assumptions "
                     + region.assumptions();
                  description += "; estimated score " + cost.sourceScore() + " -> " + cost.candidateScore()
                        + "; measured speedup: not assessed";
                  if (!region.arrayReads().isEmpty()) description += "; array capture work " //$NON-NLS-1$
                        + (cost.candidateCost().operationWork() - arithmeticWork)
                        + "; additional peak storage " + region.arrayReads().size() + " primitive values"; //$NON-NLS-1$ //$NON-NLS-2$
                  if (contract != null) {
                     description += "; " + contract;
                     documentation.add(ast, region, contract);
                  }
                  if (options.safety() == SafetyProfile.CHECKED_THROW) {
                     description = description
                        + "; CHECKED_THROW changes the Java contract: numerical violations may throw ArithmeticException. Both the original and replacement operations must be checked.";
                  }

                  description += "\n" + explanationText.detail();
                  replacements.add(new MathematicalAnalysis.Replacement(region.start(), region.length(), replacement, description));
                  ASTParser declarationParser = ASTParser.newParser(ast.getAST().apiLevel());
                  declarationParser.setKind(2);
                  declarationParser.setSource(replacement.toCharArray());
                  declarationParser.createAST(null).accept(new ASTVisitor() {
                     public boolean visit(SimpleName name) {
                        if (name.isDeclaration()) {
                           generatedNames.add(name.getIdentifier());
                        }

                        return true;
                     }
                  });
                  evidence.add(
                     new MathematicalAnalysis.VerifiedRegion(
                        region.start(), region.length(), candidate.evidence(), cost, source.substring(region.start(), region.start() + region.length()), replacement, explanation, sourcePlan
                     )
                  );
                  diagnostics.add(diagnostic("VERIFIED_CANDIDATE", description, region));
               } catch (IllegalArgumentException | UnsupportedOperationException unsupported) {
                  diagnostics.add(new Diagnostic("UNSUPPORTED_OR_UNPROVED", unsupported.getMessage(), region.start(), region.length(), failureStage));
               } finally {
                  if (slice != null && remainingWork > 0 && replacements.size() == replacementsBefore && !isCancelled(monitor)) {
                     pending.addAll(regionIndex + 1, slice.parts());
                     diagnostics.add(diagnostic("SOURCE_SLICE_LOCAL_RETRY", //$NON-NLS-1$
                           "Joint search produced no applicable edit; retrying the original regions within the file budget", region)); //$NON-NLS-1$
                  }
                  // A connected memory region must not hide a useful scalar calculation.
                  // Both search and presentation have already consumed their file budget.
                  if (retryArrayScalars && !region.arrayReads().isEmpty() && remainingWork > 0
                        && replacements.size() == replacementsBefore && !isCancelled(monitor)) {
                     if (scalarAlternatives == null)
                        scalarAlternatives = new JavaComputationExtractor().extractRuntimeScalars(ast, source, options).regions();
                     var future = pending.subList(regionIndex + 1, pending.size());
                     var smaller = scalarAlternatives.stream().filter(part -> part.arrayReads().isEmpty()
                           && part.start() >= region.start() && part.start() + part.length() <= region.start() + region.length()
                           && part.length() < region.length()
                           && future.stream().noneMatch(other -> overlaps(part.start(), part.length(), other.start(), other.length()))
                           && deferredAssignments.stream().noneMatch(other -> overlaps(part.start(), part.length(), other.start(), other.length()))
                           && replacements.stream().noneMatch(other -> overlaps(part.start(), part.length(), other.offset(), other.length())))
                           .toList();
                     if (!smaller.isEmpty()) {
                        scalarRetries.addAll(smaller);
                        scheduleRetries(pending, regionIndex + 1, smaller, deferredAssignments, selectionOffset < 0);
                        diagnostics.add(diagnostic("ARRAY_SCALAR_RETRY", //$NON-NLS-1$
                              "Retrying contained scalar regions within the remaining file budget", region)); //$NON-NLS-1$
                     }
                  }
               }

               monitor.worked(1);
            }
         }
      } finally {
         monitor.done();
      }

      if (isCancelled(monitor)) {
         return cancelled(sourceDigest, compilerOptions);
      }

      MathematicalAnalysis.Analysis analysis = new MathematicalAnalysis.Analysis(replacements, diagnostics, sourceDigest, compilerOptions, evidence, documentation.edits(source));
      if (replacements.size() > 1 || !analysis.documentation().isEmpty()) {
         try {
            Document combined = new Document(source);
            analysis.newEdit().apply(combined);
            checkGeneratedSource(ast, combined.get(), compilerOptions, options.targetJava(), (IProgressMonitor)monitor, environment);
         } catch (IllegalArgumentException | BadLocationException invalidEdits) {
            diagnostics.add(new MathematicalAnalysis.Diagnostic("COMBINED_EDITS_INVALID", invalidEdits.getMessage(), 0, source.length()));
            return new MathematicalAnalysis.Analysis(List.of(), diagnostics, sourceDigest, compilerOptions);
         }
      }

      return isCancelled(monitor) ? cancelled(sourceDigest, compilerOptions) : analysis;
   }

   /** Interrupts abort the entire invocation, never just the current candidate. */
   private static boolean isCancelled(IProgressMonitor monitor) {
      if (Thread.currentThread().isInterrupted()) {
         throw new OperationCanceledException("Mathematics analysis interrupted");
      }
      return monitor.isCanceled();
   }

   private static long failedPreparationWork(long allocation, long remainingWork) {
      long unreported = Math.min(remainingWork,
            de.regelsuche.search.program.JointComputationPlan.MAX_RESOLUTION_WORK
                  + de.regelsuche.search.program.JointComputationPlan.MAX_NODES);
      // Unsupported has no work counter: earlier trace checks may already have
      // spent the request allowance before this last bounded preparation failed.
      return unreported + Math.min(remainingWork - unreported, allocation);
   }

   private static long consumedWork(OptimizationResult result, long allocation) {
      if (result instanceof NoImprovement ordinary) return ordinary.work();
      if (result instanceof OptimizationResult.Inconclusive inconclusive && inconclusive.work() >= 0) return inconclusive.work();
      if (result instanceof BudgetExceeded exceeded) return Math.max(allocation, exceeded.work());
      return allocation;
   }

   private static long regionAllowance(List<JavaComputationRegion> pending, int index, long remainingWork) {
      long total = 0;
      for (int i = index; i < pending.size(); i++) total += regionWeight(pending.get(i));
      return Math.max(1L, remainingWork * regionWeight(pending.get(index)) / total);
   }

   private static void scheduleRetries(List<JavaComputationRegion> pending, int index, List<JavaComputationRegion> retries,
         List<JavaComputationRegion> deferredAssignments, boolean wholeFile) {
      var immediate = new ArrayList<JavaComputationRegion>();
      for (var retry : retries) {
         if (wholeFile && incomingIntegralAssignment(retry)) deferredAssignments.add(retry);
         else immediate.add(retry);
      }
      pending.addAll(index, immediate);
   }

   private static boolean incomingIntegralAssignment(JavaComputationRegion region) {
      if (region.outputs().size() != 1 || !region.internalBindings().isEmpty()) return false;
      var output = region.outputs().getFirst();
      return !output.declaration() && !output.returnValue() && !output.arrayStore() && output.declaredKind().integral()
            && output.declaredKind() != NumericKind.BIG_INTEGER;
   }

   private static int regionWeight(JavaComputationRegion region) {
      return Math.clamp(region.trace().occurrences().size(), 1, 64);
   }

   private static boolean overlaps(int leftStart, int leftLength, int rightStart, int rightLength) {
      return leftStart < (long) rightStart + rightLength && rightStart < (long) leftStart + leftLength;
   }

   private static String rewriteValues(CompilationUnit ast, String source, JavaComputationRegion region, Map<String, String> values, Map<String, String> compilerOptions) {
      return JavaRegionSourceRewriter.rewrite(ast, source, region, values, compilerOptions);
   }

   /** The first statement's indentation is outside the edit; only generated continuation lines need it. */
   private static String indentGenerated(String generated, String source, int offset) {
      int lineStart = Math.max(source.lastIndexOf('\n', offset - 1), source.lastIndexOf('\r', offset - 1)) + 1;
      int indentationEnd = lineStart;
      while (indentationEnd < offset && (source.charAt(indentationEnd) == ' ' || source.charAt(indentationEnd) == '\t')) indentationEnd++;
      String indentation = source.substring(lineStart, indentationEnd);
      int newline = source.indexOf('\n', offset);
      if (newline < 0) newline = source.indexOf('\n');
      String delimiter = newline >= 0 && newline > 0 && source.charAt(newline - 1) == '\r' ? "\r\n"
            : newline < 0 && source.indexOf('\r') >= 0 ? "\r" : "\n";
      return generated.replace("\n", delimiter + indentation);
   }

   private static boolean matchesAstSource(CompilationUnit original, String source, Map<String, String> options, int targetJava) {
      // Standalone clients can parse structured Javadoc with either setting.
      // Retry only that parser setting: syntax, documented text and source
      // positions must still match exactly; no stale-source guard is bypassed.
      for (String documentation : List.of(JavaCore.ENABLED, JavaCore.DISABLED)) {
         ASTParser parser = ASTParser.newParser(original.getAST().apiLevel());
         parser.setSource(source.toCharArray());
         HashMap<String, String> compilerOptions = new HashMap<>(options);
         if (compilerOptions.isEmpty()) {
            JavaCore.setComplianceOptions(targetJava == 8 ? "1.8" : Integer.toString(targetJava), compilerOptions);
         }
         compilerOptions.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, documentation);
         parser.setCompilerOptions(compilerOptions);
         CompilationUnit parsed = (CompilationUnit) parser.createAST(null);
         if (original.subtreeMatch(new ASTMatcher(true), parsed)
               && sourcePositions(original).equals(sourcePositions(parsed))) return true;
      }
      return false;
   }

   private static List<Integer> sourcePositions(CompilationUnit ast) {
      final ArrayList<Integer> positions = new ArrayList<>();
      ast.accept(new ASTVisitor() {
         public void preVisit(ASTNode node) {
            positions.add(node.getStartPosition());
            positions.add(node.getLength());
         }
      });
      return positions;
   }

   private static String guardedSource(
      CompilationUnit ast, String source, JavaComputationRegion region, String prelude, String guard, JavaComputationEmitter.Emission emitted
   ) {
      StringBuilder declarations = new StringBuilder();
      String original = source.substring(region.start(), region.start() + region.length());
      StringBuilder fallback = new StringBuilder(original);

      var escaping = region.outputs().stream().filter(output -> !output.returnValue())
            .map(JavaComputationRegion.OutputBinding::bindingKey).collect(Collectors.toSet());
      var bindings = new ArrayList<>(region.outputs());
      bindings.addAll(region.internalBindings());
      bindings.sort(Comparator.comparingInt(JavaComputationRegion.OutputBinding::statementStart).reversed());
      for (JavaComputationRegion.OutputBinding output : bindings) {
         if (output.declaration() && escaping.contains(output.bindingKey())) {
            String declarationPrefix = source.substring(output.statementStart(), output.initializerStart());
            int equalsOffset = declarationPrefix.lastIndexOf(61);
            if (equalsOffset < 0) {
               throw new IllegalArgumentException("DECLARATION_SHAPE_CHANGED");
            }

            String declaration = declarationPrefix.substring(0, equalsOffset).stripTrailing();
            if (!(NodeFinder.perform(ast, output.statementStart(), output.statementLength()) instanceof VariableDeclarationStatement statement)) {
               throw new IllegalArgumentException("DECLARATION_SHAPE_CHANGED");
            }

            if (statement.getType().isVar()) {
               int typeOffset = statement.getType().getStartPosition() - output.statementStart();
               declaration = declaration.substring(0, typeOffset) + javaType(output.declaredKind()) + declaration.substring(typeOffset + statement.getType().getLength());
            }

            declarations.insert(0, declaration + ";\n");
            fallback.replace(output.statementStart() - region.start(), output.initializerStart() - region.start(), output.javaName() + " = ");
         }
      }

      StringBuilder branch = new StringBuilder(emitted.statements());

      for (JavaComputationRegion.OutputBinding output : region.outputs()) {
         branch.append(output.returnValue() ? "return " : output.javaName() + " = ")
               .append(emitted.outputValues().get(output.id())).append(";\n");
      }

      return indentGenerated(declarations + prelude + "if (" + guard + ") {\n" + branch + "} else {\n", source, region.start())
            + indentGenerated("// " + JavaComputationExtractor.ORIGINAL_FALLBACK_MARKER + "\n", source, region.start())
            + fallback + indentGenerated("\n}", source, region.start());
   }

   private static void checkGeneratedSource(
      CompilationUnit original, String generated, Map<String, String> compilerOptions, int targetJava, IProgressMonitor monitor, MathematicalAnalysis.SourceEnvironment environment
   ) {
      ASTParser parser = ASTParser.newParser(original.getAST().apiLevel());
      if (original.getJavaElement() != null) {
         parser.setProject(original.getJavaElement().getJavaProject());
         parser.setUnitName(original.getJavaElement().getPath().toPortableString());
      } else if (environment != null) {
         parser.setEnvironment(
            environment.classpath().toArray(String[]::new),
            environment.sourcepath().toArray(String[]::new),
            environment.encodings().isEmpty() ? null : environment.encodings().toArray(String[]::new),
            true
         );
         parser.setUnitName(environment.unitName());
      } else {
         parser.setEnvironment(new String[0], new String[0], null, true);
         String typeName = "Calculation";

         for (Object topLevel : original.types()) {
            if (topLevel instanceof AbstractTypeDeclaration type) {
               typeName = type.getName().getIdentifier();
               if (Modifier.isPublic(type.getModifiers())) {
                  break;
               }
            }
         }

         String packagePath = original.getPackage() == null ? "" : original.getPackage().getName().getFullyQualifiedName().replace('.', '/') + "/";
         parser.setUnitName(packagePath + typeName + ".java");
      }

      HashMap<String, String> effectiveOptions = new HashMap<>(compilerOptions);
      if (effectiveOptions.isEmpty()) {
         JavaCore.setComplianceOptions(targetJava == 8 ? "1.8" : Integer.toString(targetJava), effectiveOptions);
      }

      parser.setCompilerOptions(effectiveOptions);
      parser.setResolveBindings(true);
      parser.setSource(generated.toCharArray());
      CompilationUnit generatedAst = (CompilationUnit)parser.createAST(monitor);

      for (IProblem problem : generatedAst.getProblems()) {
         if (problem.isError()) {
            throw new IllegalArgumentException("GENERATED_REGION_INVALID: " + problem.getMessage());
         }
      }
   }

   static String javaType(NumericKind kind) {
      return kind == NumericKind.BIG_INTEGER ? "java.math.BigInteger" : kind.name().toLowerCase(Locale.ROOT);
   }

   private static MathematicalAnalysis.Diagnostic diagnostic(String code, String message, JavaComputationRegion region) {
      return new MathematicalAnalysis.Diagnostic(code, message, region.start(), region.length());
   }

   private static MathematicalAnalysis.Analysis cancelled(String sourceDigest, Map<String, String> compilerOptions) {
      return new MathematicalAnalysis.Analysis(
         List.of(), List.of(new MathematicalAnalysis.Diagnostic("CANCELLED", "Analysis cancelled; source unchanged", 0, 0)), sourceDigest, compilerOptions
      );
   }

   private static String digest(String source) {
      try {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
      } catch (NoSuchAlgorithmException unavailable) {
         throw new IllegalStateException(unavailable);
      }
   }

   public record Analysis(
      List<MathematicalAnalysis.Replacement> replacements,
      List<MathematicalAnalysis.Diagnostic> diagnostics,
      String sourceDigest,
      Map<String, String> compilerOptions,
      List<MathematicalAnalysis.VerifiedRegion> evidence,
      List<MathematicalAnalysis.Replacement> documentation,
      @com.fasterxml.jackson.annotation.JsonIgnore JavaSourceContext.Checkpoint sourceDependencies,
      List<JavaSourceContext.Receipt> sourceDependencyReceipts
   ) {
      public Analysis(
         List<MathematicalAnalysis.Replacement> replacements,
         List<MathematicalAnalysis.Diagnostic> diagnostics,
         String sourceDigest,
         Map<String, String> compilerOptions,
         List<MathematicalAnalysis.VerifiedRegion> evidence,
         List<MathematicalAnalysis.Replacement> documentation,
         JavaSourceContext.Checkpoint sourceDependencies,
         List<JavaSourceContext.Receipt> sourceDependencyReceipts
      ) {
         replacements = List.copyOf(replacements);
         diagnostics = List.copyOf(diagnostics);
         compilerOptions = Map.copyOf(compilerOptions);
         evidence = List.copyOf(evidence);
         this.replacements = replacements;
         this.diagnostics = diagnostics;
         this.sourceDigest = sourceDigest;
         this.compilerOptions = compilerOptions;
         this.evidence = evidence;
         this.documentation = List.copyOf(documentation);
         sourceDependencyReceipts = sourceDependencyReceipts == null ? List.of() : List.copyOf(sourceDependencyReceipts);
         this.sourceDependencies = sourceDependencies == null
               ? (sourceDependencyReceipts.isEmpty() ? JavaSourceContext.Checkpoint.EMPTY
                     : JavaSourceContext.Checkpoint.detached(sourceDependencyReceipts)) : sourceDependencies;
         if (!sourceDependencyReceipts.equals(this.sourceDependencies.receipts()))
            throw new IllegalArgumentException("SOURCE_RECEIPTS_DIFFER_FROM_CONTEXT"); //$NON-NLS-1$
         this.sourceDependencyReceipts = sourceDependencyReceipts;
      }

      public Analysis(List<Replacement> replacements, List<Diagnostic> diagnostics, String sourceDigest,
            Map<String, String> compilerOptions, List<VerifiedRegion> evidence, List<Replacement> documentation,
            JavaSourceContext.Checkpoint sourceDependencies) {
         this(replacements, diagnostics, sourceDigest, compilerOptions, evidence, documentation, sourceDependencies,
               sourceDependencies == null ? List.of() : sourceDependencies.receipts());
      }

      public Analysis(List<Replacement> replacements, List<Diagnostic> diagnostics, String sourceDigest,
            Map<String, String> compilerOptions, List<VerifiedRegion> evidence, List<Replacement> documentation) {
         this(replacements, diagnostics, sourceDigest, compilerOptions, evidence, documentation, JavaSourceContext.Checkpoint.EMPTY);
      }

      public Analysis(List<Replacement> replacements, List<Diagnostic> diagnostics, String sourceDigest,
            Map<String, String> compilerOptions, List<VerifiedRegion> evidence) {
         this(replacements, diagnostics, sourceDigest, compilerOptions, evidence, List.of());
      }

      public Analysis(List<MathematicalAnalysis.Replacement> replacements, List<MathematicalAnalysis.Diagnostic> diagnostics, String sourceDigest, Map<String, String> compilerOptions) {
         this(replacements, diagnostics, sourceDigest, compilerOptions, List.of());
      }

      public boolean changed() {
         return !this.replacements.isEmpty();
      }

      public boolean matches(String source, Map<String, String> options) {
         return this.sourceDigest.equals(MathematicalAnalysis.digest(source)) && this.compilerOptions.equals(options) && sourceDependencies.current();
      }

      public TextEdit newEdit() {
         sourceDependencies.requireCurrent();
         MultiTextEdit edit = new MultiTextEdit();

         for (MathematicalAnalysis.Replacement replacement : this.replacements) {
            var plan = evidence.stream().filter(e -> e.offset() == replacement.offset() && e.length() == replacement.length()
                  && e.replacement().equals(replacement.replacement())).map(VerifiedRegion::sourcePlan).filter(Objects::nonNull).findFirst();
            if (plan.isPresent()) {
               if (!plan.get().sourceSha256().equals(sourceDigest)) throw new IllegalArgumentException("SOURCE_PLAN_VERSION_DIFFERS"); //$NON-NLS-1$
               edit.addChild(plan.get().newEdit());
            } else edit.addChild(new ReplaceEdit(replacement.offset(), replacement.length(), replacement.replacement()));
         }
         for (MathematicalAnalysis.Replacement note : documentation) {
            edit.addChild(new ReplaceEdit(note.offset(), note.length(), note.replacement()));
         }

         return edit;
      }
   }

   public enum Stage { SOURCE, SEARCH, SEARCH_OR_PROOF, PROOF, EMISSION, COST, BUDGET, CANCELLATION, RESULT, UNKNOWN }

   public record Diagnostic(String code, String message, int offset, int length, Stage stage) {
      public Diagnostic {
         Objects.requireNonNull(code);
         if (stage == null) stage = stageFor(code);
      }
      /** Old reports and source clients remain readable; unknown categories are not guessed. */
      public Diagnostic(String code, String message, int offset, int length) {
         this(code, message, offset, length, stageFor(code));
      }
      private static Stage stageFor(String code) {
         return switch (code) {
            case "SIDE_EFFECT_BOUNDARY", "SOURCE_SLICE_CONNECTED", "SOURCE_SLICE_LIMIT", "SOURCE_SLICE_LOCAL_RETRY", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                 "PINNED_SOURCE_EFFECT", "STALE_AST_SOURCE", "EXCLUDED_FILE", "EXCLUSION_REQUIRES_FILE_CONTEXT" -> Stage.SOURCE; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            case "NOIMPROVEMENT" -> Stage.SEARCH; //$NON-NLS-1$
            case "INCONCLUSIVE", "UNSUPPORTED" -> Stage.SEARCH_OR_PROOF; //$NON-NLS-1$ //$NON-NLS-2$
            case "REVERIFICATION_FAILED" -> Stage.PROOF; //$NON-NLS-1$
            case "COMBINED_EDITS_INVALID" -> Stage.EMISSION; //$NON-NLS-1$
            case "NO_RUNTIME_IMPROVEMENT", "GUARD_COST_EXCEEDS_BENEFIT", "ARRAY_CAPTURE_COST_EXCEEDS_BENEFIT" -> Stage.COST; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            case "BUDGET_EXCEEDED", "BUDGETEXCEEDED" -> Stage.BUDGET; //$NON-NLS-1$ //$NON-NLS-2$
            case "CANCELLED" -> Stage.CANCELLATION; //$NON-NLS-1$
            case "VERIFIED_CANDIDATE" -> Stage.RESULT; //$NON-NLS-1$
            default -> Stage.UNKNOWN;
         };
      }
   }

   public record Replacement(int offset, int length, String replacement, String description) {
   }

   public record SourceEnvironment(String unitName, List<String> classpath, List<String> sourcepath, List<String> encodings) {
      public SourceEnvironment(String unitName, List<String> classpath, List<String> sourcepath, List<String> encodings) {
         Objects.requireNonNull(unitName);
         classpath = List.copyOf(classpath);
         sourcepath = List.copyOf(sourcepath);
         encodings = List.copyOf(encodings);
         if (!unitName.isBlank() && (encodings.isEmpty() || encodings.size() == sourcepath.size())) {
            this.unitName = unitName;
            this.classpath = classpath;
            this.sourcepath = sourcepath;
            this.encodings = encodings;
         } else {
            throw new IllegalArgumentException("INVALID_SOURCE_ENVIRONMENT");
         }
      }
   }

   public record VerifiedRegion(int offset, int length, VerificationEvidence proof, CostAssessment cost,
      String original, String replacement, ComputationExplanation explanation, JavaSourceRewritePlan sourcePlan) {
      public VerifiedRegion {
         if (sourcePlan != null) sourcePlan.requireEnvelope(offset, length, original, replacement);
         if (explanation != null && !proof.equals(explanation.verified().proof())) {
            throw new IllegalArgumentException("EXPLANATION_PROOF_BINDING_DIFFERS");
         }
      }
      public VerifiedRegion(int offset, int length, VerificationEvidence proof, CostAssessment cost,
            String original, String replacement, ComputationExplanation explanation) {
         this(offset, length, proof, cost, original, replacement, explanation, null);
      }
      /** Compatibility for explicitly constructed historical report records. */
      public VerifiedRegion(int offset, int length, VerificationEvidence proof, CostAssessment cost,
            String original, String replacement) {
         this(offset, length, proof, cost, original, replacement, null);
      }
   }
}

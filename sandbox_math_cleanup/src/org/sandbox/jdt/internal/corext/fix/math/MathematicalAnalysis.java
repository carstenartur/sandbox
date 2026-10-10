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
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationBudget;
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

      if (monitor.isCanceled()) {
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

      JavaComputationExtractor.Extraction extraction = new JavaComputationExtractor().extractRuntime(ast, source, options);

      for (JavaComputationExtractor.Diagnostic diagnostic : extraction.diagnostics()) {
         diagnostics.add(new MathematicalAnalysis.Diagnostic(diagnostic.code(), diagnostic.message(), diagnostic.offset(), diagnostic.length()));
      }

      monitor.beginTask("Verify mathematics regions", extraction.regions().size());
      ComputationOptimizer optimizer = new ComputationOptimizer();
      JavaComputationEmitter emitter = new JavaComputationEmitter();
      MathematicalJavadoc documentation = new MathematicalJavadoc();
      CancellationToken cancellation = monitor::isCanceled;
      long remainingWork = options.workBudget();
      List<JavaComputationRegion> pending = new ArrayList<>(extraction.regions());
      List<JavaComputationRegion> fragments = null;

      try {
         for (int regionIndex = 0; regionIndex < pending.size(); regionIndex++) {
            JavaComputationRegion region = pending.get(regionIndex);
            int regionsLeft = pending.size() - regionIndex;
            if (monitor.isCanceled()) {
               return cancelled(sourceDigest, compilerOptions);
            }

            if (selectionOffset < 0 || selectionOffset < region.start() + region.length() && (long)selectionOffset + Math.max(1, selectionLength) > region.start()) {
               if (remainingWork <= 0L) {
                  diagnostics.add(diagnostic("BUDGET_EXCEEDED", "Analysis budget exhausted", region));
                  break;
               }

               // One difficult method must not consume the search allocation of all later methods.
               long regionWork = selectionOffset >= 0 ? remainingWork : Math.max(1L, remainingWork / regionsLeft);
               boolean mathematicalIntegral = false;
               boolean canTryMathematical = options.mathematicalOptIn() && IntegralMathematicalDomain.supports(region);
               long ordinaryWork = canTryMathematical ? Math.max(1L, regionWork / 2) : regionWork;
               boolean mathematicalBigInteger = options.mathematicalOptIn()
                     && region.trace().occurrences().stream().anyMatch(o -> o.evaluatedKind() == NumericKind.BIG_INTEGER);
               OptimizationRequest request = new OptimizationRequest(
                  region.plan(),
                  region.trace(),
                  options.kinds(),
                  "java25-numeric/v1",
                  region.assumptions(),
                  options.safety(),
                  options.goal(),
                  new OptimizationBudget(ordinaryWork, options.maxStates(), 64, 5000L),
                  options.safety() == SafetyProfile.CHECKED_THROW ? CheckedPolicy.EXPLICIT_DEFAULT : CheckedPolicy.NONE
               );

               try {
                  OptimizationResult result = optimizer.optimize(request, cancellation);
                  // Keep ordinary Java sharing improvements. A different overflow contract is
                  // only needed after that search fails, and both attempts share one work budget.
                  // Inconclusive does not report consumed work; conservatively debit its
                  // whole allocation. Reserve half up front so that fallback stays bounded.
                  long ordinarySpent = result instanceof NoImprovement ordinary ? ordinary.work() : ordinaryWork;
                  if (canTryMathematical && (result instanceof NoImprovement || result instanceof OptimizationResult.Inconclusive)
                        && ordinarySpent < regionWork) {
                     remainingWork -= ordinarySpent;
                     mathematicalIntegral = true;
                     request = new OptimizationRequest(region.plan(), region.trace(), options.kinds(), "java25-numeric/v1",
                           region.assumptions(), SafetyProfile.CHECKED_THROW, OptimizationGoal.READABILITY,
                           new OptimizationBudget(regionWork - ordinarySpent, options.maxStates(), 64, 5000L), CheckedPolicy.EXPLICIT_DEFAULT);
                     result = optimizer.optimize(request, cancellation);
                  }
                  if (!(result instanceof Candidate candidate)) {
                     if (result instanceof OptimizationResult.Unsupported unsupported
                           && unsupported.toString().contains("plan preparation work bound exceeded")) {
                        if (fragments == null) fragments = new JavaComputationExtractor().extractRuntimeFragments(ast, source, options).regions();
                        var smaller = fragments.stream().filter(part -> part.start() >= region.start()
                              && part.start() + part.length() <= region.start() + region.length() && part.length() < region.length()).toList();
                        if (!smaller.isEmpty()) {
                           // Failed preparation is bounded by the SDK structural cap; reserve
                           // a conservative full-cap charge before scheduling smaller requests.
                           remainingWork -= Math.min(remainingWork, 2L * de.regelsuche.search.program.JointComputationPlan.MAX_NODES);
                           pending.addAll(regionIndex + 1, smaller);
                           diagnostics.add(diagnostic("REGION_PREPARATION_SPLIT", "Preparation limit reached; retrying " + smaller.size()
                                 + " smaller source regions within the remaining file budget", region));
                           continue;
                        }
                     }
                     diagnostics.add(diagnostic(result.getClass().getSimpleName().toUpperCase(Locale.ROOT), result.toString(), region));
                     if (result instanceof NoImprovement noImprovement) {
                        remainingWork -= noImprovement.work();
                     }

                     if (result instanceof BudgetExceeded) {
                        remainingWork -= regionWork;
                     }
                     continue;
                  }

                  remainingWork -= candidate.work();
                  if (!(optimizer.reverify(request, candidate, cancellation) instanceof Verified)) {
                     diagnostics.add(diagnostic("REVERIFICATION_FAILED", "Candidate evidence did not pass independent checking", region));
                     continue;
                  }

                  String contract = mathematicalIntegral ? IntegralMathematicalDomain.verify(region, candidate.plan())
                        : mathematicalBigInteger ? "BigInteger numerical values are preserved; result reference identity may change. "
                              + "Where receivers are unproved, the optimized branch requires positive exact BigInteger instances "
                              + "with bitLength() below 4096; otherwise the original calculation executes. "
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

                  HashSet<String> reserved = new HashSet<>(region.reservedNames());
                  reserved.addAll(generatedNames);
                  JavaComputationEmitter.Emission plain = emitter.emit(candidate.prepared(), region.inputNames(), reserved, options.targetJava());
                  JavaEmissionVerifier.verify(request, candidate, plain, region, options, cancellation);
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
                              + (mathematicalBigInteger ? " && " + receiver + ".signum() > 0" : "") + ")")
                        .collect(Collectors.joining(" && "));
                     replacement = guardedSource(ast, source, region, "", receiverGuard, plain);
                  } else if (candidate.obligations().guard() != GuardKind.NONE) {
                     JavaComputationEmitter.GuardEmission guarded = emitter.emitGuard(request, candidate, region.inputNames(), reserved, options.targetJava());
                     replacement = guardedSource(ast, source, region, guarded.statements(), guarded.guardJava(), guarded.replacement());
                  } else {
                     replacement = indentGenerated(plain.statements(), source, region.start()) + rewriteValues(ast, source, region, plain.outputValues(), compilerOptions);
                  }

                  checkGeneratedSource(
                     ast,
                     source.substring(0, region.start()) + replacement + source.substring(region.start() + region.length()),
                     compilerOptions,
                     options.targetJava(),
                     (IProgressMonitor)monitor,
                     environment
                  );
                  if (monitor.isCanceled()) {
                     return cancelled(sourceDigest, compilerOptions);
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
                  if (contract != null) {
                     description += "; " + contract;
                     documentation.add(ast, region, contract);
                  }
                  if (options.safety() == SafetyProfile.CHECKED_THROW) {
                     description = description
                        + "; CHECKED_THROW changes the Java contract: numerical violations may throw ArithmeticException. Both the original and replacement operations must be checked.";
                  }

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
                        region.start(), region.length(), candidate.evidence(), cost, source.substring(region.start(), region.start() + region.length()), replacement
                     )
                  );
                  diagnostics.add(diagnostic("VERIFIED_CANDIDATE", description, region));
               } catch (IllegalArgumentException | UnsupportedOperationException unsupported) {
                  diagnostics.add(diagnostic("UNSUPPORTED_OR_UNPROVED", unsupported.getMessage(), region));
               }

               monitor.worked(1);
            }
         }
      } finally {
         monitor.done();
      }

      if (monitor.isCanceled()) {
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

      return monitor.isCanceled() ? cancelled(sourceDigest, compilerOptions) : analysis;
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

      for (JavaComputationRegion.OutputBinding output : region.outputs().reversed()) {
         if (output.declaration()) {
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
      List<MathematicalAnalysis.Replacement> documentation
   ) {
      public Analysis(
         List<MathematicalAnalysis.Replacement> replacements,
         List<MathematicalAnalysis.Diagnostic> diagnostics,
         String sourceDigest,
         Map<String, String> compilerOptions,
         List<MathematicalAnalysis.VerifiedRegion> evidence,
         List<MathematicalAnalysis.Replacement> documentation
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
         return this.sourceDigest.equals(MathematicalAnalysis.digest(source)) && this.compilerOptions.equals(options);
      }

      public TextEdit newEdit() {
         MultiTextEdit edit = new MultiTextEdit();

         for (MathematicalAnalysis.Replacement replacement : this.replacements) {
            edit.addChild(new ReplaceEdit(replacement.offset(), replacement.length(), replacement.replacement()));
         }
         for (MathematicalAnalysis.Replacement note : documentation) {
            edit.addChild(new ReplaceEdit(note.offset(), note.length(), note.replacement()));
         }

         return edit;
      }
   }

   public record Diagnostic(String code, String message, int offset, int length) {
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

   public record VerifiedRegion(int offset, int length, VerificationEvidence proof, CostAssessment cost, String original, String replacement) {
   }
}

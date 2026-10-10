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
      CancellationToken cancellation = monitor::isCanceled;
      long remainingWork = options.workBudget();
      int unvisitedRegions = extraction.regions().size();

      try {
         for (JavaComputationRegion region : extraction.regions()) {
            int regionsLeft = unvisitedRegions--;
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
               OptimizationRequest request = new OptimizationRequest(
                  region.plan(),
                  region.trace(),
                  options.kinds(),
                  "java25-numeric/v1",
                  region.assumptions(),
                  options.safety(),
                  options.goal(),
                  new OptimizationBudget(regionWork, options.maxStates(), 64, 5000L),
                  options.safety() == SafetyProfile.CHECKED_THROW ? CheckedPolicy.EXPLICIT_DEFAULT : CheckedPolicy.NONE
               );

               try {
                  OptimizationResult result = optimizer.optimize(request, cancellation);
                  if (!(result instanceof Candidate candidate)) {
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
                  var explanation = ComputationExplanations.describe(request, candidate, cancellation);
                  if (!(explanation.verification() instanceof Verified)) {
                     diagnostics.add(diagnostic("REVERIFICATION_FAILED", "Candidate evidence did not pass independent checking", region));
                     continue;
                  }

                  HashSet<String> reserved = new HashSet<>(region.reservedNames());
                  reserved.addAll(generatedNames);
                  JavaComputationEmitter.Emission plain = emitter.emit(candidate.prepared(), region.inputNames(), reserved, options.targetJava());
                  JavaEmissionVerifier.verify(request, candidate, plain, region, options, cancellation);
                  MathExplanation explanationText = MathExplanation.render(explanation.explanation().orElseThrow(),
                     request, candidate, region, plain, options, cancellation);
                  String replacement;
                  if (options.safety() == SafetyProfile.CHECKED_THROW) {
                     JavaComputationEmitter.Emission checked = emitter.emitChecked(request, candidate, region.inputNames(), reserved, options.targetJava());
                     replacement = indentGenerated(checked.statements(), source, region.start()) + rewriteValues(ast, source, region, checked.outputValues(), compilerOptions);
                  } else if (!region.receiverGuards().isEmpty()) {
                     if (candidate.obligations().guard() != GuardKind.NONE) {
                        throw new IllegalArgumentException("MIXED_RECEIVER_NUMERIC_GUARD_UNSUPPORTED");
                     }

                     if (options.goal() == OptimizationGoal.LOWER_ESTIMATED_RUNTIME) {
                        throw new IllegalArgumentException("RECEIVER_GUARD_RUNTIME_COST_UNQUALIFIED");
                     }

                     String receiverGuard = region.receiverGuards()
                        .stream()
                        .sorted()
                        .map(receiver -> "(" + receiver + " != null && " + receiver + ".getClass() == java.math.BigInteger.class && " + receiver + ".bitLength() < 4096)")
                        .collect(Collectors.joining(" && "));
                     replacement = guardedSource(ast, source, region, "", receiverGuard, plain);
                  } else if (candidate.obligations().guard() != GuardKind.NONE) {
                     JavaComputationEmitter.GuardEmission guarded = emitter.emitGuard(request, candidate, region.inputNames(), reserved, options.targetJava());
                     replacement = guardedSource(ast, source, region, guarded.statements(), guarded.guardJava(), guarded.replacement());
                  } else {
                     replacement = indentGenerated(plain.statements(), source, region.start()) + rewriteValues(ast, source, region, plain.outputValues(), compilerOptions);
                  }

                  if (options.explanations() == MathCleanUpOptions.ExplanationMode.ALL
                        || options.explanations() == MathCleanUpOptions.ExplanationMode.NONTRIVIAL && explanationText.nontrivial()) {
                     replacement = indentGenerated(explanationText.sourceComment(), source, region.start()) + replacement;
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
                  if (!region.receiverGuards().isEmpty()) {
                     long receiverCheckCost = 5L * region.receiverGuards().size();
                     cost = new CostAssessment(
                        cost.sourceScore(),
                        cost.candidateScore() + receiverCheckCost,
                        cost.checkWork() + receiverCheckCost,
                        region.trace().occurrences().size(),
                        cost.sourceCost(),
                        cost.candidateCost(),
                        false
                     );
                  }

                  String description = "Mathematics "
                     + options.safety()
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

      MathematicalAnalysis.Analysis analysis = new MathematicalAnalysis.Analysis(replacements, diagnostics, sourceDigest, compilerOptions, evidence);
      if (replacements.size() > 1) {
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
      List<MathematicalAnalysis.VerifiedRegion> evidence
   ) {
      public Analysis(
         List<MathematicalAnalysis.Replacement> replacements,
         List<MathematicalAnalysis.Diagnostic> diagnostics,
         String sourceDigest,
         Map<String, String> compilerOptions,
         List<MathematicalAnalysis.VerifiedRegion> evidence
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

/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.ast.Expr;
import de.regelsuche.ast.FunctionExpr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.sdk.optimization.CancellationToken;
import de.regelsuche.sdk.optimization.CheckedPolicy;
import de.regelsuche.sdk.optimization.ComputationOptimizer;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationRequest;
import de.regelsuche.sdk.optimization.SafetyProfile;
import de.regelsuche.sdk.optimization.SemanticAssumption;
import de.regelsuche.sdk.optimization.SourceEvaluationTrace;
import de.regelsuche.sdk.optimization.VerificationResult;
import de.regelsuche.sdk.optimization.OptimizationResult.Candidate;
import de.regelsuche.sdk.optimization.SemanticAssumption.Kind;
import de.regelsuche.sdk.optimization.VerificationResult.Verified;
import de.regelsuche.search.program.JointComputationPlan;
import de.regelsuche.search.program.ComputationBackend.Type;
import de.regelsuche.search.program.JointComputationPlan.Output;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

final class JavaEmissionVerifier {
   private JavaEmissionVerifier() {
   }

   static void verify(
      OptimizationRequest request,
      Candidate candidate,
      JavaComputationEmitter.Emission emission,
      JavaComputationRegion region,
      MathCleanUpOptions options,
      CancellationToken cancellation
   ) {
      if (cancellation.isCancelled()) {
         throw new IllegalArgumentException("CANCELLED");
      }

      if (!emission.outputValues().keySet().equals(candidate.prepared().outputBindings().keySet())) {
         throw new IllegalArgumentException("EMITTED_OUTPUT_BINDINGS_MISMATCH");
      }

      StringBuilder source = new StringBuilder("class MathRoundTrip { static void verify(");
      ArrayList<String> parameters = new ArrayList<>();
      HashMap<String, String> originalInputIds = new HashMap<>();

      for (Entry<String, Type> input : request.plan().inputs().entrySet()) {
         String javaName = region.inputNames().get(input.getKey());
         parameters.add(MathematicalAnalysis.javaType(NumericKind.fromType(input.getValue())) + " " + javaName);
         originalInputIds.put(javaName, input.getKey());
      }

      source.append(String.join(",", parameters)).append(") {\n").append(emission.statements());
      LinkedHashMap<String, String> outputNames = new LinkedHashMap<>();
      HashSet<String> reservedNames = new HashSet<>(region.reservedNames());
      int nextOutput = 0;

      for (Output output : candidate.plan().outputs()) {
         String name;
         do {
            name = "_mathVerifiedOutput" + nextOutput++;
         } while (!reservedNames.add(name));

         outputNames.put(output.name(), name);
         source.append(MathematicalAnalysis.javaType(NumericKind.fromType(output.type())))
            .append(' ')
            .append(name)
            .append(" = ")
            .append(emission.outputValues().get(output.name()))
            .append(";\n");
      }

      source.append("}}\n");
      ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
      parser.setSource(source.toString().toCharArray());
      parser.setKind(ASTParser.K_COMPILATION_UNIT);
      parser.setUnitName("MathRoundTrip.java");
      parser.setEnvironment(new String[0], new String[0], null, true);
      parser.setResolveBindings(true);
      HashMap<String, String> compilerOptions = new HashMap<>();
      JavaCore.setComplianceOptions(options.targetJava() == 8 ? "1.8" : Integer.toString(options.targetJava()), compilerOptions);
      parser.setCompilerOptions(compilerOptions);
      CompilationUnit ast = (CompilationUnit)parser.createAST(null);

      for (IProblem problem : ast.getProblems()) {
         if (problem.isError()) {
            throw new IllegalArgumentException("EMITTED_JAVA_INVALID: " + problem.getMessage());
         }
      }
      Block body = declarationBody(ast);

      MathCleanUpOptions extractionOptions = new MathCleanUpOptions(
         true,
         EnumSet.allOf(NumericKind.class),
         SafetyProfile.GUARDED_FALLBACK,
         options.goal(),
         options.workBudget(),
         options.maxStates(),
         false,
         options.targetJava(),
         List.of()
      );
      JavaComputationExtractor.Extraction extraction = new JavaComputationExtractor().extractForVerification(ast, source.toString(), extractionOptions);
      // A correct output cannot excuse skipped statements or an incompletely decoded initializer.
      if (!extraction.diagnostics().isEmpty() || extraction.regions().size() != 1
            || extraction.regions().getFirst().outputs().size() != body.statements().size()) {
         throw new IllegalArgumentException("EMITTED_BODY_NOT_FULLY_EXTRACTED: " + extraction.diagnostics());
      }
      HashMap<String, Expr> expressionsByJavaName = new HashMap<>();
      ArrayList<SourceEvaluationTrace.Occurrence> emittedOccurrences = new ArrayList<>();

      for (JavaComputationRegion decodedRegion : extraction.regions()) {
         HashMap<String, String> inputIds = new HashMap<>();

         for (Entry<String, String> decodedInput : decodedRegion.inputNames().entrySet()) {
            String originalId = originalInputIds.get(decodedInput.getValue());
            if (originalId == null) {
               throw new IllegalArgumentException("EMITTED_INPUT_NOT_BOUND");
            }

            inputIds.put(decodedInput.getKey(), originalId);
         }

         List<Expr> expressions = decodedRegion.plan().outputExpressions();

         for (int index = 0; index < decodedRegion.outputs().size(); index++) {
            expressionsByJavaName.put(decodedRegion.outputs().get(index).javaName(), rename(expressions.get(index), inputIds));
         }
         for (var occurrence : decodedRegion.trace().occurrences()) {
            emittedOccurrences.add(new SourceEvaluationTrace.Occurrence(occurrence.sourceId(),
                  rename(occurrence.expression(), inputIds), occurrence.declaredKind(), occurrence.evaluatedKind()));
         }
      }

      ArrayList<Expr> decodedOutputs = new ArrayList<>();

      for (String outputName : outputNames.values()) {
         Expr expression = expressionsByJavaName.get(outputName);
         if (expression == null) {
            throw new IllegalArgumentException("EMITTED_OUTPUT_NOT_EXTRACTED: " + extraction.diagnostics());
         }

         decodedOutputs.add(expression);
      }

      JointComputationPlan decodedPlan = request.plan().withOutputs(decodedOutputs);
      HashSet<SemanticAssumption> replacementAssumptions = new HashSet<>(request.assumptions());
      if (request.safetyProfile() != SafetyProfile.PRESERVE_JAVA) {
         replacementAssumptions.add(
            new SemanticAssumption(
               Kind.NO_NAN_PAYLOAD_OBSERVATION, "finiteReplacementBranch", "", "replacement is committed only after finite original and candidate checks"
            )
         );
      }

      OptimizationRequest replacementRequest = new OptimizationRequest(
         candidate.plan(),
         SourceEvaluationTrace.fromPlan(candidate.plan()),
         request.selectedKinds(),
         request.semanticsRevision(),
         replacementAssumptions,
         SafetyProfile.PRESERVE_JAVA,
         request.goal(),
         request.budget(),
         CheckedPolicy.NONE
      );
      VerificationResult replacementProof = new ComputationOptimizer().verify(replacementRequest, decodedPlan, cancellation);
      if (!(replacementProof instanceof Verified)) {
         throw new IllegalArgumentException("EMITTED_CANDIDATE_MISMATCH: " + replacementProof);
      }

      // Reverse the strict proof with the entire decoded execution trace. This includes dead
      // operations: division, exact arithmetic and BigInteger range failures remain observable.
      OptimizationRequest emittedRequest = new OptimizationRequest(decodedPlan,
            new SourceEvaluationTrace(emittedOccurrences), request.selectedKinds(), request.semanticsRevision(),
            replacementAssumptions, SafetyProfile.PRESERVE_JAVA, request.goal(), request.budget(), CheckedPolicy.NONE);
      VerificationResult emittedProof = new ComputationOptimizer().verify(emittedRequest, candidate.plan(), cancellation);
      if (!(emittedProof instanceof Verified)) {
         throw new IllegalArgumentException("EMITTED_TRACE_NOT_PROVED: " + emittedProof);
      }

      VerificationResult originalProof = new ComputationOptimizer().verify(request, decodedPlan, cancellation);
      if (!(originalProof instanceof Verified)) {
         throw new IllegalArgumentException("EMITTED_JAVA_NOT_PROVED: " + originalProof);
      }
   }

   private static Block declarationBody(CompilationUnit ast) {
      if (ast.types().size() != 1 || !(ast.types().getFirst() instanceof TypeDeclaration type)
            || type.bodyDeclarations().size() != 1
            || !(type.bodyDeclarations().getFirst() instanceof MethodDeclaration method)
            || !method.getName().getIdentifier().equals("verify") || method.getBody() == null) {
         throw new IllegalArgumentException("EMITTED_CONTAINER_SHAPE_CHANGED");
      }
      Block body = method.getBody();
      for (Object child : body.statements()) {
         if (!(child instanceof VariableDeclarationStatement declaration)
               || declaration.fragments().size() != 1
               || ((VariableDeclarationFragment) declaration.fragments().getFirst()).getInitializer() == null) {
            throw new IllegalArgumentException("EMITTED_NON_DECLARATION_STATEMENT");
         }
      }
      return body;
   }

   private static Expr rename(Expr expression, Map<String, String> inputIds) {
      if (expression instanceof VariableExpr variable) {
         String originalId = inputIds.get(variable.name());
         if (originalId == null) {
            throw new IllegalArgumentException("EMITTED_UNBOUND_VALUE");
         } else {
            return new VariableExpr(originalId);
         }
      } else {
         return (expression instanceof FunctionExpr function
            ? new FunctionExpr(function.name(), function.arguments().stream().map(operand -> rename(operand, inputIds)).toList())
            : expression);
      }
   }
}

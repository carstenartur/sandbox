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
import org.eclipse.jdt.core.dom.CompilationUnit;

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

      StringBuilder source = new StringBuilder("class MathRoundTrip { static void verify(");
      ArrayList<String> parameters = new ArrayList<>();
      HashMap<String, String> originalInputIds = new HashMap<>();

      for (Entry<String, Type> input : request.plan().inputs().entrySet()) {
         String javaName = region.inputNames().get(input.getKey());
         parameters.add(MathematicalAnalysis.javaType(NumericKind.fromType((Type)input.getValue())) + " " + javaName);
         originalInputIds.put(javaName, (String)input.getKey());
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
      parser.setKind(8);
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
      HashMap<String, Expr> expressionsByJavaName = new HashMap<>();

      for (JavaComputationRegion decodedRegion : extraction.regions()) {
         HashMap<String, String> inputIds = new HashMap<>();

         for (Entry<String, String> decodedInput : decodedRegion.inputNames().entrySet()) {
            String originalId = (String)originalInputIds.get(decodedInput.getValue());
            if (originalId == null) {
               throw new IllegalArgumentException("EMITTED_INPUT_NOT_BOUND");
            }

            inputIds.put((String)decodedInput.getKey(), originalId);
         }

         List<Expr> expressions = decodedRegion.plan().outputExpressions();

         for (int index = 0; index < decodedRegion.outputs().size(); index++) {
            expressionsByJavaName.put(decodedRegion.outputs().get(index).javaName(), rename((Expr)expressions.get(index), inputIds));
         }
      }

      ArrayList<Expr> decodedOutputs = new ArrayList<>();

      for (String outputName : outputNames.values()) {
         Expr expression = (Expr)expressionsByJavaName.get(outputName);
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

      VerificationResult originalProof = new ComputationOptimizer().verify(request, decodedPlan, cancellation);
      if (!(originalProof instanceof Verified)) {
         throw new IllegalArgumentException("EMITTED_JAVA_NOT_PROVED: " + originalProof);
      }
   }

   private static Expr rename(Expr expression, Map<String, String> inputIds) {
      if (expression instanceof VariableExpr variable) {
         String originalId = (String)inputIds.get(variable.name());
         if (originalId == null) {
            throw new IllegalArgumentException("EMITTED_UNBOUND_VALUE");
         } else {
            return new VariableExpr(originalId);
         }
      } else {
         return (Expr)(expression instanceof FunctionExpr function
            ? new FunctionExpr(function.name(), function.arguments().stream().map(operand -> rename(operand, inputIds)).toList())
            : expression);
      }
   }
}

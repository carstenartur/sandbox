/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.SemanticAssumption;
import de.regelsuche.sdk.optimization.SourceEvaluationTrace;
import de.regelsuche.search.program.JointComputationPlan;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record JavaComputationRegion(
   JointComputationPlan plan,
   SourceEvaluationTrace trace,
   List<JavaComputationRegion.OutputBinding> outputs,
   Map<String, String> inputNames,
   Set<SemanticAssumption> assumptions,
   Set<String> reservedNames,
   Set<String> receiverGuards,
   int start,
   int length
) {
   public JavaComputationRegion(
      JointComputationPlan plan,
      SourceEvaluationTrace trace,
      List<JavaComputationRegion.OutputBinding> outputs,
      Map<String, String> inputNames,
      Set<SemanticAssumption> assumptions,
      Set<String> reservedNames,
      Set<String> receiverGuards,
      int start,
      int length
   ) {
      outputs = List.copyOf(outputs);
      inputNames = Map.copyOf(inputNames);
      assumptions = Set.copyOf(assumptions);
      reservedNames = Set.copyOf(reservedNames);
      receiverGuards = Set.copyOf(receiverGuards);
      this.plan = plan;
      this.trace = trace;
      this.outputs = outputs;
      this.inputNames = inputNames;
      this.assumptions = assumptions;
      this.reservedNames = reservedNames;
      this.receiverGuards = receiverGuards;
      this.start = start;
      this.length = length;
   }

   public record OutputBinding(
      String id,
      String javaName,
      NumericKind declaredKind,
      int initializerStart,
      int initializerLength,
      int statementStart,
      int statementLength,
      boolean declaration,
      String bindingKey
   ) {
   }
}

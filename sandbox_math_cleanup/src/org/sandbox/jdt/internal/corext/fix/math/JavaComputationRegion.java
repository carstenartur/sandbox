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

/** Value outputs and removable source bindings are distinct from the original execution trace. */
public record JavaComputationRegion(
      JointComputationPlan plan, SourceEvaluationTrace trace, List<OutputBinding> outputs,
      Map<String, String> inputNames, Set<SemanticAssumption> assumptions,
      Set<String> reservedNames, Set<String> receiverGuards, int start, int length,
      List<OutputBinding> internalBindings) {
   public JavaComputationRegion {
      outputs = List.copyOf(outputs);
      inputNames = Map.copyOf(inputNames);
      assumptions = Set.copyOf(assumptions);
      reservedNames = Set.copyOf(reservedNames);
      receiverGuards = Set.copyOf(receiverGuards);
      internalBindings = List.copyOf(internalBindings);
   }

   public JavaComputationRegion(JointComputationPlan plan, SourceEvaluationTrace trace,
         List<OutputBinding> outputs, Map<String, String> inputNames,
         Set<SemanticAssumption> assumptions, Set<String> reservedNames,
         Set<String> receiverGuards, int start, int length) {
      this(plan, trace, outputs, inputNames, assumptions, reservedNames, receiverGuards,
            start, length, List.of());
   }

   public record OutputBinding(String id, String javaName, NumericKind declaredKind,
         int initializerStart, int initializerLength, int statementStart, int statementLength,
         boolean declaration, String bindingKey, boolean returnValue) {
      public OutputBinding(String id, String javaName, NumericKind declaredKind,
            int initializerStart, int initializerLength, int statementStart, int statementLength,
            boolean declaration, String bindingKey) {
         this(id, javaName, declaredKind, initializerStart, initializerLength, statementStart,
               statementLength, declaration, bindingKey, false);
      }
   }
}

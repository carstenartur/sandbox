/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import de.regelsuche.search.program.JointComputationPlan;

/** Projects only value outputs. Dead operations remain in the source trace and must be proved total. */
final class JavaRegionLiveness {
   private JavaRegionLiveness() {}

   static JavaComputationRegion project(JavaComputationRegion region, CompilationUnit unit,
         Map<String, IntSummaryStatistics> uses) {
      Set<String> visible = new HashSet<>();
      var lastVersions = new HashMap<String, JavaComputationRegion.OutputBinding>();
      int end = region.start() + region.length();
      for (var output : region.outputs()) {
         if (output.returnValue()) continue;
         lastVersions.put(output.bindingKey(), output);
         // A declaration, read or write outside [start, end) requires the binding to remain visible.
         var positions = uses.get(output.bindingKey());
         if (positions != null && (positions.getMin() < region.start() || positions.getMax() >= end)) {
            visible.add(output.bindingKey());
         }
      }
      // Never remove a comment embedded in a declaration or assignment. Inter-statement
      // comments are retained by the exact source-range rewrite instead.
      Set<String> commented = new HashSet<>();
      for (var output : region.outputs()) {
         for (Object value : unit.getCommentList()) {
            ASTNode comment = (ASTNode) value;
            if (comment.getStartPosition() >= output.statementStart()
                  && comment.getStartPosition() < output.statementStart() + output.statementLength()) {
               commented.add(output.bindingKey());
            }
         }
      }
      var outputs = new ArrayList<JavaComputationRegion.OutputBinding>();
      var internal = new ArrayList<JavaComputationRegion.OutputBinding>();
      var planOutputs = new ArrayList<JointComputationPlan.Output>();
      for (int index = 0; index < region.outputs().size(); index++) {
         var output = region.outputs().get(index);
         if (output.returnValue() || output.arrayStore() || commented.contains(output.bindingKey())
               || visible.contains(output.bindingKey()) && output == lastVersions.get(output.bindingKey())) {
            outputs.add(output);
            planOutputs.add(region.plan().outputs().get(index));
         } else {
            internal.add(output);
         }
      }
      if (outputs.isEmpty()) return null; // No observable value is not permission to delete computations.
      var projected = new JavaComputationRegion(new JointComputationPlan(region.plan().inputs(), Map.of(), planOutputs),
            region.trace(), outputs, region.inputNames(), region.assumptions(), region.reservedNames(),
            region.receiverGuards(), region.start(), region.length(), internal, region.arrayReads());
      // The projected region is the source boundary for both facts and emitted checks.
      return JavaSourceGuardRefinement.refine(projected, unit);
   }
}

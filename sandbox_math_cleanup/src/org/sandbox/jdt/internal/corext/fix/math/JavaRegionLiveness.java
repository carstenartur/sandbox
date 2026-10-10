/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import de.regelsuche.search.program.JointComputationPlan;

/** Projects only value outputs. Dead operations remain in the source trace and must be proved total. */
final class JavaRegionLiveness {
   private JavaRegionLiveness() {}

   static JavaComputationRegion project(JavaComputationRegion region, CompilationUnit unit) {
      Set<String> locals = new HashSet<>();
      for (var output : region.outputs()) {
         if (!output.returnValue()) locals.add(output.bindingKey());
      }
      Set<String> visible = new HashSet<>();
      int end = region.start() + region.length();
      unit.accept(new ASTVisitor() {
         @Override public boolean visit(SimpleName name) {
            if ((name.getStartPosition() < region.start() || name.getStartPosition() >= end)
                  && name.resolveBinding() instanceof IVariableBinding binding
                  && locals.contains(binding.getKey())) {
               // Even an external write still requires the local declaration to remain in scope.
               visible.add(binding.getKey());
            }
            return true;
         }
      });
      // Never remove a comment embedded in a declaration or assignment. Inter-statement
      // comments are retained by the exact source-range rewrite instead.
      for (var output : region.outputs()) {
         for (Object value : unit.getCommentList()) {
            ASTNode comment = (ASTNode) value;
            if (comment.getStartPosition() >= output.statementStart()
                  && comment.getStartPosition() < output.statementStart() + output.statementLength()) {
               visible.add(output.bindingKey());
            }
         }
      }
      var outputs = new ArrayList<JavaComputationRegion.OutputBinding>();
      var internal = new ArrayList<JavaComputationRegion.OutputBinding>();
      var planOutputs = new ArrayList<JointComputationPlan.Output>();
      for (int index = 0; index < region.outputs().size(); index++) {
         var output = region.outputs().get(index);
         if (output.returnValue() || visible.contains(output.bindingKey())) {
            outputs.add(output);
            planOutputs.add(region.plan().outputs().get(index));
         } else {
            internal.add(output);
         }
      }
      if (outputs.isEmpty()) return null; // No observable value is not permission to delete computations.
      return new JavaComputationRegion(new JointComputationPlan(region.plan().inputs(), Map.of(), planOutputs),
            region.trace(), outputs, region.inputNames(), region.assumptions(), region.reservedNames(),
            region.receiverGuards(), region.start(), region.length(), internal);
   }
}

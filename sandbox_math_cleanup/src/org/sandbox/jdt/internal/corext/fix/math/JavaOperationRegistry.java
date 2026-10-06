/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.NumericOperation;
import java.util.Optional;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;

public final class JavaOperationRegistry {
   private JavaOperationRegistry() {
   }

   public static Optional<NumericKind> kind(ITypeBinding binding) {
      if (binding != null && !binding.isRecovered()) {
         return switch (binding.getQualifiedName()) {
            case "java.math.BigInteger" -> Optional.of(NumericKind.BIG_INTEGER);
            case "byte" -> Optional.of(NumericKind.BYTE);
            case "short" -> Optional.of(NumericKind.SHORT);
            case "char" -> Optional.of(NumericKind.CHAR);
            case "int" -> Optional.of(NumericKind.INT);
            case "long" -> Optional.of(NumericKind.LONG);
            case "float" -> Optional.of(NumericKind.FLOAT);
            case "double" -> Optional.of(NumericKind.DOUBLE);
            default -> Optional.empty();
         };
      } else {
         return Optional.empty();
      }
   }

   public static Optional<NumericOperation> infix(Operator operator) {
      return switch (operator.toString()) {
         case "+" -> Optional.of(NumericOperation.ADD);
         case "-" -> Optional.of(NumericOperation.SUBTRACT);
         case "*" -> Optional.of(NumericOperation.MULTIPLY);
         case "/" -> Optional.of(NumericOperation.DIVIDE);
         case "%" -> Optional.of(NumericOperation.REMAINDER);
         case "&" -> Optional.of(NumericOperation.AND);
         case "|" -> Optional.of(NumericOperation.OR);
         case "^" -> Optional.of(NumericOperation.XOR);
         case "<<" -> Optional.of(NumericOperation.SHIFT_LEFT);
         case ">>" -> Optional.of(NumericOperation.SHIFT_RIGHT);
         case ">>>" -> Optional.of(NumericOperation.UNSIGNED_SHIFT_RIGHT);
         default -> Optional.empty();
      };
   }

   public static Optional<NumericOperation> method(IMethodBinding binding) {
      if (binding != null && !binding.isRecovered()) {
         String owner = binding.getMethodDeclaration().getDeclaringClass().getQualifiedName();
         if (owner.equals("java.math.BigInteger")) {
            return switch (binding.getName()) {
               case "add" -> Optional.of(NumericOperation.ADD);
               case "subtract" -> Optional.of(NumericOperation.SUBTRACT);
               case "multiply" -> Optional.of(NumericOperation.MULTIPLY);
               case "divide" -> Optional.of(NumericOperation.DIVIDE);
               case "remainder" -> Optional.of(NumericOperation.REMAINDER);
               case "negate" -> Optional.of(NumericOperation.NEGATE);
               case "abs" -> Optional.of(NumericOperation.ABS);
               case "mod" -> Optional.of(NumericOperation.MOD);
               case "modPow" -> Optional.of(NumericOperation.MOD_POW);
               case "pow" -> Optional.of(NumericOperation.POW);
               case "and" -> Optional.of(NumericOperation.AND);
               case "or" -> Optional.of(NumericOperation.OR);
               case "xor" -> Optional.of(NumericOperation.XOR);
               case "not" -> Optional.of(NumericOperation.NOT);
               case "shiftLeft" -> Optional.of(NumericOperation.SHIFT_LEFT);
               case "shiftRight" -> Optional.of(NumericOperation.SHIFT_RIGHT);
               default -> Optional.empty();
            };
         } else if (owner.equals("java.lang.Math")) {
            return switch (binding.getName()) {
               case "addExact" -> Optional.of(NumericOperation.ADD_EXACT);
               case "subtractExact" -> Optional.of(NumericOperation.SUBTRACT_EXACT);
               case "multiplyExact" -> Optional.of(NumericOperation.MULTIPLY_EXACT);
               case "negateExact" -> Optional.of(NumericOperation.NEGATE_EXACT);
               case "abs" -> Optional.of(NumericOperation.ABS);
               default -> Optional.empty();
            };
         } else {
            return Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }
}

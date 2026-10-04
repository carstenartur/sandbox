/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.ast.Expr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.sdk.optimization.JavaExpressions;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.NumericOperation;
import de.regelsuche.sdk.optimization.OptimizationRequest;
import de.regelsuche.sdk.optimization.RuntimeObligations;
import de.regelsuche.sdk.optimization.SafetyProfile;
import de.regelsuche.sdk.optimization.OptimizationResult.Candidate;
import de.regelsuche.sdk.optimization.SourceEvaluationTrace.Occurrence;
import de.regelsuche.search.program.PreparedJointComputation;
import de.regelsuche.search.program.ComputationBackend.Type;
import de.regelsuche.search.program.JointComputationPlan.Output;
import de.regelsuche.search.program.PreparedJointComputation.Node;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class JavaComputationEmitter {
   private static final Set<String> KEYWORDS = Set.of(
      "abstract",
      "assert",
      "boolean",
      "break",
      "byte",
      "case",
      "catch",
      "char",
      "class",
      "const",
      "continue",
      "default",
      "do",
      "double",
      "else",
      "enum",
      "extends",
      "final",
      "finally",
      "float",
      "for",
      "goto",
      "if",
      "implements",
      "import",
      "instanceof",
      "int",
      "interface",
      "long",
      "native",
      "new",
      "package",
      "private",
      "protected",
      "public",
      "return",
      "short",
      "static",
      "strictfp",
      "super",
      "switch",
      "synchronized",
      "this",
      "throw",
      "throws",
      "transient",
      "try",
      "void",
      "volatile",
      "while",
      "true",
      "false",
      "null",
      "_"
   );

   public JavaComputationEmitter.Emission emit(PreparedJointComputation plan, Map<String, String> inputNames, Set<String> reservedNames, int targetJava) {
      JavaComputationEmitter.State state = new JavaComputationEmitter.State(inputNames, reservedNames, targetJava, JavaComputationEmitter.Mode.PRESERVE, false, false);
      Map<String, String> outputs = schedule(plan, state);
      return new JavaComputationEmitter.Emission(state.code.toString(), outputs);
   }

   public JavaComputationEmitter.Emission emitChecked(OptimizationRequest request, Candidate candidate, Map<String, String> inputNames, Set<String> reservedNames, int targetJava) {
      validateContract(request, candidate, SafetyProfile.CHECKED_THROW);
      RuntimeObligations obligations = candidate.obligations();
      JavaComputationEmitter.State state = new JavaComputationEmitter.State(
         inputNames, reservedNames, targetJava, JavaComputationEmitter.Mode.CHECKED, obligations.checkIntegralRange(), obligations.requireFinite()
      );
      Map<String, String> originalOutputs = source(request, state);
      Map<String, String> replacementOutputs = schedule(candidate.prepared(), state);
      compareOutputs(request, candidate, originalOutputs, replacementOutputs, state);
      return new JavaComputationEmitter.Emission(state.code.toString(), replacementOutputs);
   }

   public JavaComputationEmitter.GuardEmission emitGuard(OptimizationRequest request, Candidate candidate, Map<String, String> inputNames, Set<String> reservedNames, int targetJava) {
      validateContract(request, candidate, SafetyProfile.GUARDED_FALLBACK);
      RuntimeObligations obligations = candidate.obligations();
      JavaComputationEmitter.State state = new JavaComputationEmitter.State(
         inputNames, reservedNames, targetJava, JavaComputationEmitter.Mode.GUARDED, obligations.checkIntegralRange(), obligations.requireFinite()
      );
      Map<String, String> originalOutputs = source(request, state);
      Map<String, String> replacementOutputs = schedule(candidate.prepared(), state);
      compareOutputs(request, candidate, originalOutputs, replacementOutputs, state);
      return new JavaComputationEmitter.GuardEmission(state.code.toString(), state.guard, new JavaComputationEmitter.Emission("", replacementOutputs));
   }

   private static void validateContract(OptimizationRequest request, Candidate candidate, SafetyProfile profile) {
      Objects.requireNonNull(request);
      Objects.requireNonNull(candidate);
      if (request.safetyProfile() != profile
         || candidate.evidence() == null
         || candidate.evidence().safetyProfile() != profile
         || !candidate.evidence().checkedPolicy().equals(request.checkedPolicy())
         || candidate.obligations() == null
         || !candidate.obligations().equals(candidate.evidence().obligations())
         || !candidate.obligations().originalTrace().equals(request.sourceTrace())) {
         throw unsupported("MATH_RUNTIME_CONTRACT_MISMATCH");
      } else if (request.checkedPolicy().checkTinyInexactUnderflow()) {
         throw unsupported("MATH_UNDERFLOW_POLICY_UNSUPPORTED");
      } else {
         HashSet<NumericKind> kinds = new HashSet<>();
         request.plan().inputs().values().forEach(value -> kinds.add(NumericKind.fromType(value)));
         request.sourceTrace().occurrences().forEach(value -> kinds.add(value.evaluatedKind()));
         candidate.prepared().nodes().forEach(value -> kinds.add(NumericKind.fromType(value.type())));
         boolean integral = kinds.stream().anyMatch(NumericKind::integral);
         boolean floating = kinds.stream().anyMatch(NumericKind::floatingPoint);
         if (profile == SafetyProfile.CHECKED_THROW
            && (
               integral && candidate.obligations().checkIntegralRange() != request.checkedPolicy().checkIntegralRange()
                  || floating
                     && (
                        candidate.obligations().requireFinite() != request.checkedPolicy().requireFinite()
                           || candidate.obligations().compareFloatingPointBits() != request.checkedPolicy().compareFloatingPointBits()
                     )
            )) {
            throw unsupported("MATH_CHECKED_POLICY_MISMATCH");
         }
      }
   }

   private static Map<String, String> source(OptimizationRequest request, JavaComputationEmitter.State state) {
      HashMap<Expr, String> evaluated = new HashMap<>();

      for (Occurrence occurrence : request.sourceTrace().occurrences()) {
         Expr expression = occurrence.expression();
         NumericKind kind = JavaExpressions.kindOf(expression, request.plan().inputs());
         if (kind != occurrence.evaluatedKind()) {
            throw unsupported("MATH_TRACE_TYPE_MISMATCH");
         }

         ArrayList<String> operands = new ArrayList<>();

         for (Expr operand : JavaExpressions.operands(expression)) {
            operands.add(sourceValue(operand, request.plan().inputs(), evaluated, state));
         }

         evaluated.put(expression, state.value(expression, kind, operands));
      }

      LinkedHashMap<String, String> outputs = new LinkedHashMap<>();
      List<Expr> expressions = request.plan().outputExpressions();

      for (int index = 0; index < expressions.size(); index++) {
         outputs.put(((Output)request.plan().outputs().get(index)).name(), sourceValue((Expr)expressions.get(index), request.plan().inputs(), evaluated, state));
      }

      return outputs;
   }

   private static String sourceValue(Expr expression, Map<String, Type> inputs, Map<Expr, String> evaluated, JavaComputationEmitter.State state) {
      String value = (String)evaluated.get(expression);
      if (value != null) {
         return value;
      }

      if (!(expression instanceof VariableExpr) && !JavaExpressions.isLiteral(expression)) {
         throw unsupported("MATH_MISSING_TRACE_OPERATION");
      }

      value = state.value(expression, JavaExpressions.kindOf(expression, inputs), List.of());
      evaluated.put(expression, value);
      return value;
   }

   private static Map<String, String> schedule(PreparedJointComputation plan, JavaComputationEmitter.State state) {
      Objects.requireNonNull(plan);
      List<Node> nodes = plan.nodes();
      HashSet<Integer> required = new HashSet<>();
      ArrayDeque<Integer> pending = new ArrayDeque<>(plan.outputBindings().values());

      while (!pending.isEmpty()) {
         int index = (Integer)pending.removeLast();
         if (index < 0 || index >= nodes.size()) {
            throw unsupported("MATH_INVALID_SCHEDULE");
         }

         if (required.add(index) && !JavaExpressions.isLiteral(((Node)nodes.get(index)).expression())) {
            pending.addAll(((Node)nodes.get(index)).arguments());
         }
      }

      HashMap<Integer, String> values = new HashMap<>();

      for (int index = 0; index < nodes.size(); index++) {
         if (required.contains(index)) {
            Node node = (Node)nodes.get(index);
            NumericKind kind = NumericKind.fromType(node.type());
            if (!(node.expression() instanceof VariableExpr) && JavaExpressions.resultKind(node.expression()) != kind) {
               throw unsupported("MATH_SCHEDULE_TYPE_MISMATCH");
            }

            ArrayList<String> arguments = new ArrayList<>();
            if (!JavaExpressions.isLiteral(node.expression())) {
               for (int dependency : node.arguments()) {
                  String value = (String)values.get(dependency);
                  if (dependency >= index || value == null) {
                     throw unsupported("MATH_NON_TOPOLOGICAL_SCHEDULE");
                  }

                  arguments.add(value);
               }
            }

            values.put(index, state.value(node.expression(), kind, arguments));
         }
      }

      LinkedHashMap<String, String> outputs = new LinkedHashMap<>();
      plan.outputBindings().forEach((name, index) -> outputs.put(name, (String)values.get(index)));
      return outputs;
   }

   private static void compareOutputs(
      OptimizationRequest request, Candidate candidate, Map<String, String> originalValues, Map<String, String> replacementValues, JavaComputationEmitter.State state
   ) {
      if (originalValues.keySet().equals(replacementValues.keySet()) && request.plan().outputs().size() == candidate.plan().outputs().size()) {
         int index = 0;

         while (index < request.plan().outputs().size()) {
            Output original = (Output)request.plan().outputs().get(index);
            Output replacement = (Output)candidate.plan().outputs().get(index);
            if (original.name().equals(replacement.name()) && original.type().equals(replacement.type())) {
               NumericKind kind = NumericKind.fromType(original.type());
               String before = (String)originalValues.get(original.name());
               String after = (String)replacementValues.get(original.name());

               String equal = switch (kind) {
                  case FLOAT -> "java.lang.Float.floatToRawIntBits(" + before + ") == java.lang.Float.floatToRawIntBits(" + after + ")";
                  case DOUBLE -> "java.lang.Double.doubleToRawLongBits(" + before + ") == java.lang.Double.doubleToRawLongBits(" + after + ")";
                  case BIG_INTEGER -> before + ".equals(" + after + ")";
                  default -> before + " == " + after;
               };
               if (!kind.floatingPoint() || candidate.obligations().requireFinite() && candidate.obligations().compareFloatingPointBits()) {
                  state.check(new JavaComputationEmitter.Check(equal, "MATH_NUMERIC_MISMATCH"));
                  index++;
                  continue;
               }

               throw unsupported("MATH_FLOATING_POINT_POLICY_INCOMPLETE");
            }

            throw unsupported("MATH_OUTPUT_TYPES_DIFFER");
         }
      } else {
         throw unsupported("MATH_OUTPUT_BINDINGS_DIFFER");
      }
   }

   private static List<JavaComputationEmitter.Check> integralChecks(NumericOperation operation, NumericKind kind, List<String> operands, boolean checkRange) {
      String left = (String)operands.getFirst();
      String right = operands.size() > 1 ? (String)operands.get(1) : "";
      ArrayList<Check> checks = new ArrayList<>();
      if (operation == NumericOperation.DIVIDE || operation == NumericOperation.REMAINDER) {
         checks.add(new JavaComputationEmitter.Check(right + " != 0", "MATH_DIVIDE_BY_ZERO"));
      }

      if (!checkRange) {
         return checks;
      }

      String condition = switch (operation) {
         case ADD, ADD_EXACT -> wideRange(kind, left, right, "+", "add");
         case SUBTRACT, SUBTRACT_EXACT -> wideRange(kind, left, right, "-", "subtract");
         case MULTIPLY, MULTIPLY_EXACT -> wideRange(kind, left, right, "*", "multiply");
         case NEGATE, NEGATE_EXACT, ABS -> left + " != " + min(kind);
         case DIVIDE -> "!(" + left + " == " + min(kind) + " && " + right + " == -1)";
         case SHIFT_LEFT -> kind == NumericKind.INT
            ? inRange("((long) " + left + " << (" + right + " & 31))", kind)
            : "java.math.BigInteger.valueOf(" + left + ").shiftLeft((int) (" + right + " & 63)).bitLength() <= 63";
         case REMAINDER, AND, OR, XOR, NOT, SHIFT_RIGHT, UNSIGNED_SHIFT_RIGHT -> null;
         default -> throw unsupported("MATH_CHECKED_OPERATION_UNSUPPORTED");
      };
      if (condition != null) {
         checks.add(new JavaComputationEmitter.Check(condition, "MATH_OVERFLOW"));
      }

      return checks;
   }

   private static String wideRange(NumericKind kind, String left, String right, String operator, String method) {
      return kind == NumericKind.INT
         ? inRange("((long) " + left + " " + operator + " " + right + ")", kind)
         : "java.math.BigInteger.valueOf(" + left + ")." + method + "(java.math.BigInteger.valueOf(" + right + ")).bitLength() <= 63";
   }

   private static String inRange(String value, NumericKind kind) {
      return "(" + value + ") >= " + min(kind) + " && (" + value + ") <= " + max(kind);
   }

   private static String operation(NumericOperation operation, NumericKind kind, List<String> operands) {
      byte arity = switch (operation) {
         case NEGATE, NEGATE_EXACT, ABS, NOT -> 1;
         default -> 2;
         case MOD_POW, MOD_MULTIPLY -> 3;
      };
      requireArity(operands, arity);
      String left = (String)operands.getFirst();
      String right = operands.size() > 1 ? (String)operands.get(1) : "";
      if (kind == NumericKind.BIG_INTEGER) {
         String method = switch (operation) {
            case ADD -> "add";
            default -> throw unsupported("MATH_BIG_INTEGER_OPERATION_UNSUPPORTED");
            case SUBTRACT -> "subtract";
            case MULTIPLY -> "multiply";
            case NEGATE -> "negate";
            case ABS -> "abs";
            case DIVIDE -> "divide";
            case SHIFT_LEFT -> "shiftLeft";
            case REMAINDER -> "remainder";
            case AND -> "and";
            case OR -> "or";
            case XOR -> "xor";
            case NOT -> "not";
            case SHIFT_RIGHT -> "shiftRight";
            case MOD_POW -> "modPow";
            case MOD_MULTIPLY -> null;
            case MOD -> "mod";
            case POW -> "pow";
         };
         return operation == NumericOperation.MOD_MULTIPLY
            ? left + ".multiply(" + right + ").mod(" + (String)operands.get(2) + ")"
            : left + "." + method + "(" + String.join(", ", operands.subList(1, operands.size())) + ")";
      } else {
         if (kind != NumericKind.INT && kind != NumericKind.LONG && !kind.floatingPoint()) {
            throw unsupported("MATH_PROMOTED_OPERATION_KIND_REQUIRED");
         }

         if (kind.floatingPoint() && (exact(operation) || switch (operation) {
            case SHIFT_LEFT, AND, OR, XOR, NOT, SHIFT_RIGHT, UNSIGNED_SHIFT_RIGHT -> true;
            default -> false;
         })) {
            throw unsupported("MATH_FLOATING_POINT_OPERATION_UNSUPPORTED");
         }

         return switch (operation) {
            case ADD -> left + " + " + right;
            case ADD_EXACT -> "java.lang.Math.addExact(" + left + ", " + right + ")";
            case SUBTRACT -> left + " - " + right;
            case SUBTRACT_EXACT -> "java.lang.Math.subtractExact(" + left + ", " + right + ")";
            case MULTIPLY -> left + " * " + right;
            case MULTIPLY_EXACT -> "java.lang.Math.multiplyExact(" + left + ", " + right + ")";
            case NEGATE -> "-" + left;
            case NEGATE_EXACT -> "java.lang.Math.negateExact(" + left + ")";
            case ABS -> "java.lang.Math.abs(" + left + ")";
            case DIVIDE -> left + " / " + right;
            case SHIFT_LEFT -> left + " << " + right;
            case REMAINDER -> left + " % " + right;
            case AND -> left + " & " + right;
            case OR -> left + " | " + right;
            case XOR -> left + " ^ " + right;
            case NOT -> "~" + left;
            case SHIFT_RIGHT -> left + " >> " + right;
            case UNSIGNED_SHIFT_RIGHT -> left + " >>> " + right;
            default -> throw unsupported("MATH_PRIMITIVE_OPERATION_UNSUPPORTED");
         };
      }
   }

   private static boolean exact(NumericOperation operation) {
      return switch (operation) {
         case ADD_EXACT, SUBTRACT_EXACT, MULTIPLY_EXACT, NEGATE_EXACT -> true;
         default -> false;
      };
   }

   private static NumericOperation checkedOperation(NumericOperation operation) {
      return switch (operation) {
         case ADD -> NumericOperation.ADD_EXACT;
         default -> operation;
         case SUBTRACT -> NumericOperation.SUBTRACT_EXACT;
         case MULTIPLY -> NumericOperation.MULTIPLY_EXACT;
         case NEGATE -> NumericOperation.NEGATE_EXACT;
      };
   }

   private static String literal(Expr expression, NumericKind kind) {
      Object value = JavaExpressions.literalValue(expression);

      return switch (kind) {
         case FLOAT -> Float.isFinite((Float)value)
            ? Float.toHexString((Float)value) + "F"
            : "java.lang.Float.intBitsToFloat(0x" + Integer.toHexString(Float.floatToRawIntBits((Float)value)) + ")";
         case DOUBLE -> Double.isFinite((Double)value)
            ? Double.toHexString((Double)value) + "D"
            : "java.lang.Double.longBitsToDouble(0x" + Long.toHexString(Double.doubleToRawLongBits((Double)value)) + "L)";
         case BIG_INTEGER -> {
            BigInteger integer = (BigInteger)value;
            yield integer.equals(BigInteger.ZERO)
               ? "java.math.BigInteger.ZERO"
               : (
                  integer.equals(BigInteger.ONE)
                     ? "java.math.BigInteger.ONE"
                     : (integer.equals(BigInteger.TEN) ? "java.math.BigInteger.TEN" : "new java.math.BigInteger(\"" + integer + "\")")
               );
         }
         case BYTE -> "(byte) " + value;
         case SHORT -> "(short) " + value;
         case CHAR -> "(char) " + (int)(Character)value;
         case INT -> value.toString();
         case LONG -> value + "L";
      };
   }

   private static String type(NumericKind kind) {
      return kind == NumericKind.BIG_INTEGER ? "java.math.BigInteger" : kind.name().toLowerCase(Locale.ROOT);
   }

   private static String zero(NumericKind kind) {
      return switch (kind) {
         case FLOAT -> "0.0F";
         case DOUBLE -> "0.0D";
         case BIG_INTEGER -> "null";
         default -> "0";
         case LONG -> "0L";
      };
   }

   private static String min(NumericKind kind) {
      return switch (kind) {
         case BYTE -> "(-128)";
         case SHORT -> "(-32768)";
         case CHAR -> "0";
         case INT -> "(-2147483648)";
         case LONG -> "(-9223372036854775808L)";
         default -> throw unsupported("MATH_INTEGRAL_RANGE_REQUIRED");
      };
   }

   private static String max(NumericKind kind) {
      return switch (kind) {
         case BYTE -> "127";
         case SHORT -> "32767";
         case CHAR -> "65535";
         case INT -> "2147483647";
         case LONG -> "9223372036854775807L";
         default -> throw unsupported("MATH_INTEGRAL_RANGE_REQUIRED");
      };
   }

   private static String isFinite(NumericKind kind, String value) {
      return (kind == NumericKind.FLOAT ? "java.lang.Float.isFinite(" : "java.lang.Double.isFinite(") + value + ")";
   }

   private static void requireArity(List<String> operands, int arity) {
      if (operands.size() != arity) {
         throw unsupported("MATH_OPERATION_ARITY_MISMATCH");
      }
   }

   private static boolean identifier(String name) {
      return name != null && !name.isEmpty() && !KEYWORDS.contains(name) && Character.isJavaIdentifierStart(name.codePointAt(0))
         ? name.codePoints().skip(1L).allMatch(Character::isJavaIdentifierPart)
         : false;
   }

   private static UnsupportedOperationException unsupported(String diagnostic) {
      return new UnsupportedOperationException(diagnostic);
   }

   private record Check(String condition, String diagnostic) {
   }

   public record Emission(String statements, Map<String, String> outputValues) {
      public Emission(String statements, Map<String, String> outputValues) {
         Objects.requireNonNull(statements);
         outputValues = Collections.unmodifiableMap(new LinkedHashMap<>(outputValues));
         this.statements = statements;
         this.outputValues = outputValues;
      }
   }

   public record GuardEmission(String statements, String guardJava, JavaComputationEmitter.Emission replacement) {
   }

   private enum Mode {
      PRESERVE,
      CHECKED,
      GUARDED;
   }

   private static final class State {
      private final Map<String, String> inputs;
      private final Set<String> names;
      private final Map<Expr, String> leaves = new HashMap<>();
      private final int targetJava;
      private final JavaComputationEmitter.Mode mode;
      private final boolean checkRange;
      private final boolean finite;
      private final StringBuilder code = new StringBuilder();
      private final String guard;
      private int nextName;

      State(Map<String, String> inputs, Set<String> reservedNames, int targetJava, JavaComputationEmitter.Mode mode, boolean checkRange, boolean finite) {
         this.inputs = Map.copyOf(inputs);
         this.names = new HashSet<>(reservedNames);
         inputs.values().forEach(name -> {
            if (!JavaComputationEmitter.identifier(name)) {
               throw new IllegalArgumentException("MATH_INPUT_LOCAL_IDENTIFIER_REQUIRED");
            }

            this.names.add(name);
         });
         if (this.names.contains("java")) {
            throw JavaComputationEmitter.unsupported("MATH_JAVA_PACKAGE_NAME_SHADOWED");
         }

         if (targetJava >= 8 && targetJava <= 25) {
            this.targetJava = targetJava;
            this.mode = mode;
            this.checkRange = checkRange;
            this.finite = finite;
            this.guard = mode == JavaComputationEmitter.Mode.GUARDED ? this.fresh() : null;
            if (this.guard != null) {
               this.code.append("boolean ").append(this.guard).append(" = true;\n");
            }
         } else {
            throw JavaComputationEmitter.unsupported("MATH_TARGET_JAVA_UNSUPPORTED");
         }
      }

      String fresh() {
         String name;
         do {
            name = "_math" + this.nextName++;
         } while (!this.names.add(name));

         return name;
      }

      String value(Expr expression, NumericKind kind, List<String> operands) {
         if (kind.floatingPoint() && this.targetJava < 17) {
            throw JavaComputationEmitter.unsupported("MATH_FLOATING_POINT_REQUIRES_JAVA_17");
         }

         if (kind == NumericKind.BIG_INTEGER && this.mode == JavaComputationEmitter.Mode.GUARDED) {
            throw JavaComputationEmitter.unsupported("MATH_BIG_INTEGER_GUARD_REQUIRES_RECEIVER_CONTRACT");
         }

         boolean leaf = expression instanceof VariableExpr || JavaExpressions.isLiteral(expression);
         if (leaf && this.leaves.containsKey(expression)) {
            return this.leaves.get(expression);
         }

         ArrayList<Check> checks = new ArrayList<>();
         String javaExpression;
         if (expression instanceof VariableExpr input) {
            javaExpression = this.inputs.get(input.name());
            if (javaExpression == null) {
               throw new IllegalArgumentException("MATH_MISSING_INPUT_MAPPING");
            }
         } else if (JavaExpressions.isLiteral(expression)) {
            javaExpression = JavaComputationEmitter.literal(expression, kind);
         } else if (JavaExpressions.castSourceKind(expression).isPresent()) {
            JavaComputationEmitter.requireArity(operands, 1);
            NumericKind sourceKind = JavaExpressions.castSourceKind(expression).orElseThrow();
            if (this.checkRange && kind.integral()) {
               if (!sourceKind.integral()) {
                  throw JavaComputationEmitter.unsupported("MATH_CHECKED_FLOAT_TO_INTEGRAL_CAST_UNSUPPORTED");
               }

               checks.add(new JavaComputationEmitter.Check(JavaComputationEmitter.inRange("(long) " + (String)operands.getFirst(), kind), "MATH_NARROWING"));
            }

            javaExpression = "(" + JavaComputationEmitter.type(kind) + ") " + (String)operands.getFirst();
         } else {
            NumericOperation originalOperation = JavaExpressions.operationOf(expression).orElseThrow(() -> JavaComputationEmitter.unsupported("MATH_UNKNOWN_OPERATION"));
            NumericOperation emittedOperation = this.mode == JavaComputationEmitter.Mode.CHECKED && this.checkRange && kind.integral()
               ? JavaComputationEmitter.checkedOperation(originalOperation)
               : originalOperation;
            javaExpression = JavaComputationEmitter.operation(emittedOperation, kind, operands);
            if (kind.integral() && this.mode != JavaComputationEmitter.Mode.PRESERVE) {
               checks.addAll(JavaComputationEmitter.integralChecks(originalOperation, kind, operands, this.checkRange || JavaComputationEmitter.exact(originalOperation)));
            }
         }

         String name = this.fresh();
         if (this.mode == JavaComputationEmitter.Mode.GUARDED) {
            this.code.append(JavaComputationEmitter.type(kind)).append(' ').append(name).append(" = ").append(JavaComputationEmitter.zero(kind)).append(";\n");
         }

         checks.forEach(this::check);
         if (this.mode == JavaComputationEmitter.Mode.GUARDED) {
            this.code.append("if (").append(this.guard).append(") {\n").append(name).append(" = ").append(javaExpression).append(";\n");
         } else {
            this.code.append("final ").append(JavaComputationEmitter.type(kind)).append(' ').append(name).append(" = ").append(javaExpression).append(";\n");
         }

         if (this.finite && kind.floatingPoint()) {
            this.check(new JavaComputationEmitter.Check(JavaComputationEmitter.isFinite(kind, name), "MATH_NON_FINITE"));
         }

         if (this.mode == JavaComputationEmitter.Mode.GUARDED) {
            this.code.append("}\n");
         }

         if (leaf) {
            this.leaves.put(expression, name);
         }

         return name;
      }

      void check(JavaComputationEmitter.Check check) {
         if (this.mode == JavaComputationEmitter.Mode.GUARDED) {
            this.code.append("if (").append(this.guard).append(" && !(").append(check.condition()).append(")) { ").append(this.guard).append(" = false; }\n");
         } else {
            this.code
               .append("if (!(")
               .append(check.condition())
               .append(")) { throw new java.lang.ArithmeticException(\"")
               .append(check.diagnostic())
               .append("\"); }\n");
         }
      }
   }
}

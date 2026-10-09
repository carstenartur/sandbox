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
import de.regelsuche.sdk.optimization.SafetyProfile;
import de.regelsuche.sdk.optimization.SemanticAssumption;
import de.regelsuche.sdk.optimization.SourceEvaluationTrace;
import de.regelsuche.sdk.optimization.SemanticAssumption.Kind;
import de.regelsuche.sdk.optimization.SourceEvaluationTrace.Occurrence;
import de.regelsuche.search.program.JointComputationPlan;
import de.regelsuche.search.program.ComputationBackend.Type;
import de.regelsuche.search.program.JointComputationPlan.Output;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;

public final class JavaComputationExtractor {
   public JavaComputationExtractor.Extraction extract(CompilationUnit unit, String source, MathCleanUpOptions options) {
      return this.extract(unit, source, options, false, false);
   }

   /** Production source policy excludes constant-only statements, but not runtime cancellations. */
   public JavaComputationExtractor.Extraction extractRuntime(CompilationUnit unit, String source, MathCleanUpOptions options) {
      return this.extract(unit, source, options, false, true);
   }

   JavaComputationExtractor.Extraction extractForVerification(CompilationUnit unit, String source, MathCleanUpOptions options) {
      return this.extract(unit, source, options, true, false);
   }

   private JavaComputationExtractor.Extraction extract(final CompilationUnit unit, String source, final MathCleanUpOptions options, final boolean includeValues, final boolean runtimeOnly) {
      final ArrayList<JavaComputationRegion> regions = new ArrayList<>();
      final ArrayList<Diagnostic> diagnostics = new ArrayList<>();
      if (!options.enabled()) {
         return new JavaComputationExtractor.Extraction(regions, diagnostics);
      }

      final HashSet<String> reserved = new HashSet<>();
      final HashMap<String, VariableDeclarationFragment> declarations = new HashMap<>();
      unit.accept(
         new ASTVisitor() {
            public boolean visit(SimpleName name) {
               if (name.isDeclaration()
                  || name.resolveBinding() instanceof IVariableBinding
                  || name.getIdentifier().equals("java") && name.resolveBinding() instanceof ITypeBinding) {
                  reserved.add(name.getIdentifier());
               }

               return true;
            }

            public boolean visit(VariableDeclarationFragment name) {
               if (name.resolveBinding() != null) {
                  declarations.put(name.resolveBinding().getKey(), name);
               }

               return true;
            }
         }
      );
      unit.accept(new ASTVisitor() {
         public boolean visit(Block block) {
            if (!JavaComputationExtractor.eligibleBlock(block, runtimeOnly)) {
               return false;
            }

            JavaComputationExtractor.Builder builder = new JavaComputationExtractor.Builder(unit, options, declarations, reserved, block, includeValues, runtimeOnly);

            for (Object child : block.statements()) {
               Statement statement = (Statement)child;
               if (runtimeOnly && RuntimeCalculationPolicy.preserve(statement, declarations)) {
                  JavaComputationExtractor.finish(builder, regions, diagnostics);
                  diagnostics.add(new Diagnostic("CONSTANT_EXPRESSION_PRESERVED", "Intentional constant source is outside runtime cleanup", statement.getStartPosition(), statement.getLength()));
                  builder = new JavaComputationExtractor.Builder(unit, options, declarations, reserved, block, includeValues, runtimeOnly);
               } else if (!(statement instanceof VariableDeclarationStatement) && !(statement instanceof ExpressionStatement)
                     && !(statement instanceof ReturnStatement)) {
                  JavaComputationExtractor.finish(builder, regions, diagnostics);
                  builder = new JavaComputationExtractor.Builder(unit, options, declarations, reserved, block, includeValues, runtimeOnly);
               } else {
                  try {
                     builder.statement(statement);
                  } catch (JavaComputationExtractor.Rejected rejected) {
                     JavaComputationExtractor.finish(builder.prior, regions, diagnostics);
                     diagnostics.add(JavaComputationExtractor.diagnostic(rejected));
                     builder = new JavaComputationExtractor.Builder(unit, options, declarations, reserved, block, includeValues, runtimeOnly);
                  }
               }
            }

            JavaComputationExtractor.finish(builder, regions, diagnostics);
            return true;
         }
      });
      return new JavaComputationExtractor.Extraction(regions, diagnostics);
   }

   private static boolean eligibleBlock(Block block, boolean runtimeOnly) {
      for (ASTNode ancestor = block.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
         if (ancestor instanceof MethodDeclaration) {
            return true;
         }

         if (ancestor instanceof LambdaExpression
            || ancestor instanceof AnonymousClassDeclaration
            || !runtimeOnly && (ancestor instanceof ForStatement
               || ancestor instanceof EnhancedForStatement
               || ancestor instanceof WhileStatement
               || ancestor instanceof DoStatement)
            || ancestor instanceof Initializer) {
            return false;
         }
      }

      return false;
   }

   private static JavaComputationExtractor.Diagnostic diagnostic(JavaComputationExtractor.Rejected rejected) {
      return new JavaComputationExtractor.Diagnostic(rejected.getMessage(), rejected.getMessage(), rejected.node.getStartPosition(), rejected.node.getLength());
   }

   private static void finish(JavaComputationExtractor.Builder builder, List<JavaComputationRegion> regions, List<JavaComputationExtractor.Diagnostic> diagnostics) {
      if (builder != null && !builder.outputs.isEmpty() && (builder.includeValues || !builder.trace.isEmpty())) {
         try {
            JavaComputationRegion region = builder.finish();
            if (!builder.includeValues) region = JavaRegionLiveness.project(region, builder.unit);
            if (region != null) regions.add(region);
         } catch (JavaComputationExtractor.Rejected rejected) {
            diagnostics.add(diagnostic(rejected));
         }
      }
   }

   private static Expression unparenthesized(Expression expression) {
      while (expression instanceof ParenthesizedExpression) {
         ParenthesizedExpression parenthesized = (ParenthesizedExpression)expression;
         expression = parenthesized.getExpression();
      }

      return expression;
   }

   private static boolean valueMethod(IMethodBinding method) {
      return method != null && method.getDeclaringClass().getQualifiedName().equals("java.math.BigInteger")
         ? JavaOperationRegistry.method(method).isPresent()
            || Set.of(
                  "equals",
                  "compareTo",
                  "signum",
                  "bitLength",
                  "bitCount",
                  "intValue",
                  "longValue",
                  "floatValue",
                  "doubleValue",
                  "toString",
                  "intValueExact",
                  "longValueExact"
               )
               .contains(method.getName())
         : false;
   }

   private static boolean valueOnlyReceiverUse(Expression expression) {
      ASTNode receiver = expression;

      while (receiver.getParent() instanceof ParenthesizedExpression) {
         receiver = receiver.getParent();
      }

      if (receiver.getParent() instanceof MethodInvocation invocation && invocation.getExpression() == receiver && valueMethod(invocation.resolveMethodBinding())) {
         ITypeBinding returnType = invocation.resolveMethodBinding().getReturnType();
         return returnType.isPrimitive() ? true : returnType.getQualifiedName().equals("java.math.BigInteger") && valueOnlyReceiverUse(invocation);
      } else {
         return false;
      }
   }

   private static boolean canonicalFloatingObservation(SimpleName name) {
      ASTNode value = name;

      while (value.getParent() instanceof ParenthesizedExpression) {
         value = value.getParent();
      }

      ASTNode parent = value.getParent();
      if (parent instanceof CastExpression cast) {
         NumericKind targetKind = JavaOperationRegistry.kind(cast.resolveTypeBinding()).orElse(null);
         return targetKind != null && targetKind.integral() && targetKind != NumericKind.BIG_INTEGER;
      } else if (parent instanceof InfixExpression comparison) {
         return Set.of(Operator.EQUALS, Operator.NOT_EQUALS, Operator.LESS, Operator.LESS_EQUALS, Operator.GREATER, Operator.GREATER_EQUALS)
            .contains(comparison.getOperator());
      } else if (!(parent instanceof MethodInvocation invocation)) {
         return false;
      } else {
         IMethodBinding method = invocation.resolveMethodBinding();
         return method != null
            && Set.of("java.lang.Float", "java.lang.Double").contains(method.getDeclaringClass().getQualifiedName())
            && Set.of("doubleToLongBits", "floatToIntBits", "isNaN", "isFinite", "isInfinite", "compare").contains(method.getName());
      }
   }

   private static NumericOperation compoundOperator(Assignment assignment) {
      return switch (assignment.getOperator().toString()) {
         case "+=" -> NumericOperation.ADD;
         case "-=" -> NumericOperation.SUBTRACT;
         case "*=" -> NumericOperation.MULTIPLY;
         case "/=" -> NumericOperation.DIVIDE;
         case "%=" -> NumericOperation.REMAINDER;
         case "&=" -> NumericOperation.AND;
         case "|=" -> NumericOperation.OR;
         case "^=" -> NumericOperation.XOR;
         case "<<=" -> NumericOperation.SHIFT_LEFT;
         case ">>=" -> NumericOperation.SHIFT_RIGHT;
         case ">>>=" -> NumericOperation.UNSIGNED_SHIFT_RIGHT;
         default -> throw reject("UNSUPPORTED_COMPOUND_OPERATOR", assignment);
      };
   }

   private static NumericKind promote(NumericKind kind) {
      return kind != NumericKind.BYTE && kind != NumericKind.SHORT && kind != NumericKind.CHAR ? kind : NumericKind.INT;
   }

   private static NumericKind promote(NumericKind left, NumericKind right) {
      if (left == NumericKind.DOUBLE || right == NumericKind.DOUBLE) {
         return NumericKind.DOUBLE;
      } else if (left == NumericKind.FLOAT || right == NumericKind.FLOAT) {
         return NumericKind.FLOAT;
      } else {
         return left != NumericKind.LONG && right != NumericKind.LONG ? NumericKind.INT : NumericKind.LONG;
      }
   }

   private static Expr literal(Object value, ASTNode node) {
      return switch (value) {
         case Integer integer -> JavaExpressions.literal(integer);
         case Long longValue -> JavaExpressions.literal(longValue);
         case Float floatValue -> JavaExpressions.literal(floatValue);
         case Double doubleValue -> JavaExpressions.literal(doubleValue);
         case Byte byteValue -> JavaExpressions.literal(byteValue);
         case Short shortValue -> JavaExpressions.literal(shortValue);
         case Character character -> JavaExpressions.literal(character);
         case null, default -> throw reject("UNSUPPORTED_LITERAL", node);
      };
   }

   private static JavaComputationExtractor.Rejected reject(String diagnostic, ASTNode node) {
      return new JavaComputationExtractor.Rejected(diagnostic, node);
   }

   private static final class Builder {
      final CompilationUnit unit;
      final MathCleanUpOptions options;
      final Map<String, VariableDeclarationFragment> declarations;
      final Set<String> reserved;
      final Block block;
      final boolean includeValues;
      final boolean runtimeOnly;
      final Map<String, Expr> values = new HashMap<>();
      final Map<String, String> inputKeys = new HashMap<>();
      final Map<String, String> inputNames = new LinkedHashMap<>();
      final Map<String, Type> inputTypes = new LinkedHashMap<>();
      final List<Output> planOutputs = new ArrayList<>();
      final List<JavaComputationRegion.OutputBinding> outputs = new ArrayList<>();
      final List<Occurrence> trace = new ArrayList<>();
      final Set<SemanticAssumption> assumptions = new LinkedHashSet<>();
      final Set<String> receiverGuards = new LinkedHashSet<>();
      JavaComputationExtractor.Builder prior;
      int first = -1;
      int end;

      Builder(CompilationUnit unit, MathCleanUpOptions options, Map<String, VariableDeclarationFragment> declarations, Set<String> reserved, Block block, boolean includeValues, boolean runtimeOnly) {
         this.unit = unit;
         this.options = options;
         this.declarations = declarations;
         this.reserved = reserved;
         this.block = block;
         this.includeValues = includeValues;
         this.runtimeOnly = runtimeOnly;
      }

      JavaComputationExtractor.Builder snapshot() {
         JavaComputationExtractor.Builder copy = new JavaComputationExtractor.Builder(
            this.unit, this.options, this.declarations, this.reserved, this.block, this.includeValues, this.runtimeOnly
         );
         copy.values.putAll(this.values);
         copy.inputKeys.putAll(this.inputKeys);
         copy.inputNames.putAll(this.inputNames);
         copy.inputTypes.putAll(this.inputTypes);
         copy.planOutputs.addAll(this.planOutputs);
         copy.outputs.addAll(this.outputs);
         copy.trace.addAll(this.trace);
         copy.assumptions.addAll(this.assumptions);
         copy.receiverGuards.addAll(this.receiverGuards);
         copy.first = this.first;
         copy.end = this.end;
         return copy;
      }

      void statement(Statement statement) {
         this.prior = this.snapshot();
         if (statement instanceof ReturnStatement returned) {
            returnStatement(returned);
            return;
         }
         if (this.outputs.size() >= 64) {
            throw JavaComputationExtractor.reject("REGION_SIZE_LIMIT", statement);
         }

         IVariableBinding binding;
         Expression initializer;
         boolean declaration;
         if (statement instanceof VariableDeclarationStatement declarationStatement) {
            if (declarationStatement.fragments().size() != 1) {
               throw JavaComputationExtractor.reject("MULTIPLE_DECLARATORS", statement);
            }

            VariableDeclarationFragment fragment = (VariableDeclarationFragment)declarationStatement.fragments().getFirst();
            binding = fragment.resolveBinding();
            initializer = fragment.getInitializer();
            declaration = true;
         } else {
            if (!(statement instanceof ExpressionStatement expressionStatement)
               || !(expressionStatement.getExpression() instanceof Assignment assignment)
               || !(assignment.getLeftHandSide() instanceof SimpleName assignedName)) {
               throw JavaComputationExtractor.reject("SIDE_EFFECT_BOUNDARY", statement);
            }

            binding = assignedName.resolveBinding() instanceof IVariableBinding local ? local : null;
            if (binding == null || !this.values.containsKey(binding.getKey())) {
               throw JavaComputationExtractor.reject("PARTIAL_OUTPUT_OR_EXTERNAL_WRITE", statement);
            }

            initializer = assignment;
            declaration = false;
         }

         if (binding != null && !binding.isRecovered() && !binding.isField() && initializer != null) {
            NumericKind declaredKind = this.kind(binding.getType(), statement);
            this.selected(declaredKind, statement);
            if (this.first < 0) {
               this.first = statement.getStartPosition();
            }

            for (Object commentNode : this.unit.getCommentList()) {
               ASTNode comment = (ASTNode)commentNode;
               if (comment.getStartPosition() >= initializer.getStartPosition() && comment.getStartPosition() < initializer.getStartPosition() + initializer.getLength()) {
                  throw JavaComputationExtractor.reject("COMMENT_IN_EXPRESSION", (ASTNode)initializer);
               }
            }

            Expr value;
            NumericKind evaluatedKind;
            if (initializer instanceof Assignment assignment) {
               Expression right = assignment.getRightHandSide();
               evaluatedKind = this.kind(right.resolveTypeBinding(), right);
               value = this.expression(right, false, 0);
               if (assignment.getOperator() != org.eclipse.jdt.core.dom.Assignment.Operator.ASSIGN) {
                  NumericOperation operation = JavaComputationExtractor.compoundOperator(assignment);
                  boolean shift = operation == NumericOperation.SHIFT_LEFT
                     || operation == NumericOperation.SHIFT_RIGHT
                     || operation == NumericOperation.UNSIGNED_SHIFT_RIGHT;
                  NumericKind promotedKind = shift ? JavaComputationExtractor.promote(declaredKind) : JavaComputationExtractor.promote(declaredKind, evaluatedKind);
                  Expr left = this.cast(this.values.get(binding.getKey()), declaredKind, promotedKind, assignment);
                  value = this.cast(value, evaluatedKind, shift ? JavaComputationExtractor.promote(evaluatedKind) : promotedKind, right);
                  value = this.operation(promotedKind, operation, assignment, left, value);
                  evaluatedKind = promotedKind;
               }
            } else {
               value = this.expression((Expression)initializer, false, 0);
               evaluatedKind = this.kind(initializer.resolveTypeBinding(), (ASTNode)initializer);
            }

            if (declaredKind != evaluatedKind) {
               value = this.cast(value, evaluatedKind, declaredKind, (ASTNode)initializer);
            }

            this.values.put(binding.getKey(), value);
            String outputId = "output" + this.outputs.size();
            this.planOutputs.add(new Output(outputId, declaredKind.type(), value));
            this.outputs
               .add(
                  new JavaComputationRegion.OutputBinding(
                     outputId, binding.getName(), declaredKind, initializer.getStartPosition(), initializer.getLength(), statement.getStartPosition(), statement.getLength(), declaration, binding.getKey()
                  )
               );
            this.end = statement.getStartPosition() + statement.getLength();
         } else {
            throw JavaComputationExtractor.reject("UNRESOLVED_OR_NONLOCAL_BINDING", statement);
         }
      }

      private void returnStatement(ReturnStatement returned) {
         Expression expression = returned.getExpression();
         if (expression == null) throw JavaComputationExtractor.reject("VOID_RETURN_BOUNDARY", returned);
         MethodDeclaration method = null;
         for (ASTNode node = returned.getParent(); node != null; node = node.getParent()) {
            if (node instanceof MethodDeclaration declaration) { method = declaration; break; }
         }
         if (method == null || method.resolveBinding() == null || method.resolveBinding().isRecovered()) {
            throw JavaComputationExtractor.reject("UNRESOLVED_RETURN_TYPE", returned);
         }
         NumericKind declared = kind(method.resolveBinding().getReturnType(), returned);
         selected(declared, returned);
         if (declared == NumericKind.BIG_INTEGER) {
            throw JavaComputationExtractor.reject("BIG_INTEGER_IDENTITY_OR_ESCAPE", returned);
         }
         if (outputs.size() >= 64) throw JavaComputationExtractor.reject("REGION_SIZE_LIMIT", returned);
         for (Object value : unit.getCommentList()) {
            ASTNode comment = (ASTNode) value;
            if (comment.getStartPosition() >= expression.getStartPosition()
                  && comment.getStartPosition() < expression.getStartPosition() + expression.getLength()) {
               throw JavaComputationExtractor.reject("COMMENT_IN_EXPRESSION", expression);
            }
         }
         if (first < 0) first = returned.getStartPosition();
         NumericKind evaluated = kind(expression.resolveTypeBinding(), expression);
         Expr value = expression(expression, false, 0);
         if (declared != evaluated) value = cast(value, evaluated, declared, expression);
         String id = "output" + outputs.size();
         planOutputs.add(new Output(id, declared.type(), value));
         outputs.add(new JavaComputationRegion.OutputBinding(id, "", declared,
               expression.getStartPosition(), expression.getLength(), returned.getStartPosition(),
               returned.getLength(), false, "return@" + returned.getStartPosition(), true));
         end = returned.getStartPosition() + returned.getLength();
      }

      Expr expression(Expression expression, boolean primitiveBoundary, int depth) {
         if (depth > 64) {
            throw JavaComputationExtractor.reject("EXPRESSION_DEPTH_LIMIT", expression);
         }

         NumericKind resultKind = this.kind(expression.resolveTypeBinding(), expression);
         if (!primitiveBoundary) {
            this.selected(resultKind, expression);
         }

         if (expression instanceof ParenthesizedExpression parenthesized) {
            return this.expression(parenthesized.getExpression(), primitiveBoundary, depth + 1);
         } else {
            if (expression instanceof NumberLiteral || expression instanceof CharacterLiteral) {
               return JavaComputationExtractor.literal(expression.resolveConstantExpressionValue(), expression);
            }

            if (expression instanceof Name name) {
               if (!(name.resolveBinding() instanceof IVariableBinding binding && !binding.isRecovered())) {
                  throw JavaComputationExtractor.reject("UNRESOLVED_BINDING", expression);
               } else if (name instanceof QualifiedName qualifiedName && !(qualifiedName.getQualifier().resolveBinding() instanceof ITypeBinding)) {
                  throw JavaComputationExtractor.reject("EFFECTFUL_CONSTANT_QUALIFIER", expression);
               } else {
                  if (binding.getConstantValue() != null) {
                     if (this.runtimeOnly && name instanceof SimpleName) {
                        // Retain the programmer's symbolic name. The independent checker
                        // proves the rewrite for every value of this additional input.
                        this.validatedConstant(expression);
                        String inputId = this.inputKeys.computeIfAbsent(binding.getKey(), key -> "input" + this.inputKeys.size());
                        this.inputNames.put(inputId, binding.getName());
                        this.inputTypes.put(inputId, resultKind.type());
                        return new VariableExpr(inputId);
                     }
                     return JavaComputationExtractor.literal(this.validatedConstant(expression), expression);
                  }

                  if (resultKind == NumericKind.BIG_INTEGER && binding.isField() && binding.getDeclaringClass().getQualifiedName().equals("java.math.BigInteger")) {
                     return switch (binding.getName()) {
                        case "ZERO" -> JavaExpressions.literal(BigInteger.ZERO);
                        case "ONE" -> JavaExpressions.literal(BigInteger.ONE);
                        case "TWO" -> JavaExpressions.literal(BigInteger.TWO);
                        case "TEN" -> JavaExpressions.literal(BigInteger.TEN);
                        default -> throw JavaComputationExtractor.reject("UNKNOWN_BIG_INTEGER_CONSTANT", expression);
                     };
                  } else if (!binding.isField() && name instanceof SimpleName) {
                     Expr localValue = this.values.get(binding.getKey());
                     if (localValue != null) {
                        return localValue;
                     }

                     if (resultKind == NumericKind.BIG_INTEGER && (!this.exactBigInteger(binding) || this.knownMagnitudeBound(binding) == null)) {
                        if (this.options.safety() != SafetyProfile.GUARDED_FALLBACK) {
                           throw JavaComputationExtractor.reject("UNPROVED_BIG_INTEGER_RECEIVER_OR_MAGNITUDE", expression);
                        }

                        this.receiverGuards.add(binding.getName());
                     }

                     String inputId = this.inputKeys.computeIfAbsent(binding.getKey(), key -> "input" + this.inputKeys.size());
                     this.inputNames.put(inputId, binding.getName());
                     this.inputTypes.put(inputId, resultKind.type());
                     if (resultKind == NumericKind.BIG_INTEGER) {
                        this.assumptions
                           .add(
                              new SemanticAssumption(
                                 Kind.BIG_INTEGER_VALUE_SEMANTICS, inputId, "", "resolved exact BigInteger receiver and checked local value uses"
                              )
                           );
                        this.addBigIntegerRangeFacts(binding, inputId);
                     }

                     return new VariableExpr(inputId);
                  } else {
                     throw JavaComputationExtractor.reject("FIELD_OR_VOLATILE_INPUT", expression);
                  }
               }
            } else if (primitiveBoundary) {
               throw JavaComputationExtractor.reject("PRIMITIVE_PRECOMPUTATION_BOUNDARY", expression);
            } else if (expression instanceof CastExpression cast) {
               Expression operand = cast.getExpression();
               return this.cast(this.expression(operand, false, depth + 1), this.kind(operand.resolveTypeBinding(), operand), resultKind, expression);
            } else if (expression instanceof InfixExpression infix) {
               NumericOperation operation = JavaOperationRegistry.infix(infix.getOperator())
                  .orElseThrow(() -> JavaComputationExtractor.reject("UNSUPPORTED_OPERATOR", expression));
               ArrayList<Expression> operands = new ArrayList<>();
               operands.add(infix.getLeftOperand());
               operands.add(infix.getRightOperand());

               for (Object extendedOperand : infix.extendedOperands()) {
                  operands.add((Expression)extendedOperand);
               }

               Expression left = (Expression)operands.getFirst();
               NumericKind leftKind = this.kind(left.resolveTypeBinding(), left);
               Expr leftValue = this.expression(left, false, depth + 1);

               for (int index = 1; index < operands.size(); index++) {
                  Expression right = (Expression)operands.get(index);
                  NumericKind rightKind = this.kind(right.resolveTypeBinding(), right);
                  boolean shift = operation == NumericOperation.SHIFT_LEFT
                     || operation == NumericOperation.SHIFT_RIGHT
                     || operation == NumericOperation.UNSIGNED_SHIFT_RIGHT;
                  NumericKind promotedKind = shift ? JavaComputationExtractor.promote(leftKind) : JavaComputationExtractor.promote(leftKind, rightKind);
                  this.selected(promotedKind, expression);
                  Expr promotedLeft = this.cast(leftValue, leftKind, promotedKind, expression);
                  Expr rightValue = this.expression(right, false, depth + 1);
                  NumericKind promotedRightKind = shift ? JavaComputationExtractor.promote(rightKind) : promotedKind;
                  rightValue = this.cast(rightValue, rightKind, promotedRightKind, right);
                  leftValue = this.operation(promotedKind, operation, expression, promotedLeft, rightValue);
                  leftKind = promotedKind;
               }

               return leftValue;
            } else if (expression instanceof PrefixExpression prefix) {
               if (prefix.getOperand() instanceof NumberLiteral && prefix.getOperator() == org.eclipse.jdt.core.dom.PrefixExpression.Operator.MINUS) {
                  return JavaComputationExtractor.literal(prefix.resolveConstantExpressionValue(), prefix);
               } else {
                  NumericKind promotedKind = JavaComputationExtractor.promote(this.kind(prefix.getOperand().resolveTypeBinding(), prefix.getOperand()));
                  Expr operandValue = this.cast(
                     this.expression(prefix.getOperand(), false, depth + 1), this.kind(prefix.getOperand().resolveTypeBinding(), prefix.getOperand()), promotedKind, prefix
                  );
                  if (prefix.getOperator() == org.eclipse.jdt.core.dom.PrefixExpression.Operator.PLUS) {
                     return operandValue;
                  } else if (prefix.getOperator() == org.eclipse.jdt.core.dom.PrefixExpression.Operator.MINUS) {
                     return this.operation(promotedKind, NumericOperation.NEGATE, prefix, operandValue);
                  } else if (prefix.getOperator() == org.eclipse.jdt.core.dom.PrefixExpression.Operator.COMPLEMENT) {
                     return this.operation(promotedKind, NumericOperation.NOT, prefix, operandValue);
                  } else {
                     throw JavaComputationExtractor.reject("MUTATING_UNARY_OPERATOR", prefix);
                  }
               }
            } else if (!(expression instanceof MethodInvocation invocation)) {
               if (expression instanceof ClassInstanceCreation construction
                  && construction.getAnonymousClassDeclaration() == null
                  && resultKind == NumericKind.BIG_INTEGER
                  && construction.arguments().size() == 1
                  && construction.arguments().getFirst() instanceof StringLiteral text) {
                  try {
                     return JavaExpressions.literal(new BigInteger(text.getLiteralValue()));
                  } catch (NumberFormatException invalidNumber) {
                     throw JavaComputationExtractor.reject("INVALID_BIG_INTEGER_LITERAL", construction);
                  }
               } else {
                  throw JavaComputationExtractor.reject("SIDE_EFFECT_OR_UNSUPPORTED_EXPRESSION", expression);
               }
            } else {
               IMethodBinding method = invocation.resolveMethodBinding();
               if (method == null || method.isRecovered()) {
                  throw JavaComputationExtractor.reject("UNRESOLVED_METHOD_BINDING", invocation);
               }
               if (Modifier.isStatic(method.getModifiers())
                  && invocation.getExpression() != null
                  && (!(invocation.getExpression() instanceof Name qualifier) || !(qualifier.resolveBinding() instanceof ITypeBinding))) {
                  throw JavaComputationExtractor.reject("STATIC_CALL_EFFECTFUL_QUALIFIER", invocation);
               }

               if (method.getDeclaringClass().getQualifiedName().equals("java.math.BigInteger")
                  && method.getName().equals("valueOf")
                  && invocation.arguments().size() == 1) {
                  Expression argument = (Expression)invocation.arguments().getFirst();
                  if ((argument instanceof NumberLiteral || argument instanceof PrefixExpression || argument instanceof Name)
                     && this.validatedConstant(argument) instanceof Number constant) {
                     return JavaExpressions.literal(BigInteger.valueOf(constant.longValue()));
                  } else {
                     throw JavaComputationExtractor.reject("PRIMITIVE_PRECOMPUTATION_BOUNDARY", invocation);
                  }
               } else {
                  if (invocation.arguments().size() == 1
                     && Set.of("java.lang.Float", "java.lang.Double").contains(method.getDeclaringClass().getQualifiedName())) {
                     Expression argument = (Expression)invocation.arguments().getFirst();
                     if (this.validatedConstant(argument) instanceof Number constant) {
                        if (method.getName().equals("intBitsToFloat")) {
                           return JavaExpressions.literal(Float.intBitsToFloat(constant.intValue()));
                        }

                        if (method.getName().equals("longBitsToDouble")) {
                           return JavaExpressions.literal(Double.longBitsToDouble(constant.longValue()));
                        }
                     }
                  }

                  NumericOperation operation = JavaOperationRegistry.method(method)
                     .orElseThrow(() -> JavaComputationExtractor.reject("UNSUPPORTED_METHOD_OR_DISPATCH", invocation));
                  ArrayList<Expr> arguments = new ArrayList<>();
                  if (!Modifier.isStatic(method.getModifiers())) {
                     if (invocation.getExpression() == null) {
                        throw JavaComputationExtractor.reject("IMPLICIT_RECEIVER", invocation);
                     }

                     arguments.add(this.expression(invocation.getExpression(), false, depth + 1));
                  } else if (invocation.getExpression() != null
                     && (!(invocation.getExpression() instanceof Name qualifier) || !(qualifier.resolveBinding() instanceof ITypeBinding))) {
                     throw JavaComputationExtractor.reject("STATIC_CALL_EFFECTFUL_QUALIFIER", invocation);
                  }

                  for (int index = 0; index < invocation.arguments().size(); index++) {
                     Expression argument = (Expression)invocation.arguments().get(index);
                     NumericKind parameterKind = this.kind(method.getParameterTypes()[index], argument);
                     boolean primitiveArgument = resultKind == NumericKind.BIG_INTEGER && parameterKind != NumericKind.BIG_INTEGER;
                     Expr argumentValue = this.expression(argument, primitiveArgument, depth + 1);
                     NumericKind argumentKind = this.kind(argument.resolveTypeBinding(), argument);
                     arguments.add(argumentKind == parameterKind ? argumentValue : this.cast(argumentValue, argumentKind, parameterKind, argument));
                  }

                  return this.operation(resultKind, operation, expression, arguments.toArray(Expr[]::new));
               }
            }
         }
      }

      Expr cast(Expr expression, NumericKind from, NumericKind to, ASTNode node) {
         if (from == to) {
            return expression;
         }

         this.selected(from, node);
         this.selected(to, node);
         Expr cast = JavaExpressions.cast(from, to, expression);
         this.trace.add(new Occurrence("cast@" + node.getStartPosition() + ":" + this.trace.size(), cast, to, to));
         return cast;
      }

      Expr operation(NumericKind kind, NumericOperation operation, ASTNode node, Expr... operands) {
         Expr expression = JavaExpressions.operation(kind, operation, operands);
         this.trace.add(new Occurrence("operation@" + node.getStartPosition() + ":" + this.trace.size(), expression, kind, kind));
         return expression;
      }

      NumericKind kind(ITypeBinding binding, ASTNode node) {
         return JavaOperationRegistry.kind(binding).orElseThrow(() -> JavaComputationExtractor.reject("UNRESOLVED_OR_NONNUMERIC_TYPE", node));
      }

      void selected(NumericKind kind, ASTNode node) {
         if (!this.options.kinds().contains(kind)) {
            throw JavaComputationExtractor.reject("EXCLUDED_NUMERIC_KIND", node);
         }

         if (kind.floatingPoint() && this.options.targetJava() < 17) {
            throw JavaComputationExtractor.reject("PRE_JAVA_17_FLOATING_POINT", node);
         }
      }

      boolean exactBigInteger(IVariableBinding binding) {
         VariableDeclarationFragment declaration = this.declarations.get(binding.getKey());
         if (declaration != null && binding.isEffectivelyFinal() && declaration.getStartPosition() < this.first) {
            Expression initializer = declaration.getInitializer();
            if (initializer instanceof ClassInstanceCreation construction) {
               return construction.getAnonymousClassDeclaration() == null
                  && JavaOperationRegistry.kind(construction.resolveTypeBinding()).orElse(null) == NumericKind.BIG_INTEGER;
            } else if (initializer instanceof MethodInvocation factory) {
               IMethodBinding method = factory.resolveMethodBinding();
               return method != null
                  && method.getName().equals("valueOf")
                  && method.getDeclaringClass().getQualifiedName().equals("java.math.BigInteger")
                  && Modifier.isStatic(method.getModifiers());
            } else {
               return initializer instanceof Name name
                  && name.resolveBinding() instanceof IVariableBinding constant
                  && constant.isField()
                  && constant.getDeclaringClass().getQualifiedName().equals("java.math.BigInteger")
                  && Set.of("ZERO", "ONE", "TWO", "TEN").contains(constant.getName());
            }
         } else {
            return false;
         }
      }

      void addBigIntegerRangeFacts(IVariableBinding binding, String inputId) {
         Integer bound = this.knownMagnitudeBound(binding);
         if (bound != null || this.receiverGuards.contains(binding.getName())) {
            this.assumptions
               .add(
                  new SemanticAssumption(
                     Kind.BIG_INTEGER_BIT_LENGTH_BOUND,
                     inputId,
                     Integer.toString(bound == null ? 4096 : bound),
                     bound == null
                        ? "exact receiver guard and bitLength() < 4096 establish abs(value).bitLength <= 4096"
                        : "effectively final BigInteger factory magnitude bound"
                  )
               );
         }

         VariableDeclarationFragment declaration = this.declarations.get(binding.getKey());
         if (declaration != null && binding.isEffectivelyFinal() && declaration.getStartPosition() < this.first) {
            if (declaration.getInitializer() instanceof MethodInvocation factory) {
               IMethodBinding method = factory.resolveMethodBinding();
               if (method.getDeclaringClass().getQualifiedName().equals("java.math.BigInteger")
                  && method.getName().equals("valueOf")
                  && factory.arguments().size() == 1) {
                  Expression argument = (Expression)factory.arguments().getFirst();
                  if (this.validatedConstant(argument) instanceof Number constant && constant.longValue() > 0L) {
                     this.assumptions
                        .add(
                           new SemanticAssumption(
                              Kind.POSITIVE, inputId, "", "effectively final BigInteger.valueOf of positive integral constant at " + argument.getStartPosition()
                           )
                        );
                  } else if (this.nonnegativeIntegral(argument)) {
                     this.assumptions
                        .add(
                           new SemanticAssumption(
                              Kind.NON_NEGATIVE,
                              inputId,
                              "",
                              "effectively final BigInteger.valueOf of nonnegative integral expression at " + argument.getStartPosition()
                           )
                        );
                  }
               }
            }
         }
      }

      Integer knownMagnitudeBound(IVariableBinding binding) {
         VariableDeclarationFragment declaration = this.declarations.get(binding.getKey());
         if (declaration != null && binding.isEffectivelyFinal() && declaration.getStartPosition() < this.first) {
            Expression initializer = declaration.getInitializer();
            if (initializer instanceof MethodInvocation factory
               && factory.resolveMethodBinding() != null
               && factory.resolveMethodBinding().getDeclaringClass().getQualifiedName().equals("java.math.BigInteger")
               && factory.resolveMethodBinding().getName().equals("valueOf")) {
               return 64;
            } else if (initializer instanceof ClassInstanceCreation construction
               && construction.getAnonymousClassDeclaration() == null
               && construction.arguments().size() == 1
               && construction.arguments().getFirst() instanceof StringLiteral text) {
               try {
                  return new BigInteger(text.getLiteralValue()).abs().bitLength();
               } catch (NumberFormatException invalidNumber) {
                  return null;
               }
            } else {
               return initializer instanceof Name name
                     && name.resolveBinding() instanceof IVariableBinding constant
                     && constant.isField()
                     && constant.getDeclaringClass().getQualifiedName().equals("java.math.BigInteger")
                     && Set.of("ZERO", "ONE", "TWO", "TEN").contains(constant.getName())
                  ? 4
                  : null;
            }
         } else {
            return null;
         }
      }

      boolean nonnegativeIntegral(Expression expression) {
         if (expression instanceof ParenthesizedExpression parenthesized) {
            return this.nonnegativeIntegral(parenthesized.getExpression());
         } else {
            NumericKind kind = JavaOperationRegistry.kind(expression.resolveTypeBinding()).orElse(null);
            if (kind != null && kind.integral() && kind != NumericKind.BIG_INTEGER) {
               Object constant = this.validatedConstant(expression);
               if (constant instanceof Number number) {
                  return number.longValue() >= 0L;
               } else if (!(constant instanceof Character) && kind != NumericKind.CHAR) {
                  if (expression instanceof InfixExpression infix && infix.getOperator() == Operator.AND) {
                     if (this.nonnegativeIntegral(infix.getLeftOperand()) || this.nonnegativeIntegral(infix.getRightOperand())) {
                        return true;
                     }

                     for (Object operand : infix.extendedOperands()) {
                        if (this.nonnegativeIntegral((Expression)operand)) {
                           return true;
                        }
                     }
                  }

                  return false;
               } else {
                  return true;
               }
            } else {
               return false;
            }
         }
      }

      Object validatedConstant(Expression expression) {
         Object constant = expression.resolveConstantExpressionValue();
         if (constant == null) {
            return null;
         }

         this.validateConstantOrigins(expression, new HashSet<>());
         return constant;
      }

      void validateConstantOrigins(Expression expression, final Set<String> visited) {
         if (visited.size() > 64) {
            throw JavaComputationExtractor.reject("CONSTANT_DEPENDENCY_DEPTH_LIMIT", expression);
         }

         expression.accept(
            new ASTVisitor() {
               public boolean visit(SimpleName expression) {
                  if (expression.resolveBinding() instanceof IVariableBinding binding && binding.getConstantValue() != null) {
                     VariableDeclarationFragment declaration = Builder.this.declarations.get(binding.getKey());
                     if (declaration != null && declaration.getInitializer() != null) {
                        if (!visited.add(binding.getKey())) {
                           throw JavaComputationExtractor.reject("CYCLIC_CONSTANT_BINDING", expression);
                        }

                        Builder.this.validateConstantOrigins(declaration.getInitializer(), visited);
                        visited.remove(binding.getKey());
                     } else if (binding.isField()
                        && !Set.of(
                              "java.lang.Byte",
                              "java.lang.Short",
                              "java.lang.Character",
                              "java.lang.Integer",
                              "java.lang.Long",
                              "java.lang.Float",
                              "java.lang.Double",
                              "java.lang.Math"
                           )
                           .contains(binding.getDeclaringClass().getQualifiedName())) {
                        throw JavaComputationExtractor.reject("EXTERNAL_CONSTANT_BINDING", expression);
                     }
                  }

                  return true;
               }

               public boolean visit(QualifiedName expression) {
                  if (expression.resolveBinding() instanceof IVariableBinding && !(expression.getQualifier().resolveBinding() instanceof ITypeBinding)) {
                     throw JavaComputationExtractor.reject("EFFECTFUL_CONSTANT_QUALIFIER", expression);
                  } else {
                     return true;
                  }
               }

               public boolean visit(FieldAccess expression) {
                  throw JavaComputationExtractor.reject("EFFECTFUL_CONSTANT_QUALIFIER", expression);
               }
            }
         );
      }

      JavaComputationRegion finish() {
         if (this.options.safety() == SafetyProfile.CHECKED_THROW && this.followedByFloatingCheck()) {
            throw JavaComputationExtractor.reject("EXISTING_FLOATING_CHECK_MUST_REMAIN", this.block);
         }

         final HashSet<String> bigIntegerOutputs = new HashSet<>();

         for (JavaComputationRegion.OutputBinding output : this.outputs) {
            if (output.declaredKind() == NumericKind.BIG_INTEGER) {
               bigIntegerOutputs.add(output.bindingKey());
            }
         }

         if (!bigIntegerOutputs.isEmpty()) {
            this.block.accept(new ASTVisitor() {
               public boolean visit(SimpleName name) {
                  if (!(name.resolveBinding() instanceof IVariableBinding binding && bigIntegerOutputs.contains(binding.getKey()))) {
                     return true;
                  } else if (name.getStartPosition() >= Builder.this.first && name.getStartPosition() < Builder.this.end) {
                     return true;
                  } else if (JavaComputationExtractor.valueOnlyReceiverUse(name)) {
                     return true;
                  } else {
                     throw JavaComputationExtractor.reject("BIG_INTEGER_IDENTITY_OR_ESCAPE", name);
                  }
               }
            });
         }

         if (this.options.safety() == SafetyProfile.PRESERVE_JAVA && this.trace.stream().anyMatch(occurrence -> occurrence.evaluatedKind().floatingPoint())) {
            final HashSet<String> floatingOutputs = new HashSet<>();

            for (JavaComputationRegion.OutputBinding output : this.outputs) {
               if (output.declaredKind().floatingPoint()) {
                  floatingOutputs.add(output.bindingKey());
               }
            }

            final boolean[] canonicalOnly = new boolean[]{this.outputs.stream()
                  .noneMatch(output -> output.returnValue() && output.declaredKind().floatingPoint())};
            this.block
               .accept(
                  new ASTVisitor() {
                     public boolean visit(SimpleName name) {
                        if (name.resolveBinding() instanceof IVariableBinding binding
                           && floatingOutputs.contains(binding.getKey())
                           && (name.getStartPosition() < Builder.this.first || name.getStartPosition() >= Builder.this.end)
                           && !JavaComputationExtractor.canonicalFloatingObservation(name)) {
                           canonicalOnly[0] = false;
                        }

                        return true;
                     }
                  }
               );
            if (canonicalOnly[0]) {
               this.assumptions
                  .add(
                     new SemanticAssumption(
                        Kind.NO_NAN_PAYLOAD_OBSERVATION, "region@" + this.first, "", "all floating outputs are confined to canonical bit/value observations"
                     )
                  );
            }
         }

         return new JavaComputationRegion(
            new JointComputationPlan(this.inputTypes, Map.of(), this.planOutputs),
            new SourceEvaluationTrace(this.trace),
            this.outputs,
            this.inputNames,
            this.assumptions,
            this.reserved,
            this.receiverGuards,
            this.first,
            this.end - this.first
         );
      }

      boolean followedByFloatingCheck() {
         for (Object child : this.block.statements()) {
            Statement statement = (Statement)child;
            if (statement.getStartPosition() >= this.end) {
               if (statement instanceof IfStatement check && check.getElseStatement() == null) {
                  if (JavaComputationExtractor.unparenthesized(check.getExpression()) instanceof PrefixExpression negation
                     && negation.getOperator() == org.eclipse.jdt.core.dom.PrefixExpression.Operator.NOT) {
                     if (!(JavaComputationExtractor.unparenthesized(negation.getOperand()) instanceof MethodInvocation finiteCall)) {
                        return false;
                     }

                     IMethodBinding method = finiteCall.resolveMethodBinding();
                     if (method != null
                        && method.getName().equals("isFinite")
                        && finiteCall.arguments().size() == 1
                        && Set.of("java.lang.Float", "java.lang.Double").contains(method.getDeclaringClass().getQualifiedName())) {
                        if (JavaComputationExtractor.unparenthesized((Expression)finiteCall.arguments().getFirst()) instanceof SimpleName name
                           && name.resolveBinding() instanceof IVariableBinding binding
                           && !this.outputs.stream().noneMatch(output -> output.bindingKey().equals(binding.getKey()) && output.declaredKind().floatingPoint())) {
                           Statement thenStatement = check.getThenStatement();
                           if (thenStatement instanceof Block block && block.statements().size() == 1) {
                              thenStatement = (Statement)block.statements().getFirst();
                           }

                           return thenStatement instanceof ThrowStatement throwStatement
                              && throwStatement.getExpression().resolveTypeBinding() != null
                              && throwStatement.getExpression().resolveTypeBinding().getQualifiedName().equals("java.lang.ArithmeticException");
                        }

                        return false;
                     }

                     return false;
                  }

                  return false;
               }

               return false;
            }
         }

         return false;
      }
   }

   public record Diagnostic(String code, String message, int offset, int length) {
   }

   public record Extraction(List<JavaComputationRegion> regions, List<JavaComputationExtractor.Diagnostic> diagnostics) {
      public Extraction(List<JavaComputationRegion> regions, List<JavaComputationExtractor.Diagnostic> diagnostics) {
         regions = List.copyOf(regions);
         diagnostics = List.copyOf(diagnostics);
         this.regions = regions;
         this.diagnostics = diagnostics;
      }
   }

   private static final class Rejected extends RuntimeException {
      private static final long serialVersionUID = 1L;
      final ASTNode node;

      Rejected(String diagnostic, ASTNode node) {
         super(diagnostic);
         this.node = node;
      }
   }
}

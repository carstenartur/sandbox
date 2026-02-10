/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.ast.api.jdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sandbox.ast.api.expr.ASTExpr;
import org.sandbox.ast.api.expr.CastExpr;
import org.sandbox.ast.api.expr.FieldAccessExpr;
import org.sandbox.ast.api.expr.InfixExpr;
import org.sandbox.ast.api.expr.InfixOperator;
import org.sandbox.ast.api.expr.MethodInvocationExpr;
import org.sandbox.ast.api.expr.SimpleNameExpr;
import org.sandbox.ast.api.info.MethodInfo;
import org.sandbox.ast.api.info.TypeInfo;
import org.sandbox.ast.api.info.VariableInfo;
import org.sandbox.ast.api.stmt.ASTStmt;
import org.sandbox.ast.api.stmt.EnhancedForStmt;
import org.sandbox.ast.api.stmt.ForLoopStmt;
import org.sandbox.ast.api.stmt.IfStmt;
import org.sandbox.ast.api.stmt.WhileLoopStmt;

/**
 * Tests for {@link JDTConverter}.
 *
 * <p>Uses {@link AST#newAST(int)} to create synthetic JDT nodes without
 * requiring a full Eclipse environment. Binding resolution returns null
 * in this context, so binding-related fields are tested as empty/unresolved.</p>
 */
@DisplayName("JDTConverter")
class JDTConverterTest {

	private AST ast;

	@BeforeEach
	void setUp() {
		ast = AST.newAST(AST.JLS21, false);
	}

	// -------------------------------------------------------------------
	// Expression conversion tests
	// -------------------------------------------------------------------

	@Nested
	@DisplayName("Expression Conversion")
	class ExpressionConversion {

		@Test
		@DisplayName("convertExpression returns empty for null")
		void convertExpressionNull() {
			assertThat(JDTConverter.convertExpression(null)).isEmpty();
		}

		@Test
		@DisplayName("convert MethodInvocation preserves structure")
		void convertMethodInvocation() {
			MethodInvocation mi = ast.newMethodInvocation();
			mi.setName(ast.newSimpleName("add"));
			SimpleName receiver = ast.newSimpleName("list");
			mi.setExpression(receiver);
			mi.arguments().add(ast.newSimpleName("item"));

			MethodInvocationExpr result = JDTConverter.convert(mi);

			assertThat(result.methodName()).isEqualTo(Optional.of("add"));
			assertThat(result.hasReceiver()).isTrue();
			assertThat(result.receiver()).isPresent();
			assertThat(result.argumentCount()).isEqualTo(1);
			// Minimal MethodInfo created from node name (binding unavailable)
			assertThat(result.method()).isPresent();
			assertThat(result.method().get().name()).isEqualTo("add");
		}

		@Test
		@DisplayName("convert MethodInvocation without receiver")
		void convertMethodInvocationNoReceiver() {
			MethodInvocation mi = ast.newMethodInvocation();
			mi.setName(ast.newSimpleName("doSomething"));

			MethodInvocationExpr result = JDTConverter.convert(mi);

			assertThat(result.methodName()).isEqualTo(Optional.of("doSomething"));
			assertThat(result.hasReceiver()).isFalse();
			assertThat(result.receiver()).isEmpty();
			assertThat(result.argumentCount()).isZero();
		}

		@Test
		@DisplayName("convert MethodInvocation with multiple arguments")
		void convertMethodInvocationMultipleArgs() {
			MethodInvocation mi = ast.newMethodInvocation();
			mi.setName(ast.newSimpleName("substring"));
			mi.arguments().add(ast.newNumberLiteral("0"));
			mi.arguments().add(ast.newNumberLiteral("5"));

			MethodInvocationExpr result = JDTConverter.convert(mi);

			assertThat(result.argumentCount()).isEqualTo(2);
			assertThat(result.arguments()).hasSize(2);
		}

		@Test
		@DisplayName("convert SimpleName preserves identifier")
		void convertSimpleName() {
			SimpleName sn = ast.newSimpleName("myVariable");

			SimpleNameExpr result = JDTConverter.convert(sn);

			assertThat(result.identifier()).isEqualTo("myVariable");
			// No bindings in synthetic AST
			assertThat(result.variableBinding()).isEmpty();
			assertThat(result.methodBinding()).isEmpty();
			assertThat(result.typeBinding()).isEmpty();
		}

		@Test
		@DisplayName("convert FieldAccess preserves field name and receiver")
		void convertFieldAccess() {
			FieldAccess fa = ast.newFieldAccess();
			fa.setExpression(ast.newSimpleName("obj"));
			fa.setName(ast.newSimpleName("value"));

			FieldAccessExpr result = JDTConverter.convert(fa);

			assertThat(result.fieldName()).isEqualTo("value");
			assertThat(result.receiver()).isNotNull();
			assertThat(result.receiver().isSimpleName()).isTrue();
		}

		@Test
		@DisplayName("convert CastExpression preserves structure")
		void convertCastExpression() {
			CastExpression ce = ast.newCastExpression();
			ce.setType(ast.newSimpleType(ast.newSimpleName("String")));
			ce.setExpression(ast.newSimpleName("obj"));

			CastExpr result = JDTConverter.convert(ce);

			assertThat(result.expression()).isNotNull();
			// Cast type is unresolved in synthetic AST (no binding)
			assertThat(result.castType()).isNotNull();
		}

		@Test
		@DisplayName("convert InfixExpression preserves operator and operands")
		void convertInfixExpression() {
			InfixExpression ie = ast.newInfixExpression();
			ie.setLeftOperand(ast.newSimpleName("a"));
			ie.setRightOperand(ast.newSimpleName("b"));
			ie.setOperator(InfixExpression.Operator.PLUS);

			InfixExpr result = JDTConverter.convert(ie);

			assertThat(result.operator()).isEqualTo(InfixOperator.PLUS);
			assertThat(result.leftOperand()).isNotNull();
			assertThat(result.rightOperand()).isNotNull();
			assertThat(result.extendedOperands()).isEmpty();
			assertThat(result.isArithmetic()).isTrue();
		}

		@Test
		@DisplayName("convert InfixExpression with extended operands")
		void convertInfixExpressionExtended() {
			InfixExpression ie = ast.newInfixExpression();
			ie.setLeftOperand(ast.newSimpleName("a"));
			ie.setRightOperand(ast.newSimpleName("b"));
			ie.setOperator(InfixExpression.Operator.PLUS);
			ie.extendedOperands().add(ast.newSimpleName("c"));
			ie.extendedOperands().add(ast.newSimpleName("d"));

			InfixExpr result = JDTConverter.convert(ie);

			assertThat(result.extendedOperands()).hasSize(2);
		}

		@Test
		@DisplayName("convertExpression dispatches MethodInvocation correctly")
		void convertExpressionDispatchMethodInvocation() {
			MethodInvocation mi = ast.newMethodInvocation();
			mi.setName(ast.newSimpleName("test"));

			Optional<ASTExpr> result = JDTConverter.convertExpression(mi);

			assertThat(result).isPresent();
			assertThat(result.get()).isInstanceOf(MethodInvocationExpr.class);
			assertThat(result.get().isMethodInvocation()).isTrue();
		}

		@Test
		@DisplayName("convertExpression dispatches SimpleName correctly")
		void convertExpressionDispatchSimpleName() {
			SimpleName sn = ast.newSimpleName("x");

			Optional<ASTExpr> result = JDTConverter.convertExpression(sn);

			assertThat(result).isPresent();
			assertThat(result.get()).isInstanceOf(SimpleNameExpr.class);
			assertThat(result.get().isSimpleName()).isTrue();
		}

		@Test
		@DisplayName("convertExpression wraps unsupported types generically")
		void convertExpressionUnsupported() {
			NumberLiteral nl = ast.newNumberLiteral("42");

			Optional<ASTExpr> result = JDTConverter.convertExpression(nl);

			assertThat(result).isPresent();
			assertThat(result.get()).isInstanceOf(JDTConverter.UnsupportedExpr.class);
		}

		@Test
		@DisplayName("convert null MethodInvocation throws")
		void convertNullMethodInvocationThrows() {
			assertThatThrownBy(() -> JDTConverter.convert((MethodInvocation) null))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("convert null SimpleName throws")
		void convertNullSimpleNameThrows() {
			assertThatThrownBy(() -> JDTConverter.convert((SimpleName) null))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("identifierOf returns identifier for SimpleName")
		void identifierOfSimpleName() {
			SimpleName sn = ast.newSimpleName("myVariable");

			Optional<String> result = JDTConverter.identifierOf(sn);

			assertThat(result).isPresent();
			assertThat(result.get()).isEqualTo("myVariable");
		}

		@Test
		@DisplayName("identifierOf returns empty for null expression")
		void identifierOfNull() {
			Optional<String> result = JDTConverter.identifierOf(null);

			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("identifierOf returns empty for non-SimpleName expression")
		void identifierOfNonSimpleName() {
			MethodInvocation mi = ast.newMethodInvocation();
			mi.setName(ast.newSimpleName("test"));

			Optional<String> result = JDTConverter.identifierOf(mi);

			assertThat(result).isEmpty();
		}
	}

	// -------------------------------------------------------------------
	// Statement conversion tests
	// -------------------------------------------------------------------

	@Nested
	@DisplayName("Statement Conversion")
	class StatementConversion {

		@Test
		@DisplayName("convertStatement returns empty for null")
		void convertStatementNull() {
			assertThat(JDTConverter.convertStatement(null)).isEmpty();
		}

		@Test
		@DisplayName("convert EnhancedForStatement preserves structure")
		void convertEnhancedFor() {
			EnhancedForStatement efs = ast.newEnhancedForStatement();
			SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
			param.setName(ast.newSimpleName("item"));
			param.setType(ast.newSimpleType(ast.newSimpleName("String")));
			efs.setParameter(param);
			efs.setExpression(ast.newSimpleName("items"));
			efs.setBody(ast.newBlock());

			EnhancedForStmt result = JDTConverter.convert(efs);

			assertThat(result.parameter()).isPresent();
			assertThat(result.parameter().get().name()).isEqualTo("item");
			assertThat(result.iterable()).isPresent();
			assertThat(result.body()).isPresent();
		}

		@Test
		@DisplayName("convert WhileStatement preserves condition and body")
		void convertWhile() {
			WhileStatement ws = ast.newWhileStatement();
			ws.setExpression(ast.newSimpleName("hasNext"));
			ws.setBody(ast.newBlock());

			WhileLoopStmt result = JDTConverter.convert(ws);

			assertThat(result.condition()).isPresent();
			assertThat(result.body()).isPresent();
		}

		@Test
		@DisplayName("convert ForStatement preserves all parts")
		void convertFor() {
			ForStatement fs = ast.newForStatement();
			fs.setExpression(ast.newSimpleName("condition"));
			fs.setBody(ast.newBlock());

			ForLoopStmt result = JDTConverter.convert(fs);

			assertThat(result.condition()).isPresent();
			assertThat(result.body()).isPresent();
			assertThat(result.initializers()).isEmpty();
			assertThat(result.updaters()).isEmpty();
		}

		@Test
		@DisplayName("convert IfStatement preserves condition and branches")
		void convertIf() {
			IfStatement is = ast.newIfStatement();
			is.setExpression(ast.newSimpleName("flag"));
			is.setThenStatement(ast.newBlock());

			IfStmt result = JDTConverter.convert(is);

			assertThat(result.condition()).isPresent();
			assertThat(result.thenStatement()).isPresent();
			assertThat(result.elseStatement()).isEmpty();
		}

		@Test
		@DisplayName("convert IfStatement with else branch")
		void convertIfElse() {
			IfStatement is = ast.newIfStatement();
			is.setExpression(ast.newSimpleName("flag"));
			is.setThenStatement(ast.newBlock());
			is.setElseStatement(ast.newBlock());

			IfStmt result = JDTConverter.convert(is);

			assertThat(result.condition()).isPresent();
			assertThat(result.thenStatement()).isPresent();
			assertThat(result.elseStatement()).isPresent();
		}

		@Test
		@DisplayName("convertStatement dispatches EnhancedForStatement correctly")
		void convertStatementDispatchEnhancedFor() {
			EnhancedForStatement efs = ast.newEnhancedForStatement();
			SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
			param.setName(ast.newSimpleName("item"));
			param.setType(ast.newSimpleType(ast.newSimpleName("String")));
			efs.setParameter(param);
			efs.setExpression(ast.newSimpleName("list"));
			efs.setBody(ast.newBlock());

			Optional<ASTStmt> result = JDTConverter.convertStatement(efs);

			assertThat(result).isPresent();
			assertThat(result.get()).isInstanceOf(EnhancedForStmt.class);
			assertThat(result.get().isEnhancedFor()).isTrue();
		}

		@Test
		@DisplayName("convertStatement wraps unsupported types generically")
		void convertStatementUnsupported() {
			// ReturnStatement is not specifically handled
			org.eclipse.jdt.core.dom.ReturnStatement rs = ast.newReturnStatement();

			Optional<ASTStmt> result = JDTConverter.convertStatement(rs);

			assertThat(result).isPresent();
			assertThat(result.get()).isInstanceOf(JDTConverter.UnsupportedStmt.class);
		}
	}

	// -------------------------------------------------------------------
	// Operator conversion tests
	// -------------------------------------------------------------------

	@Nested
	@DisplayName("Operator Conversion")
	class OperatorConversion {

		@Test
		@DisplayName("converts PLUS operator")
		void convertPlus() {
			assertThat(JDTConverter.convertOperator(InfixExpression.Operator.PLUS))
					.isEqualTo(InfixOperator.PLUS);
		}

		@Test
		@DisplayName("converts MINUS operator")
		void convertMinus() {
			assertThat(JDTConverter.convertOperator(InfixExpression.Operator.MINUS))
					.isEqualTo(InfixOperator.MINUS);
		}

		@Test
		@DisplayName("converts TIMES operator")
		void convertTimes() {
			assertThat(JDTConverter.convertOperator(InfixExpression.Operator.TIMES))
					.isEqualTo(InfixOperator.TIMES);
		}

		@Test
		@DisplayName("converts DIVIDE operator")
		void convertDivide() {
			assertThat(JDTConverter.convertOperator(InfixExpression.Operator.DIVIDE))
					.isEqualTo(InfixOperator.DIVIDE);
		}

		@Test
		@DisplayName("converts EQUALS operator")
		void convertEquals() {
			assertThat(JDTConverter.convertOperator(InfixExpression.Operator.EQUALS))
					.isEqualTo(InfixOperator.EQUALS);
		}

		@Test
		@DisplayName("converts NOT_EQUALS operator")
		void convertNotEquals() {
			assertThat(JDTConverter.convertOperator(InfixExpression.Operator.NOT_EQUALS))
					.isEqualTo(InfixOperator.NOT_EQUALS);
		}

		@Test
		@DisplayName("converts CONDITIONAL_AND operator")
		void convertConditionalAnd() {
			assertThat(JDTConverter.convertOperator(InfixExpression.Operator.CONDITIONAL_AND))
					.isEqualTo(InfixOperator.CONDITIONAL_AND);
		}

		@Test
		@DisplayName("converts CONDITIONAL_OR operator")
		void convertConditionalOr() {
			assertThat(JDTConverter.convertOperator(InfixExpression.Operator.CONDITIONAL_OR))
					.isEqualTo(InfixOperator.CONDITIONAL_OR);
		}

		@Test
		@DisplayName("converts LESS operator")
		void convertLess() {
			assertThat(JDTConverter.convertOperator(InfixExpression.Operator.LESS))
					.isEqualTo(InfixOperator.LESS);
		}

		@Test
		@DisplayName("converts GREATER operator")
		void convertGreater() {
			assertThat(JDTConverter.convertOperator(InfixExpression.Operator.GREATER))
					.isEqualTo(InfixOperator.GREATER);
		}

		@Test
		@DisplayName("converts REMAINDER operator")
		void convertRemainder() {
			assertThat(JDTConverter.convertOperator(InfixExpression.Operator.REMAINDER))
					.isEqualTo(InfixOperator.REMAINDER);
		}

		@Test
		@DisplayName("null operator throws")
		void convertNullOperatorThrows() {
			assertThatThrownBy(() -> JDTConverter.convertOperator(null))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	// -------------------------------------------------------------------
	// Binding conversion tests
	// -------------------------------------------------------------------

	@Nested
	@DisplayName("Binding Conversion")
	class BindingConversion {

		@Test
		@DisplayName("convertTypeBinding returns empty for null")
		void convertTypeBindingNull() {
			assertThat(JDTConverter.convertTypeBinding(null)).isEmpty();
		}

		@Test
		@DisplayName("convertMethodBinding returns empty for null")
		void convertMethodBindingNull() {
			assertThat(JDTConverter.convertMethodBinding(null)).isEmpty();
		}

		@Test
		@DisplayName("convertVariableBinding returns empty for null")
		void convertVariableBindingNull() {
			assertThat(JDTConverter.convertVariableBinding(null)).isEmpty();
		}

		@Test
		@DisplayName("convertTypeBinding converts simple class type")
		void convertTypeBindingSimpleClass() {
			ITypeBinding binding = typeBinding("java.util.List", "List",
					false, false, 0, new ITypeBinding[0]);

			Optional<TypeInfo> result = JDTConverter.convertTypeBinding(binding);

			assertThat(result).isPresent();
			assertThat(result.get().qualifiedName()).isEqualTo("java.util.List");
			assertThat(result.get().simpleName()).isEqualTo("List");
			assertThat(result.get().isPrimitive()).isFalse();
			assertThat(result.get().isArray()).isFalse();
		}

		@Test
		@DisplayName("convertTypeBinding converts primitive type")
		void convertTypeBindingPrimitive() {
			ITypeBinding binding = typeBinding("int", "int",
					true, false, 0, new ITypeBinding[0]);

			Optional<TypeInfo> result = JDTConverter.convertTypeBinding(binding);

			assertThat(result).isPresent();
			assertThat(result.get().qualifiedName()).isEqualTo("int");
			assertThat(result.get().isPrimitive()).isTrue();
		}

		@Test
		@DisplayName("convertTypeBinding converts array type")
		void convertTypeBindingArray() {
			ITypeBinding binding = typeBinding("java.lang.String[]", "String[]",
					false, true, 1, new ITypeBinding[0]);

			Optional<TypeInfo> result = JDTConverter.convertTypeBinding(binding);

			assertThat(result).isPresent();
			assertThat(result.get().isArray()).isTrue();
			assertThat(result.get().arrayDimensions()).isEqualTo(1);
		}

		@Test
		@DisplayName("convertTypeBinding converts generic type with type arguments")
		void convertTypeBindingGeneric() {
			ITypeBinding stringBinding = typeBinding("java.lang.String", "String",
					false, false, 0, new ITypeBinding[0]);
			ITypeBinding binding = typeBinding("java.util.List", "List",
					false, false, 0, new ITypeBinding[]{stringBinding});

			Optional<TypeInfo> result = JDTConverter.convertTypeBinding(binding);

			assertThat(result).isPresent();
			assertThat(result.get().hasTypeArguments()).isTrue();
			assertThat(result.get().typeArguments()).hasSize(1);
			assertThat(result.get().firstTypeArgument().get().qualifiedName())
					.isEqualTo("java.lang.String");
		}

		@Test
		@DisplayName("convertMethodBinding converts method with parameters and modifiers")
		void convertMethodBindingWithParams() {
			ITypeBinding stringType = typeBinding("java.lang.String", "String",
					false, false, 0, new ITypeBinding[0]);
			ITypeBinding intType = typeBinding("int", "int",
					true, false, 0, new ITypeBinding[0]);
			ITypeBinding listType = typeBinding("java.util.List", "List",
					false, false, 0, new ITypeBinding[0]);

			IMethodBinding binding = methodBinding("add", listType, stringType,
					new ITypeBinding[]{intType, stringType},
					org.eclipse.jdt.core.dom.Modifier.PUBLIC);

			Optional<MethodInfo> result = JDTConverter.convertMethodBinding(binding);

			assertThat(result).isPresent();
			assertThat(result.get().name()).isEqualTo("add");
			assertThat(result.get().declaringType().qualifiedName()).isEqualTo("java.util.List");
			assertThat(result.get().returnType().qualifiedName()).isEqualTo("java.lang.String");
			assertThat(result.get().parameters()).hasSize(2);
			assertThat(result.get().parameters().get(0).type().qualifiedName()).isEqualTo("int");
			assertThat(result.get().parameters().get(1).type().qualifiedName()).isEqualTo("java.lang.String");
			assertThat(result.get().isPublic()).isTrue();
		}

		@Test
		@DisplayName("convertMethodBinding converts static method")
		void convertMethodBindingStatic() {
			ITypeBinding mathType = typeBinding("java.lang.Math", "Math",
					false, false, 0, new ITypeBinding[0]);
			ITypeBinding intType = typeBinding("int", "int",
					true, false, 0, new ITypeBinding[0]);

			IMethodBinding binding = methodBinding("max", mathType, intType,
					new ITypeBinding[]{intType, intType},
					org.eclipse.jdt.core.dom.Modifier.PUBLIC | org.eclipse.jdt.core.dom.Modifier.STATIC);

			Optional<MethodInfo> result = JDTConverter.convertMethodBinding(binding);

			assertThat(result).isPresent();
			assertThat(result.get().isStatic()).isTrue();
			assertThat(result.get().isPublic()).isTrue();
		}

		@Test
		@DisplayName("convertMethodBinding handles null declaring class")
		void convertMethodBindingNullDeclaringClass() {
			ITypeBinding voidType = typeBinding("void", "void",
					true, false, 0, new ITypeBinding[0]);

			IMethodBinding binding = methodBinding("lambda", null, voidType,
					new ITypeBinding[0], 0);

			Optional<MethodInfo> result = JDTConverter.convertMethodBinding(binding);

			assertThat(result).isPresent();
			assertThat(result.get().name()).isEqualTo("lambda");
			assertThat(result.get().declaringType().qualifiedName()).isEqualTo("<unresolved>");
		}

		@Test
		@DisplayName("convertVariableBinding converts field")
		void convertVariableBindingField() {
			ITypeBinding stringType = typeBinding("java.lang.String", "String",
					false, false, 0, new ITypeBinding[0]);

			IVariableBinding binding = variableBinding("name", stringType,
					org.eclipse.jdt.core.dom.Modifier.PRIVATE | org.eclipse.jdt.core.dom.Modifier.FINAL,
					true, false);

			Optional<VariableInfo> result = JDTConverter.convertVariableBinding(binding);

			assertThat(result).isPresent();
			assertThat(result.get().name()).isEqualTo("name");
			assertThat(result.get().type().qualifiedName()).isEqualTo("java.lang.String");
			assertThat(result.get().isField()).isTrue();
			assertThat(result.get().isParameter()).isFalse();
			assertThat(result.get().isPrivate()).isTrue();
			assertThat(result.get().isFinal()).isTrue();
		}

		@Test
		@DisplayName("convertVariableBinding converts parameter")
		void convertVariableBindingParameter() {
			ITypeBinding intType = typeBinding("int", "int",
					true, false, 0, new ITypeBinding[0]);

			IVariableBinding binding = variableBinding("index", intType,
					0, false, true);

			Optional<VariableInfo> result = JDTConverter.convertVariableBinding(binding);

			assertThat(result).isPresent();
			assertThat(result.get().name()).isEqualTo("index");
			assertThat(result.get().isField()).isFalse();
			assertThat(result.get().isParameter()).isTrue();
			assertThat(result.get().type().isPrimitive()).isTrue();
		}

		// -----------------------------------------------------------
		// Proxy-based stubs for JDT binding interfaces
		// -----------------------------------------------------------

		private ITypeBinding typeBinding(String qualifiedName, String simpleName,
				boolean isPrimitive, boolean isArray, int dimensions,
				ITypeBinding[] typeArguments) {
			return (ITypeBinding) java.lang.reflect.Proxy.newProxyInstance(
					getClass().getClassLoader(),
					new Class[]{ITypeBinding.class},
					(proxy, method, args) -> switch (method.getName()) {
						case "getQualifiedName" -> qualifiedName;
						case "getName" -> simpleName;
						case "isPrimitive" -> isPrimitive;
						case "isArray" -> isArray;
						case "getDimensions" -> dimensions;
						case "getTypeArguments" -> typeArguments;
						case "toString" -> qualifiedName;
						case "hashCode" -> System.identityHashCode(proxy);
						case "equals" -> proxy == args[0];
						default -> defaultValue(method.getReturnType());
					});
		}

		private IMethodBinding methodBinding(String name, ITypeBinding declaringClass,
				ITypeBinding returnType, ITypeBinding[] paramTypes, int modifiers) {
			return (IMethodBinding) java.lang.reflect.Proxy.newProxyInstance(
					getClass().getClassLoader(),
					new Class[]{IMethodBinding.class},
					(proxy, method, args) -> switch (method.getName()) {
						case "getName" -> name;
						case "getDeclaringClass" -> declaringClass;
						case "getReturnType" -> returnType;
						case "getParameterTypes" -> paramTypes;
						case "getModifiers" -> modifiers;
						case "toString" -> name;
						case "hashCode" -> System.identityHashCode(proxy);
						case "equals" -> proxy == args[0];
						default -> defaultValue(method.getReturnType());
					});
		}

		private IVariableBinding variableBinding(String name, ITypeBinding type,
				int modifiers, boolean isField, boolean isParameter) {
			return (IVariableBinding) java.lang.reflect.Proxy.newProxyInstance(
					getClass().getClassLoader(),
					new Class[]{IVariableBinding.class},
					(proxy, method, args) -> switch (method.getName()) {
						case "getName" -> name;
						case "getType" -> type;
						case "getModifiers" -> modifiers;
						case "isField" -> isField;
						case "isParameter" -> isParameter;
						case "toString" -> name;
						case "hashCode" -> System.identityHashCode(proxy);
						case "equals" -> proxy == args[0];
						default -> defaultValue(method.getReturnType());
					});
		}

		private Object defaultValue(Class<?> type) {
			if (type == boolean.class) return false;
			if (type == int.class) return 0;
			if (type == long.class) return 0L;
			if (type == byte.class) return (byte) 0;
			if (type == short.class) return (short) 0;
			if (type == char.class) return '\0';
			if (type == float.class) return 0.0f;
			if (type == double.class) return 0.0;
			return null;
		}
	}

	// -------------------------------------------------------------------
	// Generic node conversion tests
	// -------------------------------------------------------------------

	@Nested
	@DisplayName("Generic Node Conversion")
	class GenericNodeConversion {

		@Test
		@DisplayName("convertNode returns empty for null")
		void convertNodeNull() {
			assertThat(JDTConverter.convertNode(null)).isEmpty();
		}

		@Test
		@DisplayName("convertNode dispatches expressions")
		void convertNodeExpression() {
			SimpleName sn = ast.newSimpleName("test");
			assertThat(JDTConverter.convertNode(sn)).isPresent();
			assertThat(JDTConverter.convertNode(sn).get()).isInstanceOf(SimpleNameExpr.class);
		}

		@Test
		@DisplayName("convertNode dispatches statements")
		void convertNodeStatement() {
			WhileStatement ws = ast.newWhileStatement();
			ws.setExpression(ast.newSimpleName("cond"));
			ws.setBody(ast.newBlock());
			assertThat(JDTConverter.convertNode(ws)).isPresent();
			assertThat(JDTConverter.convertNode(ws).get()).isInstanceOf(WhileLoopStmt.class);
		}
	}

	// -------------------------------------------------------------------
	// Receiver chaining test (integration-like)
	// -------------------------------------------------------------------

	@Nested
	@DisplayName("Fluent API Integration")
	class FluentAPIIntegration {

		@Test
		@DisplayName("converted MethodInvocation works with fluent API")
		void convertedMethodInvocationFluentAPI() {
			MethodInvocation mi = ast.newMethodInvocation();
			mi.setName(ast.newSimpleName("add"));
			mi.setExpression(ast.newSimpleName("list"));
			mi.arguments().add(ast.newSimpleName("element"));

			MethodInvocationExpr result = JDTConverter.convert(mi);

			// Use fluent API methods
			assertThat(result.isMethodCall("add", 1)).isTrue();
			assertThat(result.isMethodCall("remove", 1)).isFalse();
			assertThat(result.hasReceiver()).isTrue();
			assertThat(result.receiver()
					.flatMap(ASTExpr::asSimpleName)
					.map(SimpleNameExpr::identifier))
					.isEqualTo(Optional.of("list"));
		}

		@Test
		@DisplayName("converted InfixExpression works with fluent API checks")
		void convertedInfixFluentAPI() {
			InfixExpression ie = ast.newInfixExpression();
			ie.setLeftOperand(ast.newSimpleName("x"));
			ie.setRightOperand(ast.newSimpleName("y"));
			ie.setOperator(InfixExpression.Operator.CONDITIONAL_AND);

			InfixExpr result = JDTConverter.convert(ie);

			assertThat(result.isLogical()).isTrue();
			assertThat(result.isArithmetic()).isFalse();
			assertThat(result.isComparison()).isFalse();
		}

		@Test
		@DisplayName("convertExpression result can be cast via fluent API")
		void convertExpressionFluentCast() {
			MethodInvocation mi = ast.newMethodInvocation();
			mi.setName(ast.newSimpleName("getValue"));

			Optional<ASTExpr> expr = JDTConverter.convertExpression(mi);

			// Demonstrates fluent type-safe casting
			Optional<MethodInvocationExpr> asMethod = expr.flatMap(ASTExpr::asMethodInvocation);
			assertThat(asMethod).isPresent();
			assertThat(asMethod.get().methodName()).isEqualTo(Optional.of("getValue"));
		}
	}
}

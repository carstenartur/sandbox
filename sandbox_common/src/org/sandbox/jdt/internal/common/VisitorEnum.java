package org.sandbox.jdt.internal.common;

/*-
 * #%L
 * Sandbox common
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */


import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.ASTNode;

/**
 * Enumeration of AST visitor types corresponding to Eclipse JDT AST node types.
 * Each enum value represents a specific type of AST node that can be visited during AST traversal.
 * This enum provides a type-safe way to identify and process different AST node types.
 * 
 * <p>Each visitor enum value corresponds to a constant defined in {@link org.eclipse.jdt.core.dom.ASTNode}.</p>
 *
 * @author chammer
 * @since 1.15
 */
public enum VisitorEnum {

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.AnnotationTypeDeclaration} nodes.
	 * Represents annotation type declarations (e.g., {@code @interface MyAnnotation}).
	 */
	AnnotationTypeDeclaration(ASTNode.ANNOTATION_TYPE_DECLARATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration} nodes.
	 * Represents members of annotation type declarations.
	 */
	AnnotationTypeMemberDeclaration(ASTNode.ANNOTATION_TYPE_MEMBER_DECLARATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.AnonymousClassDeclaration} nodes.
	 * Represents anonymous class declarations.
	 */
	AnonymousClassDeclaration(ASTNode.ANONYMOUS_CLASS_DECLARATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ArrayAccess} nodes.
	 * Represents array element access expressions (e.g., {@code array[i]}).
	 */
	ArrayAccess(ASTNode.ARRAY_ACCESS),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ArrayCreation} nodes.
	 * Represents array creation expressions (e.g., {@code new int[10]}).
	 */
	ArrayCreation(ASTNode.ARRAY_CREATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ArrayInitializer} nodes.
	 * Represents array initializer expressions (e.g., {@code {1, 2, 3}}).
	 */
	ArrayInitializer(ASTNode.ARRAY_INITIALIZER),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ArrayType} nodes.
	 * Represents array type references (e.g., {@code int[]}).
	 */
	ArrayType(ASTNode.ARRAY_TYPE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.AssertStatement} nodes.
	 * Represents assert statements (e.g., {@code assert condition : message;}).
	 */
	AssertStatement(ASTNode.ASSERT_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.Assignment} nodes.
	 * Represents assignment expressions (e.g., {@code x = 5}, {@code x += 1}).
	 */
	Assignment(ASTNode.ASSIGNMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.Block} nodes.
	 * Represents block statements delimited by braces.
	 */
	Block(ASTNode.BLOCK),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.BlockComment} nodes.
	 * Represents block comments ({@code /* ... * /}).
	 */
	BlockComment(ASTNode.BLOCK_COMMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.BooleanLiteral} nodes.
	 * Represents boolean literal values ({@code true} or {@code false}).
	 */
	BooleanLiteral(ASTNode.BOOLEAN_LITERAL),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.BreakStatement} nodes.
	 * Represents break statements in loops and switch blocks.
	 */
	BreakStatement(ASTNode.BREAK_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.CastExpression} nodes.
	 * Represents type cast expressions (e.g., {@code (String) obj}).
	 */
	CastExpression(ASTNode.CAST_EXPRESSION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.CatchClause} nodes.
	 * Represents catch clauses in try statements.
	 */
	CatchClause(ASTNode.CATCH_CLAUSE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.CharacterLiteral} nodes.
	 * Represents character literal values (e.g., {@code 'a'}).
	 */
	CharacterLiteral(ASTNode.CHARACTER_LITERAL),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ClassInstanceCreation} nodes.
	 * Represents constructor calls (e.g., {@code new ArrayList<>()}).
	 */
	ClassInstanceCreation(ASTNode.CLASS_INSTANCE_CREATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.CompilationUnit} nodes.
	 * Represents the root of a Java source file.
	 */
	CompilationUnit(ASTNode.COMPILATION_UNIT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ConditionalExpression} nodes.
	 * Represents ternary expressions (e.g., {@code condition ? a : b}).
	 */
	ConditionalExpression(ASTNode.CONDITIONAL_EXPRESSION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ConstructorInvocation} nodes.
	 * Represents {@code this(...)} constructor calls.
	 */
	ConstructorInvocation(ASTNode.CONSTRUCTOR_INVOCATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ContinueStatement} nodes.
	 * Represents continue statements in loops.
	 */
	ContinueStatement(ASTNode.CONTINUE_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.CreationReference} nodes.
	 * Represents constructor method references (e.g., {@code ArrayList::new}).
	 */
	CreationReference(ASTNode.CREATION_REFERENCE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.Dimension} nodes.
	 * Represents extra dimensions in variable declarations.
	 */
	Dimension(ASTNode.DIMENSION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.DoStatement} nodes.
	 * Represents do-while loops.
	 */
	DoStatement(ASTNode.DO_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.EmptyStatement} nodes.
	 * Represents empty statements (standalone semicolons).
	 */
	EmptyStatement(ASTNode.EMPTY_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.EnhancedForStatement} nodes.
	 * Represents enhanced for-each loops (e.g., {@code for (Item i : items)}).
	 */
	EnhancedForStatement(ASTNode.ENHANCED_FOR_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.EnumConstantDeclaration} nodes.
	 * Represents enum constant declarations.
	 */
	EnumConstantDeclaration(ASTNode.ENUM_CONSTANT_DECLARATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.EnumDeclaration} nodes.
	 * Represents enum type declarations.
	 */
	EnumDeclaration(ASTNode.ENUM_DECLARATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ExportsDirective} nodes.
	 * Represents module exports directives.
	 */
	ExportsDirective(ASTNode.EXPORTS_DIRECTIVE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ExpressionMethodReference} nodes.
	 * Represents expression-based method references (e.g., {@code str::length}).
	 */
	ExpressionMethodReference(ASTNode.EXPRESSION_METHOD_REFERENCE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ExpressionStatement} nodes.
	 * Represents expression statements (expressions used as statements).
	 */
	ExpressionStatement(ASTNode.EXPRESSION_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.FieldAccess} nodes.
	 * Represents field access expressions (e.g., {@code obj.field}).
	 */
	FieldAccess(ASTNode.FIELD_ACCESS),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.FieldDeclaration} nodes.
	 * Represents field declarations in a type body.
	 */
	FieldDeclaration(ASTNode.FIELD_DECLARATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ForStatement} nodes.
	 * Represents classic for loops.
	 */
	ForStatement(ASTNode.FOR_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.IfStatement} nodes.
	 * Represents if statements with optional else branches.
	 */
	IfStatement(ASTNode.IF_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ImportDeclaration} nodes.
	 * Represents import declarations.
	 */
	ImportDeclaration(ASTNode.IMPORT_DECLARATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.InfixExpression} nodes.
	 * Represents binary infix expressions (e.g., {@code a + b}, {@code x == y}).
	 */
	InfixExpression(ASTNode.INFIX_EXPRESSION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.Initializer} nodes.
	 * Represents static and instance initializer blocks.
	 */
	Initializer(ASTNode.INITIALIZER),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.InstanceofExpression} nodes.
	 * Represents instanceof type tests.
	 */
	InstanceofExpression(ASTNode.INSTANCEOF_EXPRESSION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.IntersectionType} nodes.
	 * Represents intersection types in casts (e.g., {@code (Serializable & Comparable)}).
	 */
	IntersectionType(ASTNode.INTERSECTION_TYPE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.Javadoc} nodes.
	 * Represents Javadoc comments.
	 */
	Javadoc(ASTNode.JAVADOC),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.LabeledStatement} nodes.
	 * Represents labeled statements.
	 */
	LabeledStatement(ASTNode.LABELED_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.LambdaExpression} nodes.
	 * Represents lambda expressions (e.g., {@code (x) -> x + 1}).
	 */
	LambdaExpression(ASTNode.LAMBDA_EXPRESSION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.LineComment} nodes.
	 * Represents single-line comments ({@code // ...}).
	 */
	LineComment(ASTNode.LINE_COMMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.MarkerAnnotation} nodes.
	 * Represents marker annotations without parameters (e.g., {@code @Override}).
	 */
	MarkerAnnotation(ASTNode.MARKER_ANNOTATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.MemberRef} nodes.
	 * Represents member references in Javadoc comments.
	 */
	MemberRef(ASTNode.MEMBER_REF),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.MemberValuePair} nodes.
	 * Represents name-value pairs in normal annotations.
	 */
	MemberValuePair(ASTNode.MEMBER_VALUE_PAIR),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.MethodRef} nodes.
	 * Represents method references in Javadoc comments.
	 */
	MethodRef(ASTNode.METHOD_REF),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.MethodRefParameter} nodes.
	 * Represents parameters in Javadoc method references.
	 */
	MethodRefParameter(ASTNode.METHOD_REF_PARAMETER),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.MethodDeclaration} nodes.
	 * Represents method and constructor declarations.
	 */
	MethodDeclaration(ASTNode.METHOD_DECLARATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.MethodInvocation} nodes.
	 * Represents method call expressions (e.g., {@code obj.method(args)}).
	 */
	MethodInvocation(ASTNode.METHOD_INVOCATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.Modifier} nodes.
	 * Represents Java modifiers (e.g., {@code public}, {@code static}, {@code final}).
	 */
	Modifier(ASTNode.MODIFIER),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ModuleDeclaration} nodes.
	 * Represents module declarations (Java 9+).
	 */
	ModuleDeclaration(ASTNode.MODULE_DECLARATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ModuleModifier} nodes.
	 * Represents module modifiers.
	 */
	ModuleModifier(ASTNode.MODULE_MODIFIER),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.NameQualifiedType} nodes.
	 * Represents name-qualified type references.
	 */
	NameQualifiedType(ASTNode.NAME_QUALIFIED_TYPE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.NormalAnnotation} nodes.
	 * Represents annotations with named parameters (e.g., {@code @Test(timeout=100)}).
	 */
	NormalAnnotation(ASTNode.NORMAL_ANNOTATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.NullLiteral} nodes.
	 * Represents the null literal ({@code null}).
	 */
	NullLiteral(ASTNode.NULL_LITERAL),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.NumberLiteral} nodes.
	 * Represents numeric literal values (e.g., {@code 42}, {@code 3.14}).
	 */
	NumberLiteral(ASTNode.NUMBER_LITERAL),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.OpensDirective} nodes.
	 * Represents module opens directives.
	 */
	OpensDirective(ASTNode.OPENS_DIRECTIVE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.PackageDeclaration} nodes.
	 * Represents package declarations.
	 */
	PackageDeclaration(ASTNode.PACKAGE_DECLARATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ParameterizedType} nodes.
	 * Represents parameterized type references (e.g., {@code List<String>}).
	 */
	ParameterizedType(ASTNode.PARAMETERIZED_TYPE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ParenthesizedExpression} nodes.
	 * Represents parenthesized expressions (e.g., {@code (a + b)}).
	 */
	ParenthesizedExpression(ASTNode.PARENTHESIZED_EXPRESSION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.PatternInstanceofExpression} nodes.
	 * Represents pattern matching instanceof (Java 16+, e.g., {@code obj instanceof String s}).
	 */
	PatternInstanceofExpression(ASTNode.PATTERN_INSTANCEOF_EXPRESSION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.PostfixExpression} nodes.
	 * Represents postfix expressions (e.g., {@code i++}, {@code i--}).
	 */
	PostfixExpression(ASTNode.POSTFIX_EXPRESSION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.PrefixExpression} nodes.
	 * Represents prefix expressions (e.g., {@code ++i}, {@code !flag}).
	 */
	PrefixExpression(ASTNode.PREFIX_EXPRESSION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ProvidesDirective} nodes.
	 * Represents module provides directives.
	 */
	ProvidesDirective(ASTNode.PROVIDES_DIRECTIVE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.PrimitiveType} nodes.
	 * Represents primitive type references (e.g., {@code int}, {@code boolean}).
	 */
	PrimitiveType(ASTNode.PRIMITIVE_TYPE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.QualifiedName} nodes.
	 * Represents qualified name references (e.g., {@code java.lang.String}).
	 */
	QualifiedName(ASTNode.QUALIFIED_NAME),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.QualifiedType} nodes.
	 * Represents qualified type references.
	 */
	QualifiedType(ASTNode.QUALIFIED_TYPE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ModuleQualifiedName} nodes.
	 * Represents module-qualified name references.
	 */
	ModuleQualifiedName(ASTNode.MODULE_QUALIFIED_NAME),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.RequiresDirective} nodes.
	 * Represents module requires directives.
	 */
	RequiresDirective(ASTNode.REQUIRES_DIRECTIVE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.RecordDeclaration} nodes.
	 * Represents record type declarations (Java 16+).
	 */
	RecordDeclaration(ASTNode.RECORD_DECLARATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ReturnStatement} nodes.
	 * Represents return statements.
	 */
	ReturnStatement(ASTNode.RETURN_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.SimpleName} nodes.
	 * Represents simple (unqualified) name references.
	 */
	SimpleName(ASTNode.SIMPLE_NAME),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.SimpleType} nodes.
	 * Represents simple type references.
	 */
	SimpleType(ASTNode.SIMPLE_TYPE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.SingleMemberAnnotation} nodes.
	 * Represents annotations with a single value (e.g., {@code @SuppressWarnings("unchecked")}).
	 */
	SingleMemberAnnotation(ASTNode.SINGLE_MEMBER_ANNOTATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.SingleVariableDeclaration} nodes.
	 * Represents single variable declarations (e.g., method parameters).
	 */
	SingleVariableDeclaration(ASTNode.SINGLE_VARIABLE_DECLARATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.StringLiteral} nodes.
	 * Represents string literal values (e.g., {@code "hello"}).
	 */
	StringLiteral(ASTNode.STRING_LITERAL),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.SuperConstructorInvocation} nodes.
	 * Represents {@code super(...)} constructor calls.
	 */
	SuperConstructorInvocation(ASTNode.SUPER_CONSTRUCTOR_INVOCATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.SuperFieldAccess} nodes.
	 * Represents {@code super.field} access expressions.
	 */
	SuperFieldAccess(ASTNode.SUPER_FIELD_ACCESS),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.SuperMethodInvocation} nodes.
	 * Represents {@code super.method()} call expressions.
	 */
	SuperMethodInvocation(ASTNode.SUPER_METHOD_INVOCATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.SuperMethodReference} nodes.
	 * Represents {@code super::method} references.
	 */
	SuperMethodReference(ASTNode.SUPER_METHOD_REFERENCE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.SwitchCase} nodes.
	 * Represents case labels in switch statements/expressions.
	 */
	SwitchCase(ASTNode.SWITCH_CASE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.SwitchExpression} nodes.
	 * Represents switch expressions (Java 14+).
	 */
	SwitchExpression(ASTNode.SWITCH_EXPRESSION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.SwitchStatement} nodes.
	 * Represents switch statements.
	 */
	SwitchStatement(ASTNode.SWITCH_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.SynchronizedStatement} nodes.
	 * Represents synchronized blocks.
	 */
	SynchronizedStatement(ASTNode.SYNCHRONIZED_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.TagElement} nodes.
	 * Represents tag elements in Javadoc comments.
	 */
	TagElement(ASTNode.TAG_ELEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.TextBlock} nodes.
	 * Represents text blocks (Java 15+, triple-quoted strings).
	 */
	TextBlock(ASTNode.TEXT_BLOCK),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.TextElement} nodes.
	 * Represents text elements in Javadoc comments.
	 */
	TextElement(ASTNode.TEXT_ELEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ThisExpression} nodes.
	 * Represents {@code this} expressions.
	 */
	ThisExpression(ASTNode.THIS_EXPRESSION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.ThrowStatement} nodes.
	 * Represents throw statements.
	 */
	ThrowStatement(ASTNode.THROW_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.TryStatement} nodes.
	 * Represents try-catch-finally statements.
	 */
	TryStatement(ASTNode.TRY_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.TypeDeclaration} nodes.
	 * Represents class and interface declarations.
	 */
	TypeDeclaration(ASTNode.TYPE_DECLARATION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.TypeDeclarationStatement} nodes.
	 * Represents local type declarations within method bodies.
	 */
	TypeDeclarationStatement(ASTNode.TYPE_DECLARATION_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.TypeLiteral} nodes.
	 * Represents type literal expressions (e.g., {@code String.class}).
	 */
	TypeLiteral(ASTNode.TYPE_LITERAL),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.TypeMethodReference} nodes.
	 * Represents type-based method references (e.g., {@code String::valueOf}).
	 */
	TypeMethodReference(ASTNode.TYPE_METHOD_REFERENCE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.TypeParameter} nodes.
	 * Represents type parameter declarations in generic types/methods.
	 */
	TypeParameter(ASTNode.TYPE_PARAMETER),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.UnionType} nodes.
	 * Represents union types in multi-catch clauses (e.g., {@code IOException | SQLException}).
	 */
	UnionType(ASTNode.UNION_TYPE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.UsesDirective} nodes.
	 * Represents module uses directives.
	 */
	UsesDirective(ASTNode.USES_DIRECTIVE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.VariableDeclarationExpression} nodes.
	 * Represents variable declarations in for-loop initializers.
	 */
	VariableDeclarationExpression(ASTNode.VARIABLE_DECLARATION_EXPRESSION),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.VariableDeclarationStatement} nodes.
	 * Represents local variable declaration statements.
	 */
	VariableDeclarationStatement(ASTNode.VARIABLE_DECLARATION_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.VariableDeclarationFragment} nodes.
	 * Represents individual variable declaration fragments within a declaration.
	 */
	VariableDeclarationFragment(ASTNode.VARIABLE_DECLARATION_FRAGMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.WhileStatement} nodes.
	 * Represents while loops.
	 */
	WhileStatement(ASTNode.WHILE_STATEMENT),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.WildcardType} nodes.
	 * Represents wildcard type references (e.g., {@code ?}, {@code ? extends Number}).
	 */
	WildcardType(ASTNode.WILDCARD_TYPE),

	/**
	 * Visitor for {@link org.eclipse.jdt.core.dom.YieldStatement} nodes.
	 * Represents yield statements in switch expressions (Java 14+).
	 */
	YieldStatement(ASTNode.YIELD_STATEMENT);

	private final int nodetype;

	VisitorEnum(int nodetype) {
		this.nodetype = nodetype;
	}

	/**
	 * Returns the ASTNode type constant for this visitor enum value.
	 *
	 * @return the ASTNode type constant (e.g., {@link ASTNode#METHOD_INVOCATION})
	 */
	public int getValue() {
		return nodetype;
	}

	/**
	 * Returns a stream of all VisitorEnum values.
	 *
	 * @return a sequential stream of all enum values
	 */
	public static Stream<VisitorEnum> stream() {
		return Stream.of(VisitorEnum.values());
	}

	/**
	 * Lookup map from ASTNode type constant to VisitorEnum value for O(1) reverse lookups.
	 */
	private static final Map<Integer, VisitorEnum> values = Arrays.stream(values())
			.collect(Collectors.toMap(VisitorEnum::getValue, Function.identity()));

	/**
	 * Returns the VisitorEnum value for the given ASTNode type constant.
	 *
	 * @param nodetype the ASTNode type constant (e.g., {@link ASTNode#METHOD_INVOCATION})
	 * @return the corresponding VisitorEnum value, or {@code null} if not found
	 */
	public static VisitorEnum fromNodetype(final int nodetype) {
		return values.get(nodetype);
	}

	/**
	 * Returns the VisitorEnum value corresponding to the type of the given AST node.
	 *
	 * @param node the AST node to look up
	 * @return the corresponding VisitorEnum value, or {@code null} if not found
	 */
	public static VisitorEnum fromNode(ASTNode node) {
		return fromNodetype(node.getNodeType());
	}
}

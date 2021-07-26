/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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
package org.sandbox.jdt.internal.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import org.eclipse.jdt.core.dom.*;

public class HelperVisitor<E> {

	ASTVisitor astvisitor;
	public E dataholder;

	enum Visitor {
		AnnotationTypeDeclaration,
		AnnotationTypeMemberDeclaration,
		AnonymousClassDeclaration,
		ArrayAccess,
		ArrayCreation,
		ArrayInitializer,
		ArrayType,
		AssertStatement,
		Assignment,
		Block,
		BlockComment,
		BooleanLiteral,
		BreakStatement,
		CastExpression,
		CatchClause,
		CharacterLiteral,
		ClassInstanceCreation,
		CompilationUnit,
		ConditionalExpression,
		ConstructorInvocation,
		ContinueStatement,
		CreationReference,
		Dimension,
		DoStatement,
		EmptyStatement,
		EnhancedForStatement,
		EnumConstantDeclaration,
		EnumDeclaration,
		ExportsDirective,
		ExpressionMethodReference,
		ExpressionStatement,
		FieldAccess,
		FieldDeclaration,
		ForStatement,
		IfStatement,
		ImportDeclaration,
		InfixExpression,
		Initializer,
		InstanceofExpression,
		IntersectionType,
		Javadoc,
		LabeledStatement,
		LambdaExpression,
		LineComment,
		MarkerAnnotation,
		MemberRef,
		MemberValuePair,
		MethodRef,
		MethodRefParameter,
		MethodDeclaration,
		MethodInvocation,
		Modifier,
		ModuleDeclaration,
		ModuleModifier,
		NameQualifiedType,
		NormalAnnotation,
		NullLiteral,
		NumberLiteral,
		OpensDirective,
		PackageDeclaration,
		ParameterizedType,
		ParenthesizedExpression,
		PatternInstanceofExpression,
		PostfixExpression,
		PrefixExpression,
		ProvidesDirective,
		PrimitiveType,
		QualifiedName,
		QualifiedType,
		ModuleQualifiedName,
		RequiresDirective,
		RecordDeclaration,
		ReturnStatement,
		SimpleName,
		SimpleType,
		SingleMemberAnnotation,
		SingleVariableDeclaration,
		StringLiteral,
		SuperConstructorInvocation,
		SuperFieldAccess,
		SuperMethodInvocation,
		SuperMethodReference,
		SwitchCase,
		SwitchExpression,
		SwitchStatement,
		SynchronizedStatement,
		TagElement,
		TextBlock,
		TextElement,
		ThisExpression,
		ThrowStatement,
		TryStatement,
		TypeDeclaration,
		TypeDeclarationStatement,
		TypeLiteral,
		TypeMethodReference,
		TypeParameter,
		UnionType,
		UsesDirective,
		VariableDeclarationExpression,
		VariableDeclarationStatement,
		VariableDeclarationFragment,
		WhileStatement,
		WildcardType,
		YieldStatement
	};

	Map<Visitor, BiFunction<? extends ASTNode,E,Boolean>> suppliermap;

	public HelperVisitor(E dataholder) {
		suppliermap = new LinkedHashMap<>();
		this.dataholder=dataholder;
	}

	public BiFunction<? extends ASTNode,E,Boolean> add(Visitor key, BiFunction<ASTNode,E,Boolean> bs) {
		return suppliermap.put(key, bs);
	}
	
	public BiFunction<? extends ASTNode,E,Boolean> addAnnotationTypeDeclaration(BiFunction<AnnotationTypeDeclaration,E,Boolean> bs) {
		return suppliermap.put(Visitor.AnnotationTypeDeclaration, bs);
	}
	
	public BiFunction<? extends ASTNode,E,Boolean> addAnnotationTypeMemberDeclaration(BiFunction<AnnotationTypeMemberDeclaration,E,Boolean> bs) {
		return suppliermap.put(Visitor.AnnotationTypeMemberDeclaration, bs);
	}
	
	public BiFunction<? extends ASTNode,E,Boolean> addVariableDeclarationStatement(BiFunction<VariableDeclarationStatement,E,Boolean> bs) {
		return suppliermap.put(Visitor.VariableDeclarationStatement, bs);
	}
	
	public BiFunction<? extends ASTNode,E,Boolean> addSingleVariableDeclaration(BiFunction<SingleVariableDeclaration,E,Boolean> bs) {
		return suppliermap.put(Visitor.SingleVariableDeclaration, bs);
	}
	
	public BiFunction<? extends ASTNode,E,Boolean> addWhileStatement(BiFunction<WhileStatement,E,Boolean> bs) {
		return suppliermap.put(Visitor.WhileStatement, bs);
	}

	public HelperVisitor<E> build(CompilationUnit compilationUnit) {
		astvisitor = new ASTVisitor() {

			@Override
			public boolean visit(AnnotationTypeDeclaration node) {
				if(suppliermap.containsKey(Visitor.AnnotationTypeDeclaration)) {
					return ((BiFunction<AnnotationTypeDeclaration,E,Boolean>)(suppliermap.get(Visitor.AnnotationTypeDeclaration))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(AnnotationTypeMemberDeclaration node) {
				if(suppliermap.containsKey(Visitor.AnnotationTypeMemberDeclaration)) {
					return ((BiFunction<AnnotationTypeMemberDeclaration,E,Boolean>)(suppliermap.get(Visitor.AnnotationTypeMemberDeclaration))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(AnonymousClassDeclaration node) {
				if(suppliermap.containsKey(Visitor.AnonymousClassDeclaration)) {
					return ((BiFunction<AnonymousClassDeclaration,E,Boolean>)(suppliermap.get(Visitor.AnonymousClassDeclaration))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ArrayAccess node) {
				if(suppliermap.containsKey(Visitor.ArrayAccess)) {
					return ((BiFunction<ArrayAccess,E,Boolean>)(suppliermap.get(Visitor.ArrayAccess))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ArrayCreation node) {
				if(suppliermap.containsKey(Visitor.ArrayCreation)) {
					return ((BiFunction<ArrayCreation,E,Boolean>)(suppliermap.get(Visitor.ArrayCreation))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ArrayInitializer node) {
				if(suppliermap.containsKey(Visitor.ArrayInitializer)) {
					return ((BiFunction<ArrayInitializer,E,Boolean>)(suppliermap.get(Visitor.ArrayInitializer))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ArrayType node) {
				if(suppliermap.containsKey(Visitor.ArrayType)) {
					return ((BiFunction<ArrayType,E,Boolean>)(suppliermap.get(Visitor.ArrayType))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(AssertStatement node) {
				if(suppliermap.containsKey(Visitor.AssertStatement)) {
					return ((BiFunction<AssertStatement,E,Boolean>)(suppliermap.get(Visitor.AssertStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(Assignment node) {
				if(suppliermap.containsKey(Visitor.Assignment)) {
					return ((BiFunction<Assignment,E,Boolean>)(suppliermap.get(Visitor.Assignment))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(Block node) {
				if(suppliermap.containsKey(Visitor.Block)) {
					return ((BiFunction<Block,E,Boolean>)(suppliermap.get(Visitor.Block))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(BlockComment node) {
				if(suppliermap.containsKey(Visitor.BlockComment)) {
					return ((BiFunction<BlockComment,E,Boolean>)(suppliermap.get(Visitor.BlockComment))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(BooleanLiteral node) {
				if(suppliermap.containsKey(Visitor.BooleanLiteral)) {
					return ((BiFunction<BooleanLiteral,E,Boolean>)(suppliermap.get(Visitor.BooleanLiteral))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(BreakStatement node) {
				if(suppliermap.containsKey(Visitor.BreakStatement)) {
					return ((BiFunction<BreakStatement,E,Boolean>)(suppliermap.get(Visitor.BreakStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(CastExpression node) {
				if(suppliermap.containsKey(Visitor.CastExpression)) {
					return ((BiFunction<CastExpression,E,Boolean>)(suppliermap.get(Visitor.CastExpression))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(CatchClause node) {
				if(suppliermap.containsKey(Visitor.CatchClause)) {
					return ((BiFunction<CatchClause,E,Boolean>)(suppliermap.get(Visitor.CatchClause))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(CharacterLiteral node) {
				if(suppliermap.containsKey(Visitor.CharacterLiteral)) {
					return ((BiFunction<CharacterLiteral,E,Boolean>)(suppliermap.get(Visitor.CharacterLiteral))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ClassInstanceCreation node) {
				if(suppliermap.containsKey(Visitor.ClassInstanceCreation)) {
					return ((BiFunction<ClassInstanceCreation,E,Boolean>)(suppliermap.get(Visitor.ClassInstanceCreation))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(CompilationUnit node) {
				if(suppliermap.containsKey(Visitor.CompilationUnit)) {
					return ((BiFunction<CompilationUnit,E,Boolean>)(suppliermap.get(Visitor.CompilationUnit))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ConditionalExpression node) {
				if(suppliermap.containsKey(Visitor.ConditionalExpression)) {
					return ((BiFunction<ConditionalExpression,E,Boolean>)(suppliermap.get(Visitor.ConditionalExpression))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ConstructorInvocation node) {
				if(suppliermap.containsKey(Visitor.ConstructorInvocation)) {
					return ((BiFunction<ConstructorInvocation,E,Boolean>)(suppliermap.get(Visitor.ConstructorInvocation))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ContinueStatement node) {
				if(suppliermap.containsKey(Visitor.ContinueStatement)) {
					return ((BiFunction<ContinueStatement,E,Boolean>)(suppliermap.get(Visitor.ContinueStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(CreationReference node) {
				if(suppliermap.containsKey(Visitor.CreationReference)) {
					return ((BiFunction<CreationReference,E,Boolean>)(suppliermap.get(Visitor.CreationReference))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(Dimension node) {
				if(suppliermap.containsKey(Visitor.Dimension)) {
					return ((BiFunction<Dimension,E,Boolean>)(suppliermap.get(Visitor.Dimension))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(DoStatement node) {
				if(suppliermap.containsKey(Visitor.DoStatement)) {
					return ((BiFunction<DoStatement,E,Boolean>)(suppliermap.get(Visitor.DoStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(EmptyStatement node) {
				if(suppliermap.containsKey(Visitor.EmptyStatement)) {
					return ((BiFunction<EmptyStatement,E,Boolean>)(suppliermap.get(Visitor.EmptyStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(EnhancedForStatement node) {
				if(suppliermap.containsKey(Visitor.EnhancedForStatement)) {
					return ((BiFunction<EnhancedForStatement,E,Boolean>)(suppliermap.get(Visitor.EnhancedForStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(EnumConstantDeclaration node) {
				if(suppliermap.containsKey(Visitor.EnumConstantDeclaration)) {
					return ((BiFunction<EnumConstantDeclaration,E,Boolean>)(suppliermap.get(Visitor.EnumConstantDeclaration))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(EnumDeclaration node) {
				if(suppliermap.containsKey(Visitor.EnumDeclaration)) {
					return ((BiFunction<EnumDeclaration,E,Boolean>)(suppliermap.get(Visitor.EnumDeclaration))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ExportsDirective node) {
				if(suppliermap.containsKey(Visitor.ExportsDirective)) {
					return ((BiFunction<ExportsDirective,E,Boolean>)(suppliermap.get(Visitor.ExportsDirective))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ExpressionMethodReference node) {
				if(suppliermap.containsKey(Visitor.BreakStatement)) {
					return ((BiFunction<ExpressionMethodReference,E,Boolean>)(suppliermap.get(Visitor.ExpressionMethodReference))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ExpressionStatement node) {
				if(suppliermap.containsKey(Visitor.ExpressionStatement)) {
					return ((BiFunction<ExpressionStatement,E,Boolean>)(suppliermap.get(Visitor.ExpressionStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(FieldAccess node) {
				if(suppliermap.containsKey(Visitor.FieldAccess)) {
					return ((BiFunction<FieldAccess,E,Boolean>)(suppliermap.get(Visitor.FieldAccess))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(FieldDeclaration node) {
				if(suppliermap.containsKey(Visitor.FieldDeclaration)) {
					return ((BiFunction<FieldDeclaration,E,Boolean>)(suppliermap.get(Visitor.FieldDeclaration))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ForStatement node) {
				if(suppliermap.containsKey(Visitor.ForStatement)) {
					return ((BiFunction<ForStatement,E,Boolean>)(suppliermap.get(Visitor.ForStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(IfStatement node) {
				if(suppliermap.containsKey(Visitor.IfStatement)) {
					return ((BiFunction<IfStatement,E,Boolean>)(suppliermap.get(Visitor.IfStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ImportDeclaration node) {
				if(suppliermap.containsKey(Visitor.ImportDeclaration)) {
					return ((BiFunction<ImportDeclaration,E,Boolean>)(suppliermap.get(Visitor.ImportDeclaration))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(InfixExpression node) {
				if(suppliermap.containsKey(Visitor.InfixExpression)) {
					return ((BiFunction<InfixExpression,E,Boolean>)(suppliermap.get(Visitor.InfixExpression))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(Initializer node) {
				if(suppliermap.containsKey(Visitor.Initializer)) {
					return ((BiFunction<Initializer,E,Boolean>)(suppliermap.get(Visitor.Initializer))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(InstanceofExpression node) {
				if(suppliermap.containsKey(Visitor.InstanceofExpression)) {
					return ((BiFunction<InstanceofExpression,E,Boolean>)(suppliermap.get(Visitor.InstanceofExpression))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(IntersectionType node) {
				if(suppliermap.containsKey(Visitor.IntersectionType)) {
					return ((BiFunction<IntersectionType,E,Boolean>)(suppliermap.get(Visitor.IntersectionType))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(Javadoc node) {
				if(suppliermap.containsKey(Visitor.Javadoc)) {
					return ((BiFunction<Javadoc,E,Boolean>)(suppliermap.get(Visitor.Javadoc))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(LabeledStatement node) {
				if(suppliermap.containsKey(Visitor.LabeledStatement)) {
					return ((BiFunction<LabeledStatement,E,Boolean>)(suppliermap.get(Visitor.LabeledStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(LambdaExpression node) {
				if(suppliermap.containsKey(Visitor.LambdaExpression)) {
					return ((BiFunction<LambdaExpression,E,Boolean>)(suppliermap.get(Visitor.LambdaExpression))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(LineComment node) {
				if(suppliermap.containsKey(Visitor.LineComment)) {
					return ((BiFunction<LineComment,E,Boolean>)(suppliermap.get(Visitor.LineComment))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(MarkerAnnotation node) {
				if(suppliermap.containsKey(Visitor.MarkerAnnotation)) {
					return ((BiFunction<MarkerAnnotation,E,Boolean>)(suppliermap.get(Visitor.MarkerAnnotation))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(MemberRef node) {
				if(suppliermap.containsKey(Visitor.MemberRef)) {
					return ((BiFunction<MemberRef,E,Boolean>)(suppliermap.get(Visitor.MemberRef))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(MemberValuePair node) {
				if(suppliermap.containsKey(Visitor.MemberValuePair)) {
					return ((BiFunction<MemberValuePair,E,Boolean>)(suppliermap.get(Visitor.MemberValuePair))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(MethodRef node) {
				if(suppliermap.containsKey(Visitor.MethodRef)) {
					return ((BiFunction<MethodRef,E,Boolean>)(suppliermap.get(Visitor.MethodRef))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(MethodRefParameter node) {
				if(suppliermap.containsKey(Visitor.MethodRefParameter)) {
					return ((BiFunction<MethodRefParameter,E,Boolean>)(suppliermap.get(Visitor.MethodRefParameter))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(MethodDeclaration node) {
				if(suppliermap.containsKey(Visitor.MethodDeclaration)) {
					return ((BiFunction<MethodDeclaration,E,Boolean>)(suppliermap.get(Visitor.MethodDeclaration))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(MethodInvocation node) {
				if(suppliermap.containsKey(Visitor.MethodInvocation)) {
					return ((BiFunction<MethodInvocation,E,Boolean>)(suppliermap.get(Visitor.MethodInvocation))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(Modifier node) {
				if(suppliermap.containsKey(Visitor.Modifier)) {
					return ((BiFunction<Modifier,E,Boolean>)(suppliermap.get(Visitor.Modifier))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ModuleDeclaration node) {
				if(suppliermap.containsKey(Visitor.ModuleDeclaration)) {
					return ((BiFunction<ModuleDeclaration,E,Boolean>)(suppliermap.get(Visitor.ModuleDeclaration))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ModuleModifier node) {
				if(suppliermap.containsKey(Visitor.ModuleModifier)) {
					return ((BiFunction<ModuleModifier,E,Boolean>)(suppliermap.get(Visitor.ModuleModifier))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(NameQualifiedType node) {
				if(suppliermap.containsKey(Visitor.NameQualifiedType)) {
					return ((BiFunction<NameQualifiedType,E,Boolean>)(suppliermap.get(Visitor.NameQualifiedType))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(NormalAnnotation node) {
				if(suppliermap.containsKey(Visitor.NormalAnnotation)) {
					return ((BiFunction<NormalAnnotation,E,Boolean>)(suppliermap.get(Visitor.NormalAnnotation))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(NullLiteral node) {
				if(suppliermap.containsKey(Visitor.NullLiteral)) {
					return ((BiFunction<NullLiteral,E,Boolean>)(suppliermap.get(Visitor.NullLiteral))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(NumberLiteral node) {
				if(suppliermap.containsKey(Visitor.NumberLiteral)) {
					return ((BiFunction<NumberLiteral,E,Boolean>)(suppliermap.get(Visitor.NumberLiteral))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(OpensDirective node) {
				if(suppliermap.containsKey(Visitor.OpensDirective)) {
					return ((BiFunction<OpensDirective,E,Boolean>)(suppliermap.get(Visitor.OpensDirective))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(PackageDeclaration node) {
				if(suppliermap.containsKey(Visitor.PackageDeclaration)) {
					return ((BiFunction<PackageDeclaration,E,Boolean>)(suppliermap.get(Visitor.PackageDeclaration))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ParameterizedType node) {
				if(suppliermap.containsKey(Visitor.ParameterizedType)) {
					return ((BiFunction<ParameterizedType,E,Boolean>)(suppliermap.get(Visitor.ParameterizedType))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ParenthesizedExpression node) {
				if(suppliermap.containsKey(Visitor.ParenthesizedExpression)) {
					return ((BiFunction<ParenthesizedExpression,E,Boolean>)(suppliermap.get(Visitor.ParenthesizedExpression))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(PatternInstanceofExpression node) {
				if(suppliermap.containsKey(Visitor.PatternInstanceofExpression)) {
					return ((BiFunction<PatternInstanceofExpression,E,Boolean>)(suppliermap.get(Visitor.PatternInstanceofExpression))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(PostfixExpression node) {
				if(suppliermap.containsKey(Visitor.PostfixExpression)) {
					return ((BiFunction<PostfixExpression,E,Boolean>)(suppliermap.get(Visitor.PostfixExpression))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(PrefixExpression node) {
				if(suppliermap.containsKey(Visitor.PrefixExpression)) {
					return ((BiFunction<PrefixExpression,E,Boolean>)(suppliermap.get(Visitor.PrefixExpression))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ProvidesDirective node) {
				if(suppliermap.containsKey(Visitor.ProvidesDirective)) {
					return ((BiFunction<ProvidesDirective,E,Boolean>)(suppliermap.get(Visitor.ProvidesDirective))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(PrimitiveType node) {
				if(suppliermap.containsKey(Visitor.PrimitiveType)) {
					return ((BiFunction<PrimitiveType,E,Boolean>)(suppliermap.get(Visitor.PrimitiveType))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(QualifiedName node) {
				if(suppliermap.containsKey(Visitor.QualifiedName)) {
					return ((BiFunction<QualifiedName,E,Boolean>)(suppliermap.get(Visitor.QualifiedName))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(QualifiedType node) {
				if(suppliermap.containsKey(Visitor.QualifiedType)) {
					return ((BiFunction<QualifiedType,E,Boolean>)(suppliermap.get(Visitor.QualifiedType))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ModuleQualifiedName node) {
				if(suppliermap.containsKey(Visitor.ModuleQualifiedName)) {
					return ((BiFunction<ModuleQualifiedName,E,Boolean>)(suppliermap.get(Visitor.ModuleQualifiedName))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(RequiresDirective node) {
				if(suppliermap.containsKey(Visitor.RequiresDirective)) {
					return ((BiFunction<RequiresDirective,E,Boolean>)(suppliermap.get(Visitor.RequiresDirective))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(RecordDeclaration node) {
				if(suppliermap.containsKey(Visitor.RecordDeclaration)) {
					return ((BiFunction<RecordDeclaration,E,Boolean>)(suppliermap.get(Visitor.RecordDeclaration))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ReturnStatement node) {
				if(suppliermap.containsKey(Visitor.ReturnStatement)) {
					return ((BiFunction<ReturnStatement,E,Boolean>)(suppliermap.get(Visitor.ReturnStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(SimpleName node) {
				if(suppliermap.containsKey(Visitor.SimpleName)) {
					return ((BiFunction<SimpleName,E,Boolean>)(suppliermap.get(Visitor.SimpleName))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(SimpleType node) {
				if(suppliermap.containsKey(Visitor.SimpleType)) {
					return ((BiFunction<SimpleType,E,Boolean>)(suppliermap.get(Visitor.SimpleType))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(SingleMemberAnnotation node) {
				if(suppliermap.containsKey(Visitor.SingleMemberAnnotation)) {
					return ((BiFunction<SingleMemberAnnotation,E,Boolean>)(suppliermap.get(Visitor.SingleMemberAnnotation))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(SingleVariableDeclaration node) {
				if(suppliermap.containsKey(Visitor.SingleVariableDeclaration)) {
					return ((BiFunction<SingleVariableDeclaration,E,Boolean>)(suppliermap.get(Visitor.SingleVariableDeclaration))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(StringLiteral node) {
				if(suppliermap.containsKey(Visitor.StringLiteral)) {
					return ((BiFunction<StringLiteral,E,Boolean>)(suppliermap.get(Visitor.StringLiteral))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(SuperConstructorInvocation node) {
				if(suppliermap.containsKey(Visitor.SuperConstructorInvocation)) {
					return ((BiFunction<SuperConstructorInvocation,E,Boolean>)(suppliermap.get(Visitor.SuperConstructorInvocation))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(SuperFieldAccess node) {
				if(suppliermap.containsKey(Visitor.SuperFieldAccess)) {
					return ((BiFunction<SuperFieldAccess,E,Boolean>)(suppliermap.get(Visitor.SuperFieldAccess))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(SuperMethodInvocation node) {
				if(suppliermap.containsKey(Visitor.SuperMethodInvocation)) {
					return ((BiFunction<SuperMethodInvocation,E,Boolean>)(suppliermap.get(Visitor.SuperMethodInvocation))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(SuperMethodReference node) {
				if(suppliermap.containsKey(Visitor.SuperMethodReference)) {
					return ((BiFunction<SuperMethodReference,E,Boolean>)(suppliermap.get(Visitor.SuperMethodReference))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(SwitchCase node) {
				if(suppliermap.containsKey(Visitor.SwitchCase)) {
					return ((BiFunction<SwitchCase,E,Boolean>)(suppliermap.get(Visitor.SwitchCase))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(SwitchExpression node) {
				if(suppliermap.containsKey(Visitor.SwitchExpression)) {
					return ((BiFunction<SwitchExpression,E,Boolean>)(suppliermap.get(Visitor.SwitchExpression))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(SwitchStatement node) {
				if(suppliermap.containsKey(Visitor.SwitchStatement)) {
					return ((BiFunction<SwitchStatement,E,Boolean>)(suppliermap.get(Visitor.SwitchStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(SynchronizedStatement node) {
				if(suppliermap.containsKey(Visitor.SynchronizedStatement)) {
					return ((BiFunction<SynchronizedStatement,E,Boolean>)(suppliermap.get(Visitor.SynchronizedStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(TagElement node) {
				if(suppliermap.containsKey(Visitor.TagElement)) {
					return ((BiFunction<TagElement,E,Boolean>)(suppliermap.get(Visitor.TagElement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(TextBlock node) {
				if(suppliermap.containsKey(Visitor.TextBlock)) {
					return ((BiFunction<TextBlock,E,Boolean>)(suppliermap.get(Visitor.TextBlock))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(TextElement node) {
				if(suppliermap.containsKey(Visitor.TextElement)) {
					return ((BiFunction<TextElement,E,Boolean>)(suppliermap.get(Visitor.TextElement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ThisExpression node) {
				if(suppliermap.containsKey(Visitor.ThisExpression)) {
					return ((BiFunction<ThisExpression,E,Boolean>)(suppliermap.get(Visitor.ThisExpression))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(ThrowStatement node) {
				if(suppliermap.containsKey(Visitor.ThrowStatement)) {
					return ((BiFunction<ThrowStatement,E,Boolean>)(suppliermap.get(Visitor.ThrowStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(TryStatement node) {
				if(suppliermap.containsKey(Visitor.TryStatement)) {
					return ((BiFunction<TryStatement,E,Boolean>)(suppliermap.get(Visitor.TryStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(TypeDeclaration node) {
				if(suppliermap.containsKey(Visitor.TypeDeclaration)) {
					return ((BiFunction<TypeDeclaration,E,Boolean>)(suppliermap.get(Visitor.TypeDeclaration))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(TypeDeclarationStatement node) {
				if(suppliermap.containsKey(Visitor.TypeDeclarationStatement)) {
					return ((BiFunction<TypeDeclarationStatement,E,Boolean>)(suppliermap.get(Visitor.TypeDeclarationStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(TypeLiteral node) {
				if(suppliermap.containsKey(Visitor.TypeLiteral)) {
					return ((BiFunction<TypeLiteral,E,Boolean>)(suppliermap.get(Visitor.TypeLiteral))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(TypeMethodReference node) {
				if(suppliermap.containsKey(Visitor.TypeMethodReference)) {
					return ((BiFunction<TypeMethodReference,E,Boolean>)(suppliermap.get(Visitor.TypeMethodReference))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(TypeParameter node) {
				if(suppliermap.containsKey(Visitor.TypeParameter)) {
					return ((BiFunction<TypeParameter,E,Boolean>)(suppliermap.get(Visitor.TypeParameter))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(UnionType node) {
				if(suppliermap.containsKey(Visitor.UnionType)) {
					return ((BiFunction<UnionType,E,Boolean>)(suppliermap.get(Visitor.UnionType))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(UsesDirective node) {
				if(suppliermap.containsKey(Visitor.UsesDirective)) {
					return ((BiFunction<UsesDirective,E,Boolean>)(suppliermap.get(Visitor.UsesDirective))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(VariableDeclarationExpression node) {
				if(suppliermap.containsKey(Visitor.VariableDeclarationExpression)) {
					return ((BiFunction<VariableDeclarationExpression,E,Boolean>)(suppliermap.get(Visitor.VariableDeclarationExpression))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(VariableDeclarationStatement node) {
				if(suppliermap.containsKey(Visitor.VariableDeclarationStatement)) {
					return ((BiFunction<VariableDeclarationStatement,E,Boolean>)(suppliermap.get(Visitor.VariableDeclarationStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(VariableDeclarationFragment node) {
				if(suppliermap.containsKey(Visitor.VariableDeclarationFragment)) {
					return ((BiFunction<VariableDeclarationFragment,E,Boolean>)(suppliermap.get(Visitor.VariableDeclarationFragment))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(WhileStatement node) {
				if(suppliermap.containsKey(Visitor.WhileStatement)) {
					return ((BiFunction<WhileStatement,E,Boolean>)(suppliermap.get(Visitor.WhileStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(WildcardType node) {
				if(suppliermap.containsKey(Visitor.WildcardType)) {
					return ((BiFunction<WildcardType,E,Boolean>)(suppliermap.get(Visitor.WildcardType))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

			@Override
			public boolean visit(YieldStatement node) {
				if(suppliermap.containsKey(Visitor.YieldStatement)) {
					return ((BiFunction<YieldStatement,E,Boolean>)(suppliermap.get(Visitor.YieldStatement))).apply(node, dataholder).booleanValue();
				}
				return true;
			}

		};
		compilationUnit.accept(astvisitor);
		return this;
	}
}

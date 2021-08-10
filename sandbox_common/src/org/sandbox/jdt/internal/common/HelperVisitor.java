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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import org.eclipse.jdt.core.dom.*;

/**
 * This class allows to use Lambda expressions for building up your visitor
 * processing
 * 
 * @author chammer
 *
 * @param <E>
 */
public class HelperVisitor<E extends HelperVisitorProvider> {

	private static final long serialVersionUID = 1L;

	ASTVisitor astvisitor;
	public E dataholder;

	/**
	 * This map contains one VisitorSupplier per kind if supplied Each BiPredicate is
	 * called with two parameters 1) ASTNode 2) your data object Call is processed
	 * when build(CompilationUnit) is called.
	 */
	Map<VisitorEnum, BiPredicate<? extends ASTNode, E>> predicatemap;
	/**
	 * This map contains one VisitorConsumer per kind if supplied Each BiConsumer is
	 * called with two parameters 1) ASTNode 2) your data object Call is processed
	 * when build(CompilationUnit) is called.
	 * Because the "visitend" does not return a boolean we need a consumer instead of a supplier here.
	 */
	Map<VisitorEnum, BiConsumer<? extends ASTNode, E>> consumermap;
	/**
	 * Here we store data to implement convenience methods like method visitor where the method name can be given as parameter
	 */
	Map<VisitorEnum, Object> predicatedata;
	Map<VisitorEnum, Object> consumerdata;

	public Map<VisitorEnum, BiPredicate<? extends ASTNode, E>> getSuppliermap() {
		return predicatemap;
	}

	public Map<VisitorEnum, BiConsumer<? extends ASTNode, E>> getConsumermap() {
		return consumermap;
	}

	
	public Set<ASTNode> nodesprocessed;

	public Set<ASTNode> getNodesprocessed() {
		return nodesprocessed;
	}

	public HelperVisitor(Set<ASTNode> nodesprocessed, E dataholder) {
		this.predicatemap = new LinkedHashMap<>();
		this.consumermap = new LinkedHashMap<>();
		this.predicatedata = new HashMap<>();
		this.consumerdata = new HashMap<>();
		
		this.dataholder = dataholder;
		dataholder.setHelperVisitor(this);
		this.nodesprocessed = nodesprocessed;
	}

	public BiPredicate<? extends ASTNode, E> addAnnotationTypeDeclaration(
			BiPredicate<AnnotationTypeDeclaration, E> bs) {
		return predicatemap.put(VisitorEnum.AnnotationTypeDeclaration, bs);
	}

	public BiPredicate<? extends ASTNode, E> addAnnotationTypeMemberDeclaration(
			BiPredicate<AnnotationTypeMemberDeclaration, E> bs) {
		return predicatemap.put(VisitorEnum.AnnotationTypeMemberDeclaration, bs);
	}

	public BiPredicate<? extends ASTNode, E> addAnonymousClassDeclaration(
			BiPredicate<AnonymousClassDeclaration, E> bs) {
		return predicatemap.put(VisitorEnum.AnonymousClassDeclaration, bs);
	}

	public BiPredicate<? extends ASTNode, E> addArrayAccess(BiPredicate<ArrayAccess, E> bs) {
		return predicatemap.put(VisitorEnum.ArrayAccess, bs);
	}

	public BiPredicate<? extends ASTNode, E> addArrayCreation(BiPredicate<ArrayCreation, E> bs) {
		return predicatemap.put(VisitorEnum.ArrayCreation, bs);
	}

	public BiPredicate<? extends ASTNode, E> addArrayInitializer(BiPredicate<ArrayInitializer, E> bs) {
		return predicatemap.put(VisitorEnum.ArrayInitializer, bs);
	}

	public BiPredicate<? extends ASTNode, E> addArrayType(BiPredicate<ArrayType, E> bs) {
		return predicatemap.put(VisitorEnum.ArrayType, bs);
	}

	public BiPredicate<? extends ASTNode, E> addAssertStatement(BiPredicate<AssertStatement, E> bs) {
		return predicatemap.put(VisitorEnum.AssertStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addAssignment(BiPredicate<Assignment, E> bs) {
		return predicatemap.put(VisitorEnum.Assignment, bs);
	}

	public BiPredicate<? extends ASTNode, E> addBlock(BiPredicate<Block, E> bs) {
		return predicatemap.put(VisitorEnum.Block, bs);
	}

	public BiPredicate<? extends ASTNode, E> addBlockComment(BiPredicate<BlockComment, E> bs) {
		return predicatemap.put(VisitorEnum.BlockComment, bs);
	}

	public BiPredicate<? extends ASTNode, E> addBooleanLiteral(BiPredicate<BooleanLiteral, E> bs) {
		return predicatemap.put(VisitorEnum.BooleanLiteral, bs);
	}

	public BiPredicate<? extends ASTNode, E> addBreakStatement(BiPredicate<BreakStatement, E> bs) {
		return predicatemap.put(VisitorEnum.BreakStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addCastExpression(BiPredicate<CastExpression, E> bs) {
		return predicatemap.put(VisitorEnum.CastExpression, bs);
	}

	public BiPredicate<? extends ASTNode, E> addCatchClause(BiPredicate<CatchClause, E> bs) {
		return predicatemap.put(VisitorEnum.CatchClause, bs);
	}

	public BiPredicate<? extends ASTNode, E> addCharacterLiteral(BiPredicate<CharacterLiteral, E> bs) {
		return predicatemap.put(VisitorEnum.CharacterLiteral, bs);
	}

	public BiPredicate<? extends ASTNode, E> addClassInstanceCreation(
			BiPredicate<ClassInstanceCreation, E> bs) {
		return predicatemap.put(VisitorEnum.ClassInstanceCreation, bs);
	}

	public BiPredicate<? extends ASTNode, E> addCompilationUnit(BiPredicate<CompilationUnit, E> bs) {
		return predicatemap.put(VisitorEnum.CompilationUnit, bs);
	}

	public BiPredicate<? extends ASTNode, E> addConditionalExpression(
			BiPredicate<ConditionalExpression, E> bs) {
		return predicatemap.put(VisitorEnum.ConditionalExpression, bs);
	}

	public BiPredicate<? extends ASTNode, E> addConstructorInvocation(
			BiPredicate<ConstructorInvocation, E> bs) {
		return predicatemap.put(VisitorEnum.ConstructorInvocation, bs);
	}

	public BiPredicate<? extends ASTNode, E> addContinueStatement(
			BiPredicate<ContinueStatement, E> bs) {
		return predicatemap.put(VisitorEnum.ContinueStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addCreationReference(
			BiPredicate<CreationReference, E> bs) {
		return predicatemap.put(VisitorEnum.CreationReference, bs);
	}

	public BiPredicate<? extends ASTNode, E> addDimension(BiPredicate<Dimension, E> bs) {
		return predicatemap.put(VisitorEnum.Dimension, bs);
	}

	public BiPredicate<? extends ASTNode, E> addDoStatement(BiPredicate<DoStatement, E> bs) {
		return predicatemap.put(VisitorEnum.DoStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addEmptyStatement(BiPredicate<EmptyStatement, E> bs) {
		return predicatemap.put(VisitorEnum.EmptyStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addEnhancedForStatement(
			BiPredicate<EnhancedForStatement, E> bs) {
		return predicatemap.put(VisitorEnum.EnhancedForStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addEnumConstantDeclaration(
			BiPredicate<EnumConstantDeclaration, E> bs) {
		return predicatemap.put(VisitorEnum.EnumConstantDeclaration, bs);
	}

	public BiPredicate<? extends ASTNode, E> addEnumDeclaration(BiPredicate<EnumDeclaration, E> bs) {
		return predicatemap.put(VisitorEnum.EnumDeclaration, bs);
	}

	public BiPredicate<? extends ASTNode, E> addExportsDirective(BiPredicate<ExportsDirective, E> bs) {
		return predicatemap.put(VisitorEnum.ExportsDirective, bs);
	}

	public BiPredicate<? extends ASTNode, E> addExpressionMethodReference(
			BiPredicate<ExpressionMethodReference, E> bs) {
		return predicatemap.put(VisitorEnum.ExpressionMethodReference, bs);
	}

	public BiPredicate<? extends ASTNode, E> addExpressionStatement(
			BiPredicate<ExpressionStatement, E> bs) {
		return predicatemap.put(VisitorEnum.ExpressionStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addFieldAccess(BiPredicate<FieldAccess, E> bs) {
		return predicatemap.put(VisitorEnum.FieldAccess, bs);
	}

	public BiPredicate<? extends ASTNode, E> addFieldDeclaration(BiPredicate<FieldDeclaration, E> bs) {
		return predicatemap.put(VisitorEnum.FieldDeclaration, bs);
	}

	public BiPredicate<? extends ASTNode, E> addForStatement(BiPredicate<ForStatement, E> bs) {
		return predicatemap.put(VisitorEnum.ForStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addIfStatement(BiPredicate<IfStatement, E> bs) {
		return predicatemap.put(VisitorEnum.IfStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addImportDeclaration(
			BiPredicate<ImportDeclaration, E> bs) {
		return predicatemap.put(VisitorEnum.ImportDeclaration, bs);
	}

	public BiPredicate<? extends ASTNode, E> addInfixExpression(BiPredicate<InfixExpression, E> bs) {
		return predicatemap.put(VisitorEnum.InfixExpression, bs);
	}

	public BiPredicate<? extends ASTNode, E> addInitializer(BiPredicate<Initializer, E> bs) {
		return predicatemap.put(VisitorEnum.Initializer, bs);
	}

	public BiPredicate<? extends ASTNode, E> addInstanceofExpression(
			BiPredicate<InstanceofExpression, E> bs) {
		return predicatemap.put(VisitorEnum.InstanceofExpression, bs);
	}

	public BiPredicate<? extends ASTNode, E> addIntersectionType(BiPredicate<IntersectionType, E> bs) {
		return predicatemap.put(VisitorEnum.IntersectionType, bs);
	}

	public BiPredicate<? extends ASTNode, E> addJavadoc(BiPredicate<Javadoc, E> bs) {
		return predicatemap.put(VisitorEnum.Javadoc, bs);
	}

	public BiPredicate<? extends ASTNode, E> addLabeledStatement(BiPredicate<LabeledStatement, E> bs) {
		return predicatemap.put(VisitorEnum.LabeledStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addLambdaExpression(BiPredicate<LambdaExpression, E> bs) {
		return predicatemap.put(VisitorEnum.LambdaExpression, bs);
	}

	public BiPredicate<? extends ASTNode, E> addLineComment(BiPredicate<LineComment, E> bs) {
		return predicatemap.put(VisitorEnum.LineComment, bs);
	}

	public BiPredicate<? extends ASTNode, E> addMarkerAnnotation(BiPredicate<MarkerAnnotation, E> bs) {
		return predicatemap.put(VisitorEnum.MarkerAnnotation, bs);
	}

	public BiPredicate<? extends ASTNode, E> addMemberRef(BiPredicate<MemberRef, E> bs) {
		return predicatemap.put(VisitorEnum.MemberRef, bs);
	}

	public BiPredicate<? extends ASTNode, E> addMemberValuePair(BiPredicate<MemberValuePair, E> bs) {
		return predicatemap.put(VisitorEnum.MemberValuePair, bs);
	}

	public BiPredicate<? extends ASTNode, E> addMethodRef(BiPredicate<MethodRef, E> bs) {
		return predicatemap.put(VisitorEnum.MethodRef, bs);
	}

	public BiPredicate<? extends ASTNode, E> addMethodRefParameter(
			BiPredicate<MethodRefParameter, E> bs) {
		return predicatemap.put(VisitorEnum.MethodRefParameter, bs);
	}

	public BiPredicate<? extends ASTNode, E> addMethodDeclaration(
			BiPredicate<MethodDeclaration, E> bs) {
		return predicatemap.put(VisitorEnum.MethodDeclaration, bs);
	}

	public BiPredicate<? extends ASTNode, E> addMethodInvocation(BiPredicate<MethodInvocation, E> bs) {
		return predicatemap.put(VisitorEnum.MethodInvocation, bs);
	}

	public BiPredicate<? extends ASTNode, E> addMethodInvocation(String methodname,
			BiPredicate<MethodInvocation, E> bs) {
		this.predicatedata.put(VisitorEnum.MethodInvocation, methodname);
		return predicatemap.put(VisitorEnum.MethodInvocation, bs);
	}

	public BiPredicate<? extends ASTNode, E> addModifier(BiPredicate<Modifier, E> bs) {
		return predicatemap.put(VisitorEnum.Modifier, bs);
	}

	public BiPredicate<? extends ASTNode, E> addModuleDeclaration(
			BiPredicate<ModuleDeclaration, E> bs) {
		return predicatemap.put(VisitorEnum.ModuleDeclaration, bs);
	}

	public BiPredicate<? extends ASTNode, E> addModuleModifier(BiPredicate<ModuleModifier, E> bs) {
		return predicatemap.put(VisitorEnum.ModuleModifier, bs);
	}

	public BiPredicate<? extends ASTNode, E> addNameQualifiedType(
			BiPredicate<NameQualifiedType, E> bs) {
		return predicatemap.put(VisitorEnum.NameQualifiedType, bs);
	}

	public BiPredicate<? extends ASTNode, E> addNormalAnnotation(BiPredicate<NormalAnnotation, E> bs) {
		return predicatemap.put(VisitorEnum.NormalAnnotation, bs);
	}

	public BiPredicate<? extends ASTNode, E> addNullLiteral(BiPredicate<NullLiteral, E> bs) {
		return predicatemap.put(VisitorEnum.NullLiteral, bs);
	}

	public BiPredicate<? extends ASTNode, E> addNumberLiteral(BiPredicate<NumberLiteral, E> bs) {
		return predicatemap.put(VisitorEnum.NumberLiteral, bs);
	}

	public BiPredicate<? extends ASTNode, E> addOpensDirective(BiPredicate<OpensDirective, E> bs) {
		return predicatemap.put(VisitorEnum.OpensDirective, bs);
	}

	public BiPredicate<? extends ASTNode, E> addPackageDeclaration(
			BiPredicate<PackageDeclaration, E> bs) {
		return predicatemap.put(VisitorEnum.PackageDeclaration, bs);
	}

	public BiPredicate<? extends ASTNode, E> addParameterizedType(
			BiPredicate<ParameterizedType, E> bs) {
		return predicatemap.put(VisitorEnum.ParameterizedType, bs);
	}

	public BiPredicate<? extends ASTNode, E> addParenthesizedExpression(
			BiPredicate<ParenthesizedExpression, E> bs) {
		return predicatemap.put(VisitorEnum.ParenthesizedExpression, bs);
	}

	public BiPredicate<? extends ASTNode, E> addPatternInstanceofExpression(
			BiPredicate<PatternInstanceofExpression, E> bs) {
		return predicatemap.put(VisitorEnum.PatternInstanceofExpression, bs);
	}

	public BiPredicate<? extends ASTNode, E> addPostfixExpression(
			BiPredicate<PostfixExpression, E> bs) {
		return predicatemap.put(VisitorEnum.PostfixExpression, bs);
	}

	public BiPredicate<? extends ASTNode, E> addPrefixExpression(BiPredicate<PrefixExpression, E> bs) {
		return predicatemap.put(VisitorEnum.PrefixExpression, bs);
	}

	public BiPredicate<? extends ASTNode, E> addProvidesDirective(
			BiPredicate<ProvidesDirective, E> bs) {
		return predicatemap.put(VisitorEnum.ProvidesDirective, bs);
	}

	public BiPredicate<? extends ASTNode, E> addPrimitiveType(BiPredicate<PrimitiveType, E> bs) {
		return predicatemap.put(VisitorEnum.PrimitiveType, bs);
	}

	public BiPredicate<? extends ASTNode, E> addQualifiedName(BiPredicate<QualifiedName, E> bs) {
		return predicatemap.put(VisitorEnum.QualifiedName, bs);
	}

	public BiPredicate<? extends ASTNode, E> addQualifiedType(BiPredicate<QualifiedType, E> bs) {
		return predicatemap.put(VisitorEnum.QualifiedType, bs);
	}

//	public BiPredicate<? extends ASTNode, E> addModuleQualifiedName(
//			BiPredicate<ModuleQualifiedName, E> bs) {
//		return suppliermap.put(VisitorEnum.ModuleQualifiedName, bs);
//	}

	public BiPredicate<? extends ASTNode, E> addRequiresDirective(
			BiPredicate<RequiresDirective, E> bs) {
		return predicatemap.put(VisitorEnum.RequiresDirective, bs);
	}

	public BiPredicate<? extends ASTNode, E> addRecordDeclaration(
			BiPredicate<RecordDeclaration, E> bs) {
		return predicatemap.put(VisitorEnum.RecordDeclaration, bs);
	}

	public BiPredicate<? extends ASTNode, E> addReturnStatement(BiPredicate<ReturnStatement, E> bs) {
		return predicatemap.put(VisitorEnum.ReturnStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addSimpleName(BiPredicate<SimpleName, E> bs) {
		return predicatemap.put(VisitorEnum.SimpleName, bs);
	}

	public BiPredicate<? extends ASTNode, E> addSimpleType(BiPredicate<SimpleType, E> bs) {
		return predicatemap.put(VisitorEnum.SimpleType, bs);
	}

	public BiPredicate<? extends ASTNode, E> addSingleMemberAnnotation(
			BiPredicate<SingleMemberAnnotation, E> bs) {
		return predicatemap.put(VisitorEnum.SingleMemberAnnotation, bs);
	}

	public BiPredicate<? extends ASTNode, E> addSingleVariableDeclaration(
			BiPredicate<SingleVariableDeclaration, E> bs) {
		return predicatemap.put(VisitorEnum.SingleVariableDeclaration, bs);
	}

	public BiPredicate<? extends ASTNode, E> addStringLiteral(BiPredicate<StringLiteral, E> bs) {
		return predicatemap.put(VisitorEnum.StringLiteral, bs);
	}

	public BiPredicate<? extends ASTNode, E> addSuperConstructorInvocation(
			BiPredicate<SuperConstructorInvocation, E> bs) {
		return predicatemap.put(VisitorEnum.SuperConstructorInvocation, bs);
	}

	public BiPredicate<? extends ASTNode, E> addSuperFieldAccess(BiPredicate<SuperFieldAccess, E> bs) {
		return predicatemap.put(VisitorEnum.SuperFieldAccess, bs);
	}

	public BiPredicate<? extends ASTNode, E> addSuperMethodInvocation(
			BiPredicate<SuperMethodInvocation, E> bs) {
		return predicatemap.put(VisitorEnum.SuperMethodInvocation, bs);
	}

	public BiPredicate<? extends ASTNode, E> addSuperMethodReference(
			BiPredicate<SuperMethodReference, E> bs) {
		return predicatemap.put(VisitorEnum.SuperMethodReference, bs);
	}

	public BiPredicate<? extends ASTNode, E> addSwitchCase(BiPredicate<SwitchCase, E> bs) {
		return predicatemap.put(VisitorEnum.SwitchCase, bs);
	}

	public BiPredicate<? extends ASTNode, E> addSwitchExpression(BiPredicate<SwitchExpression, E> bs) {
		return predicatemap.put(VisitorEnum.SwitchExpression, bs);
	}

	public BiPredicate<? extends ASTNode, E> addSwitchStatement(BiPredicate<SwitchStatement, E> bs) {
		return predicatemap.put(VisitorEnum.SwitchStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addSynchronizedStatement(
			BiPredicate<SynchronizedStatement, E> bs) {
		return predicatemap.put(VisitorEnum.SynchronizedStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addTagElement(BiPredicate<TagElement, E> bs) {
		return predicatemap.put(VisitorEnum.TagElement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addTextBlock(BiPredicate<TextBlock, E> bs) {
		return predicatemap.put(VisitorEnum.TextBlock, bs);
	}

	public BiPredicate<? extends ASTNode, E> addTextElement(BiPredicate<TextElement, E> bs) {
		return predicatemap.put(VisitorEnum.TextElement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addThisExpression(BiPredicate<ThisExpression, E> bs) {
		return predicatemap.put(VisitorEnum.ThisExpression, bs);
	}

	public BiPredicate<? extends ASTNode, E> addThrowStatement(BiPredicate<ThrowStatement, E> bs) {
		return predicatemap.put(VisitorEnum.ThrowStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addTryStatement(BiPredicate<TryStatement, E> bs) {
		return predicatemap.put(VisitorEnum.TryStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addTypeDeclaration(BiPredicate<TypeDeclaration, E> bs) {
		return predicatemap.put(VisitorEnum.TypeDeclaration, bs);
	}

	public BiPredicate<? extends ASTNode, E> addTypeDeclarationStatement(
			BiPredicate<TypeDeclarationStatement, E> bs) {
		return predicatemap.put(VisitorEnum.TypeDeclarationStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addTypeLiteral(BiPredicate<TypeLiteral, E> bs) {
		return predicatemap.put(VisitorEnum.TypeLiteral, bs);
	}

	public BiPredicate<? extends ASTNode, E> addTypeMethodReference(
			BiPredicate<TypeMethodReference, E> bs) {
		return predicatemap.put(VisitorEnum.TypeMethodReference, bs);
	}

	public BiPredicate<? extends ASTNode, E> addTypeParameter(BiPredicate<TypeParameter, E> bs) {
		return predicatemap.put(VisitorEnum.TypeParameter, bs);
	}

	public BiPredicate<? extends ASTNode, E> addUnionType(BiPredicate<UnionType, E> bs) {
		return predicatemap.put(VisitorEnum.UnionType, bs);
	}

	public BiPredicate<? extends ASTNode, E> addUsesDirective(BiPredicate<UsesDirective, E> bs) {
		return predicatemap.put(VisitorEnum.UsesDirective, bs);
	}

	public BiPredicate<? extends ASTNode, E> addVariableDeclarationExpression(
			BiPredicate<VariableDeclarationExpression, E> bs) {
		return predicatemap.put(VisitorEnum.VariableDeclarationExpression, bs);
	}

	public BiPredicate<? extends ASTNode, E> addVariableDeclarationStatement(
			BiPredicate<VariableDeclarationStatement, E> bs) {
		return predicatemap.put(VisitorEnum.VariableDeclarationStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addVariableDeclarationFragment(
			BiPredicate<VariableDeclarationFragment, E> bs) {
		return predicatemap.put(VisitorEnum.VariableDeclarationFragment, bs);
	}

	public BiPredicate<? extends ASTNode, E> addWhileStatement(BiPredicate<WhileStatement, E> bs) {
		return predicatemap.put(VisitorEnum.WhileStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> addWildcardType(BiPredicate<WildcardType, E> bs) {
		return predicatemap.put(VisitorEnum.WildcardType, bs);
	}

	public BiPredicate<? extends ASTNode, E> addYieldStatement(BiPredicate<YieldStatement, E> bs) {
		return predicatemap.put(VisitorEnum.YieldStatement, bs);
	}

	public BiPredicate<? extends ASTNode, E> add(VisitorEnum key, BiPredicate<ASTNode, E> bs) {
		return predicatemap.put(key, bs);
	}

	public BiConsumer<? extends ASTNode, E> addAnnotationTypeDeclaration(BiConsumer<AnnotationTypeDeclaration, E> bc) {
		return consumermap.put(VisitorEnum.AnnotationTypeDeclaration, bc);
	}

	public BiConsumer<? extends ASTNode, E> addAnnotationTypeMemberDeclaration(
			BiConsumer<AnnotationTypeMemberDeclaration, E> bc) {
		return consumermap.put(VisitorEnum.AnnotationTypeMemberDeclaration, bc);
	}

	public BiConsumer<? extends ASTNode, E> addAnonymousClassDeclaration(BiConsumer<AnonymousClassDeclaration, E> bc) {
		return consumermap.put(VisitorEnum.AnonymousClassDeclaration, bc);
	}

	public BiConsumer<? extends ASTNode, E> addArrayAccess(BiConsumer<ArrayAccess, E> bc) {
		return consumermap.put(VisitorEnum.ArrayAccess, bc);
	}

	public BiConsumer<? extends ASTNode, E> addArrayCreation(BiConsumer<ArrayCreation, E> bc) {
		return consumermap.put(VisitorEnum.ArrayCreation, bc);
	}

	public BiConsumer<? extends ASTNode, E> addArrayInitializer(BiConsumer<ArrayInitializer, E> bc) {
		return consumermap.put(VisitorEnum.ArrayInitializer, bc);
	}

	public BiConsumer<? extends ASTNode, E> addArrayType(BiConsumer<ArrayType, E> bc) {
		return consumermap.put(VisitorEnum.ArrayType, bc);
	}

	public BiConsumer<? extends ASTNode, E> addAssertStatement(BiConsumer<AssertStatement, E> bc) {
		return consumermap.put(VisitorEnum.AssertStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addAssignment(BiConsumer<Assignment, E> bc) {
		return consumermap.put(VisitorEnum.Assignment, bc);
	}

	public BiConsumer<? extends ASTNode, E> addBlock(BiConsumer<Block, E> bc) {
		return consumermap.put(VisitorEnum.Block, bc);
	}

	public BiConsumer<? extends ASTNode, E> addBlockComment(BiConsumer<BlockComment, E> bc) {
		return consumermap.put(VisitorEnum.BlockComment, bc);
	}

	public BiConsumer<? extends ASTNode, E> addBooleanLiteral(BiConsumer<BooleanLiteral, E> bc) {
		return consumermap.put(VisitorEnum.BooleanLiteral, bc);
	}

	public BiConsumer<? extends ASTNode, E> addBreakStatement(BiConsumer<BreakStatement, E> bc) {
		return consumermap.put(VisitorEnum.BreakStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addCastExpression(BiConsumer<CastExpression, E> bc) {
		return consumermap.put(VisitorEnum.CastExpression, bc);
	}

	public BiConsumer<? extends ASTNode, E> addCatchClause(BiConsumer<CatchClause, E> bc) {
		return consumermap.put(VisitorEnum.CatchClause, bc);
	}

	public BiConsumer<? extends ASTNode, E> addCharacterLiteral(BiConsumer<CharacterLiteral, E> bc) {
		return consumermap.put(VisitorEnum.CharacterLiteral, bc);
	}

	public BiConsumer<? extends ASTNode, E> addClassInstanceCreation(BiConsumer<ClassInstanceCreation, E> bc) {
		return consumermap.put(VisitorEnum.ClassInstanceCreation, bc);
	}

	public BiConsumer<? extends ASTNode, E> addCompilationUnit(BiConsumer<CompilationUnit, E> bc) {
		return consumermap.put(VisitorEnum.CompilationUnit, bc);
	}

	public BiConsumer<? extends ASTNode, E> addConditionalExpression(BiConsumer<ConditionalExpression, E> bc) {
		return consumermap.put(VisitorEnum.ConditionalExpression, bc);
	}

	public BiConsumer<? extends ASTNode, E> addConstructorInvocation(BiConsumer<ConstructorInvocation, E> bc) {
		return consumermap.put(VisitorEnum.ConstructorInvocation, bc);
	}

	public BiConsumer<? extends ASTNode, E> addContinueStatement(BiConsumer<ContinueStatement, E> bc) {
		return consumermap.put(VisitorEnum.ContinueStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addCreationReference(BiConsumer<CreationReference, E> bc) {
		return consumermap.put(VisitorEnum.CreationReference, bc);
	}

	public BiConsumer<? extends ASTNode, E> addDimension(BiConsumer<Dimension, E> bc) {
		return consumermap.put(VisitorEnum.Dimension, bc);
	}

	public BiConsumer<? extends ASTNode, E> addDoStatement(BiConsumer<DoStatement, E> bc) {
		return consumermap.put(VisitorEnum.DoStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addEmptyStatement(BiConsumer<EmptyStatement, E> bc) {
		return consumermap.put(VisitorEnum.EmptyStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addEnhancedForStatement(BiConsumer<EnhancedForStatement, E> bc) {
		return consumermap.put(VisitorEnum.EnhancedForStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addEnumConstantDeclaration(BiConsumer<EnumConstantDeclaration, E> bc) {
		return consumermap.put(VisitorEnum.EnumConstantDeclaration, bc);
	}

	public BiConsumer<? extends ASTNode, E> addEnumDeclaration(BiConsumer<EnumDeclaration, E> bc) {
		return consumermap.put(VisitorEnum.EnumDeclaration, bc);
	}

	public BiConsumer<? extends ASTNode, E> addExportsDirective(BiConsumer<ExportsDirective, E> bc) {
		return consumermap.put(VisitorEnum.ExportsDirective, bc);
	}

	public BiConsumer<? extends ASTNode, E> addExpressionMethodReference(BiConsumer<ExpressionMethodReference, E> bc) {
		return consumermap.put(VisitorEnum.ExpressionMethodReference, bc);
	}

	public BiConsumer<? extends ASTNode, E> addExpressionStatement(BiConsumer<ExpressionStatement, E> bc) {
		return consumermap.put(VisitorEnum.ExpressionStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addFieldAccess(BiConsumer<FieldAccess, E> bc) {
		return consumermap.put(VisitorEnum.FieldAccess, bc);
	}

	public BiConsumer<? extends ASTNode, E> addFieldDeclaration(BiConsumer<FieldDeclaration, E> bc) {
		return consumermap.put(VisitorEnum.FieldDeclaration, bc);
	}

	public BiConsumer<? extends ASTNode, E> addForStatement(BiConsumer<ForStatement, E> bc) {
		return consumermap.put(VisitorEnum.ForStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addIfStatement(BiConsumer<IfStatement, E> bc) {
		return consumermap.put(VisitorEnum.IfStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addImportDeclaration(BiConsumer<ImportDeclaration, E> bc) {
		return consumermap.put(VisitorEnum.ImportDeclaration, bc);
	}

	public BiConsumer<? extends ASTNode, E> addInfixExpression(BiConsumer<InfixExpression, E> bc) {
		return consumermap.put(VisitorEnum.InfixExpression, bc);
	}

	public BiConsumer<? extends ASTNode, E> addInitializer(BiConsumer<Initializer, E> bc) {
		return consumermap.put(VisitorEnum.Initializer, bc);
	}

	public BiConsumer<? extends ASTNode, E> addInstanceofExpression(BiConsumer<InstanceofExpression, E> bc) {
		return consumermap.put(VisitorEnum.InstanceofExpression, bc);
	}

	public BiConsumer<? extends ASTNode, E> addIntersectionType(BiConsumer<IntersectionType, E> bc) {
		return consumermap.put(VisitorEnum.IntersectionType, bc);
	}

	public BiConsumer<? extends ASTNode, E> addJavadoc(BiConsumer<Javadoc, E> bc) {
		return consumermap.put(VisitorEnum.Javadoc, bc);
	}

	public BiConsumer<? extends ASTNode, E> addLabeledStatement(BiConsumer<LabeledStatement, E> bc) {
		return consumermap.put(VisitorEnum.LabeledStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addLambdaExpression(BiConsumer<LambdaExpression, E> bc) {
		return consumermap.put(VisitorEnum.LambdaExpression, bc);
	}

	public BiConsumer<? extends ASTNode, E> addLineComment(BiConsumer<LineComment, E> bc) {
		return consumermap.put(VisitorEnum.LineComment, bc);
	}

	public BiConsumer<? extends ASTNode, E> addMarkerAnnotation(BiConsumer<MarkerAnnotation, E> bc) {
		return consumermap.put(VisitorEnum.MarkerAnnotation, bc);
	}

	public BiConsumer<? extends ASTNode, E> addMemberRef(BiConsumer<MemberRef, E> bc) {
		return consumermap.put(VisitorEnum.MemberRef, bc);
	}

	public BiConsumer<? extends ASTNode, E> addMemberValuePair(BiConsumer<MemberValuePair, E> bc) {
		return consumermap.put(VisitorEnum.MemberValuePair, bc);
	}

	public BiConsumer<? extends ASTNode, E> addMethodRef(BiConsumer<MethodRef, E> bc) {
		return consumermap.put(VisitorEnum.MethodRef, bc);
	}

	public BiConsumer<? extends ASTNode, E> addMethodRefParameter(BiConsumer<MethodRefParameter, E> bc) {
		return consumermap.put(VisitorEnum.MethodRefParameter, bc);
	}

	public BiConsumer<? extends ASTNode, E> addMethodDeclaration(BiConsumer<MethodDeclaration, E> bc) {
		return consumermap.put(VisitorEnum.MethodDeclaration, bc);
	}

	public BiConsumer<? extends ASTNode, E> addMethodInvocation(BiConsumer<MethodInvocation, E> bc) {
		return consumermap.put(VisitorEnum.MethodInvocation, bc);
	}

	public BiConsumer<? extends ASTNode, E> addMethodInvocation(String methodname, BiConsumer<MethodInvocation, E> bc) {
		this.consumerdata.put(VisitorEnum.MethodInvocation, methodname);
		return consumermap.put(VisitorEnum.MethodInvocation, bc);
	}

	public BiConsumer<? extends ASTNode, E> addModifier(BiConsumer<Modifier, E> bc) {
		return consumermap.put(VisitorEnum.Modifier, bc);
	}

	public BiConsumer<? extends ASTNode, E> addModuleDeclaration(BiConsumer<ModuleDeclaration, E> bc) {
		return consumermap.put(VisitorEnum.ModuleDeclaration, bc);
	}

	public BiConsumer<? extends ASTNode, E> addModuleModifier(BiConsumer<ModuleModifier, E> bc) {
		return consumermap.put(VisitorEnum.ModuleModifier, bc);
	}

	public BiConsumer<? extends ASTNode, E> addNameQualifiedType(BiConsumer<NameQualifiedType, E> bc) {
		return consumermap.put(VisitorEnum.NameQualifiedType, bc);
	}

	public BiConsumer<? extends ASTNode, E> addNormalAnnotation(BiConsumer<NormalAnnotation, E> bc) {
		return consumermap.put(VisitorEnum.NormalAnnotation, bc);
	}

	public BiConsumer<? extends ASTNode, E> addNullLiteral(BiConsumer<NullLiteral, E> bc) {
		return consumermap.put(VisitorEnum.NullLiteral, bc);
	}

	public BiConsumer<? extends ASTNode, E> addNumberLiteral(BiConsumer<NumberLiteral, E> bc) {
		return consumermap.put(VisitorEnum.NumberLiteral, bc);
	}

	public BiConsumer<? extends ASTNode, E> addOpensDirective(BiConsumer<OpensDirective, E> bc) {
		return consumermap.put(VisitorEnum.OpensDirective, bc);
	}

	public BiConsumer<? extends ASTNode, E> addPackageDeclaration(BiConsumer<PackageDeclaration, E> bc) {
		return consumermap.put(VisitorEnum.PackageDeclaration, bc);
	}

	public BiConsumer<? extends ASTNode, E> addParameterizedType(BiConsumer<ParameterizedType, E> bc) {
		return consumermap.put(VisitorEnum.ParameterizedType, bc);
	}

	public BiConsumer<? extends ASTNode, E> addParenthesizedExpression(BiConsumer<ParenthesizedExpression, E> bc) {
		return consumermap.put(VisitorEnum.ParenthesizedExpression, bc);
	}

	public BiConsumer<? extends ASTNode, E> addPatternInstanceofExpression(
			BiConsumer<PatternInstanceofExpression, E> bc) {
		return consumermap.put(VisitorEnum.PatternInstanceofExpression, bc);
	}

	public BiConsumer<? extends ASTNode, E> addPostfixExpression(BiConsumer<PostfixExpression, E> bc) {
		return consumermap.put(VisitorEnum.PostfixExpression, bc);
	}

	public BiConsumer<? extends ASTNode, E> addPrefixExpression(BiConsumer<PrefixExpression, E> bc) {
		return consumermap.put(VisitorEnum.PrefixExpression, bc);
	}

	public BiConsumer<? extends ASTNode, E> addProvidesDirective(BiConsumer<ProvidesDirective, E> bc) {
		return consumermap.put(VisitorEnum.ProvidesDirective, bc);
	}

	public BiConsumer<? extends ASTNode, E> addPrimitiveType(BiConsumer<PrimitiveType, E> bc) {
		return consumermap.put(VisitorEnum.PrimitiveType, bc);
	}

	public BiConsumer<? extends ASTNode, E> addQualifiedName(BiConsumer<QualifiedName, E> bc) {
		return consumermap.put(VisitorEnum.QualifiedName, bc);
	}

	public BiConsumer<? extends ASTNode, E> addQualifiedType(BiConsumer<QualifiedType, E> bc) {
		return consumermap.put(VisitorEnum.QualifiedType, bc);
	}

//	public BiConsumer<? extends ASTNode, E> addModuleQualifiedName(BiConsumer<ModuleQualifiedName, E> bc) {
//		return consumermap.put(VisitorEnum.ModuleQualifiedName, bc);
//	}

	public BiConsumer<? extends ASTNode, E> addRequiresDirective(BiConsumer<RequiresDirective, E> bc) {
		return consumermap.put(VisitorEnum.RequiresDirective, bc);
	}

	public BiConsumer<? extends ASTNode, E> addRecordDeclaration(BiConsumer<RecordDeclaration, E> bc) {
		return consumermap.put(VisitorEnum.RecordDeclaration, bc);
	}

	public BiConsumer<? extends ASTNode, E> addReturnStatement(BiConsumer<ReturnStatement, E> bc) {
		return consumermap.put(VisitorEnum.ReturnStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addSimpleName(BiConsumer<SimpleName, E> bc) {
		return consumermap.put(VisitorEnum.SimpleName, bc);
	}

	public BiConsumer<? extends ASTNode, E> addSimpleType(BiConsumer<SimpleType, E> bc) {
		return consumermap.put(VisitorEnum.SimpleType, bc);
	}

	public BiConsumer<? extends ASTNode, E> addSingleMemberAnnotation(BiConsumer<SingleMemberAnnotation, E> bc) {
		return consumermap.put(VisitorEnum.SingleMemberAnnotation, bc);
	}

	public BiConsumer<? extends ASTNode, E> addSingleVariableDeclaration(BiConsumer<SingleVariableDeclaration, E> bc) {
		return consumermap.put(VisitorEnum.SingleVariableDeclaration, bc);
	}

	public BiConsumer<? extends ASTNode, E> addStringLiteral(BiConsumer<StringLiteral, E> bc) {
		return consumermap.put(VisitorEnum.StringLiteral, bc);
	}

	public BiConsumer<? extends ASTNode, E> addSuperConstructorInvocation(
			BiConsumer<SuperConstructorInvocation, E> bc) {
		return consumermap.put(VisitorEnum.SuperConstructorInvocation, bc);
	}

	public BiConsumer<? extends ASTNode, E> addSuperFieldAccess(BiConsumer<SuperFieldAccess, E> bc) {
		return consumermap.put(VisitorEnum.SuperFieldAccess, bc);
	}

	public BiConsumer<? extends ASTNode, E> addSuperMethodInvocation(BiConsumer<SuperMethodInvocation, E> bc) {
		return consumermap.put(VisitorEnum.SuperMethodInvocation, bc);
	}

	public BiConsumer<? extends ASTNode, E> addSuperMethodReference(BiConsumer<SuperMethodReference, E> bc) {
		return consumermap.put(VisitorEnum.SuperMethodReference, bc);
	}

	public BiConsumer<? extends ASTNode, E> addSwitchCase(BiConsumer<SwitchCase, E> bc) {
		return consumermap.put(VisitorEnum.SwitchCase, bc);
	}

	public BiConsumer<? extends ASTNode, E> addSwitchExpression(BiConsumer<SwitchExpression, E> bc) {
		return consumermap.put(VisitorEnum.SwitchExpression, bc);
	}

	public BiConsumer<? extends ASTNode, E> addSwitchStatement(BiConsumer<SwitchStatement, E> bc) {
		return consumermap.put(VisitorEnum.SwitchStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addSynchronizedStatement(BiConsumer<SynchronizedStatement, E> bc) {
		return consumermap.put(VisitorEnum.SynchronizedStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addTagElement(BiConsumer<TagElement, E> bc) {
		return consumermap.put(VisitorEnum.TagElement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addTextBlock(BiConsumer<TextBlock, E> bc) {
		return consumermap.put(VisitorEnum.TextBlock, bc);
	}

	public BiConsumer<? extends ASTNode, E> addTextElement(BiConsumer<TextElement, E> bc) {
		return consumermap.put(VisitorEnum.TextElement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addThisExpression(BiConsumer<ThisExpression, E> bc) {
		return consumermap.put(VisitorEnum.ThisExpression, bc);
	}

	public BiConsumer<? extends ASTNode, E> addThrowStatement(BiConsumer<ThrowStatement, E> bc) {
		return consumermap.put(VisitorEnum.ThrowStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addTryStatement(BiConsumer<TryStatement, E> bc) {
		return consumermap.put(VisitorEnum.TryStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addTypeDeclaration(BiConsumer<TypeDeclaration, E> bc) {
		return consumermap.put(VisitorEnum.TypeDeclaration, bc);
	}

	public BiConsumer<? extends ASTNode, E> addTypeDeclarationStatement(BiConsumer<TypeDeclarationStatement, E> bc) {
		return consumermap.put(VisitorEnum.TypeDeclarationStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addTypeLiteral(BiConsumer<TypeLiteral, E> bc) {
		return consumermap.put(VisitorEnum.TypeLiteral, bc);
	}

	public BiConsumer<? extends ASTNode, E> addTypeMethodReference(BiConsumer<TypeMethodReference, E> bc) {
		return consumermap.put(VisitorEnum.TypeMethodReference, bc);
	}

	public BiConsumer<? extends ASTNode, E> addTypeParameter(BiConsumer<TypeParameter, E> bc) {
		return consumermap.put(VisitorEnum.TypeParameter, bc);
	}

	public BiConsumer<? extends ASTNode, E> addUnionType(BiConsumer<UnionType, E> bc) {
		return consumermap.put(VisitorEnum.UnionType, bc);
	}

	public BiConsumer<? extends ASTNode, E> addUsesDirective(BiConsumer<UsesDirective, E> bc) {
		return consumermap.put(VisitorEnum.UsesDirective, bc);
	}

	public BiConsumer<? extends ASTNode, E> addVariableDeclarationExpression(
			BiConsumer<VariableDeclarationExpression, E> bc) {
		return consumermap.put(VisitorEnum.VariableDeclarationExpression, bc);
	}

	public BiConsumer<? extends ASTNode, E> addVariableDeclarationStatement(
			BiConsumer<VariableDeclarationStatement, E> bc) {
		return consumermap.put(VisitorEnum.VariableDeclarationStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addVariableDeclarationFragment(
			BiConsumer<VariableDeclarationFragment, E> bc) {
		return consumermap.put(VisitorEnum.VariableDeclarationFragment, bc);
	}

	public BiConsumer<? extends ASTNode, E> addWhileStatement(BiConsumer<WhileStatement, E> bc) {
		return consumermap.put(VisitorEnum.WhileStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> addWildcardType(BiConsumer<WildcardType, E> bc) {
		return consumermap.put(VisitorEnum.WildcardType, bc);
	}

	public BiConsumer<? extends ASTNode, E> addYieldStatement(BiConsumer<YieldStatement, E> bc) {
		return consumermap.put(VisitorEnum.YieldStatement, bc);
	}

	public BiConsumer<? extends ASTNode, E> add(VisitorEnum key, BiConsumer<ASTNode, E> bc) {
		return consumermap.put(key, bc);
	}

	public void addAnnotationTypeDeclaration(BiPredicate<AnnotationTypeDeclaration, E> bs,
			BiConsumer<AnnotationTypeDeclaration, E> bc) {
		predicatemap.put(VisitorEnum.AnnotationTypeDeclaration, bs);
		consumermap.put(VisitorEnum.AnnotationTypeDeclaration, bc);
	}

	public void addAnnotationTypeMemberDeclaration(BiPredicate<AnnotationTypeMemberDeclaration, E> bs,
			BiConsumer<AnnotationTypeMemberDeclaration, E> bc) {
		predicatemap.put(VisitorEnum.AnnotationTypeMemberDeclaration, bs);
		consumermap.put(VisitorEnum.AnnotationTypeMemberDeclaration, bc);
	}

	public void addAnonymousClassDeclaration(BiPredicate<AnonymousClassDeclaration, E> bs,
			BiConsumer<AnonymousClassDeclaration, E> bc) {
		predicatemap.put(VisitorEnum.AnonymousClassDeclaration, bs);
		consumermap.put(VisitorEnum.AnonymousClassDeclaration, bc);
	}

	public void addArrayAccess(BiPredicate<ArrayAccess, E> bs, BiConsumer<ArrayAccess, E> bc) {
		predicatemap.put(VisitorEnum.ArrayAccess, bs);
		consumermap.put(VisitorEnum.ArrayAccess, bc);
	}

	public void addArrayCreation(BiPredicate<ArrayCreation, E> bs, BiConsumer<ArrayCreation, E> bc) {
		predicatemap.put(VisitorEnum.ArrayCreation, bs);
		consumermap.put(VisitorEnum.ArrayCreation, bc);
	}

	public void addArrayInitializer(BiPredicate<ArrayInitializer, E> bs, BiConsumer<ArrayInitializer, E> bc) {
		predicatemap.put(VisitorEnum.ArrayInitializer, bs);
		consumermap.put(VisitorEnum.ArrayInitializer, bc);
	}

	public void addArrayType(BiPredicate<ArrayType, E> bs, BiConsumer<ArrayType, E> bc) {
		predicatemap.put(VisitorEnum.ArrayType, bs);
		consumermap.put(VisitorEnum.ArrayType, bc);
	}

	public void addAssertStatement(BiPredicate<AssertStatement, E> bs, BiConsumer<AssertStatement, E> bc) {
		predicatemap.put(VisitorEnum.AssertStatement, bs);
		consumermap.put(VisitorEnum.AssertStatement, bc);
	}

	public void addAssignment(BiPredicate<Assignment, E> bs, BiConsumer<Assignment, E> bc) {
		predicatemap.put(VisitorEnum.Assignment, bs);
		consumermap.put(VisitorEnum.Assignment, bc);
	}

	public void addBlock(BiPredicate<Block, E> bs, BiConsumer<Block, E> bc) {
		predicatemap.put(VisitorEnum.Block, bs);
		consumermap.put(VisitorEnum.Block, bc);
	}

	public void addBlockComment(BiPredicate<BlockComment, E> bs, BiConsumer<BlockComment, E> bc) {
		predicatemap.put(VisitorEnum.BlockComment, bs);
		consumermap.put(VisitorEnum.BlockComment, bc);
	}

	public void addBooleanLiteral(BiPredicate<BooleanLiteral, E> bs, BiConsumer<BooleanLiteral, E> bc) {
		predicatemap.put(VisitorEnum.BooleanLiteral, bs);
		consumermap.put(VisitorEnum.BooleanLiteral, bc);
	}

	public void addBreakStatement(BiPredicate<BreakStatement, E> bs, BiConsumer<BreakStatement, E> bc) {
		predicatemap.put(VisitorEnum.BreakStatement, bs);
		consumermap.put(VisitorEnum.BreakStatement, bc);
	}

	public void addCastExpression(BiPredicate<CastExpression, E> bs, BiConsumer<CastExpression, E> bc) {
		predicatemap.put(VisitorEnum.CastExpression, bs);
		consumermap.put(VisitorEnum.CastExpression, bc);
	}

	public void addCatchClause(BiPredicate<CatchClause, E> bs, BiConsumer<CatchClause, E> bc) {
		predicatemap.put(VisitorEnum.CatchClause, bs);
		consumermap.put(VisitorEnum.CatchClause, bc);
	}

	public void addCharacterLiteral(BiPredicate<CharacterLiteral, E> bs, BiConsumer<CharacterLiteral, E> bc) {
		predicatemap.put(VisitorEnum.CharacterLiteral, bs);
		consumermap.put(VisitorEnum.CharacterLiteral, bc);
	}

	public void addClassInstanceCreation(BiPredicate<ClassInstanceCreation, E> bs,
			BiConsumer<ClassInstanceCreation, E> bc) {
		predicatemap.put(VisitorEnum.ClassInstanceCreation, bs);
		consumermap.put(VisitorEnum.ClassInstanceCreation, bc);
	}

	public void addCompilationUnit(BiPredicate<CompilationUnit, E> bs, BiConsumer<CompilationUnit, E> bc) {
		predicatemap.put(VisitorEnum.CompilationUnit, bs);
		consumermap.put(VisitorEnum.CompilationUnit, bc);
	}

	public void addConditionalExpression(BiPredicate<ConditionalExpression, E> bs,
			BiConsumer<ConditionalExpression, E> bc) {
		predicatemap.put(VisitorEnum.ConditionalExpression, bs);
		consumermap.put(VisitorEnum.ConditionalExpression, bc);
	}

	public void addConstructorInvocation(BiPredicate<ConstructorInvocation, E> bs,
			BiConsumer<ConstructorInvocation, E> bc) {
		predicatemap.put(VisitorEnum.ConstructorInvocation, bs);
		consumermap.put(VisitorEnum.ConstructorInvocation, bc);
	}

	public void addContinueStatement(BiPredicate<ContinueStatement, E> bs,
			BiConsumer<ContinueStatement, E> bc) {
		predicatemap.put(VisitorEnum.ContinueStatement, bs);
		consumermap.put(VisitorEnum.ContinueStatement, bc);
	}

	public void addCreationReference(BiPredicate<CreationReference, E> bs,
			BiConsumer<CreationReference, E> bc) {
		predicatemap.put(VisitorEnum.CreationReference, bs);
		consumermap.put(VisitorEnum.CreationReference, bc);
	}

	public void addDimension(BiPredicate<Dimension, E> bs, BiConsumer<Dimension, E> bc) {
		predicatemap.put(VisitorEnum.Dimension, bs);
		consumermap.put(VisitorEnum.Dimension, bc);
	}

	public void addDoStatement(BiPredicate<DoStatement, E> bs, BiConsumer<DoStatement, E> bc) {
		predicatemap.put(VisitorEnum.DoStatement, bs);
		consumermap.put(VisitorEnum.DoStatement, bc);
	}

	public void addEmptyStatement(BiPredicate<EmptyStatement, E> bs, BiConsumer<EmptyStatement, E> bc) {
		predicatemap.put(VisitorEnum.EmptyStatement, bs);
		consumermap.put(VisitorEnum.EmptyStatement, bc);
	}

	public void addEnhancedForStatement(BiPredicate<EnhancedForStatement, E> bs,
			BiConsumer<EnhancedForStatement, E> bc) {
		predicatemap.put(VisitorEnum.EnhancedForStatement, bs);
		consumermap.put(VisitorEnum.EnhancedForStatement, bc);
	}

	public void addEnumConstantDeclaration(BiPredicate<EnumConstantDeclaration, E> bs,
			BiConsumer<EnumConstantDeclaration, E> bc) {
		predicatemap.put(VisitorEnum.EnumConstantDeclaration, bs);
		consumermap.put(VisitorEnum.EnumConstantDeclaration, bc);
	}

	public void addEnumDeclaration(BiPredicate<EnumDeclaration, E> bs, BiConsumer<EnumDeclaration, E> bc) {
		predicatemap.put(VisitorEnum.EnumDeclaration, bs);
		consumermap.put(VisitorEnum.EnumDeclaration, bc);
	}

	public void addExportsDirective(BiPredicate<ExportsDirective, E> bs, BiConsumer<ExportsDirective, E> bc) {
		predicatemap.put(VisitorEnum.ExportsDirective, bs);
		consumermap.put(VisitorEnum.ExportsDirective, bc);
	}

	public void addExpressionMethodReference(BiPredicate<ExpressionMethodReference, E> bs,
			BiConsumer<ExpressionMethodReference, E> bc) {
		predicatemap.put(VisitorEnum.ExpressionMethodReference, bs);
		consumermap.put(VisitorEnum.ExpressionMethodReference, bc);
	}

	public void addExpressionStatement(BiPredicate<ExpressionStatement, E> bs,
			BiConsumer<ExpressionStatement, E> bc) {
		predicatemap.put(VisitorEnum.ExpressionStatement, bs);
		consumermap.put(VisitorEnum.ExpressionStatement, bc);
	}

	public void addFieldAccess(BiPredicate<FieldAccess, E> bs, BiConsumer<FieldAccess, E> bc) {
		predicatemap.put(VisitorEnum.FieldAccess, bs);
		consumermap.put(VisitorEnum.FieldAccess, bc);
	}

	public void addFieldDeclaration(BiPredicate<FieldDeclaration, E> bs, BiConsumer<FieldDeclaration, E> bc) {
		predicatemap.put(VisitorEnum.FieldDeclaration, bs);
		consumermap.put(VisitorEnum.FieldDeclaration, bc);
	}

	public void addForStatement(BiPredicate<ForStatement, E> bs, BiConsumer<ForStatement, E> bc) {
		predicatemap.put(VisitorEnum.ForStatement, bs);
		consumermap.put(VisitorEnum.ForStatement, bc);
	}

	public void addIfStatement(BiPredicate<IfStatement, E> bs, BiConsumer<IfStatement, E> bc) {
		predicatemap.put(VisitorEnum.IfStatement, bs);
		consumermap.put(VisitorEnum.IfStatement, bc);
	}

	public void addImportDeclaration(BiPredicate<ImportDeclaration, E> bs,
			BiConsumer<ImportDeclaration, E> bc) {
		predicatemap.put(VisitorEnum.ImportDeclaration, bs);
		consumermap.put(VisitorEnum.ImportDeclaration, bc);
	}

	public void addInfixExpression(BiPredicate<InfixExpression, E> bs, BiConsumer<InfixExpression, E> bc) {
		predicatemap.put(VisitorEnum.InfixExpression, bs);
		consumermap.put(VisitorEnum.InfixExpression, bc);
	}

	public void addInitializer(BiPredicate<Initializer, E> bs, BiConsumer<Initializer, E> bc) {
		predicatemap.put(VisitorEnum.Initializer, bs);
		consumermap.put(VisitorEnum.Initializer, bc);
	}

	public void addInstanceofExpression(BiPredicate<InstanceofExpression, E> bs,
			BiConsumer<InstanceofExpression, E> bc) {
		predicatemap.put(VisitorEnum.InstanceofExpression, bs);
		consumermap.put(VisitorEnum.InstanceofExpression, bc);
	}

	public void addIntersectionType(BiPredicate<IntersectionType, E> bs, BiConsumer<IntersectionType, E> bc) {
		predicatemap.put(VisitorEnum.IntersectionType, bs);
		consumermap.put(VisitorEnum.IntersectionType, bc);
	}

	public void addJavadoc(BiPredicate<Javadoc, E> bs, BiConsumer<Javadoc, E> bc) {
		predicatemap.put(VisitorEnum.Javadoc, bs);
		consumermap.put(VisitorEnum.Javadoc, bc);
	}

	public void addLabeledStatement(BiPredicate<LabeledStatement, E> bs, BiConsumer<LabeledStatement, E> bc) {
		predicatemap.put(VisitorEnum.LabeledStatement, bs);
		consumermap.put(VisitorEnum.LabeledStatement, bc);
	}

	public void addLambdaExpression(BiPredicate<LambdaExpression, E> bs, BiConsumer<LambdaExpression, E> bc) {
		predicatemap.put(VisitorEnum.LambdaExpression, bs);
		consumermap.put(VisitorEnum.LambdaExpression, bc);
	}

	public void addLineComment(BiPredicate<LineComment, E> bs, BiConsumer<LineComment, E> bc) {
		predicatemap.put(VisitorEnum.LineComment, bs);
		consumermap.put(VisitorEnum.LineComment, bc);
	}

	public void addMarkerAnnotation(BiPredicate<MarkerAnnotation, E> bs, BiConsumer<MarkerAnnotation, E> bc) {
		predicatemap.put(VisitorEnum.MarkerAnnotation, bs);
		consumermap.put(VisitorEnum.MarkerAnnotation, bc);
	}

	public void addMemberRef(BiPredicate<MemberRef, E> bs, BiConsumer<MemberRef, E> bc) {
		predicatemap.put(VisitorEnum.MemberRef, bs);
		consumermap.put(VisitorEnum.MemberRef, bc);
	}

	public void addMemberValuePair(BiPredicate<MemberValuePair, E> bs, BiConsumer<MemberValuePair, E> bc) {
		predicatemap.put(VisitorEnum.MemberValuePair, bs);
		consumermap.put(VisitorEnum.MemberValuePair, bc);
	}

	public void addMethodRef(BiPredicate<MethodRef, E> bs, BiConsumer<MethodRef, E> bc) {
		predicatemap.put(VisitorEnum.MethodRef, bs);
		consumermap.put(VisitorEnum.MethodRef, bc);
	}

	public void addMethodRefParameter(BiPredicate<MethodRefParameter, E> bs,
			BiConsumer<MethodRefParameter, E> bc) {
		predicatemap.put(VisitorEnum.MethodRefParameter, bs);
		consumermap.put(VisitorEnum.MethodRefParameter, bc);
	}

	public void addMethodDeclaration(BiPredicate<MethodDeclaration, E> bs,
			BiConsumer<MethodDeclaration, E> bc) {
		predicatemap.put(VisitorEnum.MethodDeclaration, bs);
		consumermap.put(VisitorEnum.MethodDeclaration, bc);
	}
	
	public void addMethodInvocation(String methodname, BiPredicate<MethodInvocation, E> bs, BiConsumer<MethodInvocation, E> bc) {
		this.predicatedata.put(VisitorEnum.MethodInvocation, methodname);
		predicatemap.put(VisitorEnum.MethodInvocation, bs);
		consumermap.put(VisitorEnum.MethodInvocation, bc);
	}
	
	public void addMethodInvocation(BiPredicate<MethodInvocation, E> bs, BiConsumer<MethodInvocation, E> bc) {
		predicatemap.put(VisitorEnum.MethodInvocation, bs);
		consumermap.put(VisitorEnum.MethodInvocation, bc);
	}

	public void addModifier(BiPredicate<Modifier, E> bs, BiConsumer<Modifier, E> bc) {
		predicatemap.put(VisitorEnum.Modifier, bs);
		consumermap.put(VisitorEnum.Modifier, bc);
	}

	public void addModuleDeclaration(BiPredicate<ModuleDeclaration, E> bs,
			BiConsumer<ModuleDeclaration, E> bc) {
		predicatemap.put(VisitorEnum.ModuleDeclaration, bs);
		consumermap.put(VisitorEnum.ModuleDeclaration, bc);
	}

	public void addModuleModifier(BiPredicate<ModuleModifier, E> bs, BiConsumer<ModuleModifier, E> bc) {
		predicatemap.put(VisitorEnum.ModuleModifier, bs);
		consumermap.put(VisitorEnum.ModuleModifier, bc);
	}

	public void addNameQualifiedType(BiPredicate<NameQualifiedType, E> bs,
			BiConsumer<NameQualifiedType, E> bc) {
		predicatemap.put(VisitorEnum.NameQualifiedType, bs);
		consumermap.put(VisitorEnum.NameQualifiedType, bc);
	}

	public void addNormalAnnotation(BiPredicate<NormalAnnotation, E> bs, BiConsumer<NormalAnnotation, E> bc) {
		predicatemap.put(VisitorEnum.NormalAnnotation, bs);
		consumermap.put(VisitorEnum.NormalAnnotation, bc);
	}

	public void addNullLiteral(BiPredicate<NullLiteral, E> bs, BiConsumer<NullLiteral, E> bc) {
		predicatemap.put(VisitorEnum.NullLiteral, bs);
		consumermap.put(VisitorEnum.NullLiteral, bc);
	}

	public void addNumberLiteral(BiPredicate<NumberLiteral, E> bs, BiConsumer<NumberLiteral, E> bc) {
		predicatemap.put(VisitorEnum.NumberLiteral, bs);
		consumermap.put(VisitorEnum.NumberLiteral, bc);
	}

	public void addOpensDirective(BiPredicate<OpensDirective, E> bs, BiConsumer<OpensDirective, E> bc) {
		predicatemap.put(VisitorEnum.OpensDirective, bs);
		consumermap.put(VisitorEnum.OpensDirective, bc);
	}

	public void addPackageDeclaration(BiPredicate<PackageDeclaration, E> bs,
			BiConsumer<PackageDeclaration, E> bc) {
		predicatemap.put(VisitorEnum.PackageDeclaration, bs);
		consumermap.put(VisitorEnum.PackageDeclaration, bc);
	}

	public void addParameterizedType(BiPredicate<ParameterizedType, E> bs,
			BiConsumer<ParameterizedType, E> bc) {
		predicatemap.put(VisitorEnum.ParameterizedType, bs);
		consumermap.put(VisitorEnum.ParameterizedType, bc);
	}

	public void addParenthesizedExpression(BiPredicate<ParenthesizedExpression, E> bs,
			BiConsumer<ParenthesizedExpression, E> bc) {
		predicatemap.put(VisitorEnum.ParenthesizedExpression, bs);
		consumermap.put(VisitorEnum.ParenthesizedExpression, bc);
	}

	public void addPatternInstanceofExpression(BiPredicate<PatternInstanceofExpression, E> bs,
			BiConsumer<PatternInstanceofExpression, E> bc) {
		predicatemap.put(VisitorEnum.PatternInstanceofExpression, bs);
		consumermap.put(VisitorEnum.PatternInstanceofExpression, bc);
	}

	public void addPostfixExpression(BiPredicate<PostfixExpression, E> bs,
			BiConsumer<PostfixExpression, E> bc) {
		predicatemap.put(VisitorEnum.PostfixExpression, bs);
		consumermap.put(VisitorEnum.PostfixExpression, bc);
	}

	public void addPrefixExpression(BiPredicate<PrefixExpression, E> bs, BiConsumer<PrefixExpression, E> bc) {
		predicatemap.put(VisitorEnum.PrefixExpression, bs);
		consumermap.put(VisitorEnum.PrefixExpression, bc);
	}

	public void addProvidesDirective(BiPredicate<ProvidesDirective, E> bs,
			BiConsumer<ProvidesDirective, E> bc) {
		predicatemap.put(VisitorEnum.ProvidesDirective, bs);
		consumermap.put(VisitorEnum.ProvidesDirective, bc);
	}

	public void addPrimitiveType(BiPredicate<PrimitiveType, E> bs, BiConsumer<PrimitiveType, E> bc) {
		predicatemap.put(VisitorEnum.PrimitiveType, bs);
		consumermap.put(VisitorEnum.PrimitiveType, bc);
	}

	public void addQualifiedName(BiPredicate<QualifiedName, E> bs, BiConsumer<QualifiedName, E> bc) {
		predicatemap.put(VisitorEnum.QualifiedName, bs);
		consumermap.put(VisitorEnum.QualifiedName, bc);
	}

	public void addQualifiedType(BiPredicate<QualifiedType, E> bs, BiConsumer<QualifiedType, E> bc) {
		predicatemap.put(VisitorEnum.QualifiedType, bs);
		consumermap.put(VisitorEnum.QualifiedType, bc);
	}

//	public void addModuleQualifiedName(BiPredicate<ModuleQualifiedName, E> bs,
//			BiConsumer<ModuleQualifiedName, E> bc) {
//		suppliermap.put(VisitorEnum.ModuleQualifiedName, bs);
//		consumermap.put(VisitorEnum.ModuleQualifiedName, bc);
//	}

	public void addRequiresDirective(BiPredicate<RequiresDirective, E> bs,
			BiConsumer<RequiresDirective, E> bc) {
		predicatemap.put(VisitorEnum.RequiresDirective, bs);
		consumermap.put(VisitorEnum.RequiresDirective, bc);
	}

	public void addRecordDeclaration(BiPredicate<RecordDeclaration, E> bs,
			BiConsumer<RecordDeclaration, E> bc) {
		predicatemap.put(VisitorEnum.RecordDeclaration, bs);
		consumermap.put(VisitorEnum.RecordDeclaration, bc);
	}

	public void addReturnStatement(BiPredicate<ReturnStatement, E> bs, BiConsumer<ReturnStatement, E> bc) {
		predicatemap.put(VisitorEnum.ReturnStatement, bs);
		consumermap.put(VisitorEnum.ReturnStatement, bc);
	}

	public void addSimpleName(BiPredicate<SimpleName, E> bs, BiConsumer<SimpleName, E> bc) {
		predicatemap.put(VisitorEnum.SimpleName, bs);
		consumermap.put(VisitorEnum.SimpleName, bc);
	}

	public void addSimpleType(BiPredicate<SimpleType, E> bs, BiConsumer<SimpleType, E> bc) {
		predicatemap.put(VisitorEnum.SimpleType, bs);
		consumermap.put(VisitorEnum.SimpleType, bc);
	}

	public void addSingleMemberAnnotation(BiPredicate<SingleMemberAnnotation, E> bs,
			BiConsumer<SingleMemberAnnotation, E> bc) {
		predicatemap.put(VisitorEnum.SingleMemberAnnotation, bs);
		consumermap.put(VisitorEnum.SingleMemberAnnotation, bc);
	}

	public void addSingleVariableDeclaration(BiPredicate<SingleVariableDeclaration, E> bs,
			BiConsumer<SingleVariableDeclaration, E> bc) {
		predicatemap.put(VisitorEnum.SingleVariableDeclaration, bs);
		consumermap.put(VisitorEnum.SingleVariableDeclaration, bc);
	}

	public void addStringLiteral(BiPredicate<StringLiteral, E> bs, BiConsumer<StringLiteral, E> bc) {
		predicatemap.put(VisitorEnum.StringLiteral, bs);
		consumermap.put(VisitorEnum.StringLiteral, bc);
	}

	public void addSuperConstructorInvocation(BiPredicate<SuperConstructorInvocation, E> bs,
			BiConsumer<SuperConstructorInvocation, E> bc) {
		predicatemap.put(VisitorEnum.SuperConstructorInvocation, bs);
		consumermap.put(VisitorEnum.SuperConstructorInvocation, bc);
	}

	public void addSuperFieldAccess(BiPredicate<SuperFieldAccess, E> bs, BiConsumer<SuperFieldAccess, E> bc) {
		predicatemap.put(VisitorEnum.SuperFieldAccess, bs);
		consumermap.put(VisitorEnum.SuperFieldAccess, bc);
	}

	public void addSuperMethodInvocation(BiPredicate<SuperMethodInvocation, E> bs,
			BiConsumer<SuperMethodInvocation, E> bc) {
		predicatemap.put(VisitorEnum.SuperMethodInvocation, bs);
		consumermap.put(VisitorEnum.SuperMethodInvocation, bc);
	}

	public void addSuperMethodReference(BiPredicate<SuperMethodReference, E> bs,
			BiConsumer<SuperMethodReference, E> bc) {
		predicatemap.put(VisitorEnum.SuperMethodReference, bs);
		consumermap.put(VisitorEnum.SuperMethodReference, bc);
	}

	public void addSwitchCase(BiPredicate<SwitchCase, E> bs, BiConsumer<SwitchCase, E> bc) {
		predicatemap.put(VisitorEnum.SwitchCase, bs);
		consumermap.put(VisitorEnum.SwitchCase, bc);
	}

	public void addSwitchExpression(BiPredicate<SwitchExpression, E> bs, BiConsumer<SwitchExpression, E> bc) {
		predicatemap.put(VisitorEnum.SwitchExpression, bs);
		consumermap.put(VisitorEnum.SwitchExpression, bc);
	}

	public void addSwitchStatement(BiPredicate<SwitchStatement, E> bs, BiConsumer<SwitchStatement, E> bc) {
		predicatemap.put(VisitorEnum.SwitchStatement, bs);
		consumermap.put(VisitorEnum.SwitchStatement, bc);
	}

	public void addSynchronizedStatement(BiPredicate<SynchronizedStatement, E> bs,
			BiConsumer<SynchronizedStatement, E> bc) {
		predicatemap.put(VisitorEnum.SynchronizedStatement, bs);
		consumermap.put(VisitorEnum.SynchronizedStatement, bc);
	}

	public void addTagElement(BiPredicate<TagElement, E> bs, BiConsumer<TagElement, E> bc) {
		predicatemap.put(VisitorEnum.TagElement, bs);
		consumermap.put(VisitorEnum.TagElement, bc);
	}

	public void addTextBlock(BiPredicate<TextBlock, E> bs, BiConsumer<TextBlock, E> bc) {
		predicatemap.put(VisitorEnum.TextBlock, bs);
		consumermap.put(VisitorEnum.TextBlock, bc);
	}

	public void addTextElement(BiPredicate<TextElement, E> bs, BiConsumer<TextElement, E> bc) {
		predicatemap.put(VisitorEnum.TextElement, bs);
		consumermap.put(VisitorEnum.TextElement, bc);
	}

	public void addThisExpression(BiPredicate<ThisExpression, E> bs, BiConsumer<ThisExpression, E> bc) {
		predicatemap.put(VisitorEnum.ThisExpression, bs);
		consumermap.put(VisitorEnum.ThisExpression, bc);
	}

	public void addThrowStatement(BiPredicate<ThrowStatement, E> bs, BiConsumer<ThrowStatement, E> bc) {
		predicatemap.put(VisitorEnum.ThrowStatement, bs);
		consumermap.put(VisitorEnum.ThrowStatement, bc);
	}

	public void addTryStatement(BiPredicate<TryStatement, E> bs, BiConsumer<TryStatement, E> bc) {
		predicatemap.put(VisitorEnum.TryStatement, bs);
		consumermap.put(VisitorEnum.TryStatement, bc);
	}

	public void addTypeDeclaration(BiPredicate<TypeDeclaration, E> bs, BiConsumer<TypeDeclaration, E> bc) {
		predicatemap.put(VisitorEnum.TypeDeclaration, bs);
		consumermap.put(VisitorEnum.TypeDeclaration, bc);
	}

	public void addTypeDeclarationStatement(BiPredicate<TypeDeclarationStatement, E> bs,
			BiConsumer<TypeDeclarationStatement, E> bc) {
		predicatemap.put(VisitorEnum.TypeDeclarationStatement, bs);
		consumermap.put(VisitorEnum.TypeDeclarationStatement, bc);
	}

	public void addTypeLiteral(BiPredicate<TypeLiteral, E> bs, BiConsumer<TypeLiteral, E> bc) {
		predicatemap.put(VisitorEnum.TypeLiteral, bs);
		consumermap.put(VisitorEnum.TypeLiteral, bc);
	}

	public void addTypeMethodReference(BiPredicate<TypeMethodReference, E> bs,
			BiConsumer<TypeMethodReference, E> bc) {
		predicatemap.put(VisitorEnum.TypeMethodReference, bs);
		consumermap.put(VisitorEnum.TypeMethodReference, bc);
	}

	public void addTypeParameter(BiPredicate<TypeParameter, E> bs, BiConsumer<TypeParameter, E> bc) {
		predicatemap.put(VisitorEnum.TypeParameter, bs);
		consumermap.put(VisitorEnum.TypeParameter, bc);
	}

	public void addUnionType(BiPredicate<UnionType, E> bs, BiConsumer<UnionType, E> bc) {
		predicatemap.put(VisitorEnum.UnionType, bs);
		consumermap.put(VisitorEnum.UnionType, bc);
	}

	public void addUsesDirective(BiPredicate<UsesDirective, E> bs, BiConsumer<UsesDirective, E> bc) {
		predicatemap.put(VisitorEnum.UsesDirective, bs);
		consumermap.put(VisitorEnum.UsesDirective, bc);
	}

	public void addVariableDeclarationExpression(BiPredicate<VariableDeclarationExpression, E> bs,
			BiConsumer<VariableDeclarationExpression, E> bc) {
		predicatemap.put(VisitorEnum.VariableDeclarationExpression, bs);
		consumermap.put(VisitorEnum.VariableDeclarationExpression, bc);
	}

	public void addVariableDeclarationStatement(BiPredicate<VariableDeclarationStatement, E> bs,
			BiConsumer<VariableDeclarationStatement, E> bc) {
		predicatemap.put(VisitorEnum.VariableDeclarationStatement, bs);
		consumermap.put(VisitorEnum.VariableDeclarationStatement, bc);
	}

	public void addVariableDeclarationFragment(BiPredicate<VariableDeclarationFragment, E> bs,
			BiConsumer<VariableDeclarationFragment, E> bc) {
		predicatemap.put(VisitorEnum.VariableDeclarationFragment, bs);
		consumermap.put(VisitorEnum.VariableDeclarationFragment, bc);
	}

	public void addWhileStatement(BiPredicate<WhileStatement, E> bs, BiConsumer<WhileStatement, E> bc) {
		predicatemap.put(VisitorEnum.WhileStatement, bs);
		consumermap.put(VisitorEnum.WhileStatement, bc);
	}

	public void addWildcardType(BiPredicate<WildcardType, E> bs, BiConsumer<WildcardType, E> bc) {
		predicatemap.put(VisitorEnum.WildcardType, bs);
		consumermap.put(VisitorEnum.WildcardType, bc);
	}

	public void addYieldStatement(BiPredicate<YieldStatement, E> bs, BiConsumer<YieldStatement, E> bc) {
		predicatemap.put(VisitorEnum.YieldStatement, bs);
		consumermap.put(VisitorEnum.YieldStatement, bc);
	}
	
	public void add(VisitorEnum key, BiPredicate<ASTNode, E> bs,BiConsumer<ASTNode, E> bc) {
		predicatemap.put(key, bs);
		consumermap.put(key, bc);
	}

	public HelperVisitor<E> build(CompilationUnit compilationUnit) {
		astvisitor = new LambdaASTVisitor<>(this);
		compilationUnit.accept(astvisitor);
		return this;
	}

	public void removeVisitor(VisitorEnum ve) {
		this.predicatemap.remove(ve);
		this.consumermap.remove(ve);
	}

	protected Map<VisitorEnum, Object> getConsumerData() {
		return this.consumerdata;
	}

	protected Map<VisitorEnum, Object> getSupplierData() {
		return this.predicatedata;
	}
	
	public static <V,T> void callVisitor(CompilationUnit cu, EnumSet<VisitorEnum> myset, ReferenceHolder<V,T> dataholder,
			BiPredicate<ASTNode, ReferenceHolder<V,T>> bs,
			BiConsumer<ASTNode, ReferenceHolder<V,T>> bc) {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<V,T>> hv = new HelperVisitor<>(nodesprocessed, dataholder);
		myset.forEach(ve -> {
			hv.add(ve, bs,bc);
		});
		hv.build(cu);
	}
	
	public static <V,T> void callVisitor(CompilationUnit cu, EnumSet<VisitorEnum> myset, ReferenceHolder<V,T> dataholder,
			BiPredicate<ASTNode, ReferenceHolder<V,T>> bs) {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<V,T>> hv = new HelperVisitor<>(nodesprocessed, dataholder);
		myset.forEach(ve -> {
			hv.add(ve, bs);
		});
		hv.build(cu);
	}
	
	public static <V,T> void callVisitor(CompilationUnit cu, EnumSet<VisitorEnum> myset, ReferenceHolder<V,T> dataholder,
			BiConsumer<ASTNode, ReferenceHolder<V,T>> bc) {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<V,T>> hv = new HelperVisitor<>(nodesprocessed, dataholder);
		myset.forEach(ve -> {
			hv.add(ve, bc);
		});
		hv.build(cu);
	}
}

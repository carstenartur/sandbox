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
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
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
	 * This map contains one VisitorSupplier per kind if supplied Each BiFunction is
	 * called with two parameters 1) ASTNode 2) your data object Call is processed
	 * when build(CompilationUnit) is called.
	 */
	Map<VisitorEnum, BiFunction<? extends ASTNode, E, Boolean>> suppliermap;
	public Map<VisitorEnum, BiFunction<? extends ASTNode, E, Boolean>> getSuppliermap() {
		return suppliermap;
	}

	public Map<VisitorEnum, BiConsumer<? extends ASTNode, E>> getConsumermap() {
		return consumermap;
	}

	Map<VisitorEnum, BiConsumer<? extends ASTNode, E>> consumermap;
	public Set<ASTNode> nodesprocessed;

	public Set<ASTNode> getNodesprocessed() {
		return nodesprocessed;
	}

	public HelperVisitor(Set<ASTNode> nodesprocessed, E dataholder) {
		this.suppliermap = new LinkedHashMap<>();
		this.consumermap = new LinkedHashMap<>();
		this.dataholder = dataholder;
		dataholder.setHelperVisitor(this);
		this.nodesprocessed = nodesprocessed;
	}

	public BiFunction<? extends ASTNode, E, Boolean> addAnnotationTypeDeclaration(
			BiFunction<AnnotationTypeDeclaration, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.AnnotationTypeDeclaration, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addAnnotationTypeMemberDeclaration(
			BiFunction<AnnotationTypeMemberDeclaration, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.AnnotationTypeMemberDeclaration, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addAnonymousClassDeclaration(
			BiFunction<AnonymousClassDeclaration, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.AnonymousClassDeclaration, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addArrayAccess(BiFunction<ArrayAccess, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ArrayAccess, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addArrayCreation(BiFunction<ArrayCreation, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ArrayCreation, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addArrayInitializer(BiFunction<ArrayInitializer, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ArrayInitializer, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addArrayType(BiFunction<ArrayType, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ArrayType, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addAssertStatement(BiFunction<AssertStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.AssertStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addAssignment(BiFunction<Assignment, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.Assignment, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addBlock(BiFunction<Block, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.Block, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addBlockComment(BiFunction<BlockComment, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.BlockComment, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addBooleanLiteral(BiFunction<BooleanLiteral, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.BooleanLiteral, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addBreakStatement(BiFunction<BreakStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.BreakStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addCastExpression(BiFunction<CastExpression, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.CastExpression, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addCatchClause(BiFunction<CatchClause, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.CatchClause, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addCharacterLiteral(BiFunction<CharacterLiteral, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.CharacterLiteral, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addClassInstanceCreation(
			BiFunction<ClassInstanceCreation, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ClassInstanceCreation, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addCompilationUnit(BiFunction<CompilationUnit, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.CompilationUnit, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addConditionalExpression(
			BiFunction<ConditionalExpression, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ConditionalExpression, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addConstructorInvocation(
			BiFunction<ConstructorInvocation, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ConstructorInvocation, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addContinueStatement(
			BiFunction<ContinueStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ContinueStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addCreationReference(
			BiFunction<CreationReference, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.CreationReference, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addDimension(BiFunction<Dimension, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.Dimension, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addDoStatement(BiFunction<DoStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.DoStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addEmptyStatement(BiFunction<EmptyStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.EmptyStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addEnhancedForStatement(
			BiFunction<EnhancedForStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.EnhancedForStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addEnumConstantDeclaration(
			BiFunction<EnumConstantDeclaration, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.EnumConstantDeclaration, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addEnumDeclaration(BiFunction<EnumDeclaration, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.EnumDeclaration, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addExportsDirective(BiFunction<ExportsDirective, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ExportsDirective, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addExpressionMethodReference(
			BiFunction<ExpressionMethodReference, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ExpressionMethodReference, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addExpressionStatement(
			BiFunction<ExpressionStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ExpressionStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addFieldAccess(BiFunction<FieldAccess, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.FieldAccess, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addFieldDeclaration(BiFunction<FieldDeclaration, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.FieldDeclaration, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addForStatement(BiFunction<ForStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ForStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addIfStatement(BiFunction<IfStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.IfStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addImportDeclaration(
			BiFunction<ImportDeclaration, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ImportDeclaration, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addInfixExpression(BiFunction<InfixExpression, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.InfixExpression, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addInitializer(BiFunction<Initializer, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.Initializer, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addInstanceofExpression(
			BiFunction<InstanceofExpression, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.InstanceofExpression, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addIntersectionType(BiFunction<IntersectionType, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.IntersectionType, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addJavadoc(BiFunction<Javadoc, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.Javadoc, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addLabeledStatement(BiFunction<LabeledStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.LabeledStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addLambdaExpression(BiFunction<LambdaExpression, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.LambdaExpression, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addLineComment(BiFunction<LineComment, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.LineComment, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addMarkerAnnotation(BiFunction<MarkerAnnotation, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.MarkerAnnotation, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addMemberRef(BiFunction<MemberRef, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.MemberRef, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addMemberValuePair(BiFunction<MemberValuePair, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.MemberValuePair, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addMethodRef(BiFunction<MethodRef, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.MethodRef, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addMethodRefParameter(
			BiFunction<MethodRefParameter, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.MethodRefParameter, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addMethodDeclaration(
			BiFunction<MethodDeclaration, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.MethodDeclaration, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addMethodInvocation(BiFunction<MethodInvocation, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.MethodInvocation, bs);
	}
	
	public BiFunction<? extends ASTNode, E, Boolean> addMethodInvocation(String methodname, BiFunction<MethodInvocation, E, Boolean> bs) {
		this.dataholder.setNodeData(VisitorEnum.MethodInvocation,methodname);
		return suppliermap.put(VisitorEnum.MethodInvocation, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addModifier(BiFunction<Modifier, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.Modifier, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addModuleDeclaration(
			BiFunction<ModuleDeclaration, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ModuleDeclaration, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addModuleModifier(BiFunction<ModuleModifier, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ModuleModifier, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addNameQualifiedType(
			BiFunction<NameQualifiedType, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.NameQualifiedType, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addNormalAnnotation(BiFunction<NormalAnnotation, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.NormalAnnotation, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addNullLiteral(BiFunction<NullLiteral, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.NullLiteral, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addNumberLiteral(BiFunction<NumberLiteral, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.NumberLiteral, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addOpensDirective(BiFunction<OpensDirective, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.OpensDirective, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addPackageDeclaration(
			BiFunction<PackageDeclaration, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.PackageDeclaration, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addParameterizedType(
			BiFunction<ParameterizedType, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ParameterizedType, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addParenthesizedExpression(
			BiFunction<ParenthesizedExpression, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ParenthesizedExpression, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addPatternInstanceofExpression(
			BiFunction<PatternInstanceofExpression, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.PatternInstanceofExpression, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addPostfixExpression(
			BiFunction<PostfixExpression, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.PostfixExpression, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addPrefixExpression(BiFunction<PrefixExpression, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.PrefixExpression, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addProvidesDirective(
			BiFunction<ProvidesDirective, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ProvidesDirective, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addPrimitiveType(BiFunction<PrimitiveType, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.PrimitiveType, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addQualifiedName(BiFunction<QualifiedName, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.QualifiedName, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addQualifiedType(BiFunction<QualifiedType, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.QualifiedType, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addModuleQualifiedName(
			BiFunction<ModuleQualifiedName, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ModuleQualifiedName, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addRequiresDirective(
			BiFunction<RequiresDirective, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.RequiresDirective, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addRecordDeclaration(
			BiFunction<RecordDeclaration, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.RecordDeclaration, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addReturnStatement(BiFunction<ReturnStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ReturnStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addSimpleName(BiFunction<SimpleName, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.SimpleName, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addSimpleType(BiFunction<SimpleType, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.SimpleType, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addSingleMemberAnnotation(
			BiFunction<SingleMemberAnnotation, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.SingleMemberAnnotation, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addSingleVariableDeclaration(
			BiFunction<SingleVariableDeclaration, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.SingleVariableDeclaration, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addStringLiteral(BiFunction<StringLiteral, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.StringLiteral, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addSuperConstructorInvocation(
			BiFunction<SuperConstructorInvocation, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.SuperConstructorInvocation, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addSuperFieldAccess(BiFunction<SuperFieldAccess, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.SuperFieldAccess, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addSuperMethodInvocation(
			BiFunction<SuperMethodInvocation, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.SuperMethodInvocation, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addSuperMethodReference(
			BiFunction<SuperMethodReference, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.SuperMethodReference, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addSwitchCase(BiFunction<SwitchCase, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.SwitchCase, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addSwitchExpression(BiFunction<SwitchExpression, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.SwitchExpression, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addSwitchStatement(BiFunction<SwitchStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.SwitchStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addSynchronizedStatement(
			BiFunction<SynchronizedStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.SynchronizedStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addTagElement(BiFunction<TagElement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.TagElement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addTextBlock(BiFunction<TextBlock, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.TextBlock, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addTextElement(BiFunction<TextElement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.TextElement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addThisExpression(BiFunction<ThisExpression, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ThisExpression, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addThrowStatement(BiFunction<ThrowStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.ThrowStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addTryStatement(BiFunction<TryStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.TryStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addTypeDeclaration(BiFunction<TypeDeclaration, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.TypeDeclaration, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addTypeDeclarationStatement(
			BiFunction<TypeDeclarationStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.TypeDeclarationStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addTypeLiteral(BiFunction<TypeLiteral, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.TypeLiteral, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addTypeMethodReference(
			BiFunction<TypeMethodReference, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.TypeMethodReference, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addTypeParameter(BiFunction<TypeParameter, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.TypeParameter, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addUnionType(BiFunction<UnionType, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.UnionType, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addUsesDirective(BiFunction<UsesDirective, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.UsesDirective, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addVariableDeclarationExpression(
			BiFunction<VariableDeclarationExpression, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.VariableDeclarationExpression, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addVariableDeclarationStatement(
			BiFunction<VariableDeclarationStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.VariableDeclarationStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addVariableDeclarationFragment(
			BiFunction<VariableDeclarationFragment, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.VariableDeclarationFragment, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addWhileStatement(BiFunction<WhileStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.WhileStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addWildcardType(BiFunction<WildcardType, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.WildcardType, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> addYieldStatement(BiFunction<YieldStatement, E, Boolean> bs) {
		return suppliermap.put(VisitorEnum.YieldStatement, bs);
	}

	public BiFunction<? extends ASTNode, E, Boolean> add(VisitorEnum key, BiFunction<ASTNode, E, Boolean> bs) {
		return suppliermap.put(key, bs);
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
		this.dataholder.setNodeData(VisitorEnum.MethodInvocation,methodname);
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

	public BiConsumer<? extends ASTNode, E> addModuleQualifiedName(BiConsumer<ModuleQualifiedName, E> bc) {
		return consumermap.put(VisitorEnum.ModuleQualifiedName, bc);
	}

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

	public HelperVisitor<E> build(CompilationUnit compilationUnit) {
		astvisitor = new LambdaASTVisitor<>(this);
		compilationUnit.accept(astvisitor);
		return this;
	}
	
	public void removeVisitor(VisitorEnum ve) {
		this.suppliermap.remove(ve);
		this.consumermap.remove(ve);
	}
}

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

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.eclipse.jdt.core.dom.*;

/**
 * 
 * @author chammer
 *
 * @param <E>
 */
public class LambdaASTVisitor<E extends HelperVisitorProvider> extends ASTVisitor {
	/**
	 * 
	 */
	private final HelperVisitor<E> helperVisitor;

	/**
	 * @param helperVisitor
	 */
	LambdaASTVisitor(HelperVisitor<E> helperVisitor) {
		this.helperVisitor = helperVisitor;
	}

	@Override
	public boolean visit(AnnotationTypeDeclaration node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.AnnotationTypeDeclaration)) {
			return ((BiFunction<AnnotationTypeDeclaration, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.AnnotationTypeDeclaration))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(AnnotationTypeMemberDeclaration node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.AnnotationTypeMemberDeclaration)) {
			return ((BiFunction<AnnotationTypeMemberDeclaration, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.AnnotationTypeMemberDeclaration))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(AnonymousClassDeclaration node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.AnonymousClassDeclaration)) {
			return ((BiFunction<AnonymousClassDeclaration, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.AnonymousClassDeclaration))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ArrayAccess node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ArrayAccess)) {
			return ((BiFunction<ArrayAccess, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.ArrayAccess)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ArrayCreation node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ArrayCreation)) {
			return ((BiFunction<ArrayCreation, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.ArrayCreation)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ArrayInitializer node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ArrayInitializer)) {
			return ((BiFunction<ArrayInitializer, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.ArrayInitializer)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ArrayType node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ArrayType)) {
			return ((BiFunction<ArrayType, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.ArrayType)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(AssertStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.AssertStatement)) {
			return ((BiFunction<AssertStatement, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.AssertStatement)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(Assignment node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.Assignment)) {
			return ((BiFunction<Assignment, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.Assignment)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(Block node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.Block)) {
			return ((BiFunction<Block, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.Block))).apply(node, this.helperVisitor.dataholder)
					.booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(BlockComment node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.BlockComment)) {
			return ((BiFunction<BlockComment, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.BlockComment)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(BooleanLiteral node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.BooleanLiteral)) {
			return ((BiFunction<BooleanLiteral, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.BooleanLiteral)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(BreakStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.BreakStatement)) {
			return ((BiFunction<BreakStatement, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.BreakStatement)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(CastExpression node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.CastExpression)) {
			return ((BiFunction<CastExpression, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.CastExpression)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(CatchClause node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.CatchClause)) {
			return ((BiFunction<CatchClause, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.CatchClause)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(CharacterLiteral node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.CharacterLiteral)) {
			return ((BiFunction<CharacterLiteral, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.CharacterLiteral)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ClassInstanceCreation node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ClassInstanceCreation)) {
			return ((BiFunction<ClassInstanceCreation, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.ClassInstanceCreation))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(CompilationUnit node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.CompilationUnit)) {
			return ((BiFunction<CompilationUnit, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.CompilationUnit)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ConditionalExpression node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ConditionalExpression)) {
			return ((BiFunction<ConditionalExpression, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.ConditionalExpression))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ConstructorInvocation node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ConstructorInvocation)) {
			return ((BiFunction<ConstructorInvocation, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.ConstructorInvocation))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ContinueStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ContinueStatement)) {
			return ((BiFunction<ContinueStatement, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.ContinueStatement)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(CreationReference node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.CreationReference)) {
			return ((BiFunction<CreationReference, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.CreationReference)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(Dimension node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.Dimension)) {
			return ((BiFunction<Dimension, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.Dimension)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(DoStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.DoStatement)) {
			return ((BiFunction<DoStatement, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.DoStatement)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(EmptyStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.EmptyStatement)) {
			return ((BiFunction<EmptyStatement, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.EmptyStatement)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(EnhancedForStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.EnhancedForStatement)) {
			return ((BiFunction<EnhancedForStatement, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.EnhancedForStatement))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(EnumConstantDeclaration node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.EnumConstantDeclaration)) {
			return ((BiFunction<EnumConstantDeclaration, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.EnumConstantDeclaration))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(EnumDeclaration node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.EnumDeclaration)) {
			return ((BiFunction<EnumDeclaration, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.EnumDeclaration)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ExportsDirective node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ExportsDirective)) {
			return ((BiFunction<ExportsDirective, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.ExportsDirective)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ExpressionMethodReference node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.BreakStatement)) {
			return ((BiFunction<ExpressionMethodReference, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.ExpressionMethodReference))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ExpressionStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ExpressionStatement)) {
			return ((BiFunction<ExpressionStatement, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.ExpressionStatement))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(FieldAccess node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.FieldAccess)) {
			return ((BiFunction<FieldAccess, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.FieldAccess)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(FieldDeclaration node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.FieldDeclaration)) {
			return ((BiFunction<FieldDeclaration, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.FieldDeclaration)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ForStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ForStatement)) {
			return ((BiFunction<ForStatement, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.ForStatement)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(IfStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.IfStatement)) {
			return ((BiFunction<IfStatement, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.IfStatement)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ImportDeclaration node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ImportDeclaration)) {
			return ((BiFunction<ImportDeclaration, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.ImportDeclaration)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(InfixExpression node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.InfixExpression)) {
			return ((BiFunction<InfixExpression, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.InfixExpression)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(Initializer node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.Initializer)) {
			return ((BiFunction<Initializer, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.Initializer)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(InstanceofExpression node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.InstanceofExpression)) {
			return ((BiFunction<InstanceofExpression, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.InstanceofExpression))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(IntersectionType node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.IntersectionType)) {
			return ((BiFunction<IntersectionType, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.IntersectionType)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(Javadoc node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.Javadoc)) {
			return ((BiFunction<Javadoc, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.Javadoc)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(LabeledStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.LabeledStatement)) {
			return ((BiFunction<LabeledStatement, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.LabeledStatement)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(LambdaExpression node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.LambdaExpression)) {
			return ((BiFunction<LambdaExpression, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.LambdaExpression)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(LineComment node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.LineComment)) {
			return ((BiFunction<LineComment, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.LineComment)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(MarkerAnnotation node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.MarkerAnnotation)) {
			return ((BiFunction<MarkerAnnotation, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.MarkerAnnotation)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(MemberRef node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.MemberRef)) {
			return ((BiFunction<MemberRef, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.MemberRef)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(MemberValuePair node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.MemberValuePair)) {
			return ((BiFunction<MemberValuePair, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.MemberValuePair)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(MethodRef node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.MethodRef)) {
			return ((BiFunction<MethodRef, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.MethodRef)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(MethodRefParameter node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.MethodRefParameter)) {
			return ((BiFunction<MethodRefParameter, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.MethodRefParameter)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(MethodDeclaration node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.MethodDeclaration)) {
			return ((BiFunction<MethodDeclaration, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.MethodDeclaration)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(MethodInvocation node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.MethodInvocation)) {
			String data=(String) this.helperVisitor.getSupplierData().get(VisitorEnum.MethodInvocation);
			if (data!= null && !node.getName().getIdentifier().equals(data)) {
				return true;
			}
			return ((BiFunction<MethodInvocation, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.MethodInvocation))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(Modifier node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.Modifier)) {
			return ((BiFunction<Modifier, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.Modifier)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ModuleDeclaration node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ModuleDeclaration)) {
			return ((BiFunction<ModuleDeclaration, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.ModuleDeclaration)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ModuleModifier node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ModuleModifier)) {
			return ((BiFunction<ModuleModifier, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.ModuleModifier)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(NameQualifiedType node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.NameQualifiedType)) {
			return ((BiFunction<NameQualifiedType, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.NameQualifiedType)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(NormalAnnotation node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.NormalAnnotation)) {
			return ((BiFunction<NormalAnnotation, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.NormalAnnotation)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(NullLiteral node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.NullLiteral)) {
			return ((BiFunction<NullLiteral, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.NullLiteral)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(NumberLiteral node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.NumberLiteral)) {
			return ((BiFunction<NumberLiteral, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.NumberLiteral)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(OpensDirective node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.OpensDirective)) {
			return ((BiFunction<OpensDirective, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.OpensDirective)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(PackageDeclaration node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.PackageDeclaration)) {
			return ((BiFunction<PackageDeclaration, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.PackageDeclaration)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ParameterizedType node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ParameterizedType)) {
			return ((BiFunction<ParameterizedType, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.ParameterizedType)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ParenthesizedExpression node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ParenthesizedExpression)) {
			return ((BiFunction<ParenthesizedExpression, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.ParenthesizedExpression))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(PatternInstanceofExpression node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.PatternInstanceofExpression)) {
			return ((BiFunction<PatternInstanceofExpression, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.PatternInstanceofExpression))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(PostfixExpression node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.PostfixExpression)) {
			return ((BiFunction<PostfixExpression, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.PostfixExpression)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(PrefixExpression node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.PrefixExpression)) {
			return ((BiFunction<PrefixExpression, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.PrefixExpression)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ProvidesDirective node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ProvidesDirective)) {
			return ((BiFunction<ProvidesDirective, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.ProvidesDirective)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(PrimitiveType node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.PrimitiveType)) {
			return ((BiFunction<PrimitiveType, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.PrimitiveType)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(QualifiedName node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.QualifiedName)) {
			return ((BiFunction<QualifiedName, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.QualifiedName)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(QualifiedType node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.QualifiedType)) {
			return ((BiFunction<QualifiedType, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.QualifiedType)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

//	@Override
//	public boolean visit(ModuleQualifiedName node) {
//		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ModuleQualifiedName)) {
//			return ((BiFunction<ModuleQualifiedName, E, Boolean>) (this.helperVisitor.suppliermap
//					.get(VisitorEnum.ModuleQualifiedName))).apply(node, this.helperVisitor.dataholder).booleanValue();
//		}
//		return true;
//	}

	@Override
	public boolean visit(RequiresDirective node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.RequiresDirective)) {
			return ((BiFunction<RequiresDirective, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.RequiresDirective)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(RecordDeclaration node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.RecordDeclaration)) {
			return ((BiFunction<RecordDeclaration, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.RecordDeclaration)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ReturnStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ReturnStatement)) {
			return ((BiFunction<ReturnStatement, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.ReturnStatement)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(SimpleName node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.SimpleName)) {
			return ((BiFunction<SimpleName, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.SimpleName)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(SimpleType node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.SimpleType)) {
			return ((BiFunction<SimpleType, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.SimpleType)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(SingleMemberAnnotation node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.SingleMemberAnnotation)) {
			return ((BiFunction<SingleMemberAnnotation, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.SingleMemberAnnotation))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(SingleVariableDeclaration node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.SingleVariableDeclaration)) {
			return ((BiFunction<SingleVariableDeclaration, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.SingleVariableDeclaration))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(StringLiteral node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.StringLiteral)) {
			return ((BiFunction<StringLiteral, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.StringLiteral)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(SuperConstructorInvocation node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.SuperConstructorInvocation)) {
			return ((BiFunction<SuperConstructorInvocation, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.SuperConstructorInvocation))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(SuperFieldAccess node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.SuperFieldAccess)) {
			return ((BiFunction<SuperFieldAccess, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.SuperFieldAccess)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(SuperMethodInvocation node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.SuperMethodInvocation)) {
			return ((BiFunction<SuperMethodInvocation, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.SuperMethodInvocation))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(SuperMethodReference node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.SuperMethodReference)) {
			return ((BiFunction<SuperMethodReference, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.SuperMethodReference))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(SwitchCase node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.SwitchCase)) {
			return ((BiFunction<SwitchCase, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.SwitchCase)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(SwitchExpression node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.SwitchExpression)) {
			return ((BiFunction<SwitchExpression, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.SwitchExpression)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(SwitchStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.SwitchStatement)) {
			return ((BiFunction<SwitchStatement, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.SwitchStatement)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(SynchronizedStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.SynchronizedStatement)) {
			return ((BiFunction<SynchronizedStatement, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.SynchronizedStatement))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(TagElement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.TagElement)) {
			return ((BiFunction<TagElement, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.TagElement)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(TextBlock node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.TextBlock)) {
			return ((BiFunction<TextBlock, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.TextBlock)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(TextElement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.TextElement)) {
			return ((BiFunction<TextElement, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.TextElement)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ThisExpression node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ThisExpression)) {
			return ((BiFunction<ThisExpression, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.ThisExpression)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(ThrowStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.ThrowStatement)) {
			return ((BiFunction<ThrowStatement, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.ThrowStatement)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(TryStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.TryStatement)) {
			return ((BiFunction<TryStatement, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.TryStatement)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.TypeDeclaration)) {
			return ((BiFunction<TypeDeclaration, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.TypeDeclaration)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(TypeDeclarationStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.TypeDeclarationStatement)) {
			return ((BiFunction<TypeDeclarationStatement, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.TypeDeclarationStatement))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(TypeLiteral node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.TypeLiteral)) {
			return ((BiFunction<TypeLiteral, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.TypeLiteral)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(TypeMethodReference node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.TypeMethodReference)) {
			return ((BiFunction<TypeMethodReference, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.TypeMethodReference))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(TypeParameter node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.TypeParameter)) {
			return ((BiFunction<TypeParameter, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.TypeParameter)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(UnionType node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.UnionType)) {
			return ((BiFunction<UnionType, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.UnionType)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(UsesDirective node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.UsesDirective)) {
			return ((BiFunction<UsesDirective, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.UsesDirective)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(VariableDeclarationExpression node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.VariableDeclarationExpression)) {
			return ((BiFunction<VariableDeclarationExpression, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.VariableDeclarationExpression))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(VariableDeclarationStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.VariableDeclarationStatement)) {
			return ((BiFunction<VariableDeclarationStatement, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.VariableDeclarationStatement))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(VariableDeclarationFragment node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.VariableDeclarationFragment)) {
			return ((BiFunction<VariableDeclarationFragment, E, Boolean>) (this.helperVisitor.suppliermap
					.get(VisitorEnum.VariableDeclarationFragment))).apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(WhileStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.WhileStatement)) {
			return ((BiFunction<WhileStatement, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.WhileStatement)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(WildcardType node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.WildcardType)) {
			return ((BiFunction<WildcardType, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.WildcardType)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public boolean visit(YieldStatement node) {
		if (this.helperVisitor.suppliermap.containsKey(VisitorEnum.YieldStatement)) {
			return ((BiFunction<YieldStatement, E, Boolean>) (this.helperVisitor.suppliermap.get(VisitorEnum.YieldStatement)))
					.apply(node, this.helperVisitor.dataholder).booleanValue();
		}
		return true;
	}

	@Override
	public void endVisit(AnnotationTypeDeclaration node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.AnnotationTypeDeclaration)) {
			((BiConsumer<AnnotationTypeDeclaration, E>) (this.helperVisitor.consumermap.get(VisitorEnum.AnnotationTypeDeclaration)))
					.accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(AnnotationTypeMemberDeclaration node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.AnnotationTypeMemberDeclaration)) {
			((BiConsumer<AnnotationTypeMemberDeclaration, E>) (this.helperVisitor.consumermap
					.get(VisitorEnum.AnnotationTypeMemberDeclaration))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(AnonymousClassDeclaration node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.AnonymousClassDeclaration)) {
			((BiConsumer<AnonymousClassDeclaration, E>) (this.helperVisitor.consumermap.get(VisitorEnum.AnonymousClassDeclaration)))
					.accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ArrayAccess node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ArrayAccess)) {
			((BiConsumer<ArrayAccess, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ArrayAccess))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ArrayCreation node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ArrayCreation)) {
			((BiConsumer<ArrayCreation, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ArrayCreation))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ArrayInitializer node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ArrayInitializer)) {
			((BiConsumer<ArrayInitializer, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ArrayInitializer))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ArrayType node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ArrayType)) {
			((BiConsumer<ArrayType, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ArrayType))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(AssertStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.AssertStatement)) {
			((BiConsumer<AssertStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.AssertStatement))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(Assignment node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.Assignment)) {
			((BiConsumer<Assignment, E>) (this.helperVisitor.consumermap.get(VisitorEnum.Assignment))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(Block node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.Block)) {
			((BiConsumer<Block, E>) (this.helperVisitor.consumermap.get(VisitorEnum.Block))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(BlockComment node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.BlockComment)) {
			((BiConsumer<BlockComment, E>) (this.helperVisitor.consumermap.get(VisitorEnum.BlockComment))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(BooleanLiteral node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.BooleanLiteral)) {
			((BiConsumer<BooleanLiteral, E>) (this.helperVisitor.consumermap.get(VisitorEnum.BooleanLiteral))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(BreakStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.BreakStatement)) {
			((BiConsumer<BreakStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.BreakStatement))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(CastExpression node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.CastExpression)) {
			((BiConsumer<CastExpression, E>) (this.helperVisitor.consumermap.get(VisitorEnum.CastExpression))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(CatchClause node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.CatchClause)) {
			((BiConsumer<CatchClause, E>) (this.helperVisitor.consumermap.get(VisitorEnum.CatchClause))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(CharacterLiteral node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.CharacterLiteral)) {
			((BiConsumer<CharacterLiteral, E>) (this.helperVisitor.consumermap.get(VisitorEnum.CharacterLiteral))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ClassInstanceCreation node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ClassInstanceCreation)) {
			((BiConsumer<ClassInstanceCreation, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ClassInstanceCreation)))
					.accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(CompilationUnit node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.CompilationUnit)) {
			((BiConsumer<CompilationUnit, E>) (this.helperVisitor.consumermap.get(VisitorEnum.CompilationUnit))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ConditionalExpression node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ConditionalExpression)) {
			((BiConsumer<ConditionalExpression, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ConditionalExpression)))
					.accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ConstructorInvocation node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ConstructorInvocation)) {
			((BiConsumer<ConstructorInvocation, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ConstructorInvocation)))
					.accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ContinueStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ContinueStatement)) {
			((BiConsumer<ContinueStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ContinueStatement))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(CreationReference node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.CreationReference)) {
			((BiConsumer<CreationReference, E>) (this.helperVisitor.consumermap.get(VisitorEnum.CreationReference))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(Dimension node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.Dimension)) {
			((BiConsumer<Dimension, E>) (this.helperVisitor.consumermap.get(VisitorEnum.Dimension))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(DoStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.DoStatement)) {
			((BiConsumer<DoStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.DoStatement))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(EmptyStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.EmptyStatement)) {
			((BiConsumer<EmptyStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.EmptyStatement))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(EnhancedForStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.EnhancedForStatement)) {
			((BiConsumer<EnhancedForStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.EnhancedForStatement))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(EnumConstantDeclaration node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.EnumConstantDeclaration)) {
			((BiConsumer<EnumConstantDeclaration, E>) (this.helperVisitor.consumermap.get(VisitorEnum.EnumConstantDeclaration)))
					.accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(EnumDeclaration node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.EnumDeclaration)) {
			((BiConsumer<EnumDeclaration, E>) (this.helperVisitor.consumermap.get(VisitorEnum.EnumDeclaration))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ExportsDirective node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ExportsDirective)) {
			((BiConsumer<ExportsDirective, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ExportsDirective))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ExpressionMethodReference node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ExpressionMethodReference)) {
			((BiConsumer<ExpressionMethodReference, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ExpressionMethodReference)))
					.accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ExpressionStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ExpressionStatement)) {
			((BiConsumer<ExpressionStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ExpressionStatement))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(FieldAccess node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.FieldAccess)) {
			((BiConsumer<FieldAccess, E>) (this.helperVisitor.consumermap.get(VisitorEnum.FieldAccess))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(FieldDeclaration node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.FieldDeclaration)) {
			((BiConsumer<FieldDeclaration, E>) (this.helperVisitor.consumermap.get(VisitorEnum.FieldDeclaration))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ForStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ForStatement)) {
			((BiConsumer<ForStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ForStatement))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(IfStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.IfStatement)) {
			((BiConsumer<IfStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.IfStatement))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ImportDeclaration node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ImportDeclaration)) {
			((BiConsumer<ImportDeclaration, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ImportDeclaration))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(InfixExpression node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.InfixExpression)) {
			((BiConsumer<InfixExpression, E>) (this.helperVisitor.consumermap.get(VisitorEnum.InfixExpression))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(Initializer node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.Initializer)) {
			((BiConsumer<Initializer, E>) (this.helperVisitor.consumermap.get(VisitorEnum.Initializer))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(InstanceofExpression node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.InstanceofExpression)) {
			((BiConsumer<InstanceofExpression, E>) (this.helperVisitor.consumermap.get(VisitorEnum.InstanceofExpression))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(IntersectionType node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.IntersectionType)) {
			((BiConsumer<IntersectionType, E>) (this.helperVisitor.consumermap.get(VisitorEnum.IntersectionType))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(Javadoc node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.Javadoc)) {
			((BiConsumer<Javadoc, E>) (this.helperVisitor.consumermap.get(VisitorEnum.Javadoc))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(LabeledStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.LabeledStatement)) {
			((BiConsumer<LabeledStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.LabeledStatement))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(LambdaExpression node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.LambdaExpression)) {
			((BiConsumer<LambdaExpression, E>) (this.helperVisitor.consumermap.get(VisitorEnum.LambdaExpression))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(LineComment node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.LineComment)) {
			((BiConsumer<LineComment, E>) (this.helperVisitor.consumermap.get(VisitorEnum.LineComment))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(MarkerAnnotation node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.MarkerAnnotation)) {
			((BiConsumer<MarkerAnnotation, E>) (this.helperVisitor.consumermap.get(VisitorEnum.MarkerAnnotation))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(MemberRef node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.MemberRef)) {
			((BiConsumer<MemberRef, E>) (this.helperVisitor.consumermap.get(VisitorEnum.MemberRef))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(MemberValuePair node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.MemberValuePair)) {
			((BiConsumer<MemberValuePair, E>) (this.helperVisitor.consumermap.get(VisitorEnum.MemberValuePair))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(MethodRef node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.MethodRef)) {
			((BiConsumer<MethodRef, E>) (this.helperVisitor.consumermap.get(VisitorEnum.MethodRef))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(MethodRefParameter node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.MethodRefParameter)) {
			((BiConsumer<MethodRefParameter, E>) (this.helperVisitor.consumermap.get(VisitorEnum.MethodRefParameter))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(MethodDeclaration node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.MethodDeclaration)) {
			((BiConsumer<MethodDeclaration, E>) (this.helperVisitor.consumermap.get(VisitorEnum.MethodDeclaration))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(MethodInvocation node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.MethodInvocation)) {
			String data=(String) this.helperVisitor.getConsumerData().get(VisitorEnum.MethodInvocation);
			if (data!= null && !node.getName().getIdentifier().equals(data)) {
				return;
			}
			((BiConsumer<MethodInvocation, E>) (this.helperVisitor.consumermap.get(VisitorEnum.MethodInvocation))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(Modifier node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.Modifier)) {
			((BiConsumer<Modifier, E>) (this.helperVisitor.consumermap.get(VisitorEnum.Modifier))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ModuleDeclaration node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ModuleDeclaration)) {
			((BiConsumer<ModuleDeclaration, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ModuleDeclaration))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ModuleModifier node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ModuleModifier)) {
			((BiConsumer<ModuleModifier, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ModuleModifier))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(NameQualifiedType node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.NameQualifiedType)) {
			((BiConsumer<NameQualifiedType, E>) (this.helperVisitor.consumermap.get(VisitorEnum.NameQualifiedType))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(NormalAnnotation node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.NormalAnnotation)) {
			((BiConsumer<NormalAnnotation, E>) (this.helperVisitor.consumermap.get(VisitorEnum.NormalAnnotation))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(NullLiteral node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.NullLiteral)) {
			((BiConsumer<NullLiteral, E>) (this.helperVisitor.consumermap.get(VisitorEnum.NullLiteral))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(NumberLiteral node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.NumberLiteral)) {
			((BiConsumer<NumberLiteral, E>) (this.helperVisitor.consumermap.get(VisitorEnum.NumberLiteral))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(OpensDirective node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.OpensDirective)) {
			((BiConsumer<OpensDirective, E>) (this.helperVisitor.consumermap.get(VisitorEnum.OpensDirective))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(PackageDeclaration node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.PackageDeclaration)) {
			((BiConsumer<PackageDeclaration, E>) (this.helperVisitor.consumermap.get(VisitorEnum.PackageDeclaration))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ParameterizedType node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ParameterizedType)) {
			((BiConsumer<ParameterizedType, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ParameterizedType))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ParenthesizedExpression node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ParenthesizedExpression)) {
			((BiConsumer<ParenthesizedExpression, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ParenthesizedExpression)))
					.accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(PatternInstanceofExpression node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.PatternInstanceofExpression)) {
			((BiConsumer<PatternInstanceofExpression, E>) (this.helperVisitor.consumermap
					.get(VisitorEnum.PatternInstanceofExpression))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(PostfixExpression node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.PostfixExpression)) {
			((BiConsumer<PostfixExpression, E>) (this.helperVisitor.consumermap.get(VisitorEnum.PostfixExpression))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(PrefixExpression node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.PrefixExpression)) {
			((BiConsumer<PrefixExpression, E>) (this.helperVisitor.consumermap.get(VisitorEnum.PrefixExpression))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ProvidesDirective node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ProvidesDirective)) {
			((BiConsumer<ProvidesDirective, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ProvidesDirective))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(PrimitiveType node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.PrimitiveType)) {
			((BiConsumer<PrimitiveType, E>) (this.helperVisitor.consumermap.get(VisitorEnum.PrimitiveType))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(QualifiedName node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.QualifiedName)) {
			((BiConsumer<QualifiedName, E>) (this.helperVisitor.consumermap.get(VisitorEnum.QualifiedName))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(QualifiedType node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.QualifiedType)) {
			((BiConsumer<QualifiedType, E>) (this.helperVisitor.consumermap.get(VisitorEnum.QualifiedType))).accept(node, this.helperVisitor.dataholder);
		}
	}

//	@Override
//	public void endVisit(ModuleQualifiedName node) {
//		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ModuleQualifiedName)) {
//			((BiConsumer<ModuleQualifiedName, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ModuleQualifiedName))).accept(node,
//					this.helperVisitor.dataholder);
//		}
//	}

	@Override
	public void endVisit(RequiresDirective node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.RequiresDirective)) {
			((BiConsumer<RequiresDirective, E>) (this.helperVisitor.consumermap.get(VisitorEnum.RequiresDirective))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(RecordDeclaration node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.RecordDeclaration)) {
			((BiConsumer<RecordDeclaration, E>) (this.helperVisitor.consumermap.get(VisitorEnum.RecordDeclaration))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ReturnStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ReturnStatement)) {
			((BiConsumer<ReturnStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ReturnStatement))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(SimpleName node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.SimpleName)) {
			((BiConsumer<SimpleName, E>) (this.helperVisitor.consumermap.get(VisitorEnum.SimpleName))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(SimpleType node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.SimpleType)) {
			((BiConsumer<SimpleType, E>) (this.helperVisitor.consumermap.get(VisitorEnum.SimpleType))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(SingleMemberAnnotation node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.SingleMemberAnnotation)) {
			((BiConsumer<SingleMemberAnnotation, E>) (this.helperVisitor.consumermap.get(VisitorEnum.SingleMemberAnnotation)))
					.accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(SingleVariableDeclaration node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.SingleVariableDeclaration)) {
			((BiConsumer<SingleVariableDeclaration, E>) (this.helperVisitor.consumermap.get(VisitorEnum.SingleVariableDeclaration)))
					.accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(StringLiteral node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.StringLiteral)) {
			((BiConsumer<StringLiteral, E>) (this.helperVisitor.consumermap.get(VisitorEnum.StringLiteral))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(SuperConstructorInvocation node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.SuperConstructorInvocation)) {
			((BiConsumer<SuperConstructorInvocation, E>) (this.helperVisitor.consumermap.get(VisitorEnum.SuperConstructorInvocation)))
					.accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(SuperFieldAccess node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.SuperFieldAccess)) {
			((BiConsumer<SuperFieldAccess, E>) (this.helperVisitor.consumermap.get(VisitorEnum.SuperFieldAccess))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(SuperMethodInvocation node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.SuperMethodInvocation)) {
			((BiConsumer<SuperMethodInvocation, E>) (this.helperVisitor.consumermap.get(VisitorEnum.SuperMethodInvocation)))
					.accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(SuperMethodReference node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.SuperMethodReference)) {
			((BiConsumer<SuperMethodReference, E>) (this.helperVisitor.consumermap.get(VisitorEnum.SuperMethodReference))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(SwitchCase node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.SwitchCase)) {
			((BiConsumer<SwitchCase, E>) (this.helperVisitor.consumermap.get(VisitorEnum.SwitchCase))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(SwitchExpression node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.SwitchExpression)) {
			((BiConsumer<SwitchExpression, E>) (this.helperVisitor.consumermap.get(VisitorEnum.SwitchExpression))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(SwitchStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.SwitchStatement)) {
			((BiConsumer<SwitchStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.SwitchStatement))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(SynchronizedStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.SynchronizedStatement)) {
			((BiConsumer<SynchronizedStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.SynchronizedStatement)))
					.accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(TagElement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.TagElement)) {
			((BiConsumer<TagElement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.TagElement))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(TextBlock node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.TextBlock)) {
			((BiConsumer<TextBlock, E>) (this.helperVisitor.consumermap.get(VisitorEnum.TextBlock))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(TextElement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.TextElement)) {
			((BiConsumer<TextElement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.TextElement))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ThisExpression node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ThisExpression)) {
			((BiConsumer<ThisExpression, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ThisExpression))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(ThrowStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.ThrowStatement)) {
			((BiConsumer<ThrowStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.ThrowStatement))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(TryStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.TryStatement)) {
			((BiConsumer<TryStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.TryStatement))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(TypeDeclaration node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.TypeDeclaration)) {
			((BiConsumer<TypeDeclaration, E>) (this.helperVisitor.consumermap.get(VisitorEnum.TypeDeclaration))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(TypeDeclarationStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.TypeDeclarationStatement)) {
			((BiConsumer<TypeDeclarationStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.TypeDeclarationStatement)))
					.accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(TypeLiteral node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.TypeLiteral)) {
			((BiConsumer<TypeLiteral, E>) (this.helperVisitor.consumermap.get(VisitorEnum.TypeLiteral))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(TypeMethodReference node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.TypeMethodReference)) {
			((BiConsumer<TypeMethodReference, E>) (this.helperVisitor.consumermap.get(VisitorEnum.TypeMethodReference))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(TypeParameter node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.TypeParameter)) {
			((BiConsumer<TypeParameter, E>) (this.helperVisitor.consumermap.get(VisitorEnum.TypeParameter))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(UnionType node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.UnionType)) {
			((BiConsumer<UnionType, E>) (this.helperVisitor.consumermap.get(VisitorEnum.UnionType))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(UsesDirective node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.UsesDirective)) {
			((BiConsumer<UsesDirective, E>) (this.helperVisitor.consumermap.get(VisitorEnum.UsesDirective))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(VariableDeclarationExpression node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.VariableDeclarationExpression)) {
			((BiConsumer<VariableDeclarationExpression, E>) (this.helperVisitor.consumermap
					.get(VisitorEnum.VariableDeclarationExpression))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(VariableDeclarationStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.VariableDeclarationStatement)) {
			((BiConsumer<VariableDeclarationStatement, E>) (this.helperVisitor.consumermap
					.get(VisitorEnum.VariableDeclarationStatement))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(VariableDeclarationFragment node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.VariableDeclarationFragment)) {
			((BiConsumer<VariableDeclarationFragment, E>) (this.helperVisitor.consumermap
					.get(VisitorEnum.VariableDeclarationFragment))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(WhileStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.WhileStatement)) {
			((BiConsumer<WhileStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.WhileStatement))).accept(node,
					this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(WildcardType node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.WildcardType)) {
			((BiConsumer<WildcardType, E>) (this.helperVisitor.consumermap.get(VisitorEnum.WildcardType))).accept(node, this.helperVisitor.dataholder);
		}
	}

	@Override
	public void endVisit(YieldStatement node) {
		if (this.helperVisitor.consumermap.containsKey(VisitorEnum.YieldStatement)) {
			((BiConsumer<YieldStatement, E>) (this.helperVisitor.consumermap.get(VisitorEnum.YieldStatement))).accept(node,
					this.helperVisitor.dataholder);
		}
	}
}
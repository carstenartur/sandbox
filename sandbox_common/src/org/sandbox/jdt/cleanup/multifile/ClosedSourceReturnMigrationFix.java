/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.sandbox.jdt.container.analysis.ClosedReturnArrayUses;
import org.sandbox.jdt.container.api.ClosedSourceReturnMigrationPlan;
import org.sandbox.jdt.container.api.ClosedSourceReturnMigrationPlan.Range;

/** Applies one unit of a validated, project-closed return and callers change. */
public final class ClosedSourceReturnMigrationFix {

	private static final String DESCRIPTION= "Migrate closed array return and all its callers"; //$NON-NLS-1$

	private ClosedSourceReturnMigrationFix() {
	}

	public static ICleanUpFix create(ICompilationUnit unit, CompilationUnit root, ClosedSourceReturnMigrationPlan plan) throws CoreException {
		String handle= unit.getPrimary().getHandleIdentifier();
		var expected= plan.consumers().stream().filter(consumer -> consumer.unitHandle().equals(handle)).toList();
		var actual= ClosedReturnArrayUses.consumers(root, handle, plan.methodBindingKey());
		if (actual.isEmpty() || !actual.get().equals(expected)) {
			throw new CoreException(Status.error("The return consumers differ in the supplied working-copy AST.")); //$NON-NLS-1$
		}
		List<CompilationUnitRewriteOperationWithSourceRange> operations= new ArrayList<>();
		List<ArrayType> types= new ArrayList<>();
		List<QualifiedName> lengths= new ArrayList<>();
		if (plan.producer().compilationUnitHandle().equals(handle)) {
			if (ClosedReturnArrayUses.producer(root, plan.methodBindingKey(), plan.producer().bindingKey())
					.filter(plan.returnType()::equals).isEmpty()) {
				throw new CoreException(Status.error("The return signature in the supplied working-copy AST changed.")); //$NON-NLS-1$
			}
			operations.add(ContainerLocalRewriteFix.directReturnOperation(unit.getPrimary(), root, plan.producer()));
			types.add((ArrayType) node(root, plan.returnType()));
		}
		for (var consumer : expected) {
			types.add((ArrayType) node(root, consumer.localType()));
			for (Range read : consumer.lengths()) {
				lengths.add((QualifiedName) node(root, read));
			}
		}
		if (types.isEmpty()) {
			return null;
		}
		operations.add(new CompilationUnitRewriteOperationWithSourceRange() {
			@Override
			public void rewriteASTInternal(CompilationUnitRewrite cu, LinkedProposalModelCore linked) {
				var ast= cu.getRoot().getAST();
				var group= createTextEditGroup(DESCRIPTION, cu);
				String list= cu.getImportRewrite().addImport(plan.producer().targetInterfaceType());
				for (ArrayType type : types) {
					ParameterizedType replacement= ast.newParameterizedType(ast.newSimpleType(ast.newName(list)));
					replacement.typeArguments().add(ASTNode.copySubtree(ast, type.getElementType()));
					cu.getASTRewrite().replace(type, replacement, group);
				}
				for (QualifiedName length : lengths) {
					var size= ast.newMethodInvocation();
					size.setExpression((Expression) ASTNode.copySubtree(ast, length.getQualifier()));
					size.setName(ast.newSimpleName("size")); //$NON-NLS-1$
					cu.getASTRewrite().replace(length, size, group);
				}
			}

			@Override
			public String getAdditionalInfo() {
				return DESCRIPTION;
			}
		});
		return new CompilationUnitRewriteOperationsFixCore(DESCRIPTION, root,
				operations.toArray(CompilationUnitRewriteOperationWithSourceRange[]::new));
	}

	private static ASTNode node(CompilationUnit root, Range range) {
		return NodeFinder.perform(root, range.start(), range.length());
	}
}

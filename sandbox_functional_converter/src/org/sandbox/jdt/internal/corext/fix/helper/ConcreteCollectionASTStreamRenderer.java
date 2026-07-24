/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import org.sandbox.functional.core.terminal.CollectTerminal;

/** Renders behavior-preserving concrete collection suppliers when present. */
final class ConcreteCollectionASTStreamRenderer extends ASTStreamRenderer {

	private static final String CONSTRUCTOR_SUFFIX= "::new"; //$NON-NLS-1$

	private final AST ast;

	ConcreteCollectionASTStreamRenderer(AST ast, ASTRewrite rewrite, CompilationUnit compilationUnit,
			Statement originalBody) {
		super(ast, rewrite, compilationUnit, originalBody);
		this.ast= ast;
	}

	@Override
	public Expression renderCollect(Expression pipeline, CollectTerminal terminal, String variableName) {
		if (!terminal.hasCollectionFactory()) {
			return super.renderCollect(pipeline, terminal, variableName);
		}
		String factory= terminal.collectionFactory();
		if (!factory.endsWith(CONSTRUCTOR_SUFFIX)) {
			throw new IllegalArgumentException("Unsupported collection factory: " + factory); //$NON-NLS-1$
		}
		String qualifiedTypeName= factory.substring(0, factory.length() - CONSTRUCTOR_SUFFIX.length());

		CreationReference constructorReference= ast.newCreationReference();
		SimpleType type= ast.newSimpleType(ast.newName(qualifiedTypeName));
		constructorReference.setType(type);

		MethodInvocation collector= ast.newMethodInvocation();
		collector.setExpression(ast.newSimpleName("Collectors")); //$NON-NLS-1$
		collector.setName(ast.newSimpleName("toCollection")); //$NON-NLS-1$
		collector.arguments().add(constructorReference);

		MethodInvocation collectCall= ast.newMethodInvocation();
		collectCall.setExpression(pipeline);
		collectCall.setName(ast.newSimpleName("collect")); //$NON-NLS-1$
		collectCall.arguments().add(collector);
		return collectCall;
	}
}

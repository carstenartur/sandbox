/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.sandbox.jdt.internal.corext.fix.helper.IteratorPatternDetector.IteratorPattern;

/** Proves the iterator is used only by its declaration, condition and initial next. */
final class IteratorLoopBindings {
	private static final String REFERENCES = IteratorLoopBindings.class.getName();
	private record References(Map<String, List<SimpleName>> byBinding) { }
	private IteratorLoopBindings() { }

	static String sourceElementType(ITypeBinding type) {
		ITypeBinding iterable = Bindings.findTypeInHierarchy(type, "java.lang.Iterable"); //$NON-NLS-1$
		return iterable.getTypeArguments().length == 1 ? iterable.getTypeArguments()[0].getQualifiedName() : "java.lang.Object"; //$NON-NLS-1$
	}

	static boolean denotableIterator(ITypeBinding source) {
		ITypeBinding iterable = Bindings.findTypeInHierarchy(source, "java.lang.Iterable"); //$NON-NLS-1$
		if (hasTypeAnnotations(iterable, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()))) return false;
		for (ITypeBinding argument : iterable.getTypeArguments()) {
			// An expression captures wildcards; the declaration must keep the original wildcard.
			if (argument.isCapture()) argument = argument.getWildcard();
			if (!(argument.isWildcardType() ? argument.getBound() == null || JdtStreamExtractor.denotable(argument.getBound())
					: JdtStreamExtractor.denotable(argument))) return false;
		}
		return true;
	}

	/** Inherited binary annotations can be inaccessible and must not leak into source. */
	private static boolean hasTypeAnnotations(ITypeBinding type, Set<ITypeBinding> visited) {
		if (type == null || !visited.add(type)) return false;
		if (type.getTypeAnnotations().length != 0
				|| hasTypeAnnotations(type.getComponentType(), visited)
				|| hasTypeAnnotations(type.getDeclaringClass(), visited)
				|| hasTypeAnnotations(type.getBound(), visited)
				|| hasTypeAnnotations(type.getWildcard(), visited)) return true;
		for (ITypeBinding argument : type.getTypeArguments()) {
			if (hasTypeAnnotations(argument, visited)) return true;
		}
		return false;
	}

	static VariableDeclarationFragment element(Statement loop, Statement previous, IteratorPattern pattern) {
		if (pattern == null || LoopConversionService.hasErrors(loop) || previous != null && LoopConversionService.hasErrors(previous)
				|| !(pattern.loopBody() instanceof Block body) || body.statements().isEmpty()
				|| !(body.statements().get(0) instanceof VariableDeclarationStatement item) || item.fragments().size() != 1) return null;
		ITypeBinding source = pattern.collectionExpression().resolveTypeBinding();
		if (source == null || source.isRecovered() || Bindings.findTypeInHierarchy(source, "java.lang.Iterable") == null) return null; //$NON-NLS-1$
		VariableDeclarationFragment element = (VariableDeclarationFragment) item.fragments().get(0);
		if (element.resolveBinding() == null || item.getType().isVar() && !JdtStreamExtractor.denotable(element.resolveBinding().getType())) return null;
		ITypeBinding iterable = Bindings.findTypeInHierarchy(source, "java.lang.Iterable"); //$NON-NLS-1$
		// A typed iterator may have come from an unchecked assignment of a raw list.
		// Enhanced-for cannot assign that list's Object elements to its narrower variable.
		if (iterable.isRawType() && !"java.lang.Object".equals(element.resolveBinding().getType().getQualifiedName())) return null; //$NON-NLS-1$
		if (!(element.getInitializer() instanceof MethodInvocation next) || !next.arguments().isEmpty()
				|| !"next".equals(next.getName().getIdentifier()) || !(next.getExpression() instanceof SimpleName nextReceiver)) return null; //$NON-NLS-1$
		Expression condition = loop instanceof WhileStatement whileLoop ? whileLoop.getExpression() : ((ForStatement) loop).getExpression();
		if (!(condition instanceof MethodInvocation hasNext) || !hasNext.arguments().isEmpty()
				|| !(hasNext.getExpression() instanceof SimpleName conditionReceiver)) return null;
		VariableDeclarationFragment declaration = loop instanceof ForStatement forLoop
				? (VariableDeclarationFragment) ((VariableDeclarationExpression) forLoop.initializers().get(0)).fragments().get(0)
				: (VariableDeclarationFragment) ((VariableDeclarationStatement) previous).fragments().get(0);
		IVariableBinding iterator = declaration.resolveBinding();
		if (iterator == null || iterator.isRecovered() || !iterator.isEqualTo(nextReceiver.resolveBinding())
				|| !iterator.isEqualTo(conditionReceiver.resolveBinding()) || Bindings.findTypeInHierarchy(iterator.getType(), "java.util.Iterator") == null) return null; //$NON-NLS-1$
		Set<SimpleName> protocol = Set.of(declaration.getName(), conditionReceiver, nextReceiver);
		return references(loop.getRoot(), iterator).stream().allMatch(protocol::contains) ? element : null;
	}

	/** Index immutable source bindings once per AST, instead of rescanning the unit per loop. */
	private static List<SimpleName> references(ASTNode root, IVariableBinding variable) {
		synchronized (root) {
			if (!(root.getProperty(REFERENCES) instanceof References)) {
				Map<String, List<SimpleName>> index = new HashMap<>();
				root.accept(new ASTVisitor() {
					@Override
					public boolean visit(SimpleName name) {
						if (name.resolveBinding() instanceof IVariableBinding binding) {
							index.computeIfAbsent(binding.getVariableDeclaration().getKey(), key -> new ArrayList<>()).add(name);
						}
						return true;
					}
				});
				root.setProperty(REFERENCES, new References(index));
			}
			return ((References) root.getProperty(REFERENCES)).byBinding().getOrDefault(variable.getVariableDeclaration().getKey(), List.of());
		}
	}
}

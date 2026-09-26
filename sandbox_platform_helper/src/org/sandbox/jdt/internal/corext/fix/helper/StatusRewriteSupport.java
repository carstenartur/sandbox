/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;

/** Target-API and binding housekeeping for the actual Status rewrite only. */
final class StatusRewriteSupport {
	private static final String STATE_KEY= StatusRewriteSupport.class.getName();

	private StatusRewriteSupport() {
	}

	/** Do not drop evaluation or orphan a documented private constant. */
	static boolean canDiscard(Expression expression) {
		if (!ASTNodes.isPassive(expression)) {
			return false;
		}
		boolean[] documented= { false };
		expression.accept(new ASTVisitor(true) {
			@Override
			public boolean visit(SimpleName name) {
				if (name.resolveBinding() instanceof IVariableBinding variable
						&& variable.isField() && Modifier.isPrivate(variable.getModifiers())
						&& variable.getConstantValue() != null) {
					expression.getRoot().accept(new ASTVisitor(true) {
						@Override
						public boolean visit(SimpleName reference) {
							if (variable.isEqualTo(reference.resolveBinding())
									&& ASTNodes.getFirstAncestorOrNull(reference, Javadoc.class) != null) {
								documented[0]= true;
							}
							return !documented[0];
						}
					});
				}
				return !documented[0];
			}
		});
		return !documented[0];
	}

	static boolean hasPreservingConstructor(ClassInstanceCreation creation) {
		IMethodBinding original= creation.resolveConstructorBinding();
		if (original == null || original.isRecovered()) {
			return false;
		}
		ITypeBinding[] old= original.getParameterTypes();
		if (old.length != 5) {
			return false;
		}
		for (IMethodBinding candidate : original.getDeclaringClass().getDeclaredMethods()) {
			ITypeBinding[] parameters= candidate.getParameterTypes();
			if (candidate.isConstructor() && !candidate.isRecovered() && Modifier.isPublic(candidate.getModifiers())
					&& parameters.length == 4
					&& parameters[0].getErasure().isEqualTo(old[0].getErasure())
					&& parameters[1].getErasure().isEqualTo(old[1].getErasure())
					&& parameters[2].getErasure().isEqualTo(old[3].getErasure())
					&& parameters[3].getErasure().isEqualTo(old[4].getErasure())
					&& candidate.getExceptionTypes().length == 0) {
				return true;
			}
		}
		return false;
	}

	/** Avoid creating a newly unused local when a factory replaces an allocation. */
	static boolean canUseFactory(ClassInstanceCreation creation) {
		VariableDeclarationFragment fragment= localInitializer(creation);
		if (fragment == null || hasReference((CompilationUnit) creation.getRoot(), fragment, Set.of())) {
			return true;
		}
		VariableDeclarationStatement declaration= (VariableDeclarationStatement) fragment.getParent();
		// Preserve annotated declarations and multi-fragment evaluation order by
		// using the shorter constructor there rather than a discarded local binding.
		return declaration.fragments().size() == 1 && declaration.modifiers().stream().allMatch(Modifier.class::isInstance);
	}

	static void replaceWithFactory(CompilationUnitRewrite cuRewrite, ClassInstanceCreation creation,
			MethodInvocation factory, TextEditGroup group) {
		VariableDeclarationFragment fragment= localInitializer(creation);
		if (fragment != null && !hasReference(cuRewrite.getRoot(), fragment, Set.of())) {
			VariableDeclarationStatement declaration= (VariableDeclarationStatement) fragment.getParent();
			ASTNodes.replaceButKeepComment(cuRewrite.getASTRewrite(), declaration,
					creation.getAST().newExpressionStatement(factory), group);
			cuRewrite.getImportRemover().registerRemovedNode(declaration);
		} else {
			ASTNodes.replaceButKeepComment(cuRewrite.getASTRewrite(), creation, factory, group);
		}
	}

	static void finish(CompilationUnitRewrite cuRewrite, ClassInstanceCreation creation,
			List<Expression> retained, List<Expression> discarded, TextEditGroup group, String... addedImports) {
		var remover= cuRewrite.getImportRemover();
		remover.registerRemovedNode(creation);
		retained.forEach(expression -> remover.registerRetainedNode(ASTNodes.getUnparenthesedExpression(expression)));
		for (String type : addedImports) {
			remover.registerAddedImport(type);
		}
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		State state= (State) rewrite.getProperty(STATE_KEY);
		if (state == null) {
			state= new State();
			rewrite.setProperty(STATE_KEY, state);
		}
		for (Expression expression : discarded) {
			state.removed.add(expression);
			state.collect(cuRewrite.getRoot(), expression);
		}
		state.removeUnreferencedConstants(cuRewrite, group);
	}

	private static VariableDeclarationFragment localInitializer(Expression expression) {
		ASTNode current= expression;
		while (current.getParent() instanceof ParenthesizedExpression) {
			current= current.getParent();
		}
		if (current.getParent() instanceof VariableDeclarationFragment fragment
				&& fragment.getInitializer() == current
				&& fragment.getParent() instanceof VariableDeclarationStatement) {
			return fragment;
		}
		return null;
	}

	private static boolean hasReference(CompilationUnit root, VariableDeclarationFragment declaration,
			Set<ASTNode> removed) {
		IVariableBinding binding= declaration.resolveBinding();
		if (binding == null || binding.isRecovered()) {
			return true;
		}
		boolean[] found= { false };
		root.accept(new ASTVisitor(true) {
			@Override
			public boolean preVisit2(ASTNode node) {
				return !found[0] && !removed.contains(node);
			}

			@Override
			public boolean visit(SimpleName name) {
				if (name != declaration.getName() && name.getIdentifier().equals(binding.getName())) {
					var reference= name.resolveBinding();
					found[0]= reference == null || reference.isRecovered() || binding.isEqualTo(reference);
				}
				return !found[0];
			}
		});
		return found[0];
	}

	private static final class State {
		final Set<ASTNode> removed= Collections.newSetFromMap(new IdentityHashMap<>());
		final Set<VariableDeclarationFragment> candidates= new LinkedHashSet<>();

		void collect(CompilationUnit root, ASTNode expression) {
			expression.accept(new ASTVisitor(true) {
				@Override
				public boolean visit(SimpleName name) {
					if (name.resolveBinding() instanceof IVariableBinding variable && !variable.isRecovered()
							&& variable.isField() && Modifier.isPrivate(variable.getModifiers())
							&& Modifier.isFinal(variable.getModifiers()) && variable.getConstantValue() != null
							&& root.findDeclaringNode(variable.getVariableDeclaration()) instanceof VariableDeclarationFragment fragment
							&& fragment.getParent() instanceof FieldDeclaration field
							&& field.modifiers().stream().allMatch(Modifier.class::isInstance)
							&& fragment.getInitializer() != null && ASTNodes.isPassive(fragment.getInitializer())) {
						candidates.add(fragment);
					}
					return true;
				}
			});
		}

		void removeUnreferencedConstants(CompilationUnitRewrite cuRewrite, TextEditGroup group) {
			boolean changed;
			do {
				changed= false;
				for (VariableDeclarationFragment fragment : List.copyOf(candidates)) {
					if (removed.contains(fragment) || hasReference(cuRewrite.getRoot(), fragment, removed)) {
						continue;
					}
					FieldDeclaration field= (FieldDeclaration) fragment.getParent();
					var fragments= cuRewrite.getASTRewrite().getListRewrite(field, FieldDeclaration.FRAGMENTS_PROPERTY);
					ASTNode deleted= fragments.getRewrittenList().size() == 1 ? field : fragment;
					ASTNodes.removeButKeepComment(cuRewrite.getASTRewrite(), deleted, group);
					cuRewrite.getImportRemover().registerRemovedNode(deleted);
					removed.add(fragment);
					removed.add(deleted);
					collect(cuRewrite.getRoot(), fragment.getInitializer());
					changed= true;
				}
			} while (changed);
		}
	}
}

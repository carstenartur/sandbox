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
package org.sandbox.jdt.triggerpattern.internal;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/** Placeholder-aware matcher for header-only type declaration patterns. */
public final class TypeDeclarationHeaderMatcher extends PlaceholderAstMatcher {

	@Override
	public boolean match(TypeDeclaration pattern, Object other) {
		if (!(other instanceof TypeDeclaration candidate)
				|| pattern.isInterface() != candidate.isInterface()) {
			return false;
		}
		return matchCommon(pattern, candidate)
				&& matchOptionalType(pattern.getSuperclassType(), candidate.getSuperclassType())
				&& matchTypeSubset(pattern.superInterfaceTypes(), candidate.superInterfaceTypes())
				&& matchOptionalList(pattern.typeParameters(), candidate.typeParameters());
	}

	@Override
	public boolean match(EnumDeclaration pattern, Object other) {
		return other instanceof EnumDeclaration candidate
				&& matchCommon(pattern, candidate)
				&& matchTypeSubset(pattern.superInterfaceTypes(), candidate.superInterfaceTypes());
	}

	@Override
	public boolean match(RecordDeclaration pattern, Object other) {
		return other instanceof RecordDeclaration candidate
				&& matchCommon(pattern, candidate)
				&& matchTypeSubset(pattern.superInterfaceTypes(), candidate.superInterfaceTypes())
				&& matchOptionalList(pattern.typeParameters(), candidate.typeParameters())
				&& matchOptionalList(pattern.recordComponents(), candidate.recordComponents());
	}

	@Override
	public boolean match(AnnotationTypeDeclaration pattern, Object other) {
		return other instanceof AnnotationTypeDeclaration candidate
				&& matchCommon(pattern, candidate);
	}

	private boolean matchCommon(org.eclipse.jdt.core.dom.AbstractTypeDeclaration pattern,
			org.eclipse.jdt.core.dom.AbstractTypeDeclaration candidate) {
		return pattern.getName().subtreeMatch(this, candidate.getName())
				&& matchModifierSubset(pattern.modifiers(), candidate.modifiers());
	}

	private boolean matchModifierSubset(List<?> patternModifiers, List<?> candidateModifiers) {
		for (Object patternModifier : patternModifiers) {
			if (!(patternModifier instanceof IExtendedModifier required)) {
				return false;
			}
			boolean found= false;
			for (Object candidateModifier : candidateModifiers) {
				if (candidateModifier instanceof IExtendedModifier actual
						&& ((ASTNode) required).subtreeMatch(this, actual)) {
					found= true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	private boolean matchOptionalType(Type pattern, Type candidate) {
		return pattern == null || candidate != null && matchType(pattern, candidate);
	}

	private boolean matchTypeSubset(List<?> patternTypes, List<?> candidateTypes) {
		if (patternTypes.isEmpty()) {
			return true;
		}
		List<Object> unmatched= new ArrayList<>(candidateTypes);
		for (Object patternType : patternTypes) {
			if (!(patternType instanceof Type required)) {
				return false;
			}
			int matchingIndex= -1;
			for (int index= 0; index < unmatched.size(); index++) {
				if (unmatched.get(index) instanceof Type actual && matchType(required, actual)) {
					matchingIndex= index;
					break;
				}
			}
			if (matchingIndex < 0) {
				return false;
			}
			unmatched.remove(matchingIndex);
		}
		return true;
	}

	private boolean matchType(Type pattern, Type candidate) {
		if (pattern.subtreeMatch(this, candidate)) {
			return true;
		}
		ITypeBinding binding= candidate.resolveBinding();
		if (binding == null) {
			return false;
		}
		ITypeBinding erasure= binding.getErasure();
		ITypeBinding declaration= (erasure == null ? binding : erasure).getTypeDeclaration();
		if (declaration == null) {
			return false;
		}
		String expected= pattern.toString().replace(" ", ""); //$NON-NLS-1$ //$NON-NLS-2$
		return expected.equals(declaration.getQualifiedName()) || expected.equals(declaration.getName());
	}

	private boolean matchOptionalList(List<?> patternNodes, List<?> candidateNodes) {
		if (patternNodes.isEmpty()) {
			return true;
		}
		if (patternNodes.size() != candidateNodes.size()) {
			return false;
		}
		for (int index= 0; index < patternNodes.size(); index++) {
			if (!(patternNodes.get(index) instanceof ASTNode pattern)
					|| !(candidateNodes.get(index) instanceof ASTNode candidate)
					|| !pattern.subtreeMatch(this, candidate)) {
				return false;
			}
		}
		return true;
	}
}

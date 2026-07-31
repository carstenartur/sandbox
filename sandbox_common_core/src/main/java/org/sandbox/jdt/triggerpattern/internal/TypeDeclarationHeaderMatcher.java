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
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/** Placeholder-aware matcher for header-only type declaration patterns. */
public final class TypeDeclarationHeaderMatcher extends PlaceholderAstMatcher {

	private boolean caseInsensitive;

	@Override
	public void setCaseInsensitive(boolean caseInsensitive) {
		super.setCaseInsensitive(caseInsensitive);
		this.caseInsensitive= caseInsensitive;
	}

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

	private boolean matchCommon(AbstractTypeDeclaration pattern,
			AbstractTypeDeclaration candidate) {
		return pattern.getName().subtreeMatch(this, candidate.getName())
				&& matchModifierSubset(pattern.modifiers(), candidate.modifiers());
	}

	private boolean matchModifierSubset(List<?> patternModifiers, List<?> candidateModifiers) {
		List<Object> unmatched= new ArrayList<>(candidateModifiers);
		for (Object patternModifier : patternModifiers) {
			if (!(patternModifier instanceof IExtendedModifier required)) {
				return false;
			}
			int matchingIndex= -1;
			TypeDeclarationHeaderMatcher successfulMatcher= null;
			for (int index= 0; index < unmatched.size(); index++) {
				Object candidateModifier= unmatched.get(index);
				if (!(candidateModifier instanceof IExtendedModifier actual)) {
					continue;
				}
				TypeDeclarationHeaderMatcher trial= speculativeMatcher();
				if (((ASTNode) required).subtreeMatch(trial, actual)) {
					matchingIndex= index;
					successfulMatcher= trial;
					break;
				}
			}
			if (matchingIndex < 0) {
				return false;
			}
			mergeBindings(successfulMatcher);
			unmatched.remove(matchingIndex);
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
			TypeDeclarationHeaderMatcher successfulMatcher= null;
			for (int index= 0; index < unmatched.size(); index++) {
				if (!(unmatched.get(index) instanceof Type actual)) {
					continue;
				}
				TypeDeclarationHeaderMatcher trial= speculativeMatcher();
				if (trial.matchType(required, actual)) {
					matchingIndex= index;
					successfulMatcher= trial;
					break;
				}
			}
			if (matchingIndex < 0) {
				return false;
			}
			mergeBindings(successfulMatcher);
			unmatched.remove(matchingIndex);
		}
		return true;
	}

	private boolean matchType(Type pattern, Type candidate) {
		if (pattern instanceof ParameterizedType patternParameterized) {
			if (!(candidate instanceof ParameterizedType candidateParameterized)) {
				return false;
			}
			return matchType(patternParameterized.getType(), candidateParameterized.getType())
					&& matchTypeList(patternParameterized.typeArguments(),
							candidateParameterized.typeArguments());
		}
		if (candidate instanceof ParameterizedType candidateParameterized) {
			return matchType(pattern, candidateParameterized.getType());
		}
		TypeDeclarationHeaderMatcher structural= speculativeMatcher();
		if (pattern.subtreeMatch(structural, candidate)) {
			mergeBindings(structural);
			return true;
		}
		return bindingEquivalent(pattern, candidate);
	}

	private boolean matchTypeList(List<?> patternTypes, List<?> candidateTypes) {
		if (patternTypes.size() != candidateTypes.size()) {
			return false;
		}
		for (int index= 0; index < patternTypes.size(); index++) {
			if (!(patternTypes.get(index) instanceof Type pattern)
					|| !(candidateTypes.get(index) instanceof Type candidate)
					|| !matchType(pattern, candidate)) {
				return false;
			}
		}
		return true;
	}

	private static boolean bindingEquivalent(Type pattern, Type candidate) {
		ITypeBinding binding= candidate.resolveBinding();
		if (binding == null) {
			return false;
		}
		ITypeBinding erasure= binding.getErasure();
		ITypeBinding declaration= (erasure == null ? binding : erasure).getTypeDeclaration();
		if (declaration == null) {
			return false;
		}
		String expected= removeWhitespace(pattern.toString());
		return expected.equals(declaration.getQualifiedName())
				|| expected.equals(declaration.getName());
	}

	private static String removeWhitespace(String value) {
		StringBuilder result= new StringBuilder(value.length());
		value.codePoints().filter(character -> !Character.isWhitespace(character))
				.forEach(result::appendCodePoint);
		return result.toString();
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

	private TypeDeclarationHeaderMatcher speculativeMatcher() {
		TypeDeclarationHeaderMatcher matcher= new TypeDeclarationHeaderMatcher();
		matcher.setCaseInsensitive(caseInsensitive);
		matcher.mergeBindings(this);
		return matcher;
	}
}

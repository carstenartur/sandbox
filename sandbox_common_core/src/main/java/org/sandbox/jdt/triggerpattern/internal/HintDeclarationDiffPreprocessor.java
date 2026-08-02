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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;

import org.sandbox.jdt.triggerpattern.api.HintPlanRequirement;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/**
 * Lowers unambiguous plan-aware method declaration diffs to structured actions.
 *
 * <p>This is deliberately a small compiler, not a general Java text rewriter.
 * It accepts one-line method headers without annotations, bodies, varargs,
 * generic parameters or changed throws/return contracts. The supported surface
 * covers method renames, modifier changes, retained-parameter type changes,
 * parameter removals and append-only parameter additions. Anything ambiguous
 * fails with a source diagnostic rather than silently becoming a text rewrite.</p>
 */
final class HintDeclarationDiffPreprocessor {

	private HintDeclarationDiffPreprocessor() {
	}

	static String preprocess(String source) throws HintParseException {
		String normalized= source == null ? "" : source.replace("\r\n", "\n").replace('\r', '\n'); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		if (HintPlanRequirement.fromContent(normalized).isEmpty()) {
			return normalized;
		}
		String[] lines= normalized.split("\n", -1); //$NON-NLS-1$
		List<String> output= new ArrayList<>(lines.length);
		boolean inBlockComment= false;
		boolean inEmbeddedJava= false;
		int ruleStart= 0;

		for (int lineIndex= 0; lineIndex < lines.length; lineIndex++) {
			String line= lines[lineIndex];
			String trimmed= line.stripLeading();
			boolean visible= true;
			if (inEmbeddedJava) {
				visible= false;
				if (trimmed.contains("?>")) { //$NON-NLS-1$
					inEmbeddedJava= false;
				}
			} else if (inBlockComment) {
				visible= false;
				if (trimmed.contains("*/")) { //$NON-NLS-1$
					inBlockComment= false;
				}
			} else if (trimmed.startsWith("<?")) { //$NON-NLS-1$
				visible= false;
				inEmbeddedJava= !trimmed.contains("?>"); //$NON-NLS-1$
			} else if (trimmed.startsWith("/*")) { //$NON-NLS-1$
				visible= false;
				inBlockComment= !trimmed.contains("*/"); //$NON-NLS-1$
			} else if (trimmed.startsWith("//")) { //$NON-NLS-1$
				visible= false;
			}

			if (visible && trimmed.startsWith("=>") && !trimmed.startsWith("=>!")) { //$NON-NLS-1$ //$NON-NLS-2$
				int sourceIndex= previousSourceLine(output, ruleStart);
				if (sourceIndex >= 0) {
					ActionAndGuard sourceSplit= splitGuard(output.get(sourceIndex).stripLeading());
					ActionAndGuard targetSplit= splitGuard(trimmed.substring(2).trim());
					Compilation compilation= compile(sourceSplit.actionText(), targetSplit.actionText(),
							sourceIndex + 1, lineIndex + 1);
					if (compilation != null && !compilation.actions().isEmpty()) {
						String indentation= line.substring(0, line.length() - trimmed.length());
						appendActions(output, indentation, compilation.actions(), targetSplit.guardText());
						continue;
					}
				}
			}

			output.add(line);
			if (visible && ";;".equals(trimmed)) { //$NON-NLS-1$
				ruleStart= output.size();
			}
		}
		return String.join("\n", output); //$NON-NLS-1$
	}

	private static int previousSourceLine(List<String> output, int ruleStart) {
		for (int index= output.size() - 1; index >= ruleStart; index--) {
			String candidate= output.get(index).stripLeading();
			if (candidate.isBlank() || candidate.startsWith("@") //$NON-NLS-1$
					|| candidate.startsWith("//") || candidate.startsWith("/*") //$NON-NLS-1$ //$NON-NLS-2$
					|| candidate.startsWith("\"") || candidate.startsWith("<!")) { //$NON-NLS-1$ //$NON-NLS-2$
				continue;
			}
			return index;
		}
		return -1;
	}

	private static void appendActions(List<String> output, String indentation,
			List<String> actions, String guard) {
		for (int index= 0; index < actions.size(); index++) {
			String prefix= index == 0 ? "=>! " : "    "; //$NON-NLS-1$ //$NON-NLS-2$
			String suffix= index + 1 < actions.size() ? ";" : ""; //$NON-NLS-1$ //$NON-NLS-2$
			if (index + 1 == actions.size() && guard != null) {
				suffix+= " :: " + guard.trim(); //$NON-NLS-1$
			}
			output.add(indentation + prefix + actions.get(index) + suffix);
		}
	}

	private static Compilation compile(String sourceText, String targetText,
			int sourceLine, int targetLine) throws HintParseException {
		MethodShape source= MethodShape.parse(sourceText);
		MethodShape target= MethodShape.parse(targetText);
		if (source == null || target == null) {
			return null;
		}
		if (source.hasAnnotations() || target.hasAnnotations()) {
			return null;
		}
		if (source.constructor() || target.constructor()) {
			return null;
		}
		if (!source.returnType().equals(target.returnType())) {
			throw new HintParseException(
					"Method declaration diff cannot change the return type yet; use a dedicated typed action", //$NON-NLS-1$
					targetLine);
		}
		if (!source.typeParameters().equals(target.typeParameters())
				|| !source.thrownTypes().equals(target.thrownTypes())) {
			throw new HintParseException(
					"Method declaration diff cannot change type parameters or thrown exceptions", targetLine); //$NON-NLS-1$
		}
		if (source.parameters().stream().anyMatch(ParameterShape::varargs)
				|| target.parameters().stream().anyMatch(ParameterShape::varargs)) {
			return null;
		}

		List<String> actions= new ArrayList<>();
		compileName(source, target, actions, targetLine);
		compileModifiers(source, target, actions);
		compileParameters(source, target, actions, sourceLine, targetLine);
		return new Compilation(actions);
	}

	private static void compileName(MethodShape source, MethodShape target,
			List<String> actions, int targetLine) throws HintParseException {
		if (source.name().equals(target.name())) {
			return;
		}
		if (target.name().startsWith("$")) { //$NON-NLS-1$
			throw new HintParseException(
					"A changed method name must be a concrete target identifier", targetLine); //$NON-NLS-1$
		}
		actions.add("renameDeclaration(name=" + quote(target.name()) + ")"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void compileModifiers(MethodShape source, MethodShape target,
			List<String> actions) {
		for (String modifier : source.modifiers()) {
			if (!target.modifiers().contains(modifier)) {
				actions.add("removeModifier(modifier=" + modifier + ")"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		for (String modifier : target.modifiers()) {
			if (!source.modifiers().contains(modifier)) {
				actions.add("addModifier(modifier=" + modifier + ")"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
	}

	private static void compileParameters(MethodShape source, MethodShape target,
			List<String> actions, int sourceLine, int targetLine) throws HintParseException {
		Map<String, ParameterShape> sourceByName= uniqueParameters(source.parameters(), sourceLine);
		Map<String, ParameterShape> targetByName= uniqueParameters(target.parameters(), targetLine);

		List<String> retainedInSource= source.parameters().stream().map(ParameterShape::name)
				.filter(targetByName::containsKey).toList();
		List<String> retainedInTarget= target.parameters().stream().map(ParameterShape::name)
				.filter(sourceByName::containsKey).toList();
		if (!retainedInSource.equals(retainedInTarget)) {
			throw new HintParseException(
					"Method declaration diff cannot reorder retained parameters; bind them in their original order", //$NON-NLS-1$
					targetLine);
		}

		boolean seenAdded= false;
		for (ParameterShape parameter : target.parameters()) {
			if (!sourceByName.containsKey(parameter.name())) {
				seenAdded= true;
				if (parameter.name().startsWith("$")) { //$NON-NLS-1$
					throw new HintParseException(
							"New parameters need a concrete target name", targetLine); //$NON-NLS-1$
				}
			} else if (seenAdded) {
				throw new HintParseException(
						"New parameters are currently append-only in declaration diffs", targetLine); //$NON-NLS-1$
			}
		}

		for (ParameterShape sourceParameter : source.parameters()) {
			ParameterShape targetParameter= targetByName.get(sourceParameter.name());
			if (targetParameter == null) {
				actions.add("removeParameter(name=" + selector(sourceParameter.name()) + ")"); //$NON-NLS-1$ //$NON-NLS-2$
			} else if (!sourceParameter.type().equals(targetParameter.type())) {
				ensureSupportedType(targetParameter.type(), targetLine);
				actions.add("replaceParameterType(name=" + selector(sourceParameter.name()) //$NON-NLS-1$
						+ ", type=" + quote(targetParameter.type()) + ")"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		for (ParameterShape targetParameter : target.parameters()) {
			if (!sourceByName.containsKey(targetParameter.name())) {
				ensureSupportedType(targetParameter.type(), targetLine);
				actions.add("addParameter(type=" + quote(targetParameter.type()) //$NON-NLS-1$
						+ ", name=" + quote(targetParameter.name()) + ")"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
	}

	private static Map<String, ParameterShape> uniqueParameters(List<ParameterShape> parameters,
			int line) throws HintParseException {
		Map<String, ParameterShape> result= new LinkedHashMap<>();
		for (ParameterShape parameter : parameters) {
			if (result.putIfAbsent(parameter.name(), parameter) != null) {
				throw new HintParseException("Method declaration contains duplicate parameter name " //$NON-NLS-1$
						+ parameter.name(), line);
			}
		}
		return result;
	}

	private static void ensureSupportedType(String type, int line) throws HintParseException {
		if (type.indexOf('<') >= 0 || type.indexOf('>') >= 0 || type.indexOf('?') >= 0
				|| type.endsWith("...") || type.contains("$")) { //$NON-NLS-1$ //$NON-NLS-2$
			throw new HintParseException(
					"Declaration diff target type requires a dedicated typed representation: " + type, line); //$NON-NLS-1$
		}
	}

	private static String selector(String name) {
		return name.startsWith("$") ? name : quote(name); //$NON-NLS-1$
	}

	private static String quote(String value) {
		return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"'; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	private static ActionAndGuard splitGuard(String text) {
		int depth= 0;
		boolean inQuote= false;
		boolean escaped= false;
		for (int index= 0; index < text.length() - 1; index++) {
			char current= text.charAt(index);
			if (inQuote) {
				if (escaped) {
					escaped= false;
				} else if (current == '\\') {
					escaped= true;
				} else if (current == '"') {
					inQuote= false;
				}
				continue;
			}
			if (current == '"') {
				inQuote= true;
			} else if (current == '(') {
				depth++;
			} else if (current == ')') {
				depth--;
			} else if (depth == 0 && current == ':' && text.charAt(index + 1) == ':') {
				return new ActionAndGuard(text.substring(0, index), text.substring(index + 2));
			}
		}
		return new ActionAndGuard(text, null);
	}

	private record ActionAndGuard(String actionText, String guardText) {
	}

	private record Compilation(List<String> actions) {
		Compilation {
			actions= List.copyOf(actions);
		}
	}

	private record ParameterShape(String name, String type, boolean varargs) {
	}

	private record MethodShape(String name, String returnType, List<ParameterShape> parameters,
			Set<String> modifiers, List<String> typeParameters, List<String> thrownTypes,
			boolean hasAnnotations, boolean constructor) {

		static MethodShape parse(String text) {
			if (text == null || text.isBlank() || text.contains("{") || text.contains("}")) { //$NON-NLS-1$ //$NON-NLS-2$
				return null;
			}
			ASTNode parsed= new PatternParser().parse(Pattern.of(text.trim(), PatternKind.METHOD_DECLARATION));
			if (!(parsed instanceof MethodDeclaration method)) {
				return null;
			}
			boolean annotations= method.modifiers().stream().anyMatch(Annotation.class::isInstance);
			Set<String> modifiers= new LinkedHashSet<>();
			for (Object modifierObject : method.modifiers()) {
				if (modifierObject instanceof Modifier modifier) {
					modifiers.add(modifier.getKeyword().toString());
				}
			}
			List<ParameterShape> parameters= new ArrayList<>();
			for (Object parameterObject : method.parameters()) {
				SingleVariableDeclaration parameter= (SingleVariableDeclaration) parameterObject;
				String type= parameter.getType().toString();
				for (int dimension= 0; dimension < parameter.getExtraDimensions(); dimension++) {
					type+= "[]"; //$NON-NLS-1$
				}
				parameters.add(new ParameterShape(parameter.getName().getIdentifier(), type,
						parameter.isVarargs()));
			}
			String returnType= method.getReturnType2() == null ? "" : method.getReturnType2().toString(); //$NON-NLS-1$
			return new MethodShape(method.getName().getIdentifier(), returnType, parameters,
					Set.copyOf(modifiers), method.typeParameters().stream().map(Object::toString).toList(),
					method.thrownExceptionTypes().stream().map(Object::toString).toList(),
					annotations, method.isConstructor());
		}
	}
}

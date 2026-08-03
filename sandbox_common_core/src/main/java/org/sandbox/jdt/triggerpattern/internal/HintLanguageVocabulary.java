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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.RewriteActionCatalog;
import org.sandbox.jdt.triggerpattern.api.RewriteActionSchema;

/** Canonical internal vocabulary shared by parser tooling and the editor. */
public final class HintLanguageVocabulary {

	/** One metadata/declaration directive and its concise editor documentation. */
	public record Directive(String name, String syntax, String description) {
	}

	/** One schema-validated structured rewrite action. */
	public record Action(String name, String replacement, String syntax, String description) {
	}

	private static final List<Directive> DIRECTIVES= List.of(
			new Directive("id", "<!id: rule.id>", "Stable hint-program identifier"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			new Directive("description", "<!description: text>", "Human-readable description"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			new Directive("severity", "<!severity: info|warning|error|hint>", "Reported severity"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			new Directive("minJavaVersion", "<!minJavaVersion: 17>", "Minimum Java source version"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			new Directive("tags", "<!tags: a, b>", "Searchable program tags"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			new Directive("include", "<!include: other.program>", "Compose rules from another program"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			new Directive("caseInsensitive", "<!caseInsensitive>", "Case-insensitive literal matching"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			new Directive("suppressWarnings", "<!suppressWarnings: key>", "Recognized suppression keys"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			new Directive("treeKind", "<!treeKind: METHOD_DECLARATION>", "AST kinds considered by the matcher"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			new Directive("requires-plan", "<!requires-plan: contract-id>",
					"Required semantic-plan contract; also implies fail-closed semantic bindings"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			new Directive("foreach", "<!foreach NAME: source -> target>", "Generate rules from key/value pairs"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			new Directive("map", "<!map NAME: source => target>", "Reusable source/replacement mapping"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			new Directive("predicate", "<!predicate name($node): guard-expression>", //$NON-NLS-1$ //$NON-NLS-2$
					"Named parameterized guard expression; predicates may compose other predicates")); //$NON-NLS-1$

	private static final List<Action> ACTIONS= RewriteActionCatalog.standard().schemas().stream()
			.sorted(java.util.Comparator.comparing(RewriteActionSchema::name))
			.map(HintLanguageVocabulary::toAction)
			.toList();

	private static final List<String> OPERATORS= List.of("=>!", "=>", "::", ";;", "&&", "||", "!"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$

	private static final Map<String, String> DESCRIPTION_OVERRIDES= Map.ofEntries(
			Map.entry("plannedRole", "Matched or bound node has the supplied semantic-plan role"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("enclosingPlannedRole", "An enclosing node has the supplied semantic-plan role"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("plannedValue", "Matched or bound node has an equal typed semantic-plan value"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("enclosingPlannedValue", "A node or enclosing declaration has an equal typed plan value"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("plannedNodeValue", "A typed semantic-plan value references the supplied bound node"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("plannedListContains", "A typed semantic-plan list contains the supplied value"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("plannedRelation", "An exact directed semantic-plan relation connects two nodes"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("plannedRelationValue", "A semantic-plan relation has an equal typed attribute"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("plannedOutgoingRelation", "A node has an outgoing semantic-plan relation of a kind"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("plannedIncomingRelation", "A node has an incoming semantic-plan relation of a kind"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("plannedRelationCount", "A node has the exact number of outgoing planned relations"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("referencedIn", "A variable is referenced in another bound node"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("hasNoSideEffect", "Expression is side-effect free"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("sourceVersionBetween", "Source version is within the supplied range"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("genericTypeIs", "Generic type argument at an index matches a type"), //$NON-NLS-1$ //$NON-NLS-2$
			Map.entry("otherwise", "Always true catch-all alternative")); //$NON-NLS-1$ //$NON-NLS-2$

	private static final Set<String> BUILT_IN_GUARD_NAMES= loadBuiltInGuardNames();

	private HintLanguageVocabulary() {
	}

	public static List<Directive> directives() {
		return DIRECTIVES;
	}

	public static Set<String> directiveNames() {
		return DIRECTIVES.stream().map(Directive::name)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	public static List<Action> actions() {
		return ACTIONS;
	}

	public static Set<String> actionNames() {
		return ACTIONS.stream().map(Action::name)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	public static List<String> operators() {
		return OPERATORS;
	}

	public static String guardDescription(String name) {
		return DESCRIPTION_OVERRIDES.getOrDefault(name, humanize(name));
	}

	public static Set<String> builtInGuardNames() {
		return Set.copyOf(BUILT_IN_GUARD_NAMES);
	}

	private static Set<String> loadBuiltInGuardNames() {
		Map<String, GuardFunction> functions= new LinkedHashMap<>();
		BuiltInGuardRegistration.registerAll(functions);
		return Set.copyOf(functions.keySet());
	}

	private static Action toAction(RewriteActionSchema schema) {
		String replacementArguments= schema.requiredArguments().stream().sorted()
				.map(name -> name + "=") //$NON-NLS-1$
				.collect(java.util.stream.Collectors.joining(", ")); //$NON-NLS-1$
		String documentedRequired= schema.requiredArguments().stream().sorted()
				.map(name -> name + "=VALUE") //$NON-NLS-1$
				.collect(java.util.stream.Collectors.joining(", ")); //$NON-NLS-1$
		String documentedOptional= optionalSyntax(schema, !documentedRequired.isEmpty());
		String replacement= schema.name() + "(" + replacementArguments + ")"; //$NON-NLS-1$ //$NON-NLS-2$
		String syntax= "=>! " + schema.name() + "(" + documentedRequired + documentedOptional + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return new Action(schema.name(), replacement, syntax, schema.description());
	}

	private static String optionalSyntax(RewriteActionSchema schema, boolean followsRequired) {
		List<String> optional= schema.optionalArguments().stream().sorted().toList();
		StringBuilder result= new StringBuilder();
		for (int index= 0; index < optional.size(); index++) {
			String prefix= followsRequired || index > 0 ? ", " : ""; //$NON-NLS-1$ //$NON-NLS-2$
			result.append('[').append(prefix).append(optional.get(index)).append("=VALUE]"); //$NON-NLS-1$
		}
		return result.toString();
	}

	private static String humanize(String name) {
		if (name == null || name.isBlank()) {
			return "Registered guard or local predicate"; //$NON-NLS-1$
		}
		String words= name.replaceAll("([a-z0-9])([A-Z])", "$1 $2") //$NON-NLS-1$ //$NON-NLS-2$
				.replace('_', ' ').toLowerCase(Locale.ROOT);
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}
}

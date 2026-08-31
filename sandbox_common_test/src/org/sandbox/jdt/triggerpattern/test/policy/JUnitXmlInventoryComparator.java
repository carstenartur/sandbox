/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Compares the exact JUnit XML test identities, multiplicities and result
 * states produced before and after a source migration.
 *
 * <p>The comparator is deliberately independent of GitHub Actions. It is used
 * by Maven/JUnit fixtures so a locally retained upstream workspace and CI use
 * the same executable evidence contract.</p>
 *
 * @since 1.3.5
 */
final class JUnitXmlInventoryComparator {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private JUnitXmlInventoryComparator() {
		// Utility class.
	}

	static Comparison compare(Path baselineDirectory, Path migratedDirectory, Mapping mapping) throws IOException {
		Objects.requireNonNull(baselineDirectory, "baselineDirectory"); //$NON-NLS-1$
		Objects.requireNonNull(migratedDirectory, "migratedDirectory"); //$NON-NLS-1$
		Objects.requireNonNull(mapping, "mapping"); //$NON-NLS-1$

		Inventory baseline = collect(baselineDirectory);
		Inventory migrated = collect(migratedDirectory);
		Map<TestStateKey, Integer> expected = mappedBaseline(baseline.states(), mapping.renames());
		Map<TestStateKey, Integer> missing = subtract(expected, migrated.states());
		Map<TestStateKey, Integer> added = subtract(migrated.states(), expected);
		List<Difference> unexpectedMissing = unexpected(missing, mapping.allowedMissing());
		List<Difference> unexpectedAdded = unexpected(added, mapping.allowedAdded());

		List<String> problems = new ArrayList<>();
		if (baseline.reportFiles() == 0 || baseline.testCount() == 0) {
			problems.add("Baseline produced no parseable test inventory"); //$NON-NLS-1$
		}
		if (migrated.reportFiles() == 0 || migrated.testCount() == 0) {
			problems.add("Migrated run produced no parseable test inventory"); //$NON-NLS-1$
		}
		baseline.parseErrors().forEach(error -> problems.add("Baseline report parse error: " + error)); //$NON-NLS-1$
		migrated.parseErrors().forEach(error -> problems.add("Migrated report parse error: " + error)); //$NON-NLS-1$
		if (baseline.count(State.FAILURE) > 0 || baseline.count(State.ERROR) > 0) {
			problems.add("Baseline contains failing or errored tests"); //$NON-NLS-1$
		}
		if (migrated.count(State.FAILURE) > 0 || migrated.count(State.ERROR) > 0) {
			problems.add("Migrated run contains failing or errored tests"); //$NON-NLS-1$
		}
		if (!unexpectedMissing.isEmpty()) {
			problems.add("Tests disappeared or changed state after migration"); //$NON-NLS-1$
		}
		if (!unexpectedAdded.isEmpty()) {
			problems.add("Unexpected tests appeared or changed state after migration"); //$NON-NLS-1$
		}

		return new Comparison(summary(baseline), summary(migrated), unexpectedMissing,
				unexpectedAdded, List.copyOf(problems), problems.isEmpty() ? "PASS" : "FAIL"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	static Mapping readMapping(Path path) throws IOException {
		Objects.requireNonNull(path, "path"); //$NON-NLS-1$
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!parsed.isJsonObject()) {
				throw new IllegalArgumentException("Mapping document must be a JSON object: " + path); //$NON-NLS-1$
			}
			JsonObject object = parsed.getAsJsonObject();
			Map<String, String> renames = stringMap(object.get("renames"), "renames"); //$NON-NLS-1$ //$NON-NLS-2$
			List<String> allowedMissing = stringList(object.get("allowedMissing"), "allowedMissing"); //$NON-NLS-1$ //$NON-NLS-2$
			List<String> allowedAdded = stringList(object.get("allowedAdded"), "allowedAdded"); //$NON-NLS-1$ //$NON-NLS-2$
			return new Mapping(renames, allowedMissing, allowedAdded);
		}
	}

	private static Inventory collect(Path directory) throws IOException {
		List<Path> reports;
		if (!Files.isDirectory(directory)) {
			reports = List.of();
		} else {
			try (Stream<Path> files = Files.walk(directory)) {
				reports = files.filter(Files::isRegularFile)
						.filter(path -> path.getFileName().toString().endsWith(".xml")) //$NON-NLS-1$
						.sorted()
						.toList();
			}
		}

		Map<TestStateKey, Integer> states = new HashMap<>();
		List<String> parseErrors = new ArrayList<>();
		for (Path report : reports) {
			try {
				collectReport(report, states);
			} catch (IOException | ParserConfigurationException | SAXException failure) {
				parseErrors.add(report + ": " + message(failure)); //$NON-NLS-1$
			}
		}
		return new Inventory(Map.copyOf(states), reports.size(), List.copyOf(parseErrors));
	}

	private static void collectReport(Path report, Map<TestStateKey, Integer> states)
			throws IOException, ParserConfigurationException, SAXException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); //$NON-NLS-1$
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document document = builder.parse(report.toFile());
		Element root = document.getDocumentElement();
		if (root == null) {
			return;
		}
		if ("testsuite".equals(root.getTagName())) { //$NON-NLS-1$
			collectSuite(root, states);
		} else {
			NodeList suites = root.getElementsByTagName("testsuite"); //$NON-NLS-1$
			for (int index = 0; index < suites.getLength(); index++) {
				Node suite = suites.item(index);
				if (suite instanceof Element element) {
					collectSuite(element, states);
				}
			}
		}
	}

	private static void collectSuite(Element suite, Map<TestStateKey, Integer> states) {
		String suiteName = attribute(suite, "name", "<unnamed-suite>"); //$NON-NLS-1$ //$NON-NLS-2$
		NodeList children = suite.getChildNodes();
		for (int index = 0; index < children.getLength(); index++) {
			Node child = children.item(index);
			if (!(child instanceof Element testcase) || !"testcase".equals(testcase.getTagName())) { //$NON-NLS-1$
				continue;
			}
			String owner = attribute(testcase, "classname", suiteName); //$NON-NLS-1$
			String name = attribute(testcase, "name", "<unnamed-test>"); //$NON-NLS-1$ //$NON-NLS-2$
			TestStateKey key = new TestStateKey(owner + "#" + name, state(testcase)); //$NON-NLS-1$
			states.merge(key, Integer.valueOf(1), Integer::sum);
		}
	}

	private static State state(Element testcase) {
		if (directChild(testcase, "failure")) { //$NON-NLS-1$
			return State.FAILURE;
		}
		if (directChild(testcase, "error")) { //$NON-NLS-1$
			return State.ERROR;
		}
		if (directChild(testcase, "skipped")) { //$NON-NLS-1$
			return State.SKIPPED;
		}
		return State.PASSED;
	}

	private static boolean directChild(Element parent, String tagName) {
		NodeList children = parent.getChildNodes();
		for (int index = 0; index < children.getLength(); index++) {
			Node child = children.item(index);
			if (child instanceof Element element && tagName.equals(element.getTagName())) {
				return true;
			}
		}
		return false;
	}

	private static Map<TestStateKey, Integer> mappedBaseline(Map<TestStateKey, Integer> baseline,
			Map<String, String> renames) {
		Map<TestStateKey, Integer> mapped = new HashMap<>();
		baseline.forEach((key, count) -> {
			String identity = renames.getOrDefault(key.identity(), key.identity());
			mapped.merge(new TestStateKey(identity, key.state()), count, Integer::sum);
		});
		return mapped;
	}

	private static Map<TestStateKey, Integer> subtract(Map<TestStateKey, Integer> left,
			Map<TestStateKey, Integer> right) {
		Map<TestStateKey, Integer> difference = new HashMap<>();
		left.forEach((key, count) -> {
			int remaining = count.intValue() - right.getOrDefault(key, Integer.valueOf(0)).intValue();
			if (remaining > 0) {
				difference.put(key, Integer.valueOf(remaining));
			}
		});
		return difference;
	}

	private static List<Difference> unexpected(Map<TestStateKey, Integer> difference, List<String> allowed) {
		List<Pattern> patterns = allowed.stream().map(JUnitXmlInventoryComparator::glob).toList();
		return difference.entrySet().stream()
				.filter(entry -> patterns.stream().noneMatch(pattern -> pattern.matcher(entry.getKey().identity()).matches()))
				.sorted(Map.Entry.comparingByKey())
				.map(entry -> new Difference(entry.getKey().identity(), entry.getKey().state().value(),
						entry.getValue().intValue()))
				.toList();
	}

	private static Pattern glob(String expression) {
		StringBuilder regex = new StringBuilder("^"); //$NON-NLS-1$
		for (int index = 0; index < expression.length(); index++) {
			char character = expression.charAt(index);
			switch (character) {
				case '*' -> regex.append(".*"); //$NON-NLS-1$
				case '?' -> regex.append('.');
				case '[' -> index = appendCharacterClass(expression, index, regex);
				default -> {
					if ("\\.(){}+^$|".indexOf(character) >= 0) { //$NON-NLS-1$
						regex.append('\\');
					}
					regex.append(character);
				}
			}
		}
		return Pattern.compile(regex.append('$').toString(), Pattern.DOTALL);
	}

	private static int appendCharacterClass(String expression, int opening, StringBuilder regex) {
		int closing = expression.indexOf(']', opening + 1);
		if (closing < 0) {
			regex.append("\\["); //$NON-NLS-1$
			return opening;
		}
		String body = expression.substring(opening + 1, closing);
		if (body.isEmpty()) {
			regex.append("\\[\\]"); //$NON-NLS-1$
			return closing;
		}
		regex.append('[');
		int index = 0;
		if (body.charAt(0) == '!' || body.charAt(0) == '^') {
			regex.append('^');
			index++;
		}
		for (; index < body.length(); index++) {
			char character = body.charAt(index);
			if (character == '\\' || character == ']') {
				regex.append('\\');
			}
			regex.append(character);
		}
		regex.append(']');
		return closing;
	}

	private static RunSummary summary(Inventory inventory) {
		return new RunSummary(inventory.reportFiles(), inventory.testCount(), inventory.count(State.PASSED),
				inventory.count(State.SKIPPED), inventory.count(State.FAILURE), inventory.count(State.ERROR));
	}

	private static Map<String, String> stringMap(JsonElement element, String label) {
		if (element == null || !element.isJsonObject()) {
			throw new IllegalArgumentException(label + " must be a JSON object"); //$NON-NLS-1$
		}
		Map<String, String> values = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
				throw new IllegalArgumentException(label + " values must be strings"); //$NON-NLS-1$
			}
			values.put(entry.getKey(), entry.getValue().getAsString());
		}
		return Map.copyOf(values);
	}

	private static List<String> stringList(JsonElement element, String label) {
		if (element == null || !element.isJsonArray()) {
			throw new IllegalArgumentException(label + " must be a JSON array"); //$NON-NLS-1$
		}
		JsonArray array = element.getAsJsonArray();
		List<String> values = new ArrayList<>(array.size());
		for (JsonElement value : array) {
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
				throw new IllegalArgumentException(label + " entries must be strings"); //$NON-NLS-1$
			}
			values.add(value.getAsString());
		}
		return List.copyOf(values);
	}

	private static String attribute(Element element, String name, String fallback) {
		String value = element.getAttribute(name);
		return value.isEmpty() ? fallback : value;
	}

	private static String message(Throwable failure) {
		String message = failure.getMessage();
		return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
	}

	enum State {
		PASSED("passed"), //$NON-NLS-1$
		SKIPPED("skipped"), //$NON-NLS-1$
		FAILURE("failure"), //$NON-NLS-1$
		ERROR("error"); //$NON-NLS-1$

		private final String value;

		State(String value) {
			this.value = value;
		}

		String value() {
			return value;
		}
	}

	record TestStateKey(String identity, State state) implements Comparable<TestStateKey> {
		TestStateKey {
			Objects.requireNonNull(identity, "identity"); //$NON-NLS-1$
			Objects.requireNonNull(state, "state"); //$NON-NLS-1$
		}

		@Override
		public int compareTo(TestStateKey other) {
			int identityComparison = identity.compareTo(other.identity);
			return identityComparison != 0 ? identityComparison : state.compareTo(other.state);
		}
	}

	record Inventory(Map<TestStateKey, Integer> states, int reportFiles, List<String> parseErrors) {
		Inventory {
			states = Map.copyOf(states);
			parseErrors = List.copyOf(parseErrors);
		}

		int testCount() {
			return states.values().stream().mapToInt(Integer::intValue).sum();
		}

		int count(State state) {
			return states.entrySet().stream()
					.filter(entry -> entry.getKey().state() == state)
					.mapToInt(entry -> entry.getValue().intValue())
					.sum();
		}
	}

	record Mapping(Map<String, String> renames, List<String> allowedMissing, List<String> allowedAdded) {
		Mapping {
			renames = Map.copyOf(renames);
			allowedMissing = List.copyOf(allowedMissing);
			allowedAdded = List.copyOf(allowedAdded);
		}

		static Mapping empty() {
			return new Mapping(Map.of(), List.of(), List.of());
		}
	}

	record RunSummary(int reportFiles, int tests, int passed, int skipped, int failures, int errors) {
		// JSON evidence record.
	}

	record Difference(String test, String state, int count) {
		// JSON evidence record.
	}

	record Comparison(RunSummary baseline, RunSummary migrated, List<Difference> unexpectedMissing,
			List<Difference> unexpectedAdded, List<String> problems, String result) {
		Comparison {
			unexpectedMissing = List.copyOf(unexpectedMissing);
			unexpectedAdded = List.copyOf(unexpectedAdded);
			problems = List.copyOf(problems);
		}

		boolean passed() {
			return "PASS".equals(result); //$NON-NLS-1$
		}

		void write(Path output) throws IOException {
			Objects.requireNonNull(output, "output"); //$NON-NLS-1$
			Path parent = output.toAbsolutePath().normalize().getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
				writer.write(System.lineSeparator());
			}
		}
	}
}

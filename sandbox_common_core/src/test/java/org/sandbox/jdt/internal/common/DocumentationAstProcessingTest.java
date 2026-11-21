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
package org.sandbox.jdt.internal.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.jdt.internal.common.ScopedAstProcessorBuilder.TraversalDecision.SKIP_CHILDREN;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.JavaDocRegion;
import org.eclipse.jdt.core.dom.JavaDocTextElement;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MemberRef;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TagProperty;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentationAstProcessingTest {

	@Test
	void independentVisitorsReachTagsTextAndReferencesInBothCommentFormats() {
		CompilationUnit unit= parseMixedDocumentation();
		List<Boolean> formats= new ArrayList<>();
		List<String> tags= new ArrayList<>();
		List<String> text= new ArrayList<>();
		List<String> methods= new ArrayList<>();
		List<String> fields= new ArrayList<>();

		AstProcessing.independent()
				.visit(Javadoc.class, doc -> formats.add(doc.isMarkdown()))
				.visit(TagElement.class, tag -> tags.add(tag.getTagName()))
				.visit(TextElement.class, part -> text.add(part.getText()))
				.visit(MethodRef.class, ref -> methods.add(ref.getName().getIdentifier()))
				.visit(MemberRef.class, ref -> fields.add(ref.getName().getIdentifier()))
				.build(unit);

		assertEquals(List.of(Boolean.FALSE, Boolean.TRUE), formats);
		assertTrue(tags.contains(TagElement.TAG_LINK));
		assertTrue(tags.contains(TagElement.TAG_PARAM));
		assertTrue(text.stream().anyMatch(part -> part.contains("Classic prose")));
		assertTrue(text.stream().anyMatch(part -> part.contains("**Markdown prose**")));
		assertEquals(List.of("classic", "markdown"), methods);
		assertEquals(List.of("classicField", "markdownField"), fields);
	}

	@Test
	void scopedVisitorUsesOnlyTheSelectedDocumentationSubtree() {
		CompilationUnit unit= parseMixedDocumentation();
		List<String> methods= new ArrayList<>();

		AstProcessing.scoped(ReferenceHolder.create())
				.find(MethodDeclaration.class, method -> "markdown".equals(method.getName().getIdentifier()),
						(method, state) -> { }, MethodDeclaration::getJavadoc)
				.thenWithTraversal(Javadoc.class, Javadoc::isMarkdown, (doc, state) -> SKIP_CHILDREN, Function.identity())
				.then(MethodRef.class, ref -> true, (ref, state) -> methods.add(ref.getName().getIdentifier()))
				.build(unit);

		assertEquals(List.of("markdown"), methods);
	}

	@Test
	void excludingDocumentationSkipsItsCallbackWhileOtherStagesReachItsReferences() {
		CompilationUnit unit= parseMixedDocumentation();
		TypeDeclaration type= (TypeDeclaration) unit.types().getFirst();
		Javadoc classic= type.getMethods()[0].getJavadoc();
		List<Boolean> formats= new ArrayList<>();
		List<String> methods= new ArrayList<>();

		AstProcessing.independent().excluding(Set.of(classic))
				.visit(Javadoc.class, doc -> formats.add(doc.isMarkdown()))
				.visit(MethodRef.class, ref -> methods.add(ref.getName().getIdentifier()))
				.build(unit);

		assertEquals(List.of(Boolean.TRUE), formats);
		assertEquals(List.of("classic", "markdown"), methods);
	}

	@Test
	void rejectingAParentTagPrunesNestedTagsForThatVisitor() {
		CompilationUnit unit= parseMixedDocumentation();
		List<String> nestedTags= new ArrayList<>();
		List<String> methods= new ArrayList<>();

		AstProcessing.independent()
				.on(TagElement.class, (tag, state) -> {
					if (tag.getTagName() == null) {
						return false;
					}
					nestedTags.add(tag.getTagName());
					return true;
				})
				.visit(MethodRef.class, ref -> methods.add(ref.getName().getIdentifier()))
				.build(unit);

		assertEquals(List.of(TagElement.TAG_PARAM, TagElement.TAG_PARAM), nestedTags);
		assertEquals(List.of("classic", "markdown"), methods);
	}

	@Test
	void independentVisitorReachesSnippetTagProperties() {
		CompilationUnit unit= parseSnippetDocumentation();
		List<String> substrings= new ArrayList<>();

		assertDoesNotThrow(() -> AstProcessing.independent()
				.visit(TagProperty.class, property -> {
					if ("substring".equals(property.getName())) {
						substrings.add(property.getStringValue());
					}
				})
				.build(unit), "The independent visitor must dispatch JDT snippet tag properties");

		assertEquals(List.of("sample"), substrings);
	}

	@Test
	void scopedVisitorReachesNamedSnippetRegions() {
		CompilationUnit unit= parseSnippetDocumentation();
		List<String> names= new ArrayList<>();

		assertDoesNotThrow(() -> AstProcessing.scoped(ReferenceHolder.create())
				.find(TagElement.class, tag -> TagElement.TAG_SNIPPET.equals(tag.getTagName()),
						(tag, state) -> { })
				.then(JavaDocRegion.class, region -> !region.isDummyRegion(),
						(region, state) -> names.add(region.getTagName()))
				.build(unit), "The scoped visitor must dispatch JDT named snippet regions");

		assertEquals(List.of("example"), names);
	}

	@Test
	void independentVisitorReachesExternalSnippetTextWithBlockCommentDelimiter() {
		JavaDocTextElement snippetText= createExternalSnippetText();
		List<String> text= new ArrayList<>();

		assertDoesNotThrow(() -> AstProcessing.independent()
				.visit(JavaDocTextElement.class, part -> text.add(part.getText()))
				.build(snippetText), "The independent visitor must dispatch JDT external snippet text");

		assertEquals(List.of("/* example */"), text);
	}

	@Test
	void legacyBuilderDispatchesTypedSnippetCallbacks() {
		CompilationUnit unit= parseSnippetDocumentation();
		ReferenceHolder<Void, Void> holder= ReferenceHolder.create();
		List<String> values= new ArrayList<>();
		AstProcessorBuilder.with(holder).onJavaDocRegion((region, state) -> values.add(region.getTagName())).build(unit);
		AstProcessorBuilder.with(holder).onTagProperty((property, state) -> values.add(property.getStringValue())).build(unit);
		AstProcessorBuilder.with(holder).onJavaDocTextElement((text, state) -> values.add(text.getText()))
				.build(createExternalSnippetText());
		assertEquals(List.of("example", "sample", "/* example */"), values);
	}

	@Test
	void factoryRunsEntryAndExitCallbacksForSnippetNodes() {
		CompilationUnit unit= parseSnippetDocumentation();
		ReferenceHolder<Void, Void> holder= ReferenceHolder.create();
		List<String> events= new ArrayList<>();
		HelperVisitorFactory.callJavaDocRegionVisitor(unit, holder, Set.of(),
				(region, state) -> enter(events, region.getTagName()),
				(region, state) -> events.add(region.getTagName()));
		HelperVisitorFactory.callTagPropertyVisitor(unit, holder, Set.of(),
				(property, state) -> enter(events, property.getStringValue()),
				(property, state) -> events.add(property.getStringValue()));
		HelperVisitorFactory.callJavaDocTextElementVisitor(createExternalSnippetText(), holder, Set.of(),
				(text, state) -> enter(events, text.getText()),
				(text, state) -> events.add(text.getText()));
		assertEquals(List.of("enter example", "example", "enter sample", "sample",
				"enter /* example */", "/* example */"), events);
	}

	@ParameterizedTest
	@ValueSource(booleans= {false, true})
	void legacyHelperFlagsKeepVisitingReferences(boolean visitJavadoc) {
		CompilationUnit unit= parseMixedDocumentation();
		HelperVisitor<ReferenceHolder<Void, Void>, Void, Void> visitor= new HelperVisitor<>(Set.of(), ReferenceHolder.create());
		List<String> refs= new ArrayList<>();
		visitor.addMethodRef((ref, state) -> {
			refs.add(ref.getName().getIdentifier());
			return true;
		});
		visitor.build(unit, visitJavadoc);
		assertEquals(List.of("classic", "markdown"), refs);
	}

	@ParameterizedTest
	@ValueSource(booleans= {false, true})
	void directRegionVisitUsesJdtFlagWithoutRegisteredPredicate(boolean visitJavadoc) {
		HelperVisitor<ReferenceHolder<Void, Void>, Void, Void> helper= new HelperVisitor<>(Set.of(), ReferenceHolder.create());
		LambdaASTVisitor<ReferenceHolder<Void, Void>, Void, Void> visitor= new LambdaASTVisitor<>(helper, visitJavadoc);
		JavaDocRegion region= AST.newAST(AST.getJLSLatest(), false).newJavaDocRegion();

		assertEquals(visitJavadoc, visitor.visit(region));
	}

	private static boolean enter(List<String> events, String text) {
		events.add("enter " + text);
		return false;
	}

	private static JavaDocTextElement createExternalSnippetText() {
		// The DOM type represents external snippet text containing a block comment delimiter.
		JavaDocTextElement snippetText= AST.newAST(AST.getJLSLatest(), false).newJavaDocTextElement();
		snippetText.setText("/* example */");
		return snippetText;
	}

	private static CompilationUnit parseMixedDocumentation() {
		return parse("""
				class Sample {
					int classicField;
					int markdownField;

					/**
					 * Classic prose with {@link #classic(int)} and {@link #classicField}.
					 * @param count a number
					 */
					void classic(int count) {}

					/// **Markdown prose** with [#markdown(int)] and [#markdownField].
					/// @param count a number
					void markdown(int count) {}
				}
				""");
	}

	private static CompilationUnit parseSnippetDocumentation() {
		return parse("""
				/**
				 * {@snippet :
				 * int sample = 42; // @highlight substring="sample"
				 * int other = 12; // @highlight region="example" substring="other"
				 * // @end region="example"
				 * }
				 */
				class Sample {}
				""");
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		var options= new HashMap<String, String>();
		JavaCore.setComplianceOptions(JavaCore.VERSION_25, options);
		options.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, JavaCore.ENABLED);
		parser.setCompilerOptions(options);
		parser.setSource(source.toCharArray());
		CompilationUnit unit= (CompilationUnit) parser.createAST(null);
		assertFalse(Arrays.stream(unit.getProblems()).anyMatch(IProblem::isError),
				() -> Arrays.toString(unit.getProblems()));
		return unit;
	}
}

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
package org.eclipse.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ClosedUniqueSequenceFlow;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ClosedUniqueSequenceFlow.Kind;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class ClosedUniqueSequenceFlowTest {
	@RegisterExtension
	final EclipseJava22 java= new EclipseJava22();

	private CompilationUnit parse(String name, String body) throws Exception {
		var pkg= java.getSourceFolder().createPackageFragment("p", false, null); //$NON-NLS-1$
		var unit= pkg.createCompilationUnit(name + ".java", "package p; import java.util.*; " + body, true, null); //$NON-NLS-1$ //$NON-NLS-2$
		var parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}

	private static final String FACTORY= """
			final class Factory {
			    static List<String> create(String value) {
			        List<String> result = new ArrayList<>();
			        if (!result.contains(value)) { result.add(value); }
			        return result;
			    }
			}
			""";
	private static final String READ= "final class Read { static int count(List<String> items) { return items.size(); } }"; //$NON-NLS-1$
	private static final String CLIENT= """
			class Client {
			    int total(String arg) {
			        List<String> lines = Factory.create(arg);
			        return Read.count(lines);
			    }
			}
			""";

	@Test
	void bindsEveryProducerConsumerAndReaderAndDisallowsMixedOrUnsafeFlows() throws Exception {
		var factory= parse("Factory", FACTORY); //$NON-NLS-1$
		var reader= parse("Read", READ); //$NON-NLS-1$
		var client= parse("Client", CLIENT); //$NON-NLS-1$
		var producer= ClosedUniqueSequenceFlow.seeds(factory).getFirst();
		var helpers= ClosedUniqueSequenceFlow.readers(client, producer).orElseThrow();
		assertEquals(1, helpers.size());
		assertEquals(List.of(Kind.SET_TYPE, Kind.SET_TYPE, Kind.ORDERED_SET_TYPE, Kind.GUARDED_ADD),
				ClosedUniqueSequenceFlow.edits(factory, producer, helpers).orElseThrow().stream().map(ClosedUniqueSequenceFlow.Edit::kind).toList());
		assertEquals(List.of(Kind.CALLER_SET_TYPE), ClosedUniqueSequenceFlow.edits(client, producer, helpers).orElseThrow().stream().map(ClosedUniqueSequenceFlow.Edit::kind).toList());
		assertEquals(List.of(Kind.SET_TYPE), ClosedUniqueSequenceFlow.edits(reader, producer, helpers).orElseThrow().stream().map(ClosedUniqueSequenceFlow.Edit::kind).toList());

		for (String extra : List.of("if (arg.isEmpty()) return new ArrayList<>();", "Runnable r = () -> System.out.println(result);", //$NON-NLS-1$ //$NON-NLS-2$
				"System.out.println(result.get(0));", "result.add(value);", "Object alias = result;")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			String changed= FACTORY.replace("return result;", extra.replace("arg.isEmpty()", "value.isEmpty()") + " return result;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			assertTrue(ClosedUniqueSequenceFlow.seeds(parse("Factory", changed)).isEmpty(), extra); //$NON-NLS-1$
		}
		parse("Factory", FACTORY); //$NON-NLS-1$
		for (String extra : List.of("Object alias=lines;", "lines.remove(0);", "lines.set(0,arg);", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"lines.contains(new Object());", "java.util.function.IntSupplier s=lines::size;", "Runnable r=()->lines.size();")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			var observed= parse("Client", CLIENT.replace("return Read.count", extra + " return Read.count")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertTrue(ClosedUniqueSequenceFlow.readers(observed, producer).isEmpty(), extra);
		}
		var mixed= parse("Other", "class Other { int count() { return Read.count(new ArrayList<>()); } }"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(ClosedUniqueSequenceFlow.edits(mixed, producer, helpers).isEmpty());
		var mutated= parse("Read", READ.replace("return items.size();", "items.clear(); return items.size();")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertTrue(ClosedUniqueSequenceFlow.edits(mutated, producer, helpers).isEmpty());
		assertTrue(ClosedUniqueSequenceFlow.seeds(parse("Factory", FACTORY.replace("static List", "public static List"))).isEmpty()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@Test
	void keepsEnumAndNullOrderAndAcceptsOnlyBoundReadOperations() throws Exception {
		parse("Color", "enum Color { RED, BLUE }"); //$NON-NLS-1$ //$NON-NLS-2$
		var factory= parse("Factory", FACTORY.replace("String", "Color")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		var producer= ClosedUniqueSequenceFlow.seeds(factory).getFirst();
		String read= """
				class Client {
				    int count(Color value) {
				        List<Color> colors = Factory.create(value);
				        int n=0;
				        for (Color item : colors) { if (item == value) n++; }
				        return n + (colors.contains(null) && colors.contains(value) && !colors.isEmpty() ? colors.size() : 0);
				    }
				}
				""";
		var client= parse("Client", read); //$NON-NLS-1$
		assertTrue(ClosedUniqueSequenceFlow.readers(client, producer).orElseThrow().isEmpty());
		assertEquals(List.of(Kind.CALLER_SET_TYPE), ClosedUniqueSequenceFlow.edits(client, producer, List.of()).orElseThrow()
				.stream().map(ClosedUniqueSequenceFlow.Edit::kind).toList());
		var ref= parse("Ref", "class Ref { java.util.function.Function<Color,List<Color>> use = Factory::create; }"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(ClosedUniqueSequenceFlow.readers(ref, producer).isEmpty());
		var comparators= parse("Client", read.replace("int n=0;", "colors.sort(null); int n=0;")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertTrue(ClosedUniqueSequenceFlow.readers(comparators, producer).isEmpty());
	}

	@Test
	void requiresStableReaderResolutionAndPreservesGuardComments() throws Exception {
		var producer= ClosedUniqueSequenceFlow.seeds(parse("Factory", FACTORY)).getFirst(); //$NON-NLS-1$
		parse("Read", READ); //$NON-NLS-1$
		parse("Other", "class Other { static int count(Set<String> names) { return 0; } }"); //$NON-NLS-1$ //$NON-NLS-2$
		var caller= parse("Client", "import static p.Read.*; import static p.Other.*;" //$NON-NLS-1$ //$NON-NLS-2$
				+ CLIENT.replace("Read.count(lines)", "count(lines)")); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(ClosedUniqueSequenceFlow.readers(caller, producer).isEmpty());
		var erased= parse("Client", CLIENT.replace("class Client {", //$NON-NLS-1$ //$NON-NLS-2$
				"class Client { static <T> T value() { return (T)(Object)new Object(); }") //$NON-NLS-1$
				.replace("return Read.count(lines);", "return lines.contains(Client.<String>value()) ? 1 : 0;")); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(ClosedUniqueSequenceFlow.readers(erased, producer).isEmpty());

		for (String guard : List.of("if (/* membership */ !result.contains(value)) { result.add(value); }", //$NON-NLS-1$
				"if (!result.contains(value)) /* reasoning */ { result.add(value); }")) { //$NON-NLS-1$
			assertTrue(ClosedUniqueSequenceFlow.seeds(parse("Factory", FACTORY.replace("if (!result.contains(value)) { result.add(value); }", guard))).isEmpty()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		var retained= parse("Factory", FACTORY.replace("result.add(value);", "/* keep insertion comment */ result.add(value);")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals(1, ClosedUniqueSequenceFlow.seeds(retained).size());
	}
}

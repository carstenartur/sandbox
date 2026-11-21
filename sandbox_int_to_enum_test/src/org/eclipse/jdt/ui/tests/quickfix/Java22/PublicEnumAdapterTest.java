/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java22;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.sandbox.jdt.cleanup.multifile.CleanUpImpact;
import org.sandbox.jdt.internal.corext.fix.helper.PublicEnumAdapter;
import org.sandbox.jdt.internal.corext.fix.multifile.IntEnumMigrationMode;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.preferences.cleanup.DefaultCleanUpOptionsInitializer;
import org.sandbox.jdt.internal.ui.preferences.cleanup.SaveActionCleanUpOptionsInitializer;
import org.sandbox.jdt.internal.ui.fix.IntToEnumCleanUp;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Legacy API linkage, named enum entries, compiler diagnostics and native Undo. */
@SuppressWarnings("nls")
class PublicEnumAdapterTest {
	@RegisterExtension final AbstractEclipseJava context= new EclipseJava22();
	@TempDir Path temporary;

	private static final String OPTION= "cleanup.int_to_enum.public_value_adapter";
	private static final String API= """
			package test1;
			class Service$processValue {}
			abstract class AbstractService { public abstract void process(TYPE state); }
			public final class Service extends AbstractService {
			    public static final TYPE STATUS_A = FIRST;
			    public static final TYPE STATUS_B = SECOND;
			    public int result;
			    public int enumValue, rawValue, value;
			    public static final class NullPointerException {}
			    @Override public void process(TYPE state) {
			        if (state == STATUS_A) result = 11;
			        else if (state == STATUS_B) result = 22;
			        else result = 33;
			    }
			}
			""";

	private static String api(boolean strings) {
		return API.replace("TYPE", strings ? "String" : "int")
				.replace("FIRST", strings ? "\"north\"" : "0")
				.replace("SECOND", strings ? "\"south\"" : "1");
	}

	@Test
	void publicAdapterIsRegisteredOnlyForExplicitCleanupProfiles() {
		var explicit= new CleanUpOptions();
		new DefaultCleanUpOptionsInitializer().setDefaultOptions(explicit);
		assertTrue(explicit.getKeys().contains(OPTION));
		assertEquals(CleanUpOptions.FALSE, explicit.getValue(OPTION));
		var onSave= new CleanUpOptions();
		new SaveActionCleanUpOptionsInitializer().setDefaultOptions(onSave);
		assertFalse(onSave.getKeys().contains(OPTION));
		var mode= IntEnumMigrationMode.PUBLIC_VALUE_ADAPTER_OPT_IN;
		assertEquals(CleanUpImpact.COMPATIBILITY_MANAGED, mode.impact());
		assertTrue(mode.implemented());
		var cleanup= new IntToEnumCleanUp(Map.of(MYCleanUpConstants.INT_TO_ENUM_CLEANUP, "true", OPTION, "true"));
		assertTrue(List.of(cleanup.getStepDescriptions()).contains(mode.previewStatement()));
	}

	@Test
	void publicAdapterProfilePreviewShowsLegacyAndTypedCalls() throws Exception {
		var cleanup= new IntToEnumCleanUp(Map.of(MYCleanUpConstants.INT_TO_ENUM_CLEANUP, "true", OPTION, "true"));
		String example= PublicEnumAdapter.preview();
		compile(temporary, "Service", example);
		compile(temporary, "PreviewClient", """
				class PreviewClient {
				    void configure(Service service) {
				        service.setPriority(Service.PRIORITY_HIGH);
				        service.setPriorityValue(Service.Priority.HIGH);
				    }
				}
				""");
		assertTrue(cleanup.getPreview().startsWith(example), cleanup.getPreview());
	}

	@ParameterizedTest
	@ValueSource(booleans= {false, true})
	void newEntryPreservesLegacySourceBinaryAndDebugNames(boolean strings) throws Exception {
		var project= context.getJavaProject();
		project.setOption(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, JavaCore.ENABLED);
		project.setOption(JavaCore.COMPILER_PB_INVALID_JAVADOC, JavaCore.ERROR);
		String original= api(strings);
		String unknown= strings ? "null" : "-301";
		String client= """
				package test1;
				public class Client {
				    static int classify(TYPE input) {
				        switch(input) { case Service.STATUS_A: return 1; default: return 2; }
				    }
				    public static int run() {
				        Service service = new Service();
				        AbstractService legacy = service;
				        legacy.process(UNKNOWN);
				        int first = service.result;
				        legacy.process(SECOND_UNKNOWN);
				        return first + service.result + classify(Service.STATUS_A);
				    }
				}
				""".replace("TYPE", strings ? "String" : "int")
				.replace("SECOND_UNKNOWN", strings ? "new String(Service.STATUS_A)" : "Integer.MAX_VALUE")
				.replace("UNKNOWN", unknown);
		var pack= context.getSourceFolder().createPackageFragment("test1", false, null);
		var owner= pack.createCompilationUnit("Service.java", original, false, null);
		var caller= pack.createCompilationUnit("Client.java", client, false, null);
		ICompilationUnit[] units= {owner, caller};
		var diagnostics= CompilerDiagnosticAssertions.capture(units);
		var options= owner.getJavaProject().getOptions(false);
		Path binary= Files.createDirectory(temporary.resolve("classes"));
		compile(binary, "Service", original);
		compile(binary, "Client", client);
		assertEquals(67, invoke(binary, "test1.Client"));
		byte[] oldClient= Files.readAllBytes(binary.resolve("test1/Client.class"));

		assertNull(fix(owner, false));
		var fix= fix(owner, true);
		assertNotNull(fix, original);
		var change= fix.createChange(null);
		try {
			String preview= change.getPreviewContent(null);
			assertTrue(preview.contains("enum Status"), preview);
			assertFalse(preview.contains("ordinal()"), preview);
			assertEquals(original, owner.getSource());
			var undo= change.perform(null);
			assertNotNull(undo);
			try {
				diagnostics.assertNoNewDiagnostics();
				assertDocumentedNullContract(owner);
				assertEquals(client, caller.getSource());
				assertEquals(preview, owner.getSource());
				assertNull(fix(owner, true));
				compile(binary, "Service", preview);
				assertEquals(67, invoke(binary, "test1.Client"));
				assertArrayEquals(oldClient, Files.readAllBytes(binary.resolve("test1/Client.class")));
				String typed= """
						package client;
						import test1.Service;
						public class Typed {
						    public static String run() {
						        Service service = new Service();
						        try {
						            service.processValue(null);
						            throw new AssertionError("An enum value is required");
						        } catch (NullPointerException expected) {
						            service.processValue(Service.Status.B);
						        }
						        return Service.Status.B + "/" + Service.Status.B.legacyValue() + "/" + service.result;
						    }
						}
						""";
				compile(binary, "Typed", typed);
				assertEquals(strings ? "B/south/22" : "B/1/22", invoke(binary, "client.Typed"));
			} finally {
				var redo= undo.perform(null);
				if (redo != null) redo.dispose();
				undo.dispose();
			}
		} finally { change.dispose(); }
		assertEquals(original, owner.getSource());
		assertEquals(client, caller.getSource());
		assertEquals(options, owner.getJavaProject().getOptions(false));
		diagnostics.assertRestored();
	}

	private static void assertDocumentedNullContract(ICompilationUnit owner) {
		List<String> exceptions= new ArrayList<>();
		ConstantToEnumTypeTest.parse(owner).accept(new ASTVisitor(true) {
			@Override
			public boolean visit(TagElement tag) {
				if (TagElement.TAG_THROWS.equals(tag.getTagName())) {
					Name name= (Name) tag.fragments().getFirst();
					assertNotNull(name.resolveTypeBinding());
					exceptions.add(name.resolveTypeBinding().getQualifiedName());
				}
				return true;
			}
		});
		assertEquals(List.of("java.lang.NullPointerException"), exceptions);
	}

	@ParameterizedTest
	@ValueSource(strings= {"same unit", "primary", "secondary"})
	void existingTopLevelTypeOwnsGeneratedBinaryName(String location) throws Exception {
		var pack= context.getSourceFolder().createPackageFragment("test1", false, null);
		String alias= "class Service$Status {}";
		String source= api(false);
		if ("same unit".equals(location)) {
			source+= "\n" + alias;
		} else {
			String fileName= "primary".equals(location) ? "Service$Status.java" : "Other.java";
			pack.createCompilationUnit(fileName, "package test1; " + alias, false, null);
		}
		var owner= pack.createCompilationUnit("Service.java", source, false, null);
		assertNull(fix(owner, true), "Service$Status.class is already owned by a top-level declaration");
		assertEquals(source, owner.getSource());
	}

	@ParameterizedTest
	@ValueSource(strings= {"open", "serialized", "method exists", "inherited method", "other type",
			"aliases", "nonconstant", "mixed visibility", "multiple methods", "generated identifier"})
	void declinesUnprovenPublicChanges(String scenario) throws Exception {
		String source= api(false);
		source= switch (scenario) {
			case "open" -> source.replace("final class", "class");
			case "serialized" -> source.replace("extends AbstractService", "extends AbstractService implements java.io.Serializable");
			case "method exists" -> source.replace("public int result;", "public int result; public void processValue(Object state) {}");
			case "inherited method" -> source.replace("class AbstractService {", "class AbstractService { void processValue(Object value) {}");
			case "other type" -> source.replace("public int result;", "public int result; Status external;")
					+ "\nclass Status {}\n";
			case "aliases" -> source.replace("STATUS_B = 1", "STATUS_B = 0");
			case "nonconstant" -> source.replace("STATUS_B = 1", "STATUS_B = Integer.valueOf(1)");
			case "mixed visibility" -> source.replace("public static final int STATUS_B", "private static final int STATUS_B");
			case "multiple methods" -> source.replace("public int result;", """
					public int result;
					public void consume(int state) {
					    if (state == STATUS_A) result = 99;
					    else if (state == STATUS_B) result = 98;
					}
					""");
			case "generated identifier" -> source.replace("STATUS_B", "STATUS_1");
			default -> throw new AssertionError(scenario);
		};
		var pack= context.getSourceFolder().createPackageFragment("test1", false, null);
		var owner= pack.createCompilationUnit("Service.java", source, false, null);
		assertNull(fix(owner, true), scenario);
		assertEquals(source, owner.getSource());
	}

	private ICleanUpFix fix(ICompilationUnit owner, boolean enabled) throws Exception {
		var cleanup= new IntToEnumCleanUp(Map.of(MYCleanUpConstants.INT_TO_ENUM_CLEANUP, "true",
				OPTION, Boolean.toString(enabled)));
		assertFalse(cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] {owner}, null).hasError());
		return cleanup.createFix(new CleanUpContext(owner, ConstantToEnumTypeTest.parse(owner)));
	}

	private void compile(Path binary, String name, String source) throws Exception {
		Path file= temporary.resolve(name + ".java");
		Files.writeString(file, source);
		assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
				"--release", "22", "-d", binary.toString(), "-cp", binary.toString(), file.toString()), source);
	}

	private static Object invoke(Path binary, String name) throws Exception {
		try (var loader= new URLClassLoader(new URL[]{binary.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
			return loader.loadClass(name).getMethod("run").invoke(null);
		}
	}
}

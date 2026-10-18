/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.wizard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.triggerpattern.cleanup.HintFileFixCore;
import org.sandbox.jdt.triggerpattern.cleanup.NlsAwareCleanUpFix;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Exercises annotation identity in the production backend, before project validation. */
public class MethodAnnotationReplacementTest {

	@RegisterExtension
	EclipseJava22 context= new EclipseJava22();

	@Test
	public void anUnqualifiedTargetDoesNotDuplicateItsFullyQualifiedExistingType() throws Exception {
		ICompilationUnit unit= unit("package trial; public class Sample { @java.lang.Deprecated void old() {} }");
		assertNull(change(unit, "Deprecated"));
	}

	@Test
	public void anUnqualifiedTargetDoesNotConflateAResolvedForeignTypeWithTheSameSimpleName() throws Exception {
		ICompilationUnit unit= unit("package trial; public class Sample { @java.lang.Deprecated void old() {} } @interface Deprecated {}");
		CompilationUnitChange change= change(unit, "Deprecated");
		assertNotNull(change);
		try {
			CompilationUnit after= parse(unit, change.getPreviewContent(null));
			Set<String> annotations= new LinkedHashSet<>();
			for (Object modifier : ((TypeDeclaration) after.types().getFirst()).getMethods()[0].modifiers()) {
				if (modifier instanceof Annotation annotation) {
					assertNotNull(annotation.resolveTypeBinding());
					annotations.add(annotation.resolveTypeBinding().getQualifiedName());
				}
			}
			assertEquals(Set.of("java.lang.Deprecated", "trial.Deprecated"), annotations);
		} finally {
			change.dispose();
		}
	}

	@Test
	public void aQualifiedTargetRetainsItsIdentityWhenAnEnclosingTypeDeclaresTheSameSimpleName() throws Exception {
		context.getSourceFolder().createPackageFragment("a", false, null)
				.createCompilationUnit("Flag.java", "package a; public @interface Flag {}", false, null);
		ICompilationUnit unit= unit("package trial; public class Sample { @interface Flag {} void old() {} }");
		CompilationUnitChange change= change(unit, "a.Flag");
		assertNotNull(change);
		try {
			CompilationUnit after= parse(unit, change.getPreviewContent(null));
			Set<String> annotations= new LinkedHashSet<>();
			for (Object modifier : ((TypeDeclaration) after.types().getFirst()).getMethods()[0].modifiers()) {
				if (modifier instanceof Annotation annotation) {
					assertNotNull(annotation.resolveTypeBinding());
					annotations.add(annotation.resolveTypeBinding().getQualifiedName());
				}
			}
			assertEquals(Set.of("a.Flag"), annotations);
		} finally {
			change.dispose();
		}
	}

	@Test
	public void anUnresolvedUnqualifiedTargetDoesNotProduceAnInvalidCleanup() throws Exception {
		ICompilationUnit unit= unit("package trial; public class Sample { @java.lang.Deprecated void old() {} }");
		assertNull(change(unit, "Missing"));
	}

	@Test
	public void anUnresolvedQualifiedTargetDoesNotProduceAnInvalidCleanup() throws Exception {
		ICompilationUnit unit= unit("package trial; public class Sample { void old() {} }");
		assertNull(change(unit, "absent.Flag"));
	}

	@Test
	public void aQualifiedNonAnnotationTargetDoesNotProduceAnInvalidCleanup() throws Exception {
		ICompilationUnit unit= unit("package trial; public class Sample { void old() {} }");
		assertNull(change(unit, "java.lang.String"));
	}

	@Test
	public void anAnnotationWithUnsupportedValuesDoesNotProduceAnInvalidCleanup() throws Exception {
		ICompilationUnit unit= unit("package trial; public class Sample { void old() {} }");
		assertNull(change(unit, "SuppressWarnings(\"deprecation\")"));
	}

	@Test
	public void qualifiedAndUnqualifiedTargetsRetainTheirDistinctIdentitiesAfterImports() throws Exception {
		var foreignPackage= context.getSourceFolder().createPackageFragment("a", false, null);
		foreignPackage.createCompilationUnit("Flag.java",
				"package a; @java.lang.annotation.Repeatable(Flags.class) public @interface Flag {}", false, null);
		foreignPackage.createCompilationUnit("Flags.java", "package a; public @interface Flags { Flag[] value(); }", false, null);
		context.getSourceFolder().createPackageFragment("trial", false, null)
				.createCompilationUnit("Flag.java", "package trial; public @interface Flag {}", false, null);
		ICompilationUnit unit= unit("package trial; public class Sample { void old() {} }");
		CompilationUnitChange change= change(unit, "a.Flag @Flag");
		assertNotNull(change);
		try {
			CompilationUnit after= parse(unit, change.getPreviewContent(null));
			Set<String> annotations= new LinkedHashSet<>();
			for (Object modifier : ((TypeDeclaration) after.types().getFirst()).getMethods()[0].modifiers()) {
				if (modifier instanceof Annotation annotation) {
					assertNotNull(annotation.resolveTypeBinding());
					annotations.add(annotation.resolveTypeBinding().getQualifiedName());
				}
			}
			assertEquals(Set.of("a.Flag", "trial.Flag"), annotations);
		} finally {
			change.dispose();
		}
	}

	@Test
	public void anAmbiguousUnqualifiedTargetDoesNotConflateImportedAnnotationTypes() throws Exception {
		ICompilationUnit unit= unit("package trial; import trial.First.*; import trial.Second.*; "
				+ "public class Sample { @trial.First.Flag void old() {} } "
				+ "class First { @interface Flag {} } class Second { @interface Flag {} }");
		assertNull(change(unit, "Flag"));
	}

	private CompilationUnitChange change(ICompilationUnit unit, String annotation) throws Exception {
		CompilationUnit root= parse(unit, null);
		Set<CompilationUnitRewriteOperation> operations= new LinkedHashSet<>();
		var hint= new HintFileParser().parse("void $name($params$)\n=> @" + annotation + " void $name($params$)\n;;\n");
		HintFileFixCore.findOperationsFromHint(root, hint, operations);
		return operations.isEmpty() ? null : new NlsAwareCleanUpFix("Add annotation", root,
				operations.toArray(CompilationUnitRewriteOperation[]::new)).createOptionalChange(null);
	}

	private CompilationUnit parse(ICompilationUnit unit, String changedSource) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		if (changedSource == null) {
			parser.setSource(unit);
		} else {
			parser.setSource(changedSource.toCharArray());
			parser.setProject(context.getJavaProject());
			parser.setUnitName(unit.getPath().toString());
		}
		parser.setCompilerOptions(context.getJavaProject().getOptions(true));
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(false);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		for (var problem : root.getProblems()) {
			assertFalse(problem.isError(), problem::getMessage);
		}
		return root;
	}

	private ICompilationUnit unit(String source) throws Exception {
		return context.getSourceFolder().createPackageFragment("trial", false, null)
				.createCompilationUnit("Sample.java", source, true, null);
	}
}

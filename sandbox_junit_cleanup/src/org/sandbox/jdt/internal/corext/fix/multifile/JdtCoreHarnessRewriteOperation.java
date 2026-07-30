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
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.eclipse.text.edits.TextEditGroup;

/** Applies exact source edits for the first direct JDT Core harness slice. */
final class JdtCoreHarnessRewriteOperation extends CompilationUnitRewriteOperationWithSourceRange {

	private static final String BRIDGE_RESOURCE=
			"org/sandbox/jdt/internal/corext/fix/multifile/jdt-core-jupiter-bridge.java.template"; //$NON-NLS-1$
	private static final String GLOBAL_HARNESS_LOCK= JdtCoreHarnessPlanner.JDT_CORE_TEST_CASE;

	private enum Kind {
		ADD_BRIDGE,
		MIGRATE_FAMILY
	}

	private final Kind kind;
	private final TypeDeclaration type;
	private final MethodDeclaration constructor;
	private final MethodDeclaration localSuite;

	private JdtCoreHarnessRewriteOperation(Kind kind, TypeDeclaration type,
			MethodDeclaration constructor, MethodDeclaration localSuite) {
		this.kind= kind;
		this.type= type;
		this.constructor= constructor;
		this.localSuite= localSuite;
	}

	static JdtCoreHarnessRewriteOperation addBridge(TypeDeclaration harnessType) {
		return new JdtCoreHarnessRewriteOperation(Kind.ADD_BRIDGE, harnessType, null, null);
	}

	static JdtCoreHarnessRewriteOperation migrateFamily(TypeDeclaration familyType,
			MethodDeclaration constructor, MethodDeclaration localSuite) {
		return new JdtCoreHarnessRewriteOperation(Kind.MIGRATE_FAMILY, familyType, constructor, localSuite);
	}

	@Override
	public void rewriteASTInternal(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel)
			throws CoreException {
		if (type == null) {
			throw failure("The planned JDT Core harness type is missing", null); //$NON-NLS-1$
		}
		TextEditGroup group= createTextEditGroup(kind == Kind.ADD_BRIDGE
				? "Add the JDT Core Jupiter compatibility bridge" //$NON-NLS-1$
				: "Migrate a direct JDT Core TestCase family to Jupiter", cuRewrite); //$NON-NLS-1$
		if (kind == Kind.ADD_BRIDGE) {
			addBridge(cuRewrite, group);
		} else {
			migrateFamily(cuRewrite, group);
		}
	}

	private void addBridge(CompilationUnitRewrite cuRewrite, TextEditGroup group) throws CoreException {
		ITypeBinding binding= type.resolveBinding();
		if (binding == null || !JdtCoreHarnessPlanner.JDT_CORE_TEST_CASE.equals(
				binding.getErasure().getQualifiedName())) {
			throw failure("The planned bridge target is not the JDT Core custom TestCase", null); //$NON-NLS-1$
		}
		for (TypeDeclaration nested : type.getTypes()) {
			if ("Jupiter".equals(nested.getName().getIdentifier())) { //$NON-NLS-1$
				throw failure("The JDT Core TestCase already contains a Jupiter bridge", null); //$NON-NLS-1$
			}
		}
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		ASTNode placeholder= rewrite.createStringPlaceholder(loadBridgeSource(), ASTNode.TYPE_DECLARATION);
		ListRewrite members= rewrite.getListRewrite(type, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		members.insertLast(placeholder, group);
	}

	@SuppressWarnings("unchecked")
	private void migrateFamily(CompilationUnitRewrite cuRewrite, TextEditGroup group) throws CoreException {
		Type superclass= type.getSuperclassType();
		ITypeBinding binding= superclass == null ? null : superclass.resolveBinding();
		if (binding == null || !JdtCoreHarnessPlanner.JDT_CORE_TEST_CASE.equals(
				binding.getErasure().getQualifiedName())) {
			throw failure("The direct family no longer extends the expected JDT Core TestCase", null); //$NON-NLS-1$
		}
		if (constructor == null) {
			throw failure("The planned JDT Core String constructor is missing", null); //$NON-NLS-1$
		}
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		String harnessName= cuRewrite.getImportRewrite().addImport(JdtCoreHarnessPlanner.JDT_CORE_TEST_CASE);
		SimpleType replacement= ast.newSimpleType(ast.newName(harnessName + ".Jupiter")); //$NON-NLS-1$
		rewrite.replace(superclass, replacement, group);

		String executionName= cuRewrite.getImportRewrite().addImport("org.junit.jupiter.api.parallel.Execution"); //$NON-NLS-1$
		String modeName= cuRewrite.getImportRewrite().addImport("org.junit.jupiter.api.parallel.ExecutionMode"); //$NON-NLS-1$
		SingleMemberAnnotation execution= ast.newSingleMemberAnnotation();
		execution.setTypeName(ast.newName(executionName));
		execution.setValue(ast.newName(modeName + ".SAME_THREAD")); //$NON-NLS-1$

		String lockName= cuRewrite.getImportRewrite().addImport("org.junit.jupiter.api.parallel.ResourceLock"); //$NON-NLS-1$
		String accessModeName= cuRewrite.getImportRewrite()
				.addImport("org.junit.jupiter.api.parallel.ResourceAccessMode"); //$NON-NLS-1$
		NormalAnnotation resourceLock= ast.newNormalAnnotation();
		resourceLock.setTypeName(ast.newName(lockName));
		MemberValuePair valuePair= ast.newMemberValuePair();
		valuePair.setName(ast.newSimpleName("value")); //$NON-NLS-1$
		StringLiteral lockValue= ast.newStringLiteral();
		lockValue.setLiteralValue(GLOBAL_HARNESS_LOCK);
		valuePair.setValue(lockValue);
		resourceLock.values().add(valuePair);
		MemberValuePair modePair= ast.newMemberValuePair();
		modePair.setName(ast.newSimpleName("mode")); //$NON-NLS-1$
		modePair.setValue(ast.newName(accessModeName + ".READ_WRITE")); //$NON-NLS-1$
		resourceLock.values().add(modePair);

		ListRewrite modifiers= rewrite.getListRewrite(type, TypeDeclaration.MODIFIERS2_PROPERTY);
		modifiers.insertFirst(resourceLock, group);
		modifiers.insertFirst(execution, group);

		rewrite.remove(constructor, group);
		cuRewrite.getImportRemover().registerRemovedNode(constructor);
		if (localSuite != null) {
			rewrite.remove(localSuite, group);
			cuRewrite.getImportRemover().registerRemovedNode(localSuite);
		}
		cuRewrite.getImportRemover().applyRemoves(cuRewrite.getImportRewrite());
	}

	@Override
	public String getAdditionalInfo() {
		return kind == Kind.ADD_BRIDGE
				? "Adds a nested Jupiter bridge while retaining the original JUnit 3 harness for unmigrated families." //$NON-NLS-1$
				: "Moves one closed direct JDT Core TestCase family to the nested Jupiter bridge under a global lock."; //$NON-NLS-1$
	}

	private static String loadBridgeSource() throws CoreException {
		try (InputStream stream= JdtCoreHarnessRewriteOperation.class.getClassLoader()
				.getResourceAsStream(BRIDGE_RESOURCE)) {
			if (stream == null) {
				throw new IOException("Missing resource " + BRIDGE_RESOURCE); //$NON-NLS-1$
			}
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw failure("Cannot load the JDT Core Jupiter bridge source template", exception); //$NON-NLS-1$
		}
	}

	private static CoreException failure(String message, Throwable cause) {
		return new CoreException(new Status(IStatus.ERROR, "sandbox_junit_cleanup", message, cause)); //$NON-NLS-1$
	}
}

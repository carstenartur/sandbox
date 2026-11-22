/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/** Prevents generated enum names from changing existing name resolution. */
public final class EnumNameSafety {

	private EnumNameSafety() { }

	/** Rejects both source-level shadowing and an existing type that would own the same class file. */
	public static boolean conflicts(TypeDeclaration owner, String name) {
		if (identifierInUse(owner, name)) {
			return true;
		}
		ITypeBinding binding= owner.resolveBinding();
		if (binding == null || binding.isRecovered() || binding.getBinaryName() == null) {
			return true;
		}
		String binaryName= binding.getBinaryName() + '$' + name;
		String simpleBinaryName= binaryName.substring(binaryName.lastIndexOf('.') + 1);
		CompilationUnit root= (CompilationUnit) owner.getRoot();
		// The current editor buffer can contain a newly declared alias before it has reached the index.
		if (declares(root.types(), simpleBinaryName)) {
			return true;
		}
		if (!(root.getTypeRoot() instanceof ICompilationUnit unit)) {
			return false;
		}
		try {
			var project= unit.getJavaProject();
			// The editor may hold a workspace scheduling rule. Never wait for the workspace index here.
			String packageName= binding.getPackage().getName();
			Map<ICompilationUnit, ICompilationUnit> copies= new HashMap<>();
			collectCopies(copies, JavaCore.getWorkingCopies(null), packageName);
			if (unit.getOwner() != null) {
				collectCopies(copies, JavaCore.getWorkingCopies(unit.getOwner()), packageName);
			}
			for (var sourceRoot : project.getAllPackageFragmentRoots()) {
				var pack= sourceRoot.getPackageFragment(packageName);
				if (sourceRoot.getKind() == IPackageFragmentRoot.K_BINARY) {
					if (pack.exists() && pack.getOrdinaryClassFile(simpleBinaryName + ".class").exists()) return true; //$NON-NLS-1$
					continue;
				}
				if (pack.exists()) {
					for (var other : pack.getCompilationUnits()) {
						if (!unit.getPrimary().equals(other.getPrimary())
								&& !copies.containsKey(other.getPrimary()) && declares(other, simpleBinaryName)) {
							return true;
						}
					}
				}
				// Includes working copies with no corresponding source file yet.
				for (var other : copies.values()) {
					if (pack.equals(other.getParent()) && !unit.getPrimary().equals(other.getPrimary())
							&& declares(other, simpleBinaryName)) return true;
				}
			}
			return false;
		} catch (JavaModelException unavailable) {
			return true;
		}
	}

	private static void collectCopies(Map<ICompilationUnit, ICompilationUnit> copies, ICompilationUnit[] candidates,
			String packageName) {
		for (var candidate : candidates) {
			if (packageName.equals(candidate.getParent().getElementName())) {
				copies.put(candidate.getPrimary(), candidate);
			}
		}
	}

	private static boolean declares(ICompilationUnit unit, String name) throws JavaModelException {
		if (unit.isWorkingCopy() && !unit.isConsistent()) {
			// Read the unsaved buffer. Reconciling another editor would alter its Java model state.
			ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
			String source= unit.getSource();
			if (source == null) return true;
			parser.setSource(source.toCharArray());
			parser.setCompilerOptions(unit.getJavaProject().getOptions(true));
			parser.setIgnoreMethodBodies(true);
			return declares(((CompilationUnit) parser.createAST(null)).types(), name);
		}
		return declares(unit.getTypes(), name);
	}

	private static boolean declares(IType[] types, String name) throws JavaModelException {
		for (var type : types) {
			String identifier= type.getElementName();
			if (name.equals(identifier) || name.startsWith(identifier + '$')
					&& declares(type.getTypes(), name.substring(identifier.length() + 1))) {
				return true;
			}
		}
		return false;
	}

	private static boolean declares(List<?> declarations, String name) {
		for (Object declaration : declarations) {
			if (declaration instanceof AbstractTypeDeclaration type) {
				String identifier= type.getName().getIdentifier();
				if (name.equals(identifier) || name.startsWith(identifier + '$')
						&& declares(type.bodyDeclarations(), name.substring(identifier.length() + 1))) {
					return true;
				}
			}
		}
		return false;
	}

	/** Returns whether a generated member name could shadow an existing declaration or reference. */
	static boolean identifierInUse(TypeDeclaration owner, String name) {
		boolean[] conflict= { false };
		owner.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				if (name.equals(node.getIdentifier())) {
					conflict[0]= true;
				}
				return !conflict[0];
			}
		});
		return conflict[0];
	}
}

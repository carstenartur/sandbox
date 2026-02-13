/*******************************************************************************
 * Copyright (c) 2020 Carsten Hammer.
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
package org.sandbox.jdt.ui.helper.views;

import java.util.List;

import org.eclipse.core.internal.resources.File;
import org.eclipse.core.internal.resources.WorkspaceRoot;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.core.JavaElement;

/**
 * Content provider with progress monitoring support for processing large numbers
 * of compilation units in packages, source folders, or projects.
 * Extracts variable bindings from AST nodes and optionally populates
 * a {@link TypeWideningCache} with type widening analysis results.
 */
public class VariableBindingContentProviderWithProgress {
	
	private static final ILog logger = UsageViewPlugin.getDefault().getLog();

	private final IProgressMonitor progressMonitor;

	private TypeWideningCache typeWideningCache;

	/**
	 * Creates a new content provider with progress monitoring.
	 * 
	 * @param progressMonitor the progress monitor to report progress to
	 */
	public VariableBindingContentProviderWithProgress(IProgressMonitor progressMonitor) {
		this.progressMonitor = progressMonitor;
	}

	/**
	 * Sets the type widening cache to populate during content loading.
	 *
	 * @param cache the cache to populate, or null to disable caching
	 */
	public void setTypeWideningCache(TypeWideningCache cache) {
		this.typeWideningCache = cache;
	}

	/**
	 * Gets the elements (variable bindings) from the input, reporting progress.
	 * 
	 * @param inputElement the input element (typically a List containing a Java element)
	 * @return array of IVariableBinding objects
	 */
	public Object[] getElements(Object inputElement) {
		VariableBindingVisitor variableVisitor = new VariableBindingVisitor();
		
		if (inputElement instanceof List list) {
			if (list.size() == 1) {
				Object object = list.get(0);
				if (object == null) {
					return new Object[0];
				}
				if (object instanceof WorkspaceRoot root) {
					logger.log(new Status(Status.INFO, UsageViewPlugin.PLUGIN_ID, "Processing workspace root: " + root.getName()));
				} else if (object instanceof File file) {
					logger.log(new Status(Status.INFO, UsageViewPlugin.PLUGIN_ID, "Processing file: " + file.getName()));
				} else if (object instanceof JavaElement) {
					IJavaElement javaElement = (IJavaElement) object;
					processJavaElement(variableVisitor, javaElement);
				}
			}
		}
		
		// Debug logging removed - use Eclipse Logger for debugging if needed
		return variableVisitor.getVariableBindings().toArray();
	}

	private void processJavaElement(VariableBindingVisitor variableVisitor, IJavaElement javaElement) {
		if (progressMonitor.isCanceled()) {
			return;
		}
		
		if (javaElement instanceof ICompilationUnit compilationUnit) {
			processCompilationUnit(variableVisitor, compilationUnit);
		} else if (javaElement instanceof IJavaProject javaProject) {
			try {
				for (IPackageFragmentRoot packageRoot : javaProject.getAllPackageFragmentRoots()) {
					if (progressMonitor.isCanceled()) {
						return;
					}
					if (packageRoot.getKind() == IPackageFragmentRoot.K_SOURCE) {
						processPackageFragmentRoot(variableVisitor, packageRoot);
					}
				}
			} catch (JavaModelException e) {
				logger.log(new Status(Status.ERROR, UsageViewPlugin.PLUGIN_ID, "Failed to process package fragment roots", e));
			}
		} else if (javaElement instanceof IPackageFragment packageFragment) {
			processPackageFragment(variableVisitor, packageFragment);
		} else if (javaElement instanceof IPackageFragmentRoot packageRoot) {
			processPackageFragmentRoot(variableVisitor, packageRoot);
		}
	}

	private void processPackageFragmentRoot(VariableBindingVisitor variableVisitor, IPackageFragmentRoot packageRoot) {
		try {
			for (IJavaElement child : packageRoot.getChildren()) {
				if (progressMonitor.isCanceled()) {
					return;
				}
				if (child instanceof IPackageFragment packageFragment) {
					processPackageFragment(variableVisitor, packageFragment);
				}
			}
		} catch (JavaModelException e) {
			logger.log(new Status(Status.ERROR, UsageViewPlugin.PLUGIN_ID, "Failed to process package fragment root children", e));
		}
	}

	private void processPackageFragment(VariableBindingVisitor variableVisitor, IPackageFragment packageFragment) {
		try {
			if (!packageFragment.containsJavaResources()) {
				return;
			}
			for (ICompilationUnit compilationUnit : packageFragment.getCompilationUnits()) {
				if (progressMonitor.isCanceled()) {
					return;
				}
				processCompilationUnit(variableVisitor, compilationUnit);
			}
		} catch (JavaModelException e) {
			logger.log(new Status(Status.ERROR, UsageViewPlugin.PLUGIN_ID, "Failed to process package fragment compilation units", e));
		}
	}

	private void processCompilationUnit(VariableBindingVisitor variableVisitor, ICompilationUnit compilationUnit) {
		progressMonitor.subTask("Processing " + compilationUnit.getElementName()); //$NON-NLS-1$
		CompilationUnit astRoot = parseCompilationUnit(compilationUnit);
		variableVisitor.process(astRoot);
		if (typeWideningCache != null) {
			typeWideningCache.analyzeAndCache(astRoot);
		}
		progressMonitor.worked(1);
	}

	private static CompilationUnit parseCompilationUnit(ICompilationUnit compilationUnit) {
		ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(compilationUnit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}
}

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
package org.sandbox.jdt.container.analysis;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;

import org.sandbox.jdt.container.api.UsageEvidence;

/** Identifies direct byte-array boundaries of resolved JDK binary I/O contracts. */
public final class BinaryArrayBoundaryAnalyzer {
	private BinaryArrayBoundaryAnalyzer() {
	}

	public record Boundary(String candidate, UsageEvidence evidence) {}

	public static List<Boundary> analyze(CompilationUnit root, Runnable checkCanceled) {
		List<Boundary> result= new ArrayList<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation call) {
				checkCanceled.run();
				if (call.arguments().isEmpty()) {
					return true;
				}
				var argument= (Expression) call.arguments().getFirst();
				var variable= ContainerAstFacts.variableBinding(argument);
				if (variable.isEmpty() || !variable.get().getType().isArray()
						|| !"byte".equals(variable.get().getType().getComponentType().getName())) { //$NON-NLS-1$
					return true;
				}
				var method= call.resolveMethodBinding();
				if (method == null || method.isRecovered() || !isBinaryApi(method.getMethodDeclaration())) {
					return true;
				}
				result.add(new Boundary(variable.get().getName(), new UsageEvidence(UsageEvidence.Kind.REJECTION_BOUNDARY,
						"Retain the byte array at this binary I/O boundary. The API reads or writes its bytes and may observe the specified offsets and lengths.", //$NON-NLS-1$
						argument.getStartPosition(), argument.getLength())));
				return true;
			}
		});
		return List.copyOf(result);
	}

	private static boolean isBinaryApi(IMethodBinding method) {
		var parameters= method.getParameterTypes();
		if ((parameters.length != 1 && parameters.length != 3) || !parameters[0].isArray()
				|| !"byte".equals(parameters[0].getComponentType().getName())) { //$NON-NLS-1$
			return false;
		}
		if (parameters.length == 3 && (!"int".equals(parameters[1].getName()) || !"int".equals(parameters[2].getName()))) { //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		String owner= method.getDeclaringClass().getErasure().getQualifiedName();
		return switch (method.getName()) {
			case "write" -> "java.io.DataOutput".equals(owner) || "java.io.OutputStream".equals(owner); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			case "read" -> "java.io.InputStream".equals(owner); //$NON-NLS-1$ //$NON-NLS-2$
			case "readFully" -> "java.io.DataInput".equals(owner); //$NON-NLS-1$ //$NON-NLS-2$
			default -> false;
		};
	}
}

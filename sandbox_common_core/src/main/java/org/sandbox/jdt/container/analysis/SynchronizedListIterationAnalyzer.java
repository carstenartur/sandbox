/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.analysis;

import static org.sandbox.jdt.container.analysis.ContainerAstFacts.unwrap;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.variableBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sandbox.jdt.container.api.ConcurrencyProtocol;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.Evidence;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.EvidenceKind;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.ReentrancyContract;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.DiagnosticOnly;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.Severity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AtomicityRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ContainerIdentity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.IterationSemantics;
import org.sandbox.jdt.container.api.ContainerUsageProfile.SynchronizationKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ContainerUsageProfile.WorkloadShape;

/** Observes the lexical monitor around a direct iteration over a synchronized list field. */
public final class SynchronizedListIterationAnalyzer {

	private static final String LIST= "java.util.List"; //$NON-NLS-1$

	/** Returns one report-only observation per matching enhanced-for loop, in source order. */
	public List<DiagnosticOnly> analyze(CompilationUnit unit) {
		List<DiagnosticOnly> observations= new ArrayList<>();
		unit.accept(new ASTVisitor() {
			@Override
			public boolean visit(EnhancedForStatement loop) {
				observe(unit, loop).ifPresent(observations::add);
				return true;
			}
		});
		return List.copyOf(observations);
	}

	private static Optional<DiagnosticOnly> observe(CompilationUnit unit, EnhancedForStatement loop) {
		Expression iterable= loop.getExpression();
		IVariableBinding field= currentInstanceField(iterable);
		if ((loop.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0
				|| field == null || !Modifier.isPrivate(field.getModifiers()) || !Modifier.isFinal(field.getModifiers())
				|| !(unit.findDeclaringNode(field) instanceof VariableDeclarationFragment declaration)
				|| declaration.getInitializer() == null || !isSynchronizedListInitializer(declaration.getInitializer())) {
			return Optional.empty();
		}
		String fieldKey= field.getKey();
		if (fieldKey == null || fieldKey.isBlank()) {
			return Optional.empty();
		}
		boolean locked= hasMatchingMonitor(loop, field);
		Evidence evidence= locked
				? new Evidence(EvidenceKind.LOCKED_ITERATION, fieldKey,
						"This loop holds the monitor of the synchronized list field.", //$NON-NLS-1$
						iterable.getStartPosition(), iterable.getLength())
				: new Evidence(EvidenceKind.UNRESOLVED_BOUNDARY, null,
						"The synchronized list monitor could not be verified for this loop.", //$NON-NLS-1$
						iterable.getStartPosition(), iterable.getLength());
		ContainerIdentity identity= new ContainerIdentity(fieldKey, field.getName(),
				declaration.getName().getStartPosition(), declaration.getName().getLength());
		ConcurrencyProfile summary= new ConcurrencyProfile(ThreadExposure.UNKNOWN,
				SynchronizationKind.SYNCHRONIZED_WRAPPER,
				locked ? IterationSemantics.EXTERNALLY_LOCKED : IterationSemantics.UNKNOWN,
				AtomicityRequirement.UNKNOWN, WorkloadShape.UNKNOWN);
		ConcurrencyProtocol protocol= new ConcurrencyProtocol(identity, summary, ReentrancyContract.UNKNOWN,
				AnalysisCompleteness.LOCAL_SEED, List.of(evidence));
		return Optional.of(new DiagnosticOnly(protocol, Severity.INFO, List.of(
				"Traversal of a synchronized list requires holding the returned list's monitor. " //$NON-NLS-1$
						+ "This observation covers the loop's enclosing synchronized blocks. Publication and other accesses remain unverified."))); //$NON-NLS-1$
	}

	private static boolean isSynchronizedListInitializer(Expression initializer) {
		if (!(unwrap(initializer) instanceof MethodInvocation invocation)
				|| invocation.arguments().size() != 1
				|| !(unwrap((Expression) invocation.arguments().getFirst()) instanceof ClassInstanceCreation backing)
				|| backing.getAnonymousClassDeclaration() != null || !backing.arguments().isEmpty()) {
			return false;
		}
		IMethodBinding method= invocation.resolveMethodBinding();
		if (method == null || method.isRecovered()) {
			return false;
		}
		IMethodBinding declaration= method.getMethodDeclaration();
		ITypeBinding[] parameters= declaration.getParameterTypes();
		IMethodBinding constructor= backing.resolveConstructorBinding();
		return parameters.length == 1 && constructor != null
				&& !declaration.isRecovered() && Modifier.isStatic(declaration.getModifiers())
				&& "synchronizedList".equals(declaration.getName()) //$NON-NLS-1$
				&& isType(declaration.getDeclaringClass(), "java.util.Collections") //$NON-NLS-1$
				&& isType(parameters[0], LIST) && isType(declaration.getReturnType(), LIST)
				&& !constructor.isRecovered()
				&& isType(constructor.getDeclaringClass(), "java.util.ArrayList"); //$NON-NLS-1$
	}

	private static boolean isType(ITypeBinding type, String qualifiedName) {
		return type != null && !type.isRecovered() && !type.isFromSource()
				&& qualifiedName.equals(type.getErasure().getQualifiedName());
	}

	private static boolean hasMatchingMonitor(EnhancedForStatement loop, IVariableBinding field) {
		for (ASTNode parent= loop.getParent(); parent != null; parent= parent.getParent()) {
			if (parent instanceof LambdaExpression || parent instanceof MethodDeclaration || parent instanceof Initializer
					|| parent instanceof AbstractTypeDeclaration || parent instanceof AnonymousClassDeclaration) {
				break;
			}
			if (parent instanceof SynchronizedStatement block) {
				IVariableBinding monitor= currentInstanceField(block.getExpression());
				if (monitor != null && field.isEqualTo(monitor)) {
					return true;
				}
			}
		}
		return false;
	}

	private static IVariableBinding currentInstanceField(Expression expression) {
		Expression reference= unwrap(expression);
		if (!(reference instanceof SimpleName)
				&& !(reference instanceof FieldAccess access && unwrap(access.getExpression()) instanceof ThisExpression self
						&& self.getQualifier() == null)) {
			return null;
		}
		IVariableBinding field= variableBinding(reference).orElse(null);
		if (field == null || field.isRecovered() || !field.isField() || Modifier.isStatic(field.getModifiers())) {
			return null;
		}
		IVariableBinding declaration= field.getVariableDeclaration();
		ITypeBinding receiver= enclosingType(reference);
		return !declaration.isRecovered() && receiver != null && !receiver.isRecovered()
				&& receiver.getTypeDeclaration().isEqualTo(declaration.getDeclaringClass()) ? declaration : null;
	}

	private static ITypeBinding enclosingType(ASTNode reference) {
		for (ASTNode parent= reference.getParent(); parent != null; parent= parent.getParent()) {
			if (parent instanceof AbstractTypeDeclaration type) {
				return type.resolveBinding();
			}
			if (parent instanceof AnonymousClassDeclaration type) {
				return type.resolveBinding();
			}
		}
		return null;
	}
}

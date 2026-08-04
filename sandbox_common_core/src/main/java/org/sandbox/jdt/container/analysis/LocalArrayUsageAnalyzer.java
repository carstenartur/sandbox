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

import static org.sandbox.jdt.container.analysis.ContainerAstFacts.isArrayLength;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.isOne;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.sameVariable;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.unwrap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AccessProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AliasingContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AtomicityRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.EscapeLevel;
import org.sandbox.jdt.container.api.ContainerUsageProfile.IterationSemantics;
import org.sandbox.jdt.container.api.ContainerUsageProfile.MutationLifecycle;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.SynchronizationKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ContainerUsageProfile.WorkloadShape;
import org.sandbox.jdt.container.api.UsageEvidence;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;
import org.sandbox.jdt.internal.common.AstProcessing;
import org.sandbox.jdt.internal.common.ReferenceHolder;

/**
 * Classifies every binding-resolved use of one array candidate inside a compilation
 * unit.
 *
 * <p>The first implementation intentionally supports a narrow, explainable set of
 * uses: declarations, recognised array growth, tail append, {@code length}, indexed
 * read/write and enhanced-for iteration. Any other use is retained as source-backed
 * rejection evidence. This fail-closed boundary prevents a local seed from becoming
 * an executable migration candidate merely because most uses looked familiar.</p>
 */
public final class LocalArrayUsageAnalyzer {

	/** Analyzes one seed profile in the supplied compilation unit. */
	public ContainerUsageProfile analyze(
			CompilationUnit compilationUnit,
			ContainerUsageProfile seed) {
		Objects.requireNonNull(compilationUnit, "compilationUnit"); //$NON-NLS-1$
		Objects.requireNonNull(seed, "seed"); //$NON-NLS-1$
		if (seed.currentShape() != ContainerShape.ARRAY) {
			throw new IllegalArgumentException("LocalArrayUsageAnalyzer requires an array profile"); //$NON-NLS-1$
		}

		Accumulator accumulator= new Accumulator(seed);
		if (!seed.identity().hasResolvedBinding()) {
			accumulator.reject(Kind.UNRESOLVED_BINDING,
					"Local usage analysis requires a resolved variable binding", //$NON-NLS-1$
					seed.identity().sourceStart(), seed.identity().sourceLength());
			return accumulator.toProfile();
		}

		ReferenceHolder<String, Object> traversalState= ReferenceHolder.create();
		AstProcessing.independent(traversalState)
				.on(SimpleName.class, (name, holder) -> {
					classifyMatchingReference(name, seed.identity().bindingKey(), accumulator);
					return true;
				})
				.build(compilationUnit);

		if (!accumulator.bindingSeen()) {
			accumulator.reject(Kind.UNRESOLVED_BINDING,
					"The candidate binding was not found in the analyzed compilation unit", //$NON-NLS-1$
					seed.identity().sourceStart(), seed.identity().sourceLength());
		}
		return accumulator.toProfile();
	}

	/** Analyzes profiles in deterministic input order. */
	public List<ContainerUsageProfile> analyzeAll(
			CompilationUnit compilationUnit,
			List<ContainerUsageProfile> seeds) {
		Objects.requireNonNull(seeds, "seeds"); //$NON-NLS-1$
		return seeds.stream().map(seed -> analyze(compilationUnit, seed)).toList();
	}

	private static void classifyMatchingReference(
			SimpleName name,
			String targetBindingKey,
			Accumulator accumulator) {
		IBinding resolved= name.resolveBinding();
		if (!(resolved instanceof IVariableBinding variable)
				|| !targetBindingKey.equals(variable.getVariableDeclaration().getKey())) {
			return;
		}

		IVariableBinding declaration= variable.getVariableDeclaration();
		accumulator.observeBinding(declaration);
		if (isDeclarationName(name)) {
			return;
		}
		if (crossesExecutableBoundary(name, declaration)) {
			accumulator.reject(Kind.CAPTURED_USAGE,
					"Array value is captured by a lambda, nested type, or different method body", name); //$NON-NLS-1$
			return;
		}

		Expression reference= completeReferenceExpression(name, targetBindingKey);
		ASTNode parent= reference.getParent();
		if (isLengthRead(reference, parent)) {
			accumulator.lengthRead(reference);
		} else if (parent instanceof ArrayAccess access && access.getArray() == reference) {
			classifyArrayAccess(access, reference, accumulator);
		} else if (parent instanceof EnhancedForStatement enhancedFor
				&& enhancedFor.getExpression() == reference) {
			accumulator.encounterIteration(reference);
		} else if (parent instanceof MethodInvocation invocation) {
			classifyMethodInvocation(reference, invocation, accumulator);
		} else if (parent instanceof Assignment assignment) {
			classifyAssignment(reference, assignment, accumulator);
		} else if (parent instanceof VariableDeclarationFragment fragment
				&& fragment.getInitializer() == reference) {
			accumulator.reject(Kind.UNSAFE_ESCAPE,
					"Array value is assigned to another local variable", reference); //$NON-NLS-1$
		} else if (parent instanceof ReturnStatement) {
			accumulator.reject(Kind.UNSAFE_ESCAPE,
					"Array value escapes through a return statement", reference); //$NON-NLS-1$
		} else if (parent instanceof InfixExpression infix && isIdentityComparison(infix)) {
			accumulator.reject(Kind.ARRAY_IDENTITY,
					"Array identity is observed by a reference comparison", reference); //$NON-NLS-1$
		} else if (parent instanceof SynchronizedStatement synchronizedStatement
				&& synchronizedStatement.getExpression() == reference) {
			accumulator.reject(Kind.ARRAY_IDENTITY,
					"Array identity is used as a synchronization monitor", reference); //$NON-NLS-1$
		} else if (parent instanceof CastExpression || parent instanceof InstanceofExpression) {
			accumulator.reject(Kind.ARRAY_IDENTITY,
					"Runtime array representation is observed by a type operation", reference); //$NON-NLS-1$
		} else {
			accumulator.reject(Kind.UNCLASSIFIED_USAGE,
					"Array use is not yet classified by local container analysis", reference); //$NON-NLS-1$
		}
	}

	private static boolean crossesExecutableBoundary(
			ASTNode reference,
			IVariableBinding declaration) {
		IMethodBinding declaringMethod= declaration.getDeclaringMethod();
		if (declaringMethod == null || declaration.isField()) {
			return false;
		}
		String declaringKey= methodKey(declaringMethod);
		ASTNode current= reference.getParent();
		while (current != null) {
			if (current instanceof LambdaExpression
					|| current instanceof AnonymousClassDeclaration
					|| current instanceof AbstractTypeDeclaration) {
				return true;
			}
			if (current instanceof MethodDeclaration method) {
				IMethodBinding currentBinding= method.resolveBinding();
				return currentBinding == null
						|| !declaringKey.equals(methodKey(currentBinding));
			}
			current= current.getParent();
		}
		return true;
	}

	private static String methodKey(IMethodBinding method) {
		IMethodBinding declaration= method.getMethodDeclaration();
		String key= declaration.getKey();
		return key == null ? "" : key; //$NON-NLS-1$
	}

	private static void classifyArrayAccess(
			ArrayAccess access,
			Expression reference,
			Accumulator accumulator) {
		Assignment assignment= enclosingAssignment(access);
		if (assignment != null && unwrap(assignment.getLeftHandSide()) == access) {
			if (isTailAppend(access, reference)) {
				accumulator.appendObserved();
			} else {
				accumulator.indexedWrite(access);
			}
		} else {
			accumulator.indexedRead(access);
		}
	}

	private static void classifyMethodInvocation(
			Expression reference,
			MethodInvocation invocation,
			Accumulator accumulator) {
		if (invocation.arguments().contains(reference)
				&& isRecognizedCopySource(invocation, reference)) {
			return;
		}
		if (invocation.arguments().contains(reference)) {
			accumulator.reject(Kind.UNSAFE_ESCAPE,
					"Array value is passed to another method", reference); //$NON-NLS-1$
		} else {
			accumulator.reject(Kind.UNCLASSIFIED_USAGE,
					"Method invocation observes array-specific behavior", reference); //$NON-NLS-1$
		}
	}

	private static void classifyAssignment(
			Expression reference,
			Assignment assignment,
			Accumulator accumulator) {
		if (assignment.getLeftHandSide() == reference
				&& isRecognizedGrowthAssignment(assignment, reference)) {
			return;
		}
		if (assignment.getRightHandSide() == reference) {
			accumulator.reject(Kind.UNSAFE_ESCAPE,
					"Array value is assigned to another variable or field", reference); //$NON-NLS-1$
		} else {
			accumulator.reject(Kind.UNCLASSIFIED_USAGE,
					"Array reference is reassigned outside the recognised growth pattern", reference); //$NON-NLS-1$
		}
	}

	private static boolean isDeclarationName(SimpleName name) {
		ASTNode parent= name.getParent();
		return parent instanceof VariableDeclarationFragment fragment && fragment.getName() == name
				|| parent instanceof SingleVariableDeclaration declaration
						&& declaration.getName() == name;
	}

	private static Expression completeReferenceExpression(SimpleName name, String bindingKey) {
		Expression reference= name;
		ASTNode parent= reference.getParent();
		if (parent instanceof QualifiedName qualified
				&& qualified.getName() == reference
				&& hasBindingKey(qualified.resolveBinding(), bindingKey)) {
			reference= qualified;
		} else if (parent instanceof FieldAccess fieldAccess
				&& fieldAccess.getName() == reference
				&& hasBindingKey(fieldAccess.resolveFieldBinding(), bindingKey)) {
			reference= fieldAccess;
		} else if (parent instanceof SuperFieldAccess superFieldAccess
				&& superFieldAccess.getName() == reference
				&& hasBindingKey(superFieldAccess.resolveFieldBinding(), bindingKey)) {
			reference= superFieldAccess;
		}
		while (reference.getParent() instanceof ParenthesizedExpression parenthesized) {
			reference= parenthesized;
		}
		return reference;
	}

	private static boolean hasBindingKey(IBinding binding, String bindingKey) {
		return binding instanceof IVariableBinding variable
				&& bindingKey.equals(variable.getVariableDeclaration().getKey());
	}

	private static boolean isLengthRead(Expression reference, ASTNode parent) {
		if (parent instanceof QualifiedName qualifiedName) {
			return qualifiedName.getQualifier() == reference
					&& "length".equals(qualifiedName.getName().getIdentifier()); //$NON-NLS-1$
		}
		if (parent instanceof FieldAccess fieldAccess) {
			return fieldAccess.getExpression() == reference
					&& "length".equals(fieldAccess.getName().getIdentifier()); //$NON-NLS-1$
		}
		return false;
	}

	private static boolean isRecognizedCopySource(
			MethodInvocation invocation,
			Expression reference) {
		return isArraysCopyOf(invocation)
				&& !invocation.arguments().isEmpty()
				&& invocation.arguments().get(0) == reference;
	}

	private static boolean isRecognizedGrowthAssignment(
			Assignment assignment,
			Expression reference) {
		Expression right= unwrap(assignment.getRightHandSide());
		if (!(right instanceof MethodInvocation copyOf)
				|| !isArraysCopyOf(copyOf)
				|| copyOf.arguments().size() != 2) {
			return false;
		}
		Expression source= (Expression) copyOf.arguments().get(0);
		Expression length= (Expression) copyOf.arguments().get(1);
		return sameVariable(reference, source) && isLengthPlusOne(length, reference);
	}

	private static boolean isArraysCopyOf(MethodInvocation invocation) {
		if (!"copyOf".equals(invocation.getName().getIdentifier())) { //$NON-NLS-1$
			return false;
		}
		IMethodBinding binding= invocation.resolveMethodBinding();
		if (binding != null && binding.getDeclaringClass() != null) {
			return "java.util.Arrays".equals( //$NON-NLS-1$
					binding.getDeclaringClass().getErasure().getQualifiedName());
		}
		Expression owner= invocation.getExpression();
		return owner != null && ("Arrays".equals(owner.toString()) //$NON-NLS-1$
				|| "java.util.Arrays".equals(owner.toString())); //$NON-NLS-1$
	}

	private static boolean isTailAppend(ArrayAccess access, Expression array) {
		return isLengthMinusOne(access.getIndex(), array);
	}

	private static boolean isLengthPlusOne(Expression expression, Expression array) {
		Expression unwrapped= unwrap(expression);
		if (!(unwrapped instanceof InfixExpression infix)
				|| infix.getOperator() != InfixExpression.Operator.PLUS
				|| !infix.extendedOperands().isEmpty()) {
			return false;
		}
		return isArrayLength(infix.getLeftOperand(), array) && isOne(infix.getRightOperand())
				|| isOne(infix.getLeftOperand()) && isArrayLength(infix.getRightOperand(), array);
	}

	private static boolean isLengthMinusOne(Expression expression, Expression array) {
		Expression unwrapped= unwrap(expression);
		if (!(unwrapped instanceof InfixExpression infix)
				|| infix.getOperator() != InfixExpression.Operator.MINUS
				|| !infix.extendedOperands().isEmpty()) {
			return false;
		}
		return isArrayLength(infix.getLeftOperand(), array) && isOne(infix.getRightOperand());
	}

	private static Assignment enclosingAssignment(ASTNode node) {
		ASTNode current= node;
		while (current.getParent() instanceof ParenthesizedExpression) {
			current= current.getParent();
		}
		return current.getParent() instanceof Assignment assignment ? assignment : null;
	}

	private static boolean isIdentityComparison(InfixExpression expression) {
		return expression.getOperator() == InfixExpression.Operator.EQUALS
				|| expression.getOperator() == InfixExpression.Operator.NOT_EQUALS;
	}

	private static UsageEvidence evidence(Kind kind, String summary, ASTNode node) {
		return new UsageEvidence(kind, summary, node.getStartPosition(), node.getLength());
	}

	private static final class Accumulator {

		private final ContainerUsageProfile seed;
		private final List<UsageEvidence> evidence;
		private boolean bindingSeen;
		private boolean field;
		private boolean parameter;
		private boolean indexedRead;
		private boolean indexedWrite;
		private boolean positionalWrite;
		private boolean append;
		private boolean encounterIteration;
		private boolean rejected;

		Accumulator(ContainerUsageProfile seed) {
			this.seed= seed;
			evidence= new ArrayList<>(seed.evidence());
			indexedRead= seed.access().indexedRead();
			indexedWrite= seed.access().indexedWrite();
			append= seed.access().append();
		}

		void observeBinding(IVariableBinding binding) {
			bindingSeen= true;
			field|= binding.isField();
			parameter|= binding.isParameter();
		}

		boolean bindingSeen() {
			return bindingSeen;
		}

		void lengthRead(ASTNode node) {
			evidence.add(LocalArrayUsageAnalyzer.evidence(Kind.ARRAY_LENGTH_READ,
					"Array length is observed", node)); //$NON-NLS-1$
		}

		void indexedRead(ASTNode node) {
			indexedRead= true;
			evidence.add(LocalArrayUsageAnalyzer.evidence(Kind.INDEXED_READ,
					"Array element is read by index", node)); //$NON-NLS-1$
		}

		void indexedWrite(ASTNode node) {
			indexedWrite= true;
			positionalWrite= true;
			evidence.add(LocalArrayUsageAnalyzer.evidence(Kind.INDEXED_WRITE,
					"Existing array position is written by index", node)); //$NON-NLS-1$
		}

		void appendObserved() {
			append= true;
		}

		void encounterIteration(ASTNode node) {
			encounterIteration= true;
			evidence.add(LocalArrayUsageAnalyzer.evidence(Kind.ENCOUNTER_ITERATION,
					"Array is traversed in encounter order", node)); //$NON-NLS-1$
		}

		void reject(Kind kind, String summary, ASTNode node) {
			reject(kind, summary, node.getStartPosition(), node.getLength());
		}

		void reject(Kind kind, String summary, int start, int length) {
			rejected= true;
			evidence.add(new UsageEvidence(kind, summary, start, length));
		}

		ContainerUsageProfile toProfile() {
			AnalysisCompleteness completeness= rejected
					? AnalysisCompleteness.REJECTED
					: AnalysisCompleteness.LOCAL_USAGE_COMPLETE;
			if (!rejected) {
				evidence.add(new UsageEvidence(Kind.LOCAL_USAGE_COMPLETE,
						"Every local use of the array binding was classified", //$NON-NLS-1$
						seed.identity().sourceStart(), seed.identity().sourceLength()));
			}

			OrderRequirement order= indexedRead || positionalWrite
					? OrderRequirement.POSITIONAL
					: encounterIteration ? OrderRequirement.ENCOUNTER : seed.orderRequirement();
			EscapeLevel escape= field
					? EscapeLevel.FIELD
					: parameter ? EscapeLevel.METHOD_BOUNDARY : EscapeLevel.LOCAL;
			AliasingContract aliasing= rejected
					? AliasingContract.UNKNOWN
					: AliasingContract.NO_OBSERVED_ALIAS;
			ConcurrencyProfile concurrency= !rejected && escape == EscapeLevel.LOCAL
					? new ConcurrencyProfile(
							ThreadExposure.THREAD_CONFINED,
							SynchronizationKind.NONE,
							IterationSemantics.LIVE,
							AtomicityRequirement.INDIVIDUAL_OPERATIONS,
							WorkloadShape.UNKNOWN)
					: seed.concurrency();

			return new ContainerUsageProfile(
					seed.identity(),
					seed.currentShape(),
					seed.elementDomain(),
					new AccessProfile(
							indexedRead,
							indexedWrite,
							append,
							false,
							false,
							seed.access().membershipQuery(),
							seed.access().keyLookup()),
					order,
					seed.uniquenessRequirement(),
					MutationLifecycle.CONTINUOUSLY_MUTABLE,
					seed.nullContract(),
					aliasing,
					escape,
					concurrency,
					completeness,
					evidence);
		}
	}
}

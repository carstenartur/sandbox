/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;

/**
 * Builder class for constructing stream pipelines from enhanced for-loops.
 * 
 * <p>
 * This class analyzes the body of an enhanced for-loop and determines if it can
 * be converted into a stream pipeline. It handles various patterns including:
 * <ul>
 * <li>Simple forEach operations</li>
 * <li>MAP operations (variable declarations with initializers)</li>
 * <li>FILTER operations (IF statements)</li>
 * <li>REDUCE operations (accumulator patterns including SUM, PRODUCT,
 * INCREMENT, MAX, MIN)</li>
 * <li>ANYMATCH/NONEMATCH/ALLMATCH operations (early returns)</li>
 * </ul>
 * 
 * <p><b>Architecture Overview:</b></p>
 * <p>The conversion process involves three phases:</p>
 * 
 * <ol>
 * <li><b>Analysis Phase</b> ({@link #analyze()}):
 *     <ul>
 *     <li>Validates preconditions via {@link PreconditionsChecker}</li>
 *     <li>Parses loop body into {@link ProspectiveOperation}s</li>
 *     <li>Validates variable scoping</li>
 *     <li>Returns true if conversion is possible</li>
 *     </ul>
 * </li>
 * <li><b>Construction Phase</b> ({@link #buildPipeline()}):
 *     <ul>
 *     <li>Determines if .stream() prefix is needed</li>
 *     <li>Chains operations into MethodInvocation</li>
 *     <li>Generates lambda parameters and arguments</li>
 *     <li>Returns the complete pipeline expression</li>
 *     </ul>
 * </li>
 * <li><b>Wrapping Phase</b> ({@link #wrapPipeline(MethodInvocation)}):
 *     <ul>
 *     <li>Wraps in appropriate Statement type</li>
 *     <li>Handles reducers (assignment to accumulator)</li>
 *     <li>Handles anyMatch/noneMatch/allMatch (IF with early return)</li>
 *     <li>Returns the final Statement to replace the for-loop</li>
 *     </ul>
 * </li>
 * </ol>
 * 
 * <p><b>Supported Patterns:</b></p>
 * <ul>
 * <li><b>FOREACH:</b> {@code for (x : xs) { action(x); }} → {@code xs.forEach(x -> action(x))}</li>
 * <li><b>MAP:</b> {@code for (x : xs) { T y = f(x); ... }} → {@code xs.stream().map(x -> f(x))...}</li>
 * <li><b>FILTER:</b> {@code for (x : xs) { if (p(x)) { ... } }} → {@code xs.stream().filter(x -> p(x))...}</li>
 * <li><b>REDUCE:</b> {@code for (x : xs) { sum += x; }} → {@code sum = xs.stream().reduce(sum, Integer::sum)}</li>
 * <li><b>ANYMATCH:</b> {@code for (x : xs) { if (p(x)) return true; } return false;} → {@code if (xs.stream().anyMatch(x -> p(x))) return true;}</li>
 * </ul>
 * 
 * <p>
 * <b>Supported Reduction Patterns:</b>
 * <ul>
 * <li>INCREMENT: {@code i++}, {@code ++i}</li>
 * <li>DECREMENT: {@code i--}, {@code --i}, {@code i -= 1}</li>
 * <li>SUM: {@code sum += value}</li>
 * <li>PRODUCT: {@code product *= value}</li>
 * <li>STRING_CONCAT: {@code str += substring}</li>
 * <li>MAX: {@code max = Math.max(max, value)}</li>
 * <li>MIN: {@code min = Math.min(min, value)}</li>
 * <li>CUSTOM_AGGREGATE: Custom aggregation patterns</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b> This class is not thread-safe. Create a new instance
 * for each loop to be analyzed.</p>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * PreconditionsChecker preconditions = new PreconditionsChecker(forLoop, ...);
 * StreamPipelineBuilder builder = new StreamPipelineBuilder(forLoop, preconditions);
 * 
 * if (builder.analyze()) {
 *     MethodInvocation pipeline = builder.buildPipeline();
 *     Statement replacement = builder.wrapPipeline(pipeline);
 *     // Replace forLoop with replacement
 * }
 * }</pre>
 * 
 * <p>
 * Based on the NetBeans mapreduce hints implementation:
 * https://github.com/apache/netbeans/tree/master/java/java.hints/src/org/netbeans/modules/java/hints/jdk/mapreduce
 * 
 * @see ProspectiveOperation
 * @see PreconditionsChecker
 * @see org.sandbox.jdt.internal.corext.util.VariableResolver
 * @see org.sandbox.jdt.internal.corext.util.ExpressionHelper
 * @see Refactorer
 */
public class StreamPipelineBuilder {

	private final EnhancedForStatement forLoop;
	private final PreconditionsChecker preconditions;
	private final ReducePatternDetector reduceDetector;
	private final CollectPatternDetector collectDetector;
	private final IfStatementAnalyzer ifAnalyzer;
	private final LoopBodyParser loopBodyParser;

	private List<ProspectiveOperation> operations;
	private String loopVariableName;
	private boolean analyzed = false;
	private boolean convertible = false;
	private boolean isAnyMatchPattern = false;
	private boolean isNoneMatchPattern = false;
	private boolean isAllMatchPattern = false;
	
	/** Assembler for building the final pipeline (initialized after analysis). */
	private PipelineAssembler pipelineAssembler;

	/**
	 * Creates a new StreamPipelineBuilder for the given for-loop.
	 * 
	 * @param forLoop       the enhanced for-loop to analyze
	 * @param preconditions the preconditions checker for the loop
	 * @throws IllegalArgumentException if forLoop or preconditions is null
	 */
	public StreamPipelineBuilder(EnhancedForStatement forLoop, PreconditionsChecker preconditions) {
		if (forLoop == null) {
			throw new IllegalArgumentException("forLoop cannot be null");
		}
		if (preconditions == null) {
			throw new IllegalArgumentException("preconditions cannot be null");
		}

		this.forLoop = forLoop;
		this.preconditions = preconditions;
		this.reduceDetector = new ReducePatternDetector(forLoop);
		this.collectDetector = new CollectPatternDetector(forLoop);
		this.ifAnalyzer = new IfStatementAnalyzer(forLoop);

		// Internal invariant: EnhancedForStatement must have a parameter with a name
		assert forLoop.getParameter() != null && forLoop.getParameter().getName() != null
				: "forLoop must have a valid parameter with a name";

		this.loopVariableName = forLoop.getParameter().getName().getIdentifier();
		this.operations = new ArrayList<>();
		this.isAnyMatchPattern = preconditions.isAnyMatchPattern();
		this.isNoneMatchPattern = preconditions.isNoneMatchPattern();
		this.isAllMatchPattern = preconditions.isAllMatchPattern();
		
		// Initialize LoopBodyParser with all required dependencies
		this.loopBodyParser = new LoopBodyParser(forLoop, reduceDetector, collectDetector, ifAnalyzer, 
				isAnyMatchPattern, isNoneMatchPattern, isAllMatchPattern);
	}

	/**
	 * Analyzes the loop body to determine if it can be converted to a stream
	 * pipeline.
	 * 
	 * <p>
	 * This method should be called before attempting to build the pipeline. It
	 * inspects the loop body and extracts a sequence of
	 * {@link ProspectiveOperation}s that represent the transformation.
	 * 
	 * @return true if the loop can be converted to a stream pipeline, false
	 *         otherwise
	 */
	public boolean analyze() {
		if (analyzed) {
			return convertible;
		}

		analyzed = true;

		// Check basic preconditions
		if (!preconditions.isSafeToRefactor() 
//				|| !preconditions.iteratesOverIterable()
				) {
			convertible = false;
			return false;
		}

		// Parse the loop body into operations
		Statement loopBody = forLoop.getBody();
		operations = parseLoopBody(loopBody, loopVariableName);

		// Check if we have any operations
		if (operations.isEmpty()) {
			convertible = false;
			return false;
		}

		// Validate variable scoping
		if (!validateVariableScope(operations, loopVariableName)) {
			convertible = false;
			return false;
		}

		// Initialize the pipeline assembler for building the final pipeline
		pipelineAssembler = new PipelineAssembler(forLoop, operations, loopVariableName);
		pipelineAssembler.setUsedVariableNames(getUsedVariableNames(forLoop));
		pipelineAssembler.setReduceDetector(reduceDetector);
		pipelineAssembler.setCollectDetector(collectDetector);

		convertible = true;
		return true;
	}

	/**
	 * Builds the stream pipeline from the analyzed operations.
	 * 
	 * <p>This method constructs a chain of method invocations representing
	 * the complete stream pipeline. It automatically determines whether to
	 * use {@code .stream()} prefix or direct collection methods like
	 * {@code .forEach()}.</p>
	 * 
	 * <p><b>Examples:</b></p>
	 * <pre>{@code
	 * // Simple forEach (no .stream() needed)
	 * list.forEach(item -> System.out.println(item))
	 * 
	 * // Complex pipeline (needs .stream())
	 * list.stream()
	 *     .filter(item -> item != null)
	 *     .map(item -> item.toString())
	 *     .reduce("", String::concat)
	 * }</pre>
	 * 
	 * <p><b>Prerequisites:</b></p>
	 * <ul>
	 * <li>{@link #analyze()} must have been called and returned {@code true}</li>
	 * <li>The operations list must not be empty</li>
	 * </ul>
	 * 
	 * @return a MethodInvocation representing the stream pipeline, or null if
	 *         the loop cannot be converted
	 * @see #analyze()
	 * @see #wrapPipeline(MethodInvocation)
	 * @see PipelineAssembler
	 */
	public MethodInvocation buildPipeline() {
		if (!analyzed || !convertible || pipelineAssembler == null) {
			return null;
		}
		return pipelineAssembler.buildPipeline();
	}
	
	/**
	 * Returns whether the pipeline needs the java.util.Arrays import.
	 * This is true when iterating over an array.
	 * 
	 * <p>This method should be called after {@link #buildPipeline()} to determine
	 * if an import needs to be added.</p>
	 * 
	 * @return true if Arrays import is needed
	 */
	public boolean needsArraysImport() {
		return pipelineAssembler != null && pipelineAssembler.needsArraysImport();
	}

	/**
	 * Wraps the stream pipeline in an appropriate statement type based on the terminal operation.
	 * 
	 * <p>
	 * The wrapping strategy depends on the type of terminal operation:
	 * <ul>
	 * <li><b>ANYMATCH</b>: Wraps in {@code if (stream.anyMatch(...)) { return true; }}</li>
	 * <li><b>NONEMATCH</b>: Wraps in {@code if (!stream.noneMatch(...)) { return false; }}</li>
	 * <li><b>ALLMATCH</b>: Wraps in {@code if (!stream.allMatch(...)) { return false; }}</li>
	 * <li><b>REDUCE</b>: Wraps in assignment {@code accumulatorVariable = stream.reduce(...)}</li>
	 * <li><b>FOREACH</b> (and others): Wraps in {@link org.eclipse.jdt.core.dom.ExpressionStatement}</li>
	 * </ul>
	 * 
	 * @param pipeline the stream pipeline to wrap (must not be null)
	 * @return a Statement wrapping the pipeline, or null if pipeline is null
	 * @see OperationType
	 * @see PipelineAssembler
	 */
	public Statement wrapPipeline(MethodInvocation pipeline) {
		if (pipelineAssembler == null) {
			return null;
		}
		return pipelineAssembler.wrapPipeline(pipeline);
	}

	/**
	 * Analyzes the body of an enhanced for-loop and extracts a list of
	 * {@link ProspectiveOperation} objects representing the operations that can be
	 * mapped to stream operations.
	 * 
	 * <p><b>Example Patterns:</b></p>
	 * <pre>{@code
	 * // MAP: Variable declaration with initializer
	 * for (Integer num : numbers) {
	 *     int squared = num * num;  // → .map(num -> num * num)
	 *     System.out.println(squared);
	 * }
	 * 
	 * // FILTER: IF statement
	 * for (String item : items) {
	 *     if (item != null) {  // → .filter(item -> item != null)
	 *         System.out.println(item);
	 *     }
	 * }
	 * 
	 * // REDUCE: Accumulator pattern
	 * int sum = 0;
	 * for (Integer num : numbers) {
	 *     sum += num;  // → .reduce(sum, Integer::sum)
	 * }
	 * }</pre>
	 * 
	 * @param body the {@link Statement} representing the loop body
	 * @param loopVarName the name of the loop variable
	 * @return a list of {@link ProspectiveOperation} objects
	 */
	private List<ProspectiveOperation> parseLoopBody(Statement body, String loopVarName) {
		return loopBodyParser.parse(body, loopVarName);
	}

	/**
	 * Validates that variables used in operations are properly scoped.
	 * 
	 * <p>
	 * This method ensures that:
	 * <ul>
	 * <li>Consumed variables are available in the current scope (defined earlier in
	 * pipeline)</li>
	 * <li>Produced variables don't shadow loop variables improperly</li>
	 * <li>Accumulator variables don't leak into lambda scopes</li>
	 * </ul>
	 * 
	 * <p>
	 * <b>Relationship with {@link #isSafeSideEffect}:</b>
	 * </p>
	 * <p>
	 * While {@code isSafeSideEffect} performs early detection of obvious assignment
	 * issues during pipeline construction, this method performs comprehensive scope
	 * checking across the entire pipeline to catch variable availability issues.
	 * Both methods work together to ensure safe conversions:
	 * <ul>
	 * <li>{@code isSafeSideEffect}: Detects unsafe assignments to external/loop
	 * variables</li>
	 * <li>{@code validateVariableScope}: Validates all variables are properly
	 * defined and scoped</li>
	 * </ul>
	 * 
	 * <p>
	 * <b>Algorithm:</b>
	 * </p>
	 * <p>
	 * Tracks available variables as we process operations in sequence. For each
	 * operation:
	 * <ol>
	 * <li>Check that all consumed variables (except loop var and accumulators) are
	 * available</li>
	 * <li>Add any produced variables to the available set for subsequent
	 * operations</li>
	 * <li>Return false if any consumed variable is used before being defined</li>
	 * </ol>
	 * 
	 * @param operations  the list of operations to validate (must not be null)
	 * @param loopVarName the loop variable name (must not be null)
	 * @return true if all variables are properly scoped, false otherwise
	 * @throws IllegalArgumentException if operations or loopVarName is null
	 */
	private boolean validateVariableScope(List<ProspectiveOperation> operations, String loopVarName) {
		if (operations == null) {
			throw new IllegalArgumentException("operations cannot be null");
		}
		if (loopVarName == null) {
			throw new IllegalArgumentException("loopVarName cannot be null");
		}

		Set<String> availableVars = new HashSet<>();
		availableVars.add(loopVarName);
		
		// Add all variables from outer scope (method parameters, fields, etc.)
		// These are always available in lambdas
		Collection<String> outerScopeVars = getUsedVariableNames(forLoop);
		availableVars.addAll(outerScopeVars);
		
		// Track if we've moved past the loop variable to a mapped variable
		boolean loopVarConsumed = false;

		for (ProspectiveOperation op : operations) {
			if (op == null) {
				throw new IllegalStateException("Encountered null ProspectiveOperation in operations list");
			}

			// Check consumed variables are available
			Set<String> consumed = op.getConsumedVariables();
			for (String var : consumed) {
				// Accumulator variables are in outer scope, always available
				if (isAccumulatorVariable(var, operations)) {
					continue;
				}
				
				// Variables from outer scope (method parameters, fields, etc.) are always available
				if (outerScopeVars.contains(var)) {
					continue;
				}
				
				// After a MAP produces a new variable, the loop variable should not be used
				// unless it's the current operation that consumes it
				if (var.equals(loopVarName)) {
					if (loopVarConsumed && op.getProducedVariableName() != null) {
						// Loop variable used after it's been replaced by a MAP - scope violation
						return false;
					}
				} else {
					// Non-loop, non-accumulator, non-outer-scope variable - must be in availableVars
					if (!availableVars.contains(var)) {
						// Variable used before it's defined - this is a scope violation
						return false;
					}
				}
			}

			// Add produced variables to available set and mark loop var as consumed if applicable
			String produced = op.getProducedVariableName();
			if (produced != null && !produced.isEmpty()) {
				availableVars.add(produced);
				
				// If this MAP operation consumed the loop variable, mark it as consumed
				if (consumed.contains(loopVarName)) {
					loopVarConsumed = true;
					// Remove loop variable from available vars - it's now been replaced
					availableVars.remove(loopVarName);
				}
			}
		}

		return true;
	}

	/**
	 * Checks if a variable is an accumulator variable in any REDUCE operation.
	 * 
	 * @param varName    the variable name to check
	 * @param operations the list of operations
	 * @return true if the variable is an accumulator, false otherwise
	 */
	private boolean isAccumulatorVariable(String varName, List<ProspectiveOperation> operations) {
		for (ProspectiveOperation op : operations) {
					if (op.getOperationType() == OperationType.REDUCE) {
				if (varName.equals(op.getAccumulatorVariableName())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Gets all variable names used in the scope of the given AST node.
	 * This is used to generate unique lambda parameter names that don't clash
	 * with existing variables in scope.
	 * 
	 * @param node the AST node to analyze
	 * @return collection of variable names used in the node's scope
	 */
	private static Collection<String> getUsedVariableNames(ASTNode node) {
		CompilationUnit root = (CompilationUnit) node.getRoot();
		return new ScopeAnalyzer(root).getUsedVariableNames(node.getStartPosition(), node.getLength());
	}
	
	/**
	 * Checks if the analyzed operations include a REDUCE operation.
	 * 
	 * @return true if there is a REDUCE operation, false otherwise
	 */
	public boolean hasReduceOperation() {
		if (!analyzed || !convertible) {
			return false;
		}
		return operations.stream()
				.anyMatch(op -> op.getOperationType() == OperationType.REDUCE);
	}
	
	/**
	 * Gets the accumulator variable name for REDUCE operations.
	 * 
	 * @return the accumulator variable name, or null if no REDUCE operation exists
	 */
	public String getAccumulatorVariableName() {
		if (!analyzed || !convertible) {
			return null;
		}
		for (ProspectiveOperation op : operations) {
			if (op.getOperationType() == OperationType.REDUCE) {
				return op.getAccumulatorVariableName();
			}
		}
		return null;
	}
}
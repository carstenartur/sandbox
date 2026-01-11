/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
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

import java.util.List;

import org.eclipse.jdt.core.dom.Statement;

/**
 * Strategy interface for handling different types of statements during loop body parsing.
 * 
 * <p>This interface implements the Strategy Pattern to cleanly separate the handling
 * logic for different statement types (IF, VariableDeclaration, etc.) from the
 * main parsing flow.</p>
 * 
 * <p><b>Benefits:</b></p>
 * <ul>
 * <li>Eliminates deep if-else-if chains</li>
 * <li>Each handler is focused on one statement type</li>
 * <li>Easy to add new statement types without modifying existing code</li>
 * <li>Improves testability - each handler can be tested in isolation</li>
 * </ul>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * List<StatementHandler> handlers = Arrays.asList(
 *     new VariableDeclarationHandler(ast),
 *     new IfStatementHandler(ifAnalyzer, matchFlags),
 *     new ReduceStatementHandler(reduceDetector),
 *     new ForEachStatementHandler()
 * );
 * 
 * for (StatementHandler handler : handlers) {
 *     if (handler.canHandle(stmt, context)) {
 *         return handler.handle(stmt, context, ops);
 *     }
 * }
 * }</pre>
 * 
 * @see LoopBodyParser
 * @see StatementParsingContext
 */
public interface StatementHandler {

	/**
	 * Checks if this handler can process the given statement.
	 * 
	 * @param stmt    the statement to check
	 * @param context the parsing context
	 * @return true if this handler can process the statement
	 */
	boolean canHandle(Statement stmt, StatementParsingContext context);

	/**
	 * Handles the statement and adds operations to the list.
	 * 
	 * @param stmt    the statement to process
	 * @param context the parsing context
	 * @param ops     the list of operations to add to
	 * @return the parse result
	 */
	LoopBodyParser.ParseResult handle(Statement stmt, StatementParsingContext context, 
			List<ProspectiveOperation> ops);
}

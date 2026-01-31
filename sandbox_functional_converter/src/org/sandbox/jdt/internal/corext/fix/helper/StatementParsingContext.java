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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Statement;

/**
 * Context object holding all state needed during statement parsing.
 * 
 * <p>This class encapsulates the parsing state to avoid passing many parameters
 * between methods and to provide a cleaner interface for {@link StatementHandler}
 * implementations.</p>
 * 
 * <p><b>Contents:</b></p>
 * <ul>
 * <li>Current variable name in the pipeline</li>
 * <li>Loop variable name</li>
 * <li>Position information (isLast, currentIndex)</li>
 * <li>Statement list for look-ahead operations</li>
 * <li>Helper objects (AST, analyzers, detectors)</li>
 * </ul>
 * 
 * @see StatementHandler
 * @see LoopBodyParser
 */
public class StatementParsingContext {

	private final String loopVariableName;
	private String currentVariableName;
	private final boolean isLastStatement;
	private final int currentIndex;
	private final List<Statement> allStatements;
	private final AST ast;
	private final IfStatementAnalyzer ifAnalyzer;
	private final ReducePatternDetector reduceDetector;
	private final CollectPatternDetector collectDetector;
	private final boolean isAnyMatchPattern;
	private final boolean isNoneMatchPattern;
	private final boolean isAllMatchPattern;

	/**
	 * Creates a new parsing context.
	 */
	public StatementParsingContext(
			String loopVariableName,
			String currentVariableName,
			boolean isLastStatement,
			int currentIndex,
			List<Statement> allStatements,
			AST ast,
			IfStatementAnalyzer ifAnalyzer,
			ReducePatternDetector reduceDetector,
			CollectPatternDetector collectDetector,
			boolean isAnyMatchPattern,
			boolean isNoneMatchPattern,
			boolean isAllMatchPattern) {
		this.loopVariableName = loopVariableName;
		this.currentVariableName = currentVariableName;
		this.isLastStatement = isLastStatement;
		this.currentIndex = currentIndex;
		this.allStatements = allStatements;
		this.ast = ast;
		this.ifAnalyzer = ifAnalyzer;
		this.reduceDetector = reduceDetector;
		this.collectDetector = collectDetector;
		this.isAnyMatchPattern = isAnyMatchPattern;
		this.isNoneMatchPattern = isNoneMatchPattern;
		this.isAllMatchPattern = isAllMatchPattern;
	}

	public String getLoopVariableName() {
		return loopVariableName;
	}

	public String getCurrentVariableName() {
		return currentVariableName;
	}

	public void setCurrentVariableName(String name) {
		this.currentVariableName = name;
	}

	public boolean isLastStatement() {
		return isLastStatement;
	}

	public int getCurrentIndex() {
		return currentIndex;
	}

	public List<Statement> getAllStatements() {
		return allStatements;
	}

	public AST getAst() {
		return ast;
	}

	public IfStatementAnalyzer getIfAnalyzer() {
		return ifAnalyzer;
	}

	public ReducePatternDetector getReduceDetector() {
		return reduceDetector;
	}

	public CollectPatternDetector getCollectDetector() {
		return collectDetector;
	}

	public boolean isAnyMatchPattern() {
		return isAnyMatchPattern;
	}

	public boolean isNoneMatchPattern() {
		return isNoneMatchPattern;
	}

	public boolean isAllMatchPattern() {
		return isAllMatchPattern;
	}

	/**
	 * Creates a context for a single statement (not in a block).
	 */
	public static StatementParsingContext forSingleStatement(
			String loopVariableName,
			AST ast,
			IfStatementAnalyzer ifAnalyzer,
			ReducePatternDetector reduceDetector,
			CollectPatternDetector collectDetector,
			boolean isAnyMatchPattern,
			boolean isNoneMatchPattern,
			boolean isAllMatchPattern) {
		return new StatementParsingContext(
				loopVariableName,
				loopVariableName,
				true, // single statement is always "last"
				0,
				null,
				ast,
				ifAnalyzer,
				reduceDetector,
				collectDetector,
				isAnyMatchPattern,
				isNoneMatchPattern,
				isAllMatchPattern);
	}
}

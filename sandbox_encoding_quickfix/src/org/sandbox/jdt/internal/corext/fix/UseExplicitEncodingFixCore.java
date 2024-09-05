/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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
package org.sandbox.jdt.internal.corext.fix;

import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.TargetSourceRangeComputer;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.helper.AbstractExplicitEncoding;
import org.sandbox.jdt.internal.corext.fix.helper.AbstractExplicitEncoding.ChangeBehavior;
import org.sandbox.jdt.internal.corext.fix.helper.ByteArrayOutputStreamExplicitEncoding;
import org.sandbox.jdt.internal.corext.fix.helper.ChannelsNewReaderExplicitEncoding;
import org.sandbox.jdt.internal.corext.fix.helper.ChannelsNewWriterExplicitEncoding;
import org.sandbox.jdt.internal.corext.fix.helper.CharsetForNameExplicitEncoding;
import org.sandbox.jdt.internal.corext.fix.helper.FileReaderExplicitEncoding;
import org.sandbox.jdt.internal.corext.fix.helper.FileWriterExplicitEncoding;
import org.sandbox.jdt.internal.corext.fix.helper.FormatterExplicitEncoding;
import org.sandbox.jdt.internal.corext.fix.helper.InputStreamReaderExplicitEncoding;
import org.sandbox.jdt.internal.corext.fix.helper.OutputStreamWriterExplicitEncoding;
import org.sandbox.jdt.internal.corext.fix.helper.PrintStreamExplicitEncoding;
import org.sandbox.jdt.internal.corext.fix.helper.PrintWriterExplicitEncoding;
import org.sandbox.jdt.internal.corext.fix.helper.ScannerExplicitEncoding;
import org.sandbox.jdt.internal.corext.fix.helper.StringGetBytesExplicitEncoding;
import org.sandbox.jdt.internal.corext.fix.helper.URLDecoderDecodeExplicitEncoding;
import org.sandbox.jdt.internal.corext.fix.helper.URLEncoderEncodeExplicitEncoding;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;

public enum UseExplicitEncodingFixCore {

	CHARSET(new CharsetForNameExplicitEncoding()),
	CHANNELSNEWREADER(new ChannelsNewReaderExplicitEncoding()),
	CHANNELSNEWWRITER(new ChannelsNewWriterExplicitEncoding()),
	STRING_GETBYTES(new StringGetBytesExplicitEncoding()),
	INPUTSTREAMREADER(new InputStreamReaderExplicitEncoding()),
	OUTPUTSTREAMWRITER(new OutputStreamWriterExplicitEncoding()),
	FILEREADER(new FileReaderExplicitEncoding()),
	FILEWRITER(new FileWriterExplicitEncoding()),
	PRINTWRITER(new PrintWriterExplicitEncoding()),
	PRINTSTREAM(new PrintStreamExplicitEncoding()),
	BYTEARRAYOUTPUTSTREAM(new ByteArrayOutputStreamExplicitEncoding()),
	FORMATTER(new FormatterExplicitEncoding()),
	URLDECODER(new URLDecoderDecodeExplicitEncoding()),
	URLENCODER(new URLEncoderEncodeExplicitEncoding()),
	SCANNER(new ScannerExplicitEncoding());

	AbstractExplicitEncoding<ASTNode> explicitencoding;

	@SuppressWarnings("unchecked")
	UseExplicitEncodingFixCore(AbstractExplicitEncoding<? extends ASTNode> explicitencoding) {
		this.explicitencoding=(AbstractExplicitEncoding<ASTNode>) explicitencoding;
	}

	public String getPreview(boolean i, ChangeBehavior cb) {
		return explicitencoding.getPreview(i,cb);
	}
	/**
	 * Compute set of CompilationUnitRewriteOperation to refactor supported situations using default encoding to make use of explicit calls
	 *
	 * @param compilationUnit unit to search in
	 * @param operations set of all CompilationUnitRewriteOperations created already
	 * @param nodesprocessed list to remember nodes already processed
	 * @param cb
	 */
	public void findOperations(final CompilationUnit compilationUnit,final Set<CompilationUnitRewriteOperation> operations,final Set<ASTNode> nodesprocessed, ChangeBehavior cb) {
		explicitencoding.find(this, compilationUnit, operations, nodesprocessed, cb);
	}

	public CompilationUnitRewriteOperation rewrite(final ASTNode visited, ChangeBehavior cb, ReferenceHolder data) {
		return new CompilationUnitRewriteOperation() {
			@Override
			public void rewriteAST(final CompilationUnitRewrite cuRewrite, final LinkedProposalModelCore linkedModel) throws CoreException {
				TextEditGroup group= createTextEditGroup(Messages.format(MultiFixMessages.ExplicitEncodingCleanUp_description,new Object[] {UseExplicitEncodingFixCore.this.toString(), cb.toString()}), cuRewrite);
				cuRewrite.getASTRewrite().setTargetSourceRangeComputer(computer);
				explicitencoding.rewrite(UseExplicitEncodingFixCore.this, visited, cuRewrite, group, cb, data);
			}
		};
	}

	final static TargetSourceRangeComputer computer= new TargetSourceRangeComputer() {
		@Override
		public SourceRange computeSourceRange(final ASTNode nodeWithComment) {
			if (Boolean.TRUE.equals(nodeWithComment.getProperty(ASTNodes.UNTOUCH_COMMENT))) {
				return new SourceRange(nodeWithComment.getStartPosition(), nodeWithComment.getLength());
			}
			return super.computeSourceRange(nodeWithComment);
		}
	};
}

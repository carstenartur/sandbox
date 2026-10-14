/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.Map;
import java.util.ArrayList;
import java.util.Objects;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.TargetSourceRangeComputer;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;

/** Applies the verified output projection without deleting adjacent source comments. */
final class JavaRegionSourceRewriter {
   private JavaRegionSourceRewriter() {}

   /** Captures are source obligations, separate from the freely scheduled scalar computation. */
   static String arrayCaptures(CompilationUnit ast, String source, JavaComputationRegion region) {
      StringBuilder prefix = new StringBuilder();
      for (var read : region.arrayReads()) {
         if (!(exactNode(ast, read.start(), read.length()) instanceof ArrayAccess))
            throw new IllegalArgumentException("ARRAY_READ_SOURCE_CHANGED"); //$NON-NLS-1$
         prefix.append("final ").append(MathematicalAnalysis.javaType(read.kind())).append(' ') //$NON-NLS-1$
               .append(read.javaName()).append(" = ").append(source, read.start(), read.start() + read.length()).append(";\n"); //$NON-NLS-1$ //$NON-NLS-2$
      }
      return prefix.toString();
   }

   static String rewrite(CompilationUnit ast, String source, JavaComputationRegion region,
         Map<String, String> values, Map<String, String> compilerOptions) {
      try {
         Document document = new Document(source);
         TextEdit edit = edits(ast, source, region, values, compilerOptions);
         edit.apply(document);
         return document.get(region.start(), region.length() + document.getLength() - source.length());
      } catch (BadLocationException invalid) {
         throw new IllegalArgumentException("STALE_SOURCE_RANGE", invalid);
      }
   }

   static TextEdit edits(CompilationUnit ast, String source, JavaComputationRegion region,
         Map<String, String> values, Map<String, String> compilerOptions) {
      ASTRewrite rewrite = ASTRewrite.create(ast.getAST());
      var preservedExpressions = new ArrayList<TextEdit>();
      rewrite.setTargetSourceRangeComputer(new TargetSourceRangeComputer() {
         @Override public SourceRange computeSourceRange(ASTNode node) {
            return new SourceRange(node.getStartPosition(), node.getLength());
         }
      });
      for (var internal : region.internalBindings()) {
         rewrite.remove(exactNode(ast, internal.statementStart(), internal.statementLength()), null);
      }
      for (var output : region.outputs()) {
         String expression = Objects.requireNonNull(values.get(output.id()), "MISSING_OUTPUT");
         if (!output.declaration() && !output.returnValue()) {
            expression = output.javaName() + " = " + expression;
         }
         ASTParser parser = ASTParser.newParser(ast.getAST().apiLevel());
         parser.setKind(ASTParser.K_EXPRESSION);
         parser.setSource(expression.toCharArray());
         ASTNode parsed = parser.createAST(null);
         ASTNode original = exactNode(ast, output.initializerStart(), output.initializerLength());
         if (expression.indexOf('\n') >= 0) {
            // The expression has already passed independent emission proof. Use its exact
            // JDT-validated boundary, without adding formatter indentation to preserved source.
            preservedExpressions.add(new ReplaceEdit(original.getStartPosition(), original.getLength(), expression));
         } else {
            rewrite.replace(original, ASTNode.copySubtree(ast.getAST(), parsed), null);
         }
      }
      TextEdit edit = rewrite.rewriteAST(new Document(source), compilerOptions);
      if (!preservedExpressions.isEmpty()) {
         MultiTextEdit combined = new MultiTextEdit();
         if (edit.hasChildren() || edit.getLength() > 0) combined.addChild(edit);
         for (var expression : preservedExpressions) combined.addChild(expression);
         edit = combined;
      }
      if (edit.getOffset() < region.start() || edit.getExclusiveEnd() > region.start() + region.length()) {
         throw new IllegalArgumentException("REWRITE_OUTSIDE_REGION");
      }
      return edit;
   }

   private static ASTNode exactNode(CompilationUnit ast, int start, int length) {
      ASTNode node = NodeFinder.perform(ast, start, length);
      if (node == null || node.getStartPosition() != start || node.getLength() != length) {
         throw new IllegalArgumentException("STALE_SOURCE_RANGE");
      }
      return node;
   }
}

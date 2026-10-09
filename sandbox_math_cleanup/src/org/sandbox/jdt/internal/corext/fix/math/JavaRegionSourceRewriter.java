/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.Map;
import java.util.Objects;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.TargetSourceRangeComputer;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

/** Applies the verified output projection without deleting adjacent source comments. */
final class JavaRegionSourceRewriter {
   private JavaRegionSourceRewriter() {}

   static String rewrite(CompilationUnit ast, String source, JavaComputationRegion region,
         Map<String, String> values, Map<String, String> compilerOptions) {
      ASTRewrite rewrite = ASTRewrite.create(ast.getAST());
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
         rewrite.replace(exactNode(ast, output.initializerStart(), output.initializerLength()),
               ASTNode.copySubtree(ast.getAST(), parser.createAST(null)), null);
      }
      try {
         Document document = new Document(source);
         TextEdit edit = rewrite.rewriteAST(document, compilerOptions);
         if (edit.getOffset() < region.start() || edit.getExclusiveEnd() > region.start() + region.length()) {
            throw new IllegalArgumentException("REWRITE_OUTSIDE_REGION");
         }
         edit.apply(document);
         return document.get(region.start(), region.length() + document.getLength() - source.length());
      } catch (BadLocationException invalid) {
         throw new IllegalArgumentException("STALE_SOURCE_RANGE", invalid);
      }
   }

   private static ASTNode exactNode(CompilationUnit ast, int start, int length) {
      ASTNode node = NodeFinder.perform(ast, start, length);
      if (node == null || node.getStartPosition() != start || node.getLength() != length) {
         throw new IllegalArgumentException("STALE_SOURCE_RANGE");
      }
      return node;
   }
}

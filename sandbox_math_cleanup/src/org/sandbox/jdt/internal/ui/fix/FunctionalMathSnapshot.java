/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.fix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.LongConsumer;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocumentListener;

/** Tracks actual native edit events, including imports and move/copy edits. */
final class FunctionalMathSnapshot implements IDocumentListener {
 static final class Span implements ISourceRange {
  int offset; final int length; final String text;
  Span(int offset,int length,String source) { this.offset=offset;this.length=length;this.text=source.substring(offset,offset+length); }
  @Override public int getOffset() { return offset; }
  @Override public int getLength() { return length; }
  boolean covers(ASTNode node) { return offset<=node.getStartPosition() && (long)node.getStartPosition()+node.getLength()<=offset+length; }
 }
 private record Name(Span occurrence,IBinding binding,Span declaration) { }
 private record Position(int offset,int length) { }
 private final List<Span> protectedText=new ArrayList<>(),tracked=new ArrayList<>();
 private final List<Name> names=new ArrayList<>();
 private final LongConsumer charge;
 private RuntimeException rejected;
 FunctionalMathSnapshot(CompilationUnit ast,String source,List<? extends ISourceRange> regions,LongConsumer charge) {
  this.charge=charge;
  for(var range:regions) protectedText.add(new Span(range.getOffset(),range.getLength(),source));
  tracked.addAll(protectedText);
  ast.accept(new ASTVisitor() {
   @Override public void preVisit(ASTNode node) { charge.accept(1); }
   @Override public boolean visit(SimpleName name) {
    charge.accept(protectedText.size());
    if(protectedText.stream().noneMatch(span->span.covers(name))) return true;
    IBinding binding=name.resolveBinding();
    if(binding==null || binding.isRecovered()) throw invalid();
    var occurrence=new Span(name.getStartPosition(),name.getLength(),source);tracked.add(occurrence);
    Span declaration=null;
    if(binding instanceof IVariableBinding variable && !variable.isField() && !variable.isEnumConstant()) {
     if(!(ast.findDeclaringNode(variable) instanceof VariableDeclaration declared)) throw invalid();
     var anchor=declared.getName();declaration=new Span(anchor.getStartPosition(),anchor.getLength(),source);tracked.add(declaration);
    }
    names.add(new Name(occurrence,binding,declaration));return true;
   }
  });
 }
 List<? extends ISourceRange> exclusions() { return List.copyOf(tracked); }
 @Override public void documentAboutToBeChanged(DocumentEvent event) {
  // JFace SafeRunner swallows listener exceptions. Retain the veto explicitly;
  // the candidate document is disposable and cannot escape before verify().
  if(rejected!=null) return;
  try {
   charge.accept((long)tracked.size()+event.getLength()+(event.getText()==null?0:event.getText().length()));
   for(var span:tracked) {
    int start=event.getOffset(),end=start+event.getLength();
    if(event.getLength()==0?start>span.offset && start<span.offset+span.length
      :start<(long)span.offset+span.length && span.offset<end) throw invalid();
   }
  } catch(RuntimeException failure) {rejected=failure;}
 }
 @Override public void documentChanged(DocumentEvent event) {
  if(rejected!=null) return;
  int delta=(event.getText()==null?0:event.getText().length())-event.getLength();
  for(var span:tracked) if(event.getOffset()+event.getLength()<=span.offset) span.offset+=delta;
 }
 void verify(CompilationUnit ast,Document source) {
  if(rejected!=null) throw rejected;
  charge.accept(source.getLength());String contents=source.get();
  for(var span:tracked) {
   charge.accept(Math.max(1,span.length));
   if(!contents.regionMatches(span.offset,span.text,0,span.length)) throw invalid();
  }
  // Resolve positions in one cancellable traversal, not a full NodeFinder walk
  // for every occurrence and declaration receipt.
  var indexed=new HashMap<Position,SimpleName>();
  ast.accept(new ASTVisitor() {
   @Override public void preVisit(ASTNode node) {charge.accept(1);}
   @Override public boolean visit(SimpleName name) {
    indexed.put(new Position(name.getStartPosition(),name.getLength()),name);return true;
   }
  });
  for(var receipt:names) {
   charge.accept(1);
   var span=receipt.occurrence();
   var actual=indexed.get(new Position(span.offset,span.length));if(actual==null) throw invalid();
   IBinding binding=actual.resolveBinding(),expected=receipt.binding();
   if(binding==null || binding.isRecovered()) throw invalid();
   if(receipt.declaration()!=null) {
    var anchor=receipt.declaration();
    var declaration=indexed.get(new Position(anchor.offset,anchor.length));
    if(declaration==null || !declaration.isDeclaration()
      || !(declaration.resolveBinding() instanceof IVariableBinding declared) || !(expected instanceof IVariableBinding original)
      || !original.getType().isEqualTo(declared.getType()) || original.isParameter()!=declared.isParameter()
      || !Objects.equals(original.getConstantValue(),declared.getConstantValue())) throw invalid();
    expected=declared;
   }
   if(!expected.isEqualTo(binding)) throw invalid();
   if(expected instanceof IMethodBinding method && binding instanceof IMethodBinding now
     && !method.getReturnType().isEqualTo(now.getReturnType())) throw invalid();
  }
 }
 private static IllegalArgumentException invalid() { return new IllegalArgumentException("PROTECTED_MATHEMATICS_CHANGED: text or bindings differ after loop conversion"); }
}

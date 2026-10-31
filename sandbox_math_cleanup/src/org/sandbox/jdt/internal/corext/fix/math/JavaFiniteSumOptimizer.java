/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;
import de.regelsuche.ast.*;
import de.regelsuche.sdk.optimization.CancellationToken;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.SafetyProfile;
import de.regelsuche.sdk.optimization.iteration.FiniteIterationOptimizer;
import de.regelsuche.sdk.optimization.iteration.FiniteIterationVerifier;
import de.regelsuche.symbol.SymbolId;

/** SDK-owned candidate/proof with source admission and actual emitted Java replay. */
final class JavaFiniteSumOptimizer {
    private JavaFiniteSumOptimizer() { }
    static long optimize(CompilationUnit ast,String source,MathCleanUpOptions options,IProgressMonitor monitor,
            int selectionOffset,int selectionLength,Map<String,String> compilerOptions,MathematicalAnalysis.SourceEnvironment environment,
            List<MathematicalAnalysis.Replacement> replacements,List<MathematicalAnalysis.VerifiedRegion> evidence,
            List<MathematicalAnalysis.Diagnostic> diagnostics,long remaining) {
        if(options.safety()!=SafetyProfile.PRESERVE_JAVA || options.mathematicalOptIn() || remaining<20000) return remaining;
        long started=System.nanoTime(),timeout=options.timeoutSeconds()*1000000000L;
        CancellationToken token=()->MathematicalAnalysis.isCancelled(monitor) || Thread.currentThread().isInterrupted() || System.nanoTime()-started>=timeout;
        var work=new JavaLoopInvariantAnalysis.Work(remaining,token);
        var loops=new ArrayList<ASTNode>();
        try {
            ast.accept(new ASTVisitor() {
                @Override public void preVisit(ASTNode node) {work.tick();}
                @Override public boolean visit(ForStatement loop) {loops.add(loop);return true;}
                @Override public boolean visit(WhileStatement loop) {loops.add(loop);return true;}
                @Override public boolean visit(DoStatement loop) {loops.add(loop);return true;}
                @Override public boolean visit(MethodInvocation invocation) {
                    if(invocation.getName().getIdentifier().equals("sum")) loops.add(invocation);return true; //$NON-NLS-1$
                }
            });
            for(var loop:loops) {
                int start=loop.getStartPosition(),length=loop.getLength();
                work.tick();
                if(selectionOffset>=0 && (selectionOffset>start || (long)selectionOffset+selectionLength<(long)start+length)) continue;
                if(replacements.stream().anyMatch(edit->overlaps(start,length,edit.offset(),edit.length()))) continue;
                // Source admission is capped separately; reserve SDK work proportionally after counting outputs.
                long share=Math.min(40000,(remaining-work.used-16L*source.length())/6);
                if(share<10000) break;
                var local=new JavaLoopInvariantAnalysis.Work(share,token);
                boolean reserved=false;
                try {
                    local.charge(source.length());
                    var context=JavaFiniteSumAnalysis.inspect(ast,loop,local);
                    if(!options.kinds().contains(context.model().arithmetic().bits()==32?NumericKind.INT:NumericKind.LONG)) continue;
                    if(context.model().range().count().signum()==0) continue; // Never introduce a read on an empty execution.
                    int outputs=context.reductions().size();
                    // Each output receives one search, one numerical check, and its share of immediate replay.
                    // Three source shares cover local work, replay's adapter work, and fixed validation overhead.
                    long sourceReservation=3*share+16L*source.length();
                    long proofShare=Math.min(share,(remaining-work.used-sourceReservation)/(3L*outputs));
                    if(proofShare<10000) continue;
                    long replayAllowance=share+outputs*proofShare;
                    work.charge(sourceReservation+3L*outputs*proofShare);reserved=true;
                    var proofs=new ArrayList<FiniteSumEvidence.ReductionProof>();
                    for(var reduction:context.reductions()) {
                        if(!options.kinds().contains(reduction.model().arithmetic().bits()==32?NumericKind.INT:NumericKind.LONG)) throw invalid();
                        var search=new FiniteIterationOptimizer().search(reduction.model(),options.optimizationBudget(proofShare),token);
                        var candidate=search.candidate().orElseThrow(JavaFiniteSumOptimizer::invalid);
                        var proof=new FiniteIterationVerifier().verify(reduction.model(),candidate,options.optimizationBudget(proofShare),token);
                        if(!proof.verified()) throw invalid();
                        proofs.add(new FiniteSumEvidence.ReductionProof(reduction.model(),candidate.certificate(),candidate.result(),reduction.bindings(),proof.cost()));
                    }
                    var first=proofs.getFirst();
                    var provisional=new FiniteSumEvidence(hash(source),first.source(),first.certificate(),first.emitted(),first.bindings(),first.checkedCost(),proofs.subList(1,proofs.size()));
                    var cost=FiniteSumEvidence.cost(provisional.totalCost(),options.goal());
                    if(!cost.estimatedRuntimeImprovement()) continue;
                    String replacement=replacement(ast,source,context,proofs,local);
                    String original=source.substring(start,start+length),sha=hash(source);
                    var plan=new JavaSourceRewritePlan("Source.java",sha,source.length(),start,length,List.of(),List.of(),Map.of(), //$NON-NLS-1$
                            List.of(new JavaSourceRewritePlan.Change(start,length,hash(original),replacement)));
                    String generated=source.substring(0,start)+replacement+source.substring(start+length);
                    local.charge(generated.length());
                    var after=MathematicalAnalysis.checkGeneratedSource(ast,generated,compilerOptions,options.targetJava(),monitor,environment);
                    var receipt=new MathematicalAnalysis.VerifiedRegion(start,length,null,cost,
                            original,replacement,null,plan,null,provisional);
                    var edit=new MathematicalAnalysis.Replacement(start,length,replacement,provisional.description());
                    verify(ast,after,source,generated,receipt,List.of(edit),options,replayAllowance,token);
                    if(token.isCancelled()) break;
                    replacements.add(edit); evidence.add(receipt);
                } catch(IllegalArgumentException | ArithmeticException unavailable) {
                    diagnostics.add(new MathematicalAnalysis.Diagnostic("FINITE_SUM_UNAVAILABLE",unavailable.getMessage(),start,length,MathematicalAnalysis.Stage.SOURCE)); //$NON-NLS-1$
                } finally {
                    if(!reserved) work.charge(local.used);
                }
            }
        } catch(JavaLoopInvariantAnalysis.Limit exhausted) {return 0;}
        return remaining-work.used;
    }

    static void verify(CompilationUnit before,CompilationUnit after,String source,String generated,
            MathematicalAnalysis.VerifiedRegion receipt,List<MathematicalAnalysis.Replacement> edits,
            MathCleanUpOptions options,long allowance,CancellationToken parent) {
        long started=System.nanoTime(),timeout=options.timeoutSeconds()*1000000000L;
        CancellationToken token=()->parent.isCancelled() || System.nanoTime()-started>=timeout;
        var work=new JavaLoopInvariantAnalysis.Work(allowance,token);
        work.charge(4L*(source.length()+generated.length()));
        var finite=receipt.finiteSum();
        if(finite==null || receipt.proof()!=null || receipt.scheduling()!=null || receipt.sourcePlan()==null
                || !hash(source).equals(finite.sourceSha256())) throw invalid();
        var expected=new StringBuilder(source);
        int previous=source.length();
        for(var edit:edits.stream().sorted(java.util.Comparator.comparingInt(MathematicalAnalysis.Replacement::offset).reversed()).toList()) {
            work.tick();
            if(edit.offset()<0 || edit.length()<0 || (long)edit.offset()+edit.length()>previous) throw invalid();
            expected.replace(edit.offset(),edit.offset()+edit.length(),edit.replacement()); previous=edit.offset();
        }
        if(!expected.toString().equals(generated)) throw invalid();
        receipt.sourcePlan().resolve(before,source);
        ASTNode original=NodeFinder.perform(before,receipt.offset(),receipt.length());
        var context=JavaFiniteSumAnalysis.inspect(before,original,work);
        if(!context.model().equals(finite.source()) || !context.bindings().equals(finite.bindings())) throw invalid();
        int mapped=position(receipt.offset(),edits);
        if(mapped<0 || (long)mapped+receipt.replacement().length()>generated.length()
                || !generated.substring(mapped,mapped+receipt.replacement().length()).equals(receipt.replacement())) throw invalid();
        ASTNode actual=NodeFinder.perform(after,mapped,receipt.replacement().length());
        var proofs=new ArrayList<FiniteSumEvidence.ReductionProof>();
        proofs.add(new FiniteSumEvidence.ReductionProof(finite.source(),finite.certificate(),finite.emitted(),finite.bindings(),finite.checkedCost()));
        proofs.addAll(finite.additional());
        if(context.reductions().size()!=proofs.size()) throw invalid();
        var expressions=new ArrayList<Expression>();
        if(context.expression()) {
            if(!(actual instanceof Expression expression)) throw invalid();expressions.add(expression);
        } else {
            List<?> statements=actual instanceof Block block?block.statements():List.of(actual);
            if(statements.size()!=proofs.size()+(context.counter()==null?0:1)) throw invalid();
            for(int i=0;i<proofs.size();i++) {
                if(!(statements.get(i) instanceof ExpressionStatement statement) || !(statement.getExpression() instanceof Assignment assignment)
                        || assignment.getOperator()!=Assignment.Operator.ASSIGN) throw invalid();
                var target=JavaFiniteSumAnalysis.variable(assignment.getLeftHandSide());
                if(target==null || !mappedBinding(before,after,context.reductions().get(i).accumulator(),edits).isEqualTo(target)) throw invalid();
                expressions.add(assignment.getRightHandSide());
            }
            if(context.counter()!=null) {
                if(!(statements.getLast() instanceof ExpressionStatement statement) || !(statement.getExpression() instanceof Assignment assignment)
                        || assignment.getOperator()!=Assignment.Operator.ASSIGN || !mappedBinding(before,after,context.counter(),edits).isEqualTo(JavaFiniteSumAnalysis.variable(assignment.getLeftHandSide()))
                        || !context.finalCounter().equals(constantValue(assignment.getRightHandSide()))) throw invalid();
            }
        }
        for(int reductionIndex=0;reductionIndex<proofs.size();reductionIndex++) {
        var reduction=context.reductions().get(reductionIndex);var numerical=proofs.get(reductionIndex);
        if(!reduction.model().equals(numerical.source()) || !reduction.bindings().equals(numerical.bindings())) throw invalid();
        var symbols=new LinkedHashMap<String,SymbolId>();
        for(var binding:numerical.bindings()) {
            ASTNode declaration=NodeFinder.perform(before,binding.declarationOffset(),binding.name().length());
            if(!(declaration instanceof SimpleName name) || !(name.resolveBinding() instanceof IVariableBinding originalBinding)) throw invalid();
            symbols.put(JavaFiniteSumAnalysis.key(mappedBinding(before,after,originalBinding,edits)),binding.symbol());
        }
        String width=reduction.model().arithmetic().bits()==32?"int":"long"; //$NON-NLS-1$ //$NON-NLS-2$
        Expr decoded=JavaFiniteSumAnalysis.decode(expressions.get(reductionIndex),width,binding->{
            SymbolId symbol=symbols.get(JavaFiniteSumAnalysis.key(binding));if(symbol==null) throw invalid();return symbol;
        },work,0);
        long permitted=allowance-work.used;
        if(permitted<1) throw new JavaLoopInvariantAnalysis.Limit();
        var verified=new FiniteIterationVerifier().verify(reduction.model(),decoded,numerical.certificate(),options.optimizationBudget(permitted),token);
        work.charge(verified.work());
        if(!verified.verified() || !numerical.checkedCost().equals(verified.cost()) || !decoded.equals(numerical.emitted())) throw invalid();
        }
        if(!FiniteSumEvidence.cost(finite.totalCost(),options.goal()).equals(receipt.cost()) || !receipt.cost().estimatedRuntimeImprovement()) throw invalid();
        // Also binds comments, block shape, and final-counter emission to the admitted source.
        if(!replacement(before,source,context,proofs,work).equals(receipt.replacement())) throw invalid();
        // Unchanged occurrences must still resolve to the same declaration, even when local binding keys renumber.
        before.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) {work.tick();}
            @Override public boolean visit(SimpleName name) {
                if(covered(name.getStartPosition(),edits) || !(name.resolveBinding() instanceof IVariableBinding binding)) return false;
                ASTNode declaration=before.findDeclaringNode(binding);
                // Other accepted passes own their replaced declarations and prove their own boundaries.
                if(declaration!=null && covered(declaration.getStartPosition(),edits)) return false;
                ASTNode node=NodeFinder.perform(after,position(name.getStartPosition(),edits),name.getLength());
                if(!(node instanceof SimpleName moved) || !(moved.resolveBinding() instanceof IVariableBinding actualBinding)
                        || !mappedBinding(before,after,binding,edits).isEqualTo(actualBinding)) throw invalid();
                return false;
            }
        });
    }
    private static java.math.BigInteger constantValue(Expression expression) {
        Object value=expression.resolveConstantExpressionValue();
        if(value instanceof Integer number) return java.math.BigInteger.valueOf(number.longValue());
        if(value instanceof Long number) return java.math.BigInteger.valueOf(number);throw invalid();
    }
    private static String replacement(CompilationUnit unit,String source,JavaFiniteSumAnalysis.Context context,
            List<FiniteSumEvidence.ReductionProof> proofs,JavaLoopInvariantAnalysis.Work work) {
        var text=new StringBuilder();
        boolean comments=hasComments(unit,context.loop());
        boolean block=!context.expression() && (proofs.size()>1 || context.counter()!=null || comments);
        if(block) text.append("{\n"); //$NON-NLS-1$
        else if(context.expression() && comments) text.append("(\n"); //$NON-NLS-1$
        for(Object item:unit.getCommentList()) {
            work.tick();ASTNode comment=(ASTNode)item;
            if(comment.getStartPosition()>=context.loop().getStartPosition() && comment.getStartPosition()+comment.getLength()<=context.loop().getStartPosition()+context.loop().getLength())
                text.append(source,comment.getStartPosition(),comment.getStartPosition()+comment.getLength()).append('\n');
        }
        for(int i=0;i<proofs.size();i++) {
            var reduction=context.reductions().get(i);var proof=proofs.get(i);
            if(!context.expression()) text.append(reduction.accumulator().getName()).append(" = "); //$NON-NLS-1$
            text.append(emit(proof.emitted(),proof.bindings(),proof.source().arithmetic().bits()==32?"int":"long",work,0)); //$NON-NLS-1$ //$NON-NLS-2$
            if(!context.expression()) text.append(';');
        }
        if(context.counter()!=null) text.append(context.counter().getName()).append(" = ").append(context.finalCounter()).append(context.counter().getType().getName().equals("long")?"L;":";"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        if(block) text.append("\n}"); //$NON-NLS-1$
        else if(context.expression() && comments) text.append("\n)"); //$NON-NLS-1$
        return text.toString();
    }
    private static boolean hasComments(CompilationUnit unit,ASTNode node) {
        for(Object item:unit.getCommentList()) {ASTNode comment=(ASTNode)item;
            if(comment.getStartPosition()>=node.getStartPosition() && comment.getStartPosition()<node.getStartPosition()+node.getLength()) return true;}
        return false;
    }
    private static IVariableBinding mappedBinding(CompilationUnit before,CompilationUnit after,IVariableBinding binding,
            List<MathematicalAnalysis.Replacement> edits) {
        ASTNode node=before.findDeclaringNode(binding);
        if(node==null && binding.isField()) return binding;
        if(!(node instanceof VariableDeclaration declaration) || covered(declaration.getName().getStartPosition(),edits)) throw invalid();
        ASTNode mapped=NodeFinder.perform(after,position(declaration.getName().getStartPosition(),edits),declaration.getName().getLength());
        if(!(mapped instanceof SimpleName name) || !name.isDeclaration() || !(name.getParent() instanceof VariableDeclaration actual)
                || !name.getIdentifier().equals(declaration.getName().getIdentifier()) || actual.getNodeType()!=declaration.getNodeType()
                || actual.getParent().getNodeType()!=declaration.getParent().getNodeType()) throw invalid();
        var result=actual.resolveBinding();
        if(result==null || result.isRecovered() || !binding.getType().isEqualTo(result.getType()) || binding.isField()!=result.isField()
                || binding.isParameter()!=result.isParameter()) throw invalid();
        return result;
    }
    private static String emit(Expr expression,List<FiniteSumEvidence.Binding> bindings,String width,JavaLoopInvariantAnalysis.Work work,int depth) {
        work.tick();if(depth>64) throw invalid();
        if(expression instanceof NumberExpr number) {
            if(!number.value().denominator().equals(java.math.BigInteger.ONE)) throw invalid();
            return number.value().numerator().toString()+(width.equals("long")?"L":""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        if(expression instanceof VariableExpr variable) {
            var binding=bindings.stream().filter(b->variable.symbol().orElseThrow().equals(b.symbol()))
                    .findFirst().orElseThrow(JavaFiniteSumOptimizer::invalid);
            return width.equals("long") && binding.primitiveType().equals("int")?"((long) "+binding.name()+")":binding.name(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        }
        if(expression instanceof BinaryExpr binary && (binary.operator()==BinaryOperator.ADD || binary.operator()==BinaryOperator.SUB || binary.operator()==BinaryOperator.MUL))
            return "("+emit(binary.left(),bindings,width,work,depth+1)+" "+binary.operator().symbol()+" "+emit(binary.right(),bindings,width,work,depth+1)+")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        throw invalid();
    }
    static String hash(String source) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));} //$NON-NLS-1$
        catch(NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
    }
    private static int position(int original,List<MathematicalAnalysis.Replacement> edits) {
        int mapped=original;for(var edit:edits) if(edit.offset()+edit.length()<=original) mapped+=edit.replacement().length()-edit.length();return mapped;
    }
    private static boolean covered(int position,List<MathematicalAnalysis.Replacement> edits) {
        return edits.stream().anyMatch(edit->position>=edit.offset() && position<edit.offset()+edit.length());
    }
    private static boolean overlaps(int a,int n,int b,int m) {return a<(long)b+m && b<(long)a+n;}
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("FINITE_SUM_REPLAY_DIFFERS");} //$NON-NLS-1$
}

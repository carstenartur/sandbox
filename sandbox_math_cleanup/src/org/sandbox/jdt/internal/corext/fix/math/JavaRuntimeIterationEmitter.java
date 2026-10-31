/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.*;
import org.eclipse.jdt.core.dom.*;
import de.regelsuche.ast.*;
import de.regelsuche.sdk.optimization.iteration.PolynomialSumPlan;
import de.regelsuche.symbol.SymbolId;

/** Deterministic standalone lowering of independently checked SDK transport. No synthesis or loop unrolling. */
final class JavaRuntimeIterationEmitter {
    private static final String BIG="java.math.BigInteger";
    private final JavaRuntimeIterationAnalysis.Context context;
    private final RuntimeIterationEvidence evidence;
    private final JavaLoopInvariantAnalysis.Work work;
    private final String prefix;
    private final Map<SymbolId,String> names=new HashMap<>();
    private JavaRuntimeIterationEmitter(JavaRuntimeIterationAnalysis.Context context,RuntimeIterationEvidence evidence,String source,JavaLoopInvariantAnalysis.Work work) {
        this.context=context;this.evidence=evidence;this.work=work;
        prefix=freshPrefix(source,context.node().getStartPosition(),work);
        names.put(context.startSymbol(),prefix+"start");names.put(context.countSymbol(),prefix+"count");
        if(context.partition()!=null)for(int part=0;part<context.partition().starts().size();part++)
            names.put(context.partition().starts().get(part),prefix+"partStart"+part);
        for(var binding:context.bindings()) names.put(binding.symbol(),binding.primitiveType().equals(BIG)?binding.name():join(BIG,".valueOf((long)(",binding.name(),"))"));
    }
    static String emit(CompilationUnit unit,String source,JavaRuntimeIterationAnalysis.Context context,RuntimeIterationEvidence evidence,JavaLoopInvariantAnalysis.Work work) {
        return new JavaRuntimeIterationEmitter(context,evidence,source,work).emit(unit,source);
    }
    private String emit(CompilationUnit unit,String source) {
        var h=context.header();String p=prefix;
        var out=new Output(work);
        if(context.stream()) out.append("(new java.util.function.").append(context.width().equals("int")?"IntSupplier":"LongSupplier").append("(){public ").append(context.width()).append(" getAs").append(context.width().equals("int")?"Int":"Long").append("(){\n");
        else out.append("{\n");
        out.append(BIG).append(' ').append(p).append("start = ").append(BIG).append(".valueOf((long)(").append(text(source,h.start())).append("));\n");
        out.append(BIG).append(' ').append(p).append("bound = ").append(BIG).append(".valueOf((long)(").append(text(source,h.bound())).append("));\n");
        if(context.partition()!=null)JavaPartitionedSumEmitter.capture(out,context,source,p);
        String distance=h.stride().signum()>0?p+"bound.subtract("+p+"start)":p+"start.subtract("+p+"bound)";
        String count=distance+(h.inclusive()?"":".subtract("+BIG+".ONE)")+".divide("+number(h.stride().abs())+").add("+BIG+".ONE)";
        // BigInteger.divide truncates toward zero; clamp a negative distance before division.
        String adjusted=distance+(h.inclusive()?"":".subtract("+BIG+".ONE)");
        out.append(BIG).append(' ').append(p).append("count = ").append(adjusted).append(".signum()<0 ? ").append(BIG).append(".ZERO : ").append(count).append(";\n");
        if(h.once()) out.append(p).append("count = ").append(p).append("count.max(").append(BIG).append(".ONE);\n");
        out.append(BIG).append(' ').append(p).append("after = ").append(p).append("start.add(").append(p).append("count.multiply(").append(number(h.stride())).append("));\n");
        String counterWidth=context.stream()?context.width():h.counter().getType().getName();
        BigInteger min=BigInteger.ONE.shiftLeft(counterWidth.equals("int")?31:63).negate(),max=min.negate().subtract(BigInteger.ONE);
        out.append("if(").append(p).append("count.compareTo(").append(number(BigInteger.valueOf(evidence.minimumIterations()))).append(")>=0");
        if(context.partition()!=null && context.partition().period()>0)out.append(" && ").append(p).append("start.signum()>=0");
        if(!context.stream()) out.append(" && ").append(p).append("after.compareTo(").append(number(min)).append(")>=0 && ").append(p).append("after.compareTo(").append(number(max)).append(")<=0");
        for(var binding:context.bindings()) if(binding.primitiveType().equals(BIG)) out.append(" && ").append(binding.name()).append("!=null && ").append(binding.name()).append(".getClass()==").append(BIG).append(".class && ").append(binding.name()).append(".bitLength()<=").append(RuntimeIterationEvidence.MAX_PARAMETER_BITS);
        if(context.nested()!=null) {
            BigInteger innerMaximum=BigInteger.ONE.shiftLeft(context.innerCounterWidth().equals("int")?31:63).subtract(BigInteger.ONE);
            String first,last;
            if(context.innerBound() instanceof SimpleName name && name.resolveBinding().isEqualTo(h.counter())) {
                first=p+"start";last=p+"after.subtract("+number(h.stride())+")";
            } else {first=join(BIG,".valueOf((long)(",text(source,context.innerBound()),"))");last=first;}
            int shift=(context.innerInclusive()?1:0)-context.innerStart();
            String low=first+".min("+last+")",high=first+".max("+last+")";
            out.append(" && ").append(low).append(".add(").append(number(BigInteger.valueOf(shift))).append(").signum()>=0");
            out.append(" && ").append(high).append(context.innerInclusive()?".add("+BIG+".ONE)":"").append(".compareTo(").append(number(innerMaximum)).append(")<=0");
        }
        out.append("){\n");
        for(Object item:unit.getCommentList()) {work.tick();ASTNode comment=(ASTNode)item;if(comment.getStartPosition()>=context.node().getStartPosition() && comment.getStartPosition()+comment.getLength()<=context.node().getStartPosition()+context.node().getLength()) out.append(text(source,comment)).append('\n');}
        if(evidence.linear()!=null) linear(out,source);
        else {
            List<PolynomialSumPlan> plans=evidence.nested()!=null?List.of(evidence.nested().outer()):evidence.sums();
            if(context.partition()!=null)JavaPartitionedSumEmitter.prepare(out,context.partition(),p);
            for(int state=0;state<plans.size();state++) polynomial(out,plans.get(state),state,context.partition()==null?p+"count":p+"partCount"+state);
            if(context.partition()==null)for(int state=0;state<plans.size();state++) assign(out,state,p+"result"+state);
            else {
                String total=p+"result0";
                for(int part=1;part<plans.size();part++)total+=".add("+p+"result"+part+")";
                assign(out,0,total);
            }
            finalCounter(out);
        }
        out.append("}else{\n").append(fallback(source)).append("\n}\n");
        if(context.stream()) out.append("}}).getAs").append(context.width().equals("int")?"Int":"Long").append("()");else out.append('}');
        return out.toString();
    }
    private void polynomial(Output out,PolynomialSumPlan plan,int state,String count) {
        String p=prefix;
        out.append(BIG).append(' ').append(p).append("result").append(state).append(" = ").append(expression(plan.initial())).append(";\n");
        out.append(BIG).append(' ').append(p).append("choose").append(state).append(" = ").append(BIG).append(".ONE;\n");
        for(int j=0;j<plan.coefficients().size();j++) {
            out.append(p).append("choose").append(state).append(" = ").append(p).append("choose").append(state).append(".multiply(").append(count).append(".subtract(").append(number(BigInteger.valueOf(j))).append(")).divide(").append(number(BigInteger.valueOf(j+1))).append(");\n");
            out.append(p).append("result").append(state).append(" = ").append(p).append("result").append(state).append(".add(").append(expression(plan.coefficients().get(j))).append(".multiply(").append(p).append("choose").append(state).append("));\n");
        }
    }
    private void linear(Output out,String source) {
        var transition=evidence.linear().transition();String p=prefix;int d=transition.matrix().size();
        out.append(BIG).append("[][] ").append(p).append("matrix = new ").append(BIG).append("[][]{");
        for(int i=0;i<d;i++) {if(i>0) out.append(',');out.append('{');for(int j=0;j<d;j++){if(j>0)out.append(',');out.append(expression(transition.matrix().get(i).get(j)));}out.append('}');}out.append("};\n");
        out.append(BIG).append("[] ").append(p).append("vector = new ").append(BIG).append("[]{");
        for(int i=0;i<d;i++){if(i>0)out.append(',');out.append(expression(transition.initial().get(i)));}out.append("};\n");
        boolean exact=context.width().equals(BIG);
        if(exact) {
            // Infinity norm bounds every intermediate matrix power and vector product before allocating them.
            out.append("int ").append(p).append("rowBits=1, ").append(p).append("initialBits=1;\n");
            out.append("for(int ").append(p).append("r=0;").append(p).append("r<").append(d).append(';').append(p).append("r++){ ").append(BIG).append(' ').append(p).append("norm=").append(BIG).append(".ZERO;for(int ").append(p).append("c=0;").append(p).append("c<").append(d).append(';').append(p).append("c++) ").append(p).append("norm=").append(p).append("norm.add(").append(p).append("matrix[").append(p).append("r][").append(p).append("c].abs());").append(p).append("rowBits=java.lang.Math.max(").append(p).append("rowBits,").append(p).append("norm.bitLength());").append(p).append("initialBits=java.lang.Math.max(").append(p).append("initialBits,").append(p).append("vector[").append(p).append("r].bitLength()+1);}\n");
            out.append("if(").append(prefix).append("count.multiply(").append(BIG).append(".valueOf(").append(p).append("rowBits)).add(").append(BIG).append(".valueOf(").append(p).append("initialBits)).compareTo(").append(number(BigInteger.valueOf(RuntimeIterationEvidence.MAX_RESULT_BITS))).append(")<=0){\n");
        } else out.append(BIG).append(' ').append(p).append("modulus=").append(BIG).append(".ONE.shiftLeft(").append(context.width().equals("int")?32:64).append(");\n");
        out.append(BIG).append(' ').append(p).append("exponent=").append(p).append("count;\nwhile(").append(p).append("exponent.signum()>0){\nif(").append(p).append("exponent.testBit(0)){\n");
        out.append(BIG).append("[] ").append(p).append("next=new ").append(BIG).append('[').append(d).append("];\n");
        out.append("for(int ").append(p).append("r=0;").append(p).append("r<").append(d).append(';').append(p).append("r++){ ").append(p).append("next[").append(p).append("r]=").append(BIG).append(".ZERO;for(int ").append(p).append("c=0;").append(p).append("c<").append(d).append(';').append(p).append("c++) ").append(p).append("next[").append(p).append("r]=").append(p).append("next[").append(p).append("r].add(").append(p).append("matrix[").append(p).append("r][").append(p).append("c].multiply(").append(p).append("vector[").append(p).append("c]))").append(exact?"":".mod("+p+"modulus)").append(";}\n").append(p).append("vector=").append(p).append("next;}\n");
        out.append(p).append("exponent=").append(p).append("exponent.shiftRight(1);if(").append(p).append("exponent.signum()>0){\n");
        out.append(BIG).append("[][] ").append(p).append("square=new ").append(BIG).append('[').append(d).append("][").append(d).append("];\n");
        out.append("for(int ").append(p).append("r=0;").append(p).append("r<").append(d).append(';').append(p).append("r++)for(int ").append(p).append("c=0;").append(p).append("c<").append(d).append(';').append(p).append("c++){ ").append(p).append("square[").append(p).append("r][").append(p).append("c]=").append(BIG).append(".ZERO;for(int ").append(p).append("k=0;").append(p).append("k<").append(d).append(';').append(p).append("k++) ").append(p).append("square[").append(p).append("r][").append(p).append("c]=").append(p).append("square[").append(p).append("r][").append(p).append("c].add(").append(p).append("matrix[").append(p).append("r][").append(p).append("k].multiply(").append(p).append("matrix[").append(p).append("k][").append(p).append("c]))").append(exact?"":".mod("+p+"modulus)").append(";}\n").append(p).append("matrix=").append(p).append("square;}}\n");
        for(int state=0;state<context.targets().size();state++) assign(out,state,p+"vector["+state+"]");finalCounter(out);
        if(exact) out.append("}else{\n").append(fallback(source)).append("\n}\n");
    }
    private void assign(Output out,int state,String value) {
        if(context.stream()) out.append("return ");else out.append(context.targets().get(state).getName()).append(" = ");
        out.append(value).append(context.width().equals(BIG)?"":context.width().equals("int")?".intValue()":".longValue()").append(";\n");
    }
    private void finalCounter(Output out) {
        if(context.header().live()) out.append(context.header().counter().getName()).append(" = ").append(prefix).append("after.").append(context.header().counter().getType().getName()).append("Value();\n");
    }
    private String fallback(String source) {return join(context.stream()?"return ":"",text(source,context.node()),context.stream()?";":"");}
    /** Every append checks size and cancellation/work before copying into the generated output. */
    static final class Output {
        private final StringBuilder text=new StringBuilder();
        private final JavaLoopInvariantAnalysis.Work work;
        Output(JavaLoopInvariantAnalysis.Work work) {this.work=work;}
        private void reserve(int length) {
            work.tick();
            if(length<0 || length>RuntimeIterationEvidence.MAX_EMITTED_CHARACTERS-text.length())throw invalid();
            work.charge(length);
        }
        Output append(String value) {reserve(value.length());text.append(value);return this;}
        Output append(char value) {reserve(1);text.append(value);return this;}
        Output append(int value) {return append(Integer.toString(value));}
        Output append(long value) {return append(Long.toString(value));}
        Output append(CharSequence value,int start,int end) {reserve(end-start);text.append(value,start,end);return this;}
        @Override public String toString() {work.charge(text.length());return text.toString();}
    }
    private static String freshPrefix(String source,int offset,JavaLoopInvariantAnalysis.Work work) {
        for(int attempt=0;attempt<RuntimeIterationEvidence.MAX_NAME_ATTEMPTS;attempt++) {
            // Each bounded-length candidate performs at most one source scan, prepaid and cancellable.
            work.charge((long)source.length()+1);
            String candidate="$series"+offset+"_"+attempt+"_";
            if(!source.contains(candidate))return candidate;
        }
        throw new IllegalArgumentException("RUNTIME_NAME_ATTEMPT_LIMIT");
    }
    private record Fraction(String numerator,BigInteger denominator) {
        Fraction {
            if(numerator.length()>RuntimeIterationEvidence.MAX_EMITTED_CHARACTERS)throw invalid();
            requireDenominator(denominator);
        }
    }
    private static void requireDenominator(BigInteger denominator) {
        if(denominator.signum()<=0 || denominator.bitLength()>RuntimeIterationEvidence.MAX_RESULT_BITS)
            throw new IllegalArgumentException("RUNTIME_EMISSION_DENOMINATOR_LIMIT");
    }
    private BigInteger denominatorProduct(BigInteger a,BigInteger b) {
        requireDenominator(a);requireDenominator(b);
        long bits=(long)a.bitLength()+b.bitLength();
        if(bits>RuntimeIterationEvidence.MAX_RESULT_BITS)throw new IllegalArgumentException("RUNTIME_EMISSION_DENOMINATOR_LIMIT");
        work.charge(bits);return a.multiply(b);
    }
    private BigInteger denominatorPower(BigInteger a,int exponent) {
        requireDenominator(a);long bits=(long)a.bitLength()*exponent;
        if(bits>RuntimeIterationEvidence.MAX_RESULT_BITS)throw new IllegalArgumentException("RUNTIME_EMISSION_DENOMINATOR_LIMIT");
        work.charge(bits+1);return a.pow(exponent);
    }
    /** Check intermediate text before allocation, independently of the final output bound. */
    private String join(String... parts) {
        work.tick();long size=0;for(String part:parts)size+=part.length();
        if(size>RuntimeIterationEvidence.MAX_EMITTED_CHARACTERS)throw invalid();
        work.charge(size);return String.join("",parts);
    }
    private String expression(Expr expression) {
        var fraction=fraction(expression,0);
        return fraction.denominator().equals(BigInteger.ONE)?fraction.numerator():join("(",fraction.numerator(),").divide(",number(fraction.denominator()),")");
    }
    private Fraction fraction(Expr expression,int depth) {
        work.tick();if(depth>64) throw invalid();
        if(expression instanceof NumberExpr value) {
            requireDenominator(value.value().denominator());
            return new Fraction(number(value.value().numerator()),value.value().denominator());
        }
        if(expression instanceof VariableExpr variable) {String name=names.get(variable.symbol().orElseThrow());if(name==null)throw invalid();return new Fraction(name,BigInteger.ONE);}
        if(!(expression instanceof BinaryExpr binary))throw invalid();
        Fraction a=fraction(binary.left(),depth+1),b=fraction(binary.right(),depth+1);
        if(binary.operator()==BinaryOperator.POW && binary.right() instanceof NumberExpr power && power.value().denominator().equals(BigInteger.ONE)) {
            int n=power.value().numerator().intValueExact();if(n<0 || n>32)throw invalid();
            BigInteger denominator=denominatorPower(a.denominator(),n);
            return new Fraction(join("(",a.numerator(),").pow(",Integer.toString(n),")"),denominator);
        }
        if(binary.operator()==BinaryOperator.MUL) {
            BigInteger denominator=denominatorProduct(a.denominator(),b.denominator());
            return new Fraction(join("(",a.numerator(),").multiply(",b.numerator(),")"),denominator);
        }
        if(binary.operator()==BinaryOperator.ADD || binary.operator()==BinaryOperator.SUB) {
            BigInteger denominator=denominatorProduct(a.denominator(),b.denominator());
            String first=join("(",a.numerator(),").multiply(",number(b.denominator()),")");
            String second=join("(",b.numerator(),").multiply(",number(a.denominator()),")");
            return new Fraction(join(first,binary.operator()==BinaryOperator.ADD?".add(":".subtract(",second,")"),denominator);
        }throw invalid();
    }
    private String number(BigInteger number) {
        int bits=number.abs().bitLength();if(bits>RuntimeIterationEvidence.MAX_RESULT_BITS)throw invalid();
        work.charge((long)bits+1);return join("new ",BIG,"(\"",number.toString(),"\")");
    }
    private String text(String source,ASTNode node) {
        if(node.getLength()>RuntimeIterationEvidence.MAX_EMITTED_CHARACTERS)throw invalid();
        work.charge(node.getLength());return source.substring(node.getStartPosition(),node.getStartPosition()+node.getLength());
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("RUNTIME_EMISSION_LIMIT_OR_EXPRESSION");}
}

/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

/** Ordinal partition arithmetic only; polynomial evaluation comes from verified SDK plans. */
final class JavaPartitionedSumEmitter {
    private static final String BIG="java.math.BigInteger";
    private JavaPartitionedSumEmitter() { }
    static void capture(JavaRuntimeIterationEmitter.Output out,JavaRuntimeIterationAnalysis.Context context,String source,String p) {
        if(context.split()!=null) {
            var split=context.split();
            out.append(BIG).append(' ').append(p).append("split = ").append(BIG).append(".valueOf((long)(")
                    .append(source,split.getStartPosition(),split.getStartPosition()+split.getLength()).append("));\n");
        }
    }
    static void prepare(JavaRuntimeIterationEmitter.Output out,RuntimeIterationEvidence.Partition partition,String p) {
        if(partition.period()>0) {
            String period=BIG+".valueOf("+partition.period()+")";
            for(int part=0;part<partition.period();part++) {
                String offset=p+"offset"+part;
                out.append(BIG).append(' ').append(offset).append(" = ").append(BIG).append(".valueOf(").append(part)
                        .append(").subtract(").append(p).append("start.mod(").append(period).append(")).mod(").append(period).append(");\n");
                out.append(BIG).append(' ').append(p).append("partStart").append(part).append(" = ").append(p).append("start.add(").append(offset).append(");\n");
                // The numerator is at least -1; clamp before division to express the SDK's nonnegative count.
                out.append(BIG).append(' ').append(p).append("partCount").append(part).append(" = ").append(p).append("count.subtract(")
                        .append(offset).append(").add(").append(BIG).append(".valueOf(").append(partition.period()-1)
                        .append(")).max(").append(BIG).append(".ZERO).divide(").append(period).append(");\n");
            }
        } else {
            out.append(BIG).append(' ').append(p).append("partCount0 = ").append(p).append("split.subtract(").append(p).append("start)")
                    .append(partition.inclusive()?".add("+BIG+".ONE)":"").append(".max(").append(BIG).append(".ZERO).min(").append(p).append("count);\n");
            out.append(BIG).append(' ').append(p).append("partCount1 = ").append(p).append("count.subtract(").append(p).append("partCount0);\n");
            out.append(BIG).append(' ').append(p).append("partStart0 = ").append(p).append("start;\n");
            out.append(BIG).append(' ').append(p).append("partStart1 = ").append(p).append("start.add(").append(p).append("partCount0);\n");
        }
    }
}

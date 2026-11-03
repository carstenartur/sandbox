/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.text.edits.*;

/** Warning credit belongs only to complete unchanged source ranges from actual edits. */
final class GeneratedSourceDiagnostics {
    private GeneratedSourceDiagnostics() { }
    record Edit(int offset, int length, int replacementLength) { }
    private record Span(int oldStart, int newStart, int length) { }
    private record Problem(int id, boolean warning, List<String> arguments, int start, int end) {
        static Problem at(IProblem problem, int start, int end) {
            return new Problem(problem.getID(),problem.isWarning(),List.copyOf(Arrays.asList(problem.getArguments())),start,end);
        }
    }
    static List<Edit> edits(JavaSourceRewritePlan plan) {
        return plan.changes().stream().map(c->new Edit(c.offset(),c.length(),c.replacement().length())).toList();
    }
    static List<Edit> edits(TextEdit edit) {
        var result=new ArrayList<Edit>(); collect(edit,result); return List.copyOf(result);
    }
    private static void collect(TextEdit edit,List<Edit> result) {
        if (edit instanceof MultiTextEdit) {
            for (var child:edit.getChildren()) collect(child,result);
        } else if (!edit.hasChildren() && edit instanceof ReplaceEdit replacement)
            result.add(new Edit(edit.getOffset(),edit.getLength(),replacement.getText().length()));
        else if (!edit.hasChildren() && edit instanceof InsertEdit insertion)
            result.add(new Edit(edit.getOffset(),0,insertion.getText().length()));
        else if (!edit.hasChildren() && edit instanceof DeleteEdit)
            result.add(new Edit(edit.getOffset(),edit.getLength(),0));
        else throw new IllegalArgumentException("UNSUPPORTED_SOURCE_EDIT_KIND"); //$NON-NLS-1$
    }
    static void verify(CompilationUnit before,String source,CompilationUnit after,String generated,List<Edit> edits,IProgressMonitor monitor) {
        // An existing error never authorizes a generated error, even at unchanged syntax.
        for (var problem:after.getProblems()) if (problem.isError())
            throw new IllegalArgumentException("GENERATED_REGION_INVALID: "+problem.getMessage()); //$NON-NLS-1$
        var sorted=new ArrayList<>(edits);
        sorted.sort(Comparator.comparingInt(Edit::offset).thenComparingInt(Edit::length));
        var spans=new ArrayList<Span>(); int old=0; long next=0;
        for (var edit:sorted) {
            cancelled(monitor);
            if (edit.offset()<old || edit.length()<0 || edit.replacementLength()<0 || (long)edit.offset()+edit.length()>source.length())
                throw new IllegalArgumentException("INVALID_DIAGNOSTIC_SOURCE_EDITS"); //$NON-NLS-1$
            int retained=edit.offset()-old;
            retain(source,generated,spans,old,next,retained);
            next+=retained+(long)edit.replacementLength(); old=edit.offset()+edit.length();
        }
        retain(source,generated,spans,old,next,source.length()-old);
        if (next+source.length()-old!=generated.length()) throw new IllegalArgumentException("DIAGNOSTIC_SOURCE_LENGTH_DIFFERS"); //$NON-NLS-1$
        var baseline=new HashMap<Problem,Integer>();
        for (var problem:before.getProblems()) {
            cancelled(monitor);
            if (problem.isWarning()) baseline.merge(Problem.at(problem,problem.getSourceStart(),problem.getSourceEnd()),1,Integer::sum);
        }
        for (var problem:after.getProblems()) {
            cancelled(monitor);
            if (!problem.isWarning()) continue;
            int start=problem.getSourceStart(),end=problem.getSourceEnd();
            Span span=find(spans,start);
            if (start<0 || end<start || span==null || (long)end>= (long)span.newStart()+span.length()) throw introduced(problem);
            int shift=span.oldStart()-span.newStart();
            var key=Problem.at(problem,start+shift,end+shift);
            int count=baseline.getOrDefault(key,0);
            if (count==0) throw introduced(problem);
            baseline.put(key,count-1);
        }
    }
    private static void retain(String source,String generated,List<Span> spans,int old,long next,int length) {
        if (next<0 || next+length>generated.length() || !source.regionMatches(old,generated,(int)next,length))
            throw new IllegalArgumentException("DIAGNOSTIC_SOURCE_PROVENANCE_DIFFERS"); //$NON-NLS-1$
        if (length>0) spans.add(new Span(old,(int)next,length));
    }
    private static Span find(List<Span> spans,int start) {
        int low=0,high=spans.size()-1;
        while (low<=high) {
            int middle=(low+high)>>>1; var span=spans.get(middle);
            if (start<span.newStart()) high=middle-1;
            else if ((long)start>= (long)span.newStart()+span.length()) low=middle+1;
            else return span;
        }
        return null;
    }
    private static IllegalArgumentException introduced(IProblem problem) {
        return new IllegalArgumentException("GENERATED_DIAGNOSTIC_INTRODUCED: "+problem.getID()+" "+Arrays.toString(problem.getArguments()) //$NON-NLS-1$ //$NON-NLS-2$
                +" @"+problem.getSourceStart()+":"+problem.getSourceEnd()+": "+problem.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }
    private static void cancelled(IProgressMonitor monitor) {
        if (Thread.currentThread().isInterrupted() || monitor!=null && monitor.isCanceled()) throw new OperationCanceledException();
    }
}

/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.internal.ui.preferences.formatter.IModifyDialogTabPage.IModificationListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.ui.preferences.cleanup.MathematicalCleanUpTabPage;

/** Exercise the native preference observers and the host's validation status. */
class FunctionalMathPreferenceTest {
    @Test void disablingMathematicsLeavesADormantOptInAndSafetyChangesClearIt() {
        onUi(()->{
            var values=new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
            values.put(MathCleanUpOptions.CLEANUP,"true");values.put(FunctionalMathOptions.ENABLED,"true");
            values.put("cleanup.loop_conversion.enabled","true");
            var status=new AtomicReference<IStatus>();var page=new MathematicalCleanUpTabPage();
            page.setModifyListener(new IModificationListener(){
                @Override public void updateStatus(IStatus value){status.set(value);}
                @Override public void valuesModified(){}
            });
            page.setWorkingValues(values);var shell=new Shell(Display.getCurrent());shell.setLayout(new FillLayout());
            try {
                page.createContents(shell);shell.setSize(1000,750);shell.open();
                var controls=controls(shell);
                var master=button(controls,"Enable explicit mathematics cleanup");
                var combined=button(controls,"Then convert remaining loops using the Functional Converter profile");
                var mathematical=button(controls,"Mathematical mode: accept changed overflow behavior and BigInteger result identity");
                var safety=controls.stream().filter(Combo.class::isInstance).map(Combo.class::cast)
                        .filter(combo->List.of(combo.getItems()).contains("Preserve Java behavior")).findFirst().orElseThrow();
                select(master,false);assertFalse(combined.getEnabled());assertTrue(combined.getSelection());
                assertTrue(status.get()==null || status.get().isOK(),()->String.valueOf(status.get()));
                assertEquals("true",values.get(FunctionalMathOptions.ENABLED),"Disabled mathematics keeps the opt-in dormant");
                select(master,true);assertTrue(combined.getEnabled());
                safety.select(1);safety.notifyListeners(SWT.Selection,new Event());
                assertFalse(combined.getSelection());assertFalse(combined.getEnabled());
                assertEquals("false",values.get(FunctionalMathOptions.ENABLED));
                assertTrue(status.get()==null || status.get().isOK(),()->String.valueOf(status.get()));
                safety.select(0);safety.notifyListeners(SWT.Selection,new Event());select(combined,true);select(mathematical,true);
                assertFalse(combined.getSelection());assertFalse(combined.getEnabled());
                assertEquals("false",values.get(FunctionalMathOptions.ENABLED));
                assertTrue(status.get()==null || status.get().isOK(),()->String.valueOf(status.get()));
            } finally {shell.dispose();}
        });
    }
    private static void select(Button button,boolean selected){button.setSelection(selected);button.notifyListeners(SWT.Selection,new Event());}
    private static Button button(List<Control> controls,String text){
        return controls.stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button->button.getText().equals(text)).findFirst().orElseThrow();
    }
    private static List<Control> controls(Composite parent){
        var result=new ArrayList<Control>();
        for(var child:parent.getChildren()){result.add(child);if(child instanceof Composite composite)result.addAll(controls(composite));}
        return result;
    }
    private static void onUi(Runnable operation){
        var failure=new AtomicReference<Throwable>();
        Display.getDefault().syncExec(()->{try{operation.run();}catch(RuntimeException|Error problem){failure.set(problem);}});
        if(failure.get() instanceof Error error)throw error;
        if(failure.get() instanceof RuntimeException error)throw error;
    }
}

import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

public final class PublicInstallerUi {
    private static final String KEY = "org.eclipse.swtbot.widget.key";
    private static Display display;
    private static Path evidence;
    private static final String PRODUCT = "Eclipse IDE for Java Developers";
    private static final String PROJECT = "Sandbox Project";

    public static void drive(Path directory, String phase) throws Exception {
        evidence = directory;
        for (int i=0; i<900 && display==null; i++) {
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                display = Display.findDisplay(thread);
                if (display != null) break;
            }
            if (display==null) Thread.sleep(200);
        }
        if (display==null) throw new IllegalStateException("Installer SWT UI did not start");
        if (!phase.equals("installer")) throw new IllegalArgumentException(phase);
        await("select-java-product", () -> {
            Tree tree = widget(Tree.class, "productTree");
            if (tree == null) return false;
            TreeItem java = findProduct(tree);
            if (java == null) return false;
            select(tree, java);
            return true;
        });
        await("choose-2026-09", () -> {
            Combo versions=widget(Combo.class,"versionChoice");
            if(versions==null) return false;
            for(int i=0;i<versions.getItemCount();i++) {
                if(versions.getItem(i).contains("2026-09") && !versions.getItem(i).toLowerCase().contains("latest")) {
                    versions.select(i);
                    versions.notifyListeners(SWT.Selection,new Event());
                    return true;
                }
            }
            return false;
        });
        await("select-temurin-jdk-25", () -> {
            Path jdk = Path.of(System.getProperty("java.home")).toRealPath();
            if (!Files.isExecutable(jdk.resolve("bin/javac"))) {
                throw new IllegalStateException("The requested Java 25 environment has no javac: " + jdk);
            }
            for (Combo combo : controls(Combo.class)) {
                for (int i = 0; i < combo.getItemCount(); i++) {
                    String label = combo.getItem(i);
                    if (!(label.equals(jdk.toString()) || label.startsWith(jdk + " "))) continue;
                    combo.select(i);
                    combo.notifyListeners(SWT.Selection, new Event());
                    if (!label.equals(combo.getText())) return false;
                    Files.writeString(evidence.resolve("installer-jdk.txt"), label + "\n");
                    capture("product");
                    return click("next");
                }
            }
            return false;
        });
        await("select-sandbox-main", () -> {
            for (Tree tree : controls(Tree.class)) {
                for (TreeItem catalog : tree.getItems()) {
                    if (!catalog.getText().equals("Github Projects")) continue;
                    expand(tree,catalog);
                    for (TreeItem project : catalog.getItems()) {
                        if (!project.getText().equals(PROJECT)) continue;
                        select(tree,project);
                        for (ToolBar toolbar : controls(ToolBar.class)) {
                            for (ToolItem action : toolbar.getItems()) {
                                if ("choose".equals(action.getData(KEY)) && action.isEnabled()) {
                                    Event e = new Event(); e.item=action;
                                    action.notifyListeners(SWT.Selection,e);
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
            return false;
        });
        await("verify-visible-stream", () -> {
            for (Table table : controls(Table.class)) {
                if (table.getColumnCount()<3) continue;
                for (TableItem item : table.getItems()) {
                    if(PROJECT.equals(item.getText(1)) && "Main".equals(item.getText(2))) {
                        capture("public-main-stream");
                        return click("next");
                    }
                }
            }
            return false;
        });
        await("variables", () -> {
            if (!controls(Button.class).stream().anyMatch(b -> "showAll".equals(b.getData(KEY)))) return false;
            capture("variables");
            return click("next");
        });
        await("confirm-bootstrap", () -> {
            Button finish=widget(Button.class,"finish");
            if (finish==null || !finish.isEnabled()) return false;
            capture("bootstrap-confirmation");
            // Finish enters a nested modal license dialog before its listener returns.
            display.asyncExec(() -> finish.notifyListeners(SWT.Selection, new Event()));
            return true;
        });
        // Oomph enables Finish only when its real Bootstrap task performer returns success.
        await("bootstrap-finished", () -> {
            Shell active = display.getActiveShell();
            if (active != null) {
                List<Button> buttons = new ArrayList<>();
                collect(active, Button.class, buttons);
                if ("Trust Artifacts".equals(active.getText())) {
                    inspectArtifactSigners(active);
                }
                boolean agreement = buttons.stream().anyMatch(b -> b.getText().equals("Decide Later"));
                String acceptLabel = agreement ? "Accept Now"
                        : "Oomph License Confirmation".equals(active.getText()) ? "Accept" : "";
                for (Button button : buttons) {
                    if (!acceptLabel.isEmpty() && acceptLabel.equals(button.getText())) {
                        capture(agreement ? "eclipse-software-user-agreement" : "license-confirmation");
                        display.asyncExec(() -> button.notifyListeners(SWT.Selection, new Event()));
                        return false;
                    }
                }
            }
            if (widget(Composite.class, "progress") == null || widget(StyledText.class, "log") == null) return false;
            if (active != null && active.getParent() instanceof Shell && active != widget(StyledText.class, "log").getShell()) {
                capture("unexpected-bootstrap-dialog");
                List<Button> buttons = new ArrayList<>();
                collect(active, Button.class, buttons);
                throw new IllegalStateException("Unexpected Oomph dialog " + active.getText()
                        + " with buttons " + buttons.stream().map(Button::getText).toList());
            }
            for (String key : List.of("launch", "dismiss")) {
                Button option=widget(Button.class,key);
                if(option!=null && option.getSelection()) {
                    option.setSelection(false);
                    option.notifyListeners(SWT.Selection,new Event());
                }
            }
            Button finish=widget(Button.class,"finish");
            if(finish==null || !finish.isEnabled()) return false;
            capture("bootstrap-complete");
            Files.writeString(evidence.resolve("bootstrap.log"),widget(StyledText.class,"log").getText());
            Files.writeString(evidence.resolve("installer-complete"),"Eclipse IDE for Java Developers 2026-09 / Github Projects / Sandbox Project / Main\n");
            return click("finish");
        });
    }

    private static void inspectArtifactSigners(Shell shell) throws Exception {
        StringBuilder report = new StringBuilder();
        List<Table> tables = new ArrayList<>();
        collect(shell, Table.class, tables);
        for (Table table : tables) {
            for (TableItem row : table.getItems()) {
                for (int i = 0; i < table.getColumnCount(); i++) {
                    if (i > 0) report.append(" | ");
                    report.append(row.getText(i));
                }
                report.append('\n');
                if (!"x509".equals(row.getText(0))) continue;
                Files.writeString(evidence.resolve("installer-signers.txt"), report);
                Object node = row.getData();
                ClassLoader loader = node.getClass().getClassLoader();
                Class<?> treeNode = Class.forName("org.eclipse.jface.viewers.TreeNode", false, loader);
                Object value = treeNode.getMethod("getValue").invoke(node);
                if (value instanceof X509Certificate certificate) {
                    String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(certificate.getEncoded()));
                    report.append("SHA-256 ").append(fingerprint).append(" subject ").append(certificate.getSubjectX500Principal())
                            .append(" issuer ").append(certificate.getIssuerX500Principal()).append(" valid ")
                            .append(certificate.getNotBefore().toInstant()).append(" through ")
                            .append(certificate.getNotAfter().toInstant()).append('\n');
                }
                Files.writeString(evidence.resolve("installer-signers.txt"), report);
                Class<?> adaptable = Class.forName("org.eclipse.core.runtime.IAdaptable", false, loader);
                if (adaptable.isInstance(node)) {
                    Class<?> key = Class.forName("org.eclipse.equinox.p2.metadata.IArtifactKey", false, loader);
                    Object artifacts = adaptable.getMethod("getAdapter", Class.class)
                            .invoke(node, Array.newInstance(key, 0).getClass());
                    if (artifacts instanceof Object[] keys) {
                        for (Object artifact : keys) report.append("associated ").append(artifact).append('\n');
                    }
                }
            }
        }
        capture("artifact-signers");
        Files.writeString(evidence.resolve("installer-signers.txt"), report);
        throw new IllegalStateException("Public installer requested trust for these signers and artifacts:\n" + report);
    }

    private static void await(String stage,Callable<Boolean> condition) throws Exception {
        Files.writeString(evidence.resolve("installer-stage.txt"), stage + "\n");
        long end=System.nanoTime()+TimeUnit.MINUTES.toNanos(stage.equals("bootstrap-finished")?75:8);
        while(System.nanoTime()<end) {
            AtomicReference<Boolean> ready=new AtomicReference<>(false);
            AtomicReference<Throwable> failure=new AtomicReference<>();
            display.syncExec(() -> {
                try {
                    ready.set(condition.call());
                } catch(Throwable t) { failure.set(t); }
            });
            if(failure.get()!=null) throw new IllegalStateException(stage,failure.get());
            if(ready.get()) { System.out.println("PUBLIC_INSTALLER " + stage); return; }
            Thread.sleep(300);
        }
        display.syncExec(() -> capture("timeout-"+stage));
        throw new IllegalStateException("Timed out waiting for visible installer state " + stage);
    }
    private static TreeItem findProduct(Tree tree) {
        for(TreeItem group : tree.getItems()) {
            if(PRODUCT.equals(group.getText())) return group;
            if(!group.getText().startsWith("Eclipse")) continue;
            expand(tree,group);
            for(TreeItem item : group.getItems()) if(PRODUCT.equals(item.getText())) return item;
        }
        return null;
    }
    private static void expand(Tree tree,TreeItem item) {
        if(item.getExpanded()) return;
        item.setExpanded(true);
        Event e=new Event(); e.item=item; tree.notifyListeners(SWT.Expand,e);
    }
    private static void select(Tree tree,TreeItem item) {
        tree.showItem(item); tree.setSelection(item);
        Event e=new Event(); e.item=item; tree.notifyListeners(SWT.Selection,e);
    }
    private static <T extends Control> List<T> controls(Class<T> type) {
        List<T> result=new ArrayList<>();
        for(Shell shell : display.getShells()) if(shell.isVisible()) collect(shell,type,result);
        return result;
    }
    private static <T extends Control> void collect(Control control,Class<T> type,List<T> list) {
        if(!control.isVisible()) return;
        if(type.isInstance(control)) list.add(type.cast(control));
        if(control instanceof Composite c) for(Control child:c.getChildren()) collect(child,type,list);
    }
    private static <T extends Control> T widget(Class<T> type,String key) {
        return controls(type).stream().filter(w -> key.equals(w.getData(KEY))).findFirst().orElse(null);
    }
    private static boolean click(String key) {
        Button b=widget(Button.class,key);
        if(b==null || !b.isEnabled()) return false;
        b.notifyListeners(SWT.Selection,new Event());
        return true;
    }
    private static void capture(String name) {
        Shell shell=display.getActiveShell();
        if(shell==null) shell=Arrays.stream(display.getShells()).filter(Shell::isVisible).findFirst().orElseThrow();
        var area=shell.getClientArea();
        Image image=new Image(display,area.width,area.height);
        try {
            GC gc=new GC(shell);
            try { gc.copyArea(image,0,0); } finally { gc.dispose(); }
            ImageLoader loader=new ImageLoader(); loader.data=new ImageData[]{ image.getImageData() };
            loader.save(evidence.resolve(name+".png").toString(),SWT.IMAGE_PNG);
        } finally { image.dispose(); }
    }
}

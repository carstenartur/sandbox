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
import java.util.jar.JarFile;
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
                    confirmReleaseWorkspaceResolver(active, buttons);
                    return false;
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
            StyledText progressLog = widget(StyledText.class, "log");
            if (widget(Composite.class, "progress") == null || progressLog == null) return false;
            Object status = progressLog.getShell().getData("org.eclipse.oomph.setup.status");
            if (status != null) {
                Class<?> type = Class.forName("org.eclipse.core.runtime.IStatus", false, status.getClass().getClassLoader());
                int severity = (int) type.getMethod("getSeverity").invoke(status);
                if (severity >= 4) {
                    capture("bootstrap-failure");
                    Files.writeString(evidence.resolve("bootstrap.log"), progressLog.getText());
                    throw new IllegalStateException("Oomph BOOTSTRAP failed: " + status + "\n" + progressLog.getText());
                }
            }
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

    // Published 2026-09 SimRel artifact, also byte-identical on Maven Central. The
    // SHA-256 pin is the trust basis. Today's PKI does not validate the 2015 TSA.
    private static final String WORKSPACE_RESOLVER = "org.eclipse.m2e.workspace.cli";
    private static final String M2E_HASH = "53376fa2d7bfd2d5166806e74af9576ee3f8f1c1ac916e2cb4d2bb0c3e72c02f";
    private static final String ECLIPSE_LEAF = "9de986bd62b59a7082505e7284d2d67556321a15b9ec7d372ccdd2ad142690a1";

    private static void confirmReleaseWorkspaceResolver(Shell shell, List<Button> buttons) throws Exception {
        capture("artifact-signers");
        List<Table> tables = new ArrayList<>();
        collect(shell, Table.class, tables);
        Table artifacts = tables.stream().filter(t -> t.getColumnCount() == 3).findFirst().orElseThrow();
        if (artifacts.getItemCount() != 1) throw new IllegalStateException("Unexpected set of p2 artifacts");
        TableItem artifact = artifacts.getItem(0);
        if (!"osgi.bundle".equals(artifact.getText(0)) || !WORKSPACE_RESOLVER.equals(artifact.getText(1))
                || !"0.4.0".equals(artifact.getText(2))) {
            throw new IllegalStateException("Unexpected p2 artifact: " + artifact.getText(0) + "/"
                    + artifact.getText(1) + "/" + artifact.getText(2));
        }
        Table signers = tables.stream().filter(t -> t.getColumnCount() == 4).findFirst().orElseThrow();
        TableItem leaf = null;
        for (TableItem row : signers.getItems()) {
            if (row.getChecked() || !"x509".equals(row.getText(0))) {
                throw new IllegalStateException("Unexpected p2 signer: " + row.getText(2));
            }
            Object node = row.getData();
            Class<?> treeNode = Class.forName("org.eclipse.jface.viewers.TreeNode", false, node.getClass().getClassLoader());
            X509Certificate cert = (X509Certificate) treeNode.getMethod("getValue").invoke(node);
            if (ECLIPSE_LEAF.equals(sha256(cert.getEncoded()))) leaf = row;
        }
        if (leaf == null) throw new IllegalStateException("The published Eclipse end-entity certificate is absent");
        Path pool = Path.of(System.getProperty("user.home"));
        String filename = WORKSPACE_RESOLVER + "_0.4.0.jar";
        List<Path> files;
        try (var paths = Files.walk(pool)) {
            files = paths.filter(p -> p.endsWith(filename) && Files.isRegularFile(p)).toList();
        }
        if (files.isEmpty()) throw new IllegalStateException("Cannot verify the actual p2 pool bytes for " + filename);
        for (Path file : files) verifyWorkspaceResolver(file);
        Button remember = buttons.stream().filter(b -> "Remember selected signers".equals(label(b))).findFirst().orElseThrow();
        Button always = buttons.stream().filter(b -> "Always trust all content".equals(label(b))).findFirst().orElseThrow();
        if (always.getSelection()) throw new IllegalStateException("Unexpected global trust setting");
        if (remember.getSelection()) {
            remember.setSelection(false);
            remember.notifyListeners(SWT.Selection, new Event());
        }
        signers.setSelection(leaf);
        leaf.setChecked(true);
        Event checked = new Event();
        checked.item = leaf;
        checked.detail = SWT.CHECK;
        signers.notifyListeners(SWT.Selection, checked);
        Event selected = new Event();
        selected.item = leaf;
        signers.notifyListeners(SWT.Selection, selected);
        Button trust = buttons.stream().filter(b -> "Trust Selected".equals(label(b))).findFirst().orElseThrow();
        if (!trust.isEnabled() || remember.getSelection() || always.getSelection()) {
            throw new IllegalStateException("Single temporary signer selection is unavailable");
        }
        Files.writeString(evidence.resolve("installer-signers.txt"), "osgi.bundle/" + WORKSPACE_RESOLVER
                + "/0.4.0 SHA-256 " + M2E_HASH + "\nEclipse Foundation leaf SHA-256 " + ECLIPSE_LEAF
                + "\nHistorical signing time 2015-05-18T13:22:08Z. The expired timestamp chain does not establish current PKI trust."
                + "\nVerified the staged JAR against Eclipse 2026-09 and Maven Central. Selected only the leaf for this transaction.\n");
        capture("verified-m2e-artifact");
        display.asyncExec(() -> trust.notifyListeners(SWT.Selection, new Event()));
    }

    private static String label(Button button) {
        return button.getText().replace("&", "");
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void verifyWorkspaceResolver(Path file) throws Exception {
        if (Files.size(file) != 17_901 || !M2E_HASH.equals(sha256(Files.readAllBytes(file)))) {
            throw new IllegalStateException("p2 pool JAR differs from the published Eclipse artifact " + file.getFileName());
        }
        try (JarFile jar = new JarFile(file.toFile(), true)) {
            var classes = jar.stream().filter(entry -> entry.getName().endsWith(".class")).toList();
            if (classes.isEmpty()) throw new IllegalStateException("Missing m2e implementation classes");
            for (var entry : classes) {
                try (var stream = jar.getInputStream(entry)) {
                    stream.transferTo(java.io.OutputStream.nullOutputStream());
                }
                var signers = entry.getCodeSigners();
                if (signers == null || signers.length != 1) throw new IllegalStateException("Missing Eclipse code signature");
                var certificate = (X509Certificate) signers[0].getSignerCertPath().getCertificates().getFirst();
                var timestamp = signers[0].getTimestamp();
                if (!ECLIPSE_LEAF.equals(sha256(certificate.getEncoded())) || timestamp == null
                        || !timestamp.getTimestamp().toInstant().equals(java.time.Instant.parse("2015-05-18T13:22:08Z"))) {
                    throw new IllegalStateException("Unexpected m2e class signature or timestamp " + entry.getName());
                }
                certificate.checkValidity(timestamp.getTimestamp());
            }
        }
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

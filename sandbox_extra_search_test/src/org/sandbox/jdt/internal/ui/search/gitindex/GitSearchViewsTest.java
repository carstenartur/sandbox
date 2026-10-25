/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search.gitindex;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.BooleanSupplier;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import com.sun.net.httpserver.HttpServer;
import java.util.stream.Stream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;

class GitSearchViewsTest {

	@Test void searchAsksForRepository() {
		var shell= new Shell();
		try {
			new GitSearchView().createPartControl(shell);
			assertTrue(widgets(shell).filter(Label.class::isInstance).map(Label.class::cast)
					.anyMatch(label -> label.getText().equals("Repository:")));
		} finally { shell.dispose(); }
	}

	@Test void historyAsksForAnExactPathAndRepository() {
		var shell= new Shell();
		try {
			new JavaTypeHistoryView().createPartControl(shell);
			var labels= widgets(shell).filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText).toList();
			assertTrue(labels.contains("Repository:"));
			assertTrue(labels.contains("File path:"));
		} finally { shell.dispose(); }
	}

	@Test void analyticsRepositoryCanBeEntered() {
		var shell= new Shell();
		try {
			new CommitAnalyticsView().createPartControl(shell);
			Combo input= widgets(shell).filter(Combo.class::isInstance).map(Combo.class::cast).findFirst().orElseThrow();
			assertEquals(0, input.getStyle() & SWT.READ_ONLY);
		} finally { shell.dispose(); }
	}

	@Test void commitRowsAreVisibleAndEditingInvalidatesThem() throws Exception {
		try (var fixture= new ViewFixture(new GitSearchView(), "Git Database Search")) {
			fixture.body= "{\"results\":[{\"objectId\":\"41a92c7\",\"message\":\"Fix clock conversion\",\"author\":\"Alice Example\"}]}";
			fixture.query("fix");
			assertEquals("41a92c7", fixture.view.table.getTable().getItem(0).getText(0));
			assertEquals("Alice Example", fixture.view.table.getTable().getItem(0).getText(2));
			assertEquals("Fix clock conversion", fixture.view.table.getTable().getItem(0).getText(3));
			fixture.capture("git-search.png");
			fixture.view.repository.setText("another-repository");
			assertEquals(0, fixture.view.table.getTable().getItemCount(), "Old rows must not appear to belong to the edited repository");
		}
	}

	@Test void exactFileHistoryShowsCommitAndBlob() throws Exception {
		try (var fixture= new ViewFixture(new JavaTypeHistoryView(), "Git File History")) {
			fixture.body= "{\"results\":[{\"commitObjectId\":\"41a92c7\",\"blobObjectId\":\"8ec40f2\",\"filePath\":\"src/example/Clock.java\",\"commitTime\":\"2026-10-25T12:00:00Z\"}]}";
			fixture.query("src/example/Clock.java");
			assertEquals("src/example/Clock.java", fixture.view.table.getTable().getItem(0).getText(1));
			assertEquals("8ec40f2", fixture.view.table.getTable().getItem(0).getText(3));
			assertTrue(fixture.request.contains("/api/search/filehistory?repo=demo&path=src%2Fexample%2FClock.java"));
			fixture.capture("git-file-history.png");
		}
	}

	@Test void authorCountsAreVisible() throws Exception {
		try (var fixture= new ViewFixture(new CommitAnalyticsView(), "Commit Analytics")) {
			fixture.body= "{\"authors\":[{\"name\":\"Alice Example\",\"email\":\"alice@example.test\",\"commits\":7}]}";
			fixture.query(null);
			assertEquals("alice@example.test", fixture.view.table.getTable().getItem(0).getText(1));
			assertEquals("7", fixture.view.table.getTable().getItem(0).getText(2));
			fixture.capture("git-authors.png");
		}
	}

	@Test void serverErrorsAreVisibleInsteadOfZeroResults() throws Exception {
		try (var fixture= new ViewFixture(new GitSearchView(), "Git Database Search")) {
			fixture.status= 503;
			fixture.query("fix");
			assertTrue(fixture.message().contains("HTTP 503"));
			assertEquals(0, fixture.view.table.getTable().getItemCount());
		}
	}

	@Test void editedInputsIgnoreAnInFlightResponse() throws Exception {
		try (var fixture= new ViewFixture(new GitSearchView(), "Git Database Search")) {
			fixture.responseGate= new java.util.concurrent.CountDownLatch(1);
			fixture.body= "{\"results\":[{\"objectId\":\"old-repository\"}]}";
			fixture.beginQuery("fix");
			fixture.await(() -> fixture.request != null);
			var job= pendingQuery();
			fixture.view.repository.setText("new-repository");
			fixture.responseGate.countDown();
			fixture.await(() -> job.getState() == org.eclipse.core.runtime.jobs.Job.NONE);
			while (org.eclipse.swt.widgets.Display.getCurrent().readAndDispatch()) { }
			assertEquals(0, fixture.view.table.getTable().getItemCount());
			assertTrue(fixture.message().startsWith("Inputs changed."));
		}
	}

	@Test void disposedViewIgnoresAnInFlightResponse() throws Exception {
		try (var fixture= new ViewFixture(new GitSearchView(), "Git Database Search")) {
			fixture.responseGate= new java.util.concurrent.CountDownLatch(1);
			fixture.beginQuery("fix");
			fixture.await(() -> fixture.request != null);
			var job= pendingQuery();
			fixture.shell.dispose();
			fixture.responseGate.countDown();
			fixture.await(() -> job.getState() == org.eclipse.core.runtime.jobs.Job.NONE);
			while (org.eclipse.swt.widgets.Display.getCurrent().readAndDispatch()) { }
			assertTrue(fixture.shell.isDisposed());
		}
	}

	private static org.eclipse.core.runtime.jobs.Job pendingQuery() {
		return Arrays.stream(org.eclipse.core.runtime.jobs.Job.getJobManager().find(null))
				.filter(job -> job.getName().equals("Search") && job.getState() == org.eclipse.core.runtime.jobs.Job.RUNNING)
				.findFirst().orElseThrow();
	}

	private static final class ViewFixture implements AutoCloseable {
		final Shell shell= new Shell();
		final GitResultsView view;
		final HttpServer server;
		volatile String body= "{\"results\":[]}";
		volatile String request;
		volatile int status= 200;
		volatile java.util.concurrent.CountDownLatch responseGate;

		ViewFixture(GitResultsView view, String title) throws Exception {
			this.view= view;
			server= HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", exchange -> {
				request= exchange.getRequestURI().toString();
				try {
					if (responseGate != null && !responseGate.await(10, java.util.concurrent.TimeUnit.SECONDS))
						throw new java.io.IOException("Timed out waiting to release fixture response");
				} catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted); }
				byte[] bytes= body.getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(status, bytes.length);
				try (var output= exchange.getResponseBody()) { output.write(bytes); }
			});
			server.start();
			SemanticSearchClient.initialize("http://127.0.0.1:" + server.getAddress().getPort());
			shell.setText(title);
			view.createPartControl(shell);
			shell.setSize(1020, 280);
			shell.open();
		}

		void query(String text) throws Exception {
			beginQuery(text);
			await(() -> !message().equals("Loading…"));
		}

		void beginQuery(String text) {
			view.repository.setText("demo");
			if (text != null) widgets(shell).filter(Text.class::isInstance).map(Text.class::cast).findFirst().orElseThrow().setText(text);
			widgets(shell).filter(Button.class::isInstance).findFirst().orElseThrow().notifyListeners(SWT.Selection, new Event());
		}

		String message() {
			return widgets(shell).filter(Label.class::isInstance).map(Label.class::cast).reduce((a, b) -> b).orElseThrow().getText();
		}

		void await(BooleanSupplier condition) throws Exception {
			long deadline= System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
			while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
				if (!org.eclipse.swt.widgets.Display.getCurrent().readAndDispatch()) Thread.sleep(10);
			}
			assertTrue(condition.getAsBoolean(), "Timed out waiting for view result");
		}

		void capture(String filename) throws Exception {
			assertEquals("1 result(s).", message());
			String output= System.getProperty("sandbox.gitSearch.screenshots");
			if (output == null) return;
			shell.layout(true, true);
			view.table.getTable().setFocus();
			var painted= new java.util.concurrent.atomic.AtomicBoolean();
			view.table.getTable().addListener(SWT.Paint, event -> painted.set(true));
			widgets(shell).forEach(Control::redraw);
			await(painted::get);
			while (shell.getDisplay().readAndDispatch()) { }
			shell.getDisplay().update();
			Path target= Files.createDirectories(Path.of(output)).resolve(filename);
			var area= shell.getClientArea();
			Image image= new Image(shell.getDisplay(), area.width, area.height);
			GC gc= new GC(shell);
			try {
				gc.copyArea(image, 0, 0);
				ImageLoader loader= new ImageLoader();
				loader.data= new org.eclipse.swt.graphics.ImageData[] { image.getImageData() };
				loader.save(target.toString(), SWT.IMAGE_PNG);
			} finally { gc.dispose(); image.dispose(); }
			String digest= HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(target)));
			Files.writeString(target.resolveSibling(filename + ".sha256"), digest + "  " + filename + "\n");
		}

		@Override public void close() {
			if (responseGate != null) responseGate.countDown();
			shell.dispose();
			server.stop(0);
			SemanticSearchClient.initialize(null);
		}
	}

	static Stream<Control> widgets(Composite parent) {
		return Arrays.stream(parent.getChildren()).flatMap(control -> control instanceof Composite composite
				? Stream.concat(Stream.of(control), widgets(composite)) : Stream.of(control));
	}
}

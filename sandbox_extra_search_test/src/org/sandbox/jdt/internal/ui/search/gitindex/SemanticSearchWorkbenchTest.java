/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search.gitindex;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.runtime.Platform;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.ui.search.SemanticCodeSearchQuery;
import org.sandbox.jdt.internal.ui.search.SemanticCodeSearchResult;
import com.sun.net.httpserver.HttpServer;

class SemanticSearchWorkbenchTest {
	@Test void resultChangesNotifyListeners() {
		var query= new SemanticCodeSearchQuery("clock", "demo", 10, SemanticCodeSearchQuery.SearchMode.HYBRID);
		var result= (SemanticCodeSearchResult) query.getSearchResult();
		var changes= new AtomicInteger();
		org.eclipse.search.ui.ISearchResultListener listener= event -> changes.incrementAndGet();
		result.addListener(listener);
		result.setMatches(List.of(new SearchHit()));
		assertEquals(1, changes.get());
		result.removeListener(listener);
		result.setMatches(List.of());
		assertEquals(1, changes.get());
	}

	@Test void searchFrameworkDisplaysAndClearsResults() throws Exception {
		assertTrue(Arrays.stream(Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.search.searchResultViewPages"))
				.anyMatch(element -> SemanticCodeSearchResult.class.getName().equals(element.getAttribute("searchResultClass"))),
				"Semantic results require a registered Eclipse Search result page");
		HttpServer server= HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		var responseStatus= new AtomicInteger(200);
		server.createContext("/", exchange -> {
			byte[] bytes= "{\"results\":[{\"commitObjectId\":\"41a92c7\",\"filePath\":\"src/example/Clock.java\",\"declaredTypes\":\"Clock\",\"packageName\":\"example\"}]}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(responseStatus.get(), bytes.length);
			try (var output= exchange.getResponseBody()) { output.write(bytes); }
		});
		server.start();
		SemanticSearchClient.initialize("http://127.0.0.1:" + server.getAddress().getPort());
		var query= new SemanticCodeSearchQuery("clock", "demo", 10, SemanticCodeSearchQuery.SearchMode.HYBRID);
		try {
			var view= NewSearchUI.activateSearchResultView();
			NewSearchUI.runQueryInBackground(query, view);
			await(() -> !NewSearchUI.isQueryRunning(query) && view.getActivePage() != null
					&& view.getActivePage().getControl() instanceof Table table && table.getItemCount() == 1);
			Table table= (Table) view.getActivePage().getControl();
			assertEquals("src/example/Clock.java", table.getItem(0).getText(1));
			assertEquals("Clock", table.getItem(0).getText(2));
			responseStatus.set(503);
			assertFalse(query.run(new org.eclipse.core.runtime.NullProgressMonitor()).isOK());
			await(() -> table.getItemCount() == 0);
		} finally {
			NewSearchUI.removeQuery(query);
			server.stop(0);
			SemanticSearchClient.initialize(null);
		}
	}

	private static void await(java.util.function.BooleanSupplier condition) throws Exception {
		long deadline= System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
			if (!Display.getCurrent().readAndDispatch()) Thread.sleep(10);
		}
		assertTrue(condition.getAsBoolean(), "Timed out waiting for the Search view");
	}
}

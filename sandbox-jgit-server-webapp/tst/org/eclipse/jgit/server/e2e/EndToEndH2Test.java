/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.eclipse.jgit.server.e2e;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.server.JGitServerApplication;
import org.eclipse.jgit.storage.hibernate.entity.GitCommitIndex;
import org.eclipse.jgit.storage.hibernate.service.GitDatabaseQueryService;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/** End-to-end integration test for the JGit server webapp. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EndToEndH2Test {

	private static JGitServerApplication server;
	private static String restBaseUrl;
	private static String gitBaseUrl;
	private static String pushedCommitSha;

	@BeforeClass
	public static void startServer() throws Exception {
		DfsBlockCache.reconfigure(new DfsBlockCacheConfig());
		Properties props = new Properties();
		String jdbcUrl = "jdbc:h2:mem:e2e-test;DB_CLOSE_DELAY=-1"; //$NON-NLS-1$
		props.put("hibernate.connection.url", jdbcUrl); //$NON-NLS-1$
		props.put("hibernate.connection.username", "sa"); //$NON-NLS-1$ //$NON-NLS-2$
		props.put("hibernate.connection.password", ""); //$NON-NLS-1$ //$NON-NLS-2$
		props.put("hibernate.connection.driver_class", "org.h2.Driver"); //$NON-NLS-1$ //$NON-NLS-2$
		props.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect"); //$NON-NLS-1$ //$NON-NLS-2$
		props.put("hibernate.hbm2ddl.auto", "create"); //$NON-NLS-1$ //$NON-NLS-2$
		props.put("hibernate.show_sql", "false"); //$NON-NLS-1$ //$NON-NLS-2$
		props.put("hibernate.search.backend.directory.type", "local-heap"); //$NON-NLS-1$ //$NON-NLS-2$
		server = new JGitServerApplication();
		server.start(props, 0, 0);
		restBaseUrl = "http://localhost:" + server.getRestPort(); //$NON-NLS-1$
		gitBaseUrl = "http://localhost:" + server.getGitPort(); //$NON-NLS-1$
	}

	@AfterClass
	public static void stopServer() throws Exception {
		if (server != null) {
			server.stop();
		}
	}

	@Test
	public void test01_HealthEndpoint() throws Exception {
		HttpURLConnection conn = TestHelper.openGet(restBaseUrl, "/api/health"); //$NON-NLS-1$
		assertEquals(200, conn.getResponseCode());
		String body = TestHelper.readBody(conn);
		assertTrue("Should contain UP status", body.contains("\"status\":\"UP\"")); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue("Should indicate database connected", //$NON-NLS-1$
				body.contains("\"database\":{\"status\":\"UP\"}")); //$NON-NLS-1$
	}

	@Test
	public void test02_CreateRepo() throws Exception {
		HttpURLConnection conn = TestHelper.openPost(restBaseUrl, "/api/repos", //$NON-NLS-1$
				"{\"name\":\"e2e-test\",\"description\":\"E2E Test Repo\"}"); //$NON-NLS-1$
		assertEquals(201, conn.getResponseCode());
		String body = TestHelper.readBody(conn);
		assertTrue("Should contain repo name", body.contains("\"name\":\"e2e-test\"")); //$NON-NLS-1$ //$NON-NLS-2$
		Repository repo = server.getRepositoryResolver().getOrCreateRepository("e2e-test"); //$NON-NLS-1$
		org.eclipse.jgit.lib.StoredConfig cfg = repo.getConfig();
		cfg.setBoolean("http", null, "receivepack", true); //$NON-NLS-1$ //$NON-NLS-2$
		cfg.save();
	}

	@Test
	public void test03_GetRepo() throws Exception {
		HttpURLConnection conn = TestHelper.openGet(restBaseUrl, "/api/repos/e2e-test"); //$NON-NLS-1$
		assertEquals(200, conn.getResponseCode());
		String body = TestHelper.readBody(conn);
		assertTrue("Should contain repo name", body.contains("\"name\":\"e2e-test\"")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void test04_CloneEmptyRepo() throws Exception {
		File localDir = Files.createTempDirectory("jgit-e2e-clone").toFile(); //$NON-NLS-1$
		try (Git git = Git.cloneRepository().setURI(gitBaseUrl + "/git/e2e-test.git") //$NON-NLS-1$
				.setDirectory(localDir).call()) {
			assertNotNull("Cloned repository should not be null", git.getRepository()); //$NON-NLS-1$
		} finally {
			deleteRecursive(localDir);
		}
	}

	@Test
	public void test05_CommitAndPush() throws Exception {
		File localDir = Files.createTempDirectory("jgit-e2e-push").toFile(); //$NON-NLS-1$
		try (Git git = Git.cloneRepository().setURI(gitBaseUrl + "/git/e2e-test.git") //$NON-NLS-1$
				.setDirectory(localDir).call()) {
			File testFile = new File(localDir, "README.md"); //$NON-NLS-1$
			Files.writeString(testFile.toPath(), "# E2E Test\nHello from Testcontainers!"); //$NON-NLS-1$
			git.add().addFilepattern("README.md").call(); //$NON-NLS-1$
			RevCommit commit = git.commit().setMessage("Initial e2e commit: add README") //$NON-NLS-1$
					.setAuthor("E2E Test", "e2e@test.org").call(); //$NON-NLS-1$ //$NON-NLS-2$
			assertNotNull("Commit should not be null", commit); //$NON-NLS-1$
			pushedCommitSha = commit.getName();
			Iterable<PushResult> results = git.push().call();
			for (PushResult result : results) {
				for (RemoteRefUpdate update : result.getRemoteUpdates()) {
					assertEquals("Push should succeed", RemoteRefUpdate.Status.OK, update.getStatus()); //$NON-NLS-1$
				}
			}
		} finally {
			deleteRecursive(localDir);
		}
	}

	@Test
	public void test06_VerifyCommitInDatabase() throws Exception {
		assertNotNull("pushedCommitSha should be set by test05", pushedCommitSha); //$NON-NLS-1$
		org.eclipse.jgit.storage.hibernate.service.CommitIndexer indexer =
				new org.eclipse.jgit.storage.hibernate.service.CommitIndexer(
						server.getSessionFactory(), "e2e-test"); //$NON-NLS-1$
		Repository repo = server.getRepositoryResolver().getOrCreateRepository("e2e-test"); //$NON-NLS-1$
		org.eclipse.jgit.lib.ObjectId tipId = repo.exactRef("refs/heads/master") != null //$NON-NLS-1$
				? repo.exactRef("refs/heads/master").getObjectId() : null; //$NON-NLS-1$
		if (tipId == null) {
			tipId = repo.exactRef("refs/heads/main") != null //$NON-NLS-1$
					? repo.exactRef("refs/heads/main").getObjectId() : null; //$NON-NLS-1$
		}
		assertNotNull("Should have a HEAD ref", tipId); //$NON-NLS-1$
		indexer.indexCommit(repo, tipId);
		GitDatabaseQueryService queryService = new GitDatabaseQueryService(server.getSessionFactory());
		List<GitCommitIndex> results = queryService.searchCommitMessages("e2e-test", "README"); //$NON-NLS-1$ //$NON-NLS-2$
		assertFalse("Should find at least one commit", results.isEmpty()); //$NON-NLS-1$
		assertEquals("Initial e2e commit: add README", results.get(0).getCommitMessage()); //$NON-NLS-1$
		assertEquals("E2E Test", results.get(0).getAuthorName()); //$NON-NLS-1$
		assertEquals("e2e@test.org", results.get(0).getAuthorEmail()); //$NON-NLS-1$
	}

	@Test
	public void test07_SearchCommitsViaRest() throws Exception {
		HttpURLConnection conn = TestHelper.openGet(restBaseUrl,
				"/api/search/commits?repo=e2e-test&q=README"); //$NON-NLS-1$
		assertEquals(200, conn.getResponseCode());
		String body = TestHelper.readBody(conn);
		assertFalse("Search body should not be empty", body.isEmpty()); //$NON-NLS-1$
		assertTrue("Should contain results array", body.contains("\"results\"")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void test08_SearchPathsViaRest() throws Exception {
		HttpURLConnection conn = TestHelper.openGet(restBaseUrl,
				"/api/search/paths?repo=e2e-test&q=README"); //$NON-NLS-1$
		assertEquals(200, conn.getResponseCode());
		String body = TestHelper.readBody(conn);
		assertFalse("Search paths body should not be empty", body.isEmpty()); //$NON-NLS-1$
		assertTrue("Should contain results array", body.contains("\"results\"")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void test09_AnalyticsAuthorsViaRest() throws Exception {
		HttpURLConnection conn = TestHelper.openGet(restBaseUrl,
				"/api/analytics/authors?repo=e2e-test"); //$NON-NLS-1$
		assertEquals(200, conn.getResponseCode());
		String body = TestHelper.readBody(conn);
		assertTrue("Should contain authors key", body.contains("\"authors\"")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void test10_AnalyticsObjectsViaRest() throws Exception {
		HttpURLConnection conn = TestHelper.openGet(restBaseUrl,
				"/api/analytics/objects?repo=e2e-test"); //$NON-NLS-1$
		assertEquals(200, conn.getResponseCode());
		String body = TestHelper.readBody(conn);
		assertTrue("Should contain objectCounts key", body.contains("\"objectCounts\"")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void test11_AnalyticsPacksViaRest() throws Exception {
		HttpURLConnection conn = TestHelper.openGet(restBaseUrl,
				"/api/analytics/packs?repo=e2e-test"); //$NON-NLS-1$
		assertEquals(200, conn.getResponseCode());
		String body = TestHelper.readBody(conn);
		assertTrue("Should contain packCount key", body.contains("\"packCount\"")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void test12_PushJavaFileAndSearchByType() throws Exception {
		Repository repo = server.getRepositoryResolver().getOrCreateRepository("e2e-test"); //$NON-NLS-1$
		org.eclipse.jgit.lib.ObjectId commitId;
		try (org.eclipse.jgit.lib.ObjectInserter inserter = repo.newObjectInserter()) {
			byte[] src = ("package org.example;\n\n" //$NON-NLS-1$
					+ "public class HelloWorld {\n" //$NON-NLS-1$
					+ "    public static void main(String[] args) {\n" //$NON-NLS-1$
					+ "        System.out.println(\"Hello\");\n" //$NON-NLS-1$
					+ "    }\n}\n").getBytes(java.nio.charset.StandardCharsets.UTF_8); //$NON-NLS-1$
			org.eclipse.jgit.lib.ObjectId blobId = inserter.insert(
					org.eclipse.jgit.lib.Constants.OBJ_BLOB, src);
			org.eclipse.jgit.lib.TreeFormatter tree = new org.eclipse.jgit.lib.TreeFormatter();
			tree.append("HelloWorld.java", org.eclipse.jgit.lib.FileMode.REGULAR_FILE, blobId); //$NON-NLS-1$
			org.eclipse.jgit.lib.ObjectId treeId = inserter.insert(tree);
			org.eclipse.jgit.lib.CommitBuilder commit = new org.eclipse.jgit.lib.CommitBuilder();
			commit.setTreeId(treeId);
			commit.setAuthor(new org.eclipse.jgit.lib.PersonIdent("E2E Test", "e2e@test.org")); //$NON-NLS-1$ //$NON-NLS-2$
			commit.setCommitter(new org.eclipse.jgit.lib.PersonIdent("E2E Test", "e2e@test.org")); //$NON-NLS-1$ //$NON-NLS-2$
			commit.setMessage("Add HelloWorld.java"); //$NON-NLS-1$
			commitId = inserter.insert(commit);
			inserter.flush();
		}
		org.eclipse.jgit.storage.hibernate.service.BlobIndexer blobIndexer =
				new org.eclipse.jgit.storage.hibernate.service.BlobIndexer(
						server.getSessionFactory(), "e2e-test"); //$NON-NLS-1$
		blobIndexer.indexCommitBlobs(repo, commitId);
		HttpURLConnection conn = TestHelper.openGet(restBaseUrl,
				"/api/search/types?repo=e2e-test&q=HelloWorld"); //$NON-NLS-1$
		assertEquals(200, conn.getResponseCode());
		String body = TestHelper.readBody(conn);
		assertTrue("Should contain results", body.contains("\"results\"")); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue("Should find HelloWorld", body.contains("HelloWorld")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void test13_SearchBySymbolViaRest() throws Exception {
		HttpURLConnection conn = TestHelper.openGet(restBaseUrl,
				"/api/search/symbols?repo=e2e-test&q=main"); //$NON-NLS-1$
		assertEquals(200, conn.getResponseCode());
		String body = TestHelper.readBody(conn);
		assertTrue("Should contain results", body.contains("\"results\"")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void test14_SearchSourceViaRest() throws Exception {
		HttpURLConnection conn = TestHelper.openGet(restBaseUrl,
				"/api/search/source?repo=e2e-test&q=println"); //$NON-NLS-1$
		assertEquals(200, conn.getResponseCode());
		String body = TestHelper.readBody(conn);
		assertTrue("Should contain results", body.contains("\"results\"")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void test15_SearchHierarchyViaRest() throws Exception {
		HttpURLConnection conn = TestHelper.openGet(restBaseUrl,
				"/api/search/hierarchy?repo=e2e-test&q=Object"); //$NON-NLS-1$
		assertEquals(200, conn.getResponseCode());
		String body = TestHelper.readBody(conn);
		assertTrue("Should contain results", body.contains("\"results\"")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void test16_AdminEndpointRequiresToken() throws Exception {
		HttpURLConnection conn = TestHelper.openPost(restBaseUrl, "/api/admin/reindex", "{}"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(401, conn.getResponseCode());
	}

	private static void deleteRecursive(File file) {
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) {
					deleteRecursive(child);
				}
			}
		}
		file.delete();
	}
}

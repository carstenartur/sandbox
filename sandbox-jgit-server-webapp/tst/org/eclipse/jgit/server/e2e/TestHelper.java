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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Utility methods for E2E integration tests providing HTTP and JDBC helpers.
 */
class TestHelper {

	private TestHelper() {
		// utility class
	}

	/**
	 * Open an HTTP GET connection.
	 *
	 * @param baseUrl
	 *            the base URL including scheme, host, port
	 * @param path
	 *            the path to request
	 * @return the open connection
	 * @throws IOException
	 *             on connection error
	 */
	static HttpURLConnection openGet(String baseUrl, String path)
			throws IOException {
		URI uri = URI.create(baseUrl + path);
		HttpURLConnection conn = (HttpURLConnection) uri.toURL()
				.openConnection();
		conn.setRequestMethod("GET"); //$NON-NLS-1$
		conn.setConnectTimeout(10_000);
		conn.setReadTimeout(10_000);
		return conn;
	}

	/**
	 * Open an HTTP POST connection with a JSON body.
	 *
	 * @param baseUrl
	 *            the base URL including scheme, host, port
	 * @param path
	 *            the path to request
	 * @param jsonBody
	 *            the JSON body to send
	 * @return the open connection
	 * @throws IOException
	 *             on connection error
	 */
	static HttpURLConnection openPost(String baseUrl, String path,
			String jsonBody) throws IOException {
		URI uri = URI.create(baseUrl + path);
		HttpURLConnection conn = (HttpURLConnection) uri.toURL()
				.openConnection();
		conn.setRequestMethod("POST"); //$NON-NLS-1$
		conn.setRequestProperty("Content-Type", //$NON-NLS-1$
				"application/json"); //$NON-NLS-1$
		conn.setDoOutput(true);
		conn.setConnectTimeout(10_000);
		conn.setReadTimeout(10_000);
		try (OutputStream os = conn.getOutputStream()) {
			os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
		}
		return conn;
	}

	/**
	 * Read the response body from an HTTP connection.
	 *
	 * @param conn
	 *            the HTTP connection
	 * @return the response body as a string
	 * @throws IOException
	 *             on read error
	 */
	static String readBody(HttpURLConnection conn) throws IOException {
		int code = conn.getResponseCode();
		java.io.InputStream is = (code >= 200 && code < 300)
				? conn.getInputStream()
				: conn.getErrorStream();
		if (is == null) {
			return ""; //$NON-NLS-1$
		}
		try (BufferedReader r = new BufferedReader(
				new InputStreamReader(is, StandardCharsets.UTF_8))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = r.readLine()) != null) {
				sb.append(line);
			}
			return sb.toString();
		}
	}

	/**
	 * Count rows in a table by repository name and object type using JDBC.
	 *
	 * @param jdbcUrl
	 *            the JDBC URL
	 * @param user
	 *            the database username
	 * @param password
	 *            the database password
	 * @param repoName
	 *            the repository name
	 * @param objectType
	 *            the object type constant
	 * @return the count
	 * @throws SQLException
	 *             on database error
	 */
	static long countObjectsByType(String jdbcUrl, String user,
			String password, String repoName, int objectType)
			throws SQLException {
		try (Connection conn = DriverManager.getConnection(jdbcUrl, user,
				password)) {
			try (PreparedStatement ps = conn.prepareStatement(
					"SELECT COUNT(*) FROM git_objects " //$NON-NLS-1$
							+ "WHERE repository_name = ? AND object_type = ?")) { //$NON-NLS-1$
				ps.setString(1, repoName);
				ps.setInt(2, objectType);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						return rs.getLong(1);
					}
				}
			}
		}
		return 0;
	}
}

/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jgit.server.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import io.github.carstenartur.jgit.storage.hibernate.schema.CoreSchemaMigrations;

/**
 * Read-only startup gate for the released Core persistence context.
 *
 * <p>The external backend is accepted only after a normal Core Flyway history
 * exists without failed rows and Hibernate is configured for schema validation.
 * The guard performs no migration and starts no Hibernate services.</p>
 */
final class ExternalCoreStartupGuard {

	private static final String CONNECTION_URL= "hibernate.connection.url"; //$NON-NLS-1$
	private static final String CONNECTION_USER= "hibernate.connection.username"; //$NON-NLS-1$
	private static final String CONNECTION_PASSWORD= "hibernate.connection.password"; //$NON-NLS-1$
	private static final String CONNECTION_DRIVER= "hibernate.connection.driver_class"; //$NON-NLS-1$
	private static final String DEFAULT_SCHEMA= "hibernate.default_schema"; //$NON-NLS-1$
	private static final String DDL_AUTO= "hibernate.hbm2ddl.auto"; //$NON-NLS-1$

	private ExternalCoreStartupGuard() {
	}

	/** Require migration evidence and validate-only Hibernate startup. */
	static void requireReady(Properties properties) {
		Objects.requireNonNull(properties, "properties"); //$NON-NLS-1$
		String ddlAuto= properties.getProperty(DDL_AUTO);
		if (ddlAuto == null || !"validate".equalsIgnoreCase(ddlAuto.strip())) { //$NON-NLS-1$
			throw new IllegalStateException(
					"External Core requires hibernate.hbm2ddl.auto=validate; " //$NON-NLS-1$
							+ "run the matching Flyway adoption or installation first."); //$NON-NLS-1$
		}
		try {
			inspectHistory(properties);
		} catch (ClassNotFoundException | SQLException exception) {
			throw new IllegalStateException(
					"Could not verify the released Core Flyway history before Hibernate startup.", //$NON-NLS-1$
					exception);
		}
	}

	static void inspectHistory(Properties properties)
			throws ClassNotFoundException, SQLException {
		Objects.requireNonNull(properties, "properties"); //$NON-NLS-1$
		String url= requireProperty(properties, CONNECTION_URL);
		String driver= properties.getProperty(CONNECTION_DRIVER);
		if (driver != null && !driver.isBlank()) {
			Class.forName(driver);
		}
		String user= properties.getProperty(CONNECTION_USER, ""); //$NON-NLS-1$
		String password= properties.getProperty(CONNECTION_PASSWORD, ""); //$NON-NLS-1$
		try (Connection connection= DriverManager.getConnection(url, user, password)) {
			try {
				connection.setReadOnly(true);
			} catch (SQLFeatureNotSupportedException exception) {
				// The guard only executes SELECT statements even when a driver cannot
				// advertise the connection as read-only.
			}
			requireSuccessfulHistory(connection,
					properties.getProperty(DEFAULT_SCHEMA));
		}
	}

	static void requireSuccessfulHistory(Connection connection,
			String preferredSchema) throws SQLException {
		Objects.requireNonNull(connection, "connection"); //$NON-NLS-1$
		DatabaseMetaData metadata= connection.getMetaData();
		TableReference history= findHistoryTable(connection, metadata,
				preferredSchema);
		Map<String, String> columns= columns(metadata, history);
		String installedRank= requiredColumn(columns, "installed_rank"); //$NON-NLS-1$
		String version= requiredColumn(columns, "version"); //$NON-NLS-1$
		String success= requiredColumn(columns, "success"); //$NON-NLS-1$
		String sql= "SELECT " + quote(metadata, version) + ", " //$NON-NLS-1$ //$NON-NLS-2$
				+ quote(metadata, success) + " FROM " //$NON-NLS-1$
				+ qualifiedName(metadata, history) + " ORDER BY " //$NON-NLS-1$
				+ quote(metadata, installedRank);

		boolean sawRow= false;
		boolean sawVersionedSuccess= false;
		try (Statement statement= connection.createStatement();
				ResultSet rows= statement.executeQuery(sql)) {
			while (rows.next()) {
				sawRow= true;
				String migrationVersion= rows.getString(1);
				boolean successful= rows.getBoolean(2);
				if (rows.wasNull() || !successful) {
					throw new IllegalStateException(
							"Core Flyway history contains an unsuccessful migration row."); //$NON-NLS-1$
				}
				if (migrationVersion != null && !migrationVersion.isBlank()) {
					sawVersionedSuccess= true;
				}
			}
		}
		if (!sawRow || !sawVersionedSuccess) {
			throw new IllegalStateException(
					"Core Flyway history contains no successful versioned migration."); //$NON-NLS-1$
		}
	}

	private static TableReference findHistoryTable(Connection connection,
			DatabaseMetaData metadata, String preferredSchema) throws SQLException {
		String catalog= connection.getCatalog();
		String schema= preferredSchema;
		if (schema == null || schema.isBlank()) {
			try {
				schema= connection.getSchema();
			} catch (SQLFeatureNotSupportedException exception) {
				schema= null;
			}
		}

		Map<String, TableReference> matches= new LinkedHashMap<>();
		for (String pattern : tableNamePatterns()) {
			collectTables(metadata, catalog, schema, pattern, matches);
		}
		if (matches.isEmpty() && schema != null && !schema.isBlank()) {
			for (String pattern : tableNamePatterns()) {
				collectTables(metadata, catalog, null, pattern, matches);
			}
		}
		if (matches.isEmpty()) {
			throw new IllegalStateException(
					"Missing Core Flyway history table " //$NON-NLS-1$
							+ CoreSchemaMigrations.SCHEMA_HISTORY_TABLE + '.');
		}
		if (matches.size() > 1) {
			throw new IllegalStateException(
					"Core Flyway history table is ambiguous across schemas: " //$NON-NLS-1$
							+ matches.values());
		}
		return matches.values().iterator().next();
	}

	private static void collectTables(DatabaseMetaData metadata, String catalog,
			String schema, String pattern,
			Map<String, TableReference> matches) throws SQLException {
		try (ResultSet tables= metadata.getTables(catalog, schema, pattern,
				new String[] { "TABLE" })) { //$NON-NLS-1$
			while (tables.next()) {
				String tableName= tables.getString("TABLE_NAME"); //$NON-NLS-1$
				if (!CoreSchemaMigrations.SCHEMA_HISTORY_TABLE
						.equalsIgnoreCase(tableName)) {
					continue;
				}
				TableReference reference= new TableReference(
						tables.getString("TABLE_CAT"), //$NON-NLS-1$
						tables.getString("TABLE_SCHEM"), tableName); //$NON-NLS-1$
				String key= String.valueOf(reference.schema()).toLowerCase(Locale.ROOT)
						+ '.' + tableName.toLowerCase(Locale.ROOT);
				matches.putIfAbsent(key, reference);
			}
		}
	}

	private static List<String> tableNamePatterns() {
		String name= CoreSchemaMigrations.SCHEMA_HISTORY_TABLE;
		return List.of(name, name.toLowerCase(Locale.ROOT),
				name.toUpperCase(Locale.ROOT));
	}

	private static Map<String, String> columns(DatabaseMetaData metadata,
			TableReference table) throws SQLException {
		Map<String, String> columns= new LinkedHashMap<>();
		try (ResultSet result= metadata.getColumns(table.catalog(), table.schema(),
				table.name(), null)) {
			while (result.next()) {
				String name= result.getString("COLUMN_NAME"); //$NON-NLS-1$
				columns.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
			}
		}
		return columns;
	}

	private static String requiredColumn(Map<String, String> columns,
			String logicalName) {
		String actualName= columns.get(logicalName.toLowerCase(Locale.ROOT));
		if (actualName == null) {
			throw new IllegalStateException(
					"Core Flyway history table is missing required column " //$NON-NLS-1$
							+ logicalName + '.');
		}
		return actualName;
	}

	private static String qualifiedName(DatabaseMetaData metadata,
			TableReference table) throws SQLException {
		if (table.schema() == null || table.schema().isBlank()) {
			return quote(metadata, table.name());
		}
		return quote(metadata, table.schema()) + '.'
				+ quote(metadata, table.name());
	}

	private static String quote(DatabaseMetaData metadata, String identifier)
			throws SQLException {
		String quote= metadata.getIdentifierQuoteString();
		if (quote == null || quote.isBlank()) {
			return identifier;
		}
		return quote + identifier.replace(quote, quote + quote) + quote;
	}

	private static String requireProperty(Properties properties, String name) {
		String value= properties.getProperty(name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
					"Missing required database property: " + name); //$NON-NLS-1$
		}
		return value;
	}

	private record TableReference(String catalog, String schema, String name) {
	}
}

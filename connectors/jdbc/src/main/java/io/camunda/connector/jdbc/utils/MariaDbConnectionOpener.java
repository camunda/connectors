/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.utils;

import io.camunda.connector.api.error.ConnectorException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import org.mariadb.jdbc.Configuration;
import org.mariadb.jdbc.Driver;
import org.mariadb.jdbc.pool.Pools;

/**
 * Opens connections handled by MariaDB Connector/J, which serves both the MariaDB and the MySQL
 * database choice.
 *
 * <p>The URL, the driver properties and the SQL statement all come from the process, so the driver
 * must not be allowed to hand data that is local to the connector runtime to the database server:
 *
 * <ul>
 *   <li>{@code allowLocalInfile} lets a {@code LOAD DATA LOCAL INFILE} statement upload any file
 *       the runtime can read. The driver enables it by default.
 *   <li>The {@code ENV} and {@code PROPERTY} credential plugins send an environment variable or
 *       system property of the runtime, named by the caller, as the login.
 * </ul>
 *
 * <p>The driver matches option names case-insensitively and lets a URL parameter replace a
 * property, so neither can be enforced by editing the input. The decision is therefore taken on the
 * {@link Configuration} the driver itself derives from the URL and the properties, and the
 * connection is opened from that same object.
 */
final class MariaDbConnectionOpener {

  static final String ALLOW_LOCAL_INFILE = "allowLocalInfile";

  private static final String MARIADB_SCHEME = "jdbc:mariadb:";
  private static final String MYSQL_SCHEME = "jdbc:mysql:";
  private static final String PERMIT_MYSQL_SCHEME = "permitMysqlScheme";
  private static final Set<String> RUNTIME_CREDENTIAL_TYPES = Set.of("ENV", "PROPERTY");

  private MariaDbConnectionOpener() {}

  /** Whether the URL is one that MariaDB Connector/J serves, or can be made to serve. */
  static boolean handles(String url) {
    return url != null && (url.startsWith(MARIADB_SCHEME) || url.startsWith(MYSQL_SCHEME));
  }

  static Connection open(String url, Properties properties) throws SQLException {
    Configuration configuration = toSafeConfiguration(url, properties);
    if (configuration.pool()) {
      return Pools.retrievePool(configuration).getPoolConnection().getConnection();
    }
    return Driver.connect(configuration);
  }

  static Configuration toSafeConfiguration(String url, Properties properties) throws SQLException {
    Properties effective = new Properties();
    effective.putAll(properties);
    if (effective.keySet().stream().noneMatch(MariaDbConnectionOpener::isLocalInfileOption)) {
      effective.setProperty(ALLOW_LOCAL_INFILE, "false");
    }

    // The driver adds the URL parameters to the properties it is given while parsing.
    Configuration configuration = Configuration.parse(withMysqlSchemePermitted(url), effective);
    if (configuration == null) {
      throw new SQLException("No suitable driver found for the given JDBC URL");
    }

    boolean localInfileRequested =
        effective.entrySet().stream()
            .anyMatch(e -> isLocalInfileOption(e.getKey()) && !isDisabled(e.getValue()));
    if (localInfileRequested || configuration.allowLocalInfile()) {
      throw new ConnectorException(
          "The '"
              + ALLOW_LOCAL_INFILE
              + "' option is not supported: the SQL connector does not upload local files");
    }

    String credentialType = configuration.credentialType();
    if (credentialType != null
        && RUNTIME_CREDENTIAL_TYPES.contains(credentialType.toUpperCase(Locale.ROOT))) {
      throw new ConnectorException(
          "The credential type '"
              + credentialType
              + "' is not supported: provide the username and password in the connection instead");
    }
    return configuration;
  }

  /** The driver only accepts the MySQL scheme when this option appears in the URL. */
  private static String withMysqlSchemePermitted(String url) {
    if (url.startsWith(MYSQL_SCHEME) && !url.contains(PERMIT_MYSQL_SCHEME)) {
      return ConnectionParameterHelper.addQueryParameterToURL(url, PERMIT_MYSQL_SCHEME);
    }
    return url;
  }

  private static boolean isLocalInfileOption(Object key) {
    return ALLOW_LOCAL_INFILE.equalsIgnoreCase(String.valueOf(key));
  }

  private static boolean isDisabled(Object value) {
    String text = String.valueOf(value);
    return "false".equalsIgnoreCase(text) || "0".equals(text);
  }
}

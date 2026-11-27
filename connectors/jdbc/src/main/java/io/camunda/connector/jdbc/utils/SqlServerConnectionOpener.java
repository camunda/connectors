/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.utils;

import com.microsoft.sqlserver.jdbc.SQLServerDriver;
import io.camunda.connector.api.error.ConnectorException;
import java.sql.Connection;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Opens connections handled by the Microsoft SQL Server driver.
 *
 * <p>The URL, the driver properties and the SQL statement all come from the process, so the driver
 * must not be allowed to read data that is local to the connector runtime, to act with the
 * runtime's own identity, or to load classes named by the caller. Several driver options do exactly
 * that:
 *
 * <ul>
 *   <li>Always Encrypted with secure enclaves makes the driver fetch an attestation URL of any
 *       scheme, including {@code file:}, and report what it read in an exception message.
 *   <li>The key store options read local files or call a key vault.
 *   <li>Managed identity, default, integrated and certificate authentication use the identity or
 *       the files of the runtime.
 *   <li>Socket factory, trust manager and token callback options load classes by name.
 *   <li>Trust store, server certificate and client certificate options read local files.
 * </ul>
 *
 * <p>The driver matches option names case-insensitively, accepts synonyms and lets a property
 * replace a URL parameter, so none of this can be enforced by editing the input. The decision is
 * therefore taken on the values the driver itself derives from the URL and the properties ({@link
 * SQLServerDriver#getPropertyInfo}, which goes through the same merge as {@link
 * SQLServerDriver#connect}), and the connection is opened by that same driver.
 */
final class SqlServerConnectionOpener {

  static final String AUTHENTICATION = "authentication";

  /** Authentication modes that only use the credentials given with the connection. */
  static final Set<String> ALLOWED_AUTHENTICATION =
      Set.of(
          "notspecified",
          "sqlpassword",
          "activedirectorypassword",
          "activedirectoryserviceprincipal");

  /** Options that must keep the driver's default value. */
  static final List<String> RESTRICTED_OPTIONS =
      List.of(
          // Always Encrypted, enclave attestation and the column master key stores
          "columnEncryptionSetting",
          "enclaveAttestationUrl",
          "enclaveAttestationProtocol",
          "keyStoreAuthentication",
          "keyStoreLocation",
          "keyStoreSecret",
          "keyStorePrincipalId",
          "keyVaultProviderClientId",
          "keyVaultProviderClientKey",
          // The runtime's own identity
          "integratedSecurity",
          "authenticationScheme",
          "useDefaultGSSCredential",
          "jaasConfigurationName",
          "useDefaultJaasConfig",
          "msiClientId",
          // Classes loaded by name
          "socketFactoryClass",
          "socketFactoryConstructorArg",
          "trustManagerClass",
          "trustManagerConstructorArg",
          "accessTokenCallbackClass",
          // Local files
          "trustStore",
          "serverCertificate",
          "clientCertificate",
          "clientKey");

  private static final SQLServerDriver DRIVER = new SQLServerDriver();
  private static final Map<String, String> DEFAULTS = defaults();

  private SqlServerConnectionOpener() {}

  /** Whether the URL is one that the SQL Server driver serves. */
  static boolean handles(String url) {
    try {
      return url != null && DRIVER.acceptsURL(url);
    } catch (SQLException e) {
      return false;
    }
  }

  static Connection open(String url, Properties properties) throws SQLException {
    checkOptions(url, properties);
    return DRIVER.connect(url, properties);
  }

  static void checkOptions(String url, Properties properties) throws SQLException {
    Map<String, String> effective = toMap(DRIVER.getPropertyInfo(url, properties));

    String authentication = effective.get(AUTHENTICATION);
    if (authentication != null
        && !ALLOWED_AUTHENTICATION.contains(authentication.toLowerCase(Locale.ROOT))) {
      throw new ConnectorException(
          "The '"
              + AUTHENTICATION
              + "' option '"
              + authentication
              + "' is not supported: use SqlPassword, ActiveDirectoryPassword or"
              + " ActiveDirectoryServicePrincipal with the credentials in the connection");
    }

    for (String option : RESTRICTED_OPTIONS) {
      if (!isDefault(effective.get(option), DEFAULTS.get(option))) {
        throw new ConnectorException(
            "The '"
                + option
                + "' option is not supported: the SQL connector does not use local files, class"
                + " names, the runtime's identity or Always Encrypted");
      }
    }
  }

  /** The options the driver reports for a URL without any option, with their default values. */
  static Map<String, String> defaults() {
    try {
      return toMap(DRIVER.getPropertyInfo("jdbc:sqlserver://localhost", new Properties()));
    } catch (SQLException e) {
      throw new IllegalStateException("Cannot read the SQL Server driver's default options", e);
    }
  }

  private static Map<String, String> toMap(DriverPropertyInfo[] info) {
    Map<String, String> values = new HashMap<>();
    for (DriverPropertyInfo property : info) {
      values.put(property.name, property.value);
    }
    return values;
  }

  private static boolean isDefault(String value, String defaultValue) {
    return normalize(value).equalsIgnoreCase(normalize(defaultValue));
  }

  private static String normalize(String value) {
    return value == null || "null".equals(value) ? "" : value.trim();
  }
}

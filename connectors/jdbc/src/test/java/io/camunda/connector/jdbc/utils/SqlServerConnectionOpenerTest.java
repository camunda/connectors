/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.microsoft.sqlserver.jdbc.SQLServerDriver;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import io.camunda.connector.jdbc.model.request.connection.UriConnection;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SqlServerConnectionOpenerTest {

  private static final String URL = "jdbc:sqlserver://db.example.com:1433;databaseName=app";

  /**
   * Every option the driver knows that is not in {@link
   * SqlServerConnectionOpener#RESTRICTED_OPTIONS}, reviewed as harmless to leave to the process.
   * {@code authentication} is checked against an allow-list of its own.
   */
  private static final Set<String> UNRESTRICTED_OPTIONS =
      Set.of(
          "AADSecurePrincipalId",
          "AADSecurePrincipalSecret",
          "TransparentNetworkIPResolution",
          // the caller's own token
          "accessToken",
          // Java objects only, which connector input cannot produce
          "accessTokenCallback",
          "gsscredential",
          "applicationIntent",
          "applicationName",
          "authentication",
          "bulkCopyForBatchInsertAllowEncryptedValueModifications",
          "bulkCopyForBatchInsertBatchSize",
          "bulkCopyForBatchInsertCheckConstraints",
          "bulkCopyForBatchInsertFireTriggers",
          "bulkCopyForBatchInsertKeepIdentity",
          "bulkCopyForBatchInsertKeepNulls",
          "bulkCopyForBatchInsertTableLock",
          "cacheBulkCopyMetadata",
          "calcBigDecimalPrecision",
          "cancelQueryTimeout",
          "clientKeyPassword",
          "concatNullYieldsNull",
          "connectRetryCount",
          "connectRetryInterval",
          "databaseName",
          "datetimeParameterType",
          "defaultTransactionIsolation",
          "delayLoadingLobs",
          "disableStatementPooling",
          "domain",
          "enablePrepareOnFirstPreparedStatementCall",
          "encrypt",
          "failoverPartner",
          "fips",
          "hostNameInCertificate",
          "iPAddressPreference",
          "instanceName",
          "lastUpdateCount",
          "lockTimeout",
          "loginTimeout",
          "maxResultBuffer",
          "multiSubnetFailover",
          "packetSize",
          "password",
          "portNumber",
          "prepareMethod",
          "queryTimeout",
          "quotedIdentifier",
          "realm",
          "replication",
          "responseBuffering",
          "retryConn",
          "retryExec",
          "selectMethod",
          "sendStringParametersAsUnicode",
          "sendTemporalDataTypesAsStringForBulkCopy",
          "sendTimeAsDatetime",
          "serverName",
          "serverNameAsACE",
          "serverPreparedStatementDiscardThreshold",
          "serverSpn",
          "socketTimeout",
          "sslProtocol",
          "statementPoolingCacheSize",
          "trustServerCertificate",
          "trustStorePassword",
          "trustStoreType",
          "useBulkCopyForBatchInsert",
          "useFmtOnly",
          "user",
          "vectorTypeSupport",
          "workstationID",
          "xopenStates");

  private static Properties properties(Map<String, String> entries) {
    Properties properties = new Properties();
    properties.putAll(entries);
    return properties;
  }

  @Test
  void everyDriverOptionIsClassified() {
    // Fails when a driver upgrade adds an option: decide whether it belongs in RESTRICTED_OPTIONS.
    // Older driver versions know a subset of these options.
    Set<String> known = new TreeSet<>(UNRESTRICTED_OPTIONS);
    known.addAll(SqlServerConnectionOpener.RESTRICTED_OPTIONS);

    assertThat(known).containsAll(SqlServerConnectionOpener.defaults().keySet());
  }

  @Test
  void everyPropertyOnlyDriverOptionIsClassified() throws Exception {
    // getPropertyInfo leaves these out; the driver keeps them in a separate list.
    Field field = SQLServerDriver.class.getDeclaredField("DRIVER_PROPERTIES_PROPERTY_ONLY");
    field.setAccessible(true);
    Object[] propertyOnly = (Object[]) field.get(null);
    Set<String> names = new TreeSet<>();
    for (Object property : propertyOnly) {
      Method getName = property.getClass().getDeclaredMethod("getName");
      getName.setAccessible(true);
      names.add((String) getName.invoke(property));
    }

    assertThat(names).isNotEmpty();
    assertThat(UNRESTRICTED_OPTIONS).containsAll(names);
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "NULL", " ", "  /etc/hostname"})
  void rejectsFileOptionsThatOnlyLookEmpty(String value) {
    assertThatThrownBy(() ->
            SqlServerConnectionOpener.checkOptions(URL, properties(Map.of("trustStore", value))))
        .as("trustStore='%s'", value)
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("option is not supported");
  }

  @Test
  void acceptsAnOrdinaryConnection() {
    Properties given =
        properties(
            Map.of(
                "user", "alice",
                "password", "s3cret",
                "encrypt", "true",
                "trustServerCertificate", "true",
                "loginTimeout", "5"));

    assertThatCode(() -> SqlServerConnectionOpener.checkOptions(URL + ";applicationName=x", given))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "NotSpecified",
        "SqlPassword",
        "sqlpassword",
        "ActiveDirectoryPassword",
        "ActiveDirectoryServicePrincipal"
      })
  void acceptsAuthenticationWithTheGivenCredentials(String authentication) {
    assertThatCode(() ->
            SqlServerConnectionOpener.checkOptions(
                URL, properties(Map.of("authentication", authentication))))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ActiveDirectoryManagedIdentity",
        "ActiveDirectoryMSI",
        "ActiveDirectoryDefault",
        "ActiveDirectoryIntegrated",
        "ActiveDirectoryInteractive",
        "ActiveDirectoryServicePrincipalCertificate",
        "activedirectorymanagedidentity"
      })
  void rejectsAuthenticationWithTheRuntimesIdentity(String authentication) {
    assertThatThrownBy(() ->
            SqlServerConnectionOpener.checkOptions(
                URL, properties(Map.of("authentication", authentication))))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("authentication");
    assertThatThrownBy(() ->
            SqlServerConnectionOpener.checkOptions(
                URL + ";Authentication=" + authentication, new Properties()))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("authentication");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "columnEncryptionSetting=Enabled",
        "COLUMNENCRYPTIONSETTING=enabled",
        "enclaveAttestationUrl=file:///etc/hostname#",
        "enclaveattestationurl=http://169.254.169.254/",
        "enclaveAttestationProtocol=HGS",
        "enclaveAttestationProtocol=AAS",
        "keyStoreAuthentication=JavaKeyStorePassword",
        "keyStoreLocation=/etc/keystore.jks",
        "keyVaultProviderClientId=x",
        "integratedSecurity=true",
        "authenticationScheme=JavaKerberos",
        "useDefaultGSSCredential=true",
        "jaasConfigurationName=Other",
        "useDefaultJaasConfig=true",
        "msiClientId=x",
        "socketFactoryClass=org.example.Factory",
        "socketFactoryConstructorArg=x",
        "trustManagerClass=org.example.TrustManager",
        "trustManagerConstructorArg=x",
        "accessTokenCallbackClass=org.example.Callback",
        "trustStore=/var/run/secrets/kubernetes.io/serviceaccount/token",
        "serverCertificate=/etc/cert.pem",
        "clientCertificate=/etc/cert.pem",
        "clientKey=/etc/key.pem"
      })
  void rejectsRestrictedOptions(String option) {
    String name = option.substring(0, option.indexOf('='));
    String value = option.substring(option.indexOf('=') + 1);

    assertThatThrownBy(
            () -> SqlServerConnectionOpener.checkOptions(URL + ";" + option, new Properties()))
        .as("URL parameter %s", option)
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("option is not supported");
    assertThatThrownBy(
            () -> SqlServerConnectionOpener.checkOptions(URL, properties(Map.of(name, value))))
        .as("property %s", option)
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("option is not supported");
  }

  @Test
  void rejectsAnOptionSetInBracesOrAsAPropertyOverridingTheUrl() {
    assertThatThrownBy(() ->
            SqlServerConnectionOpener.checkOptions(
                URL + ";enclaveAttestationUrl={file:///etc/hostname;#}", new Properties()))
        .isInstanceOf(ConnectorException.class);
    assertThatThrownBy(() ->
            SqlServerConnectionOpener.checkOptions(
                URL + ";columnEncryptionSetting=Disabled",
                properties(Map.of("ColumnEncryptionSetting", "Enabled"))))
        .isInstanceOf(ConnectorException.class);
  }

  @Test
  void acceptsRestrictedOptionsSetToTheirDefault() {
    assertThatCode(() ->
            SqlServerConnectionOpener.checkOptions(
                URL + ";columnEncryptionSetting=Disabled;integratedSecurity=false",
                properties(Map.of("authenticationScheme", "NativeAuthentication"))))
        .doesNotThrowAnyException();
  }

  @Test
  void handlesOnlySqlServerUrls() {
    assertThat(SqlServerConnectionOpener.handles(URL)).isTrue();
    assertThat(SqlServerConnectionOpener.handles("jdbc:postgresql://db/app")).isFalse();
    assertThat(SqlServerConnectionOpener.handles("jdbc:mariadb://db/app")).isFalse();
    assertThat(SqlServerConnectionOpener.handles(null)).isFalse();
  }

  @Test
  void rejectsBeforeConnecting() throws Exception {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    var connection =
        new UriConnection(
            "jdbc:sqlserver://127.0.0.1:" + closedPort + ";encrypt=false",
            Map.of(
                "columnEncryptionSetting", "Enabled",
                "enclaveAttestationProtocol", "HGS",
                "enclaveAttestationUrl", "file:///etc/hostname#"));

    // A connection attempt would fail with an SQLException about the closed port.
    assertThatThrownBy(() ->
            ConnectionHelper.openConnection(
                SupportedDatabase.MSSQL, connection, Duration.ofSeconds(2)))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("option is not supported");
  }
}

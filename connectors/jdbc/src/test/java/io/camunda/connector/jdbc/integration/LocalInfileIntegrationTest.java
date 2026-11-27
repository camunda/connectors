/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.jdbc.model.client.JdbiJdbcClient;
import io.camunda.connector.jdbc.model.request.JdbcRequest;
import io.camunda.connector.jdbc.model.request.JdbcRequestData;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import io.camunda.connector.jdbc.model.request.connection.DetailedConnection;
import io.camunda.connector.jdbc.model.request.connection.JdbcConnection;
import io.camunda.connector.jdbc.model.request.connection.UriConnection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mariadb.jdbc.Configuration;
import org.mariadb.jdbc.Driver;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A process must not be able to make the connector runtime upload one of its own files to the
 * database server. The servers here accept local files, as a server run by the process author
 * would.
 */
class LocalInfileIntegrationTest {

  private static final String RUNTIME_FILE_CONTENT = "content of a file on the connector runtime";

  static final MySQLContainer<?> mySqlServer =
      new MySQLContainer<>("mysql:5.7.34").withCommand("--local-infile=1");
  static final MariaDBContainer<?> mariaDbServer =
      new MariaDBContainer<>(DockerImageName.parse("mariadb:11.3.2"))
          .withCommand("--local-infile=1");

  @TempDir static Path tempDir;
  static Path runtimeFile;

  record Server(
      SupportedDatabase database,
      String host,
      String port,
      String username,
      String password,
      String databaseName) {

    String mariaDbUrl() {
      return "jdbc:mariadb://" + host + ":" + port + "/" + databaseName;
    }

    DetailedConnection detailed(String databaseName, Map<String, String> properties) {
      return new DetailedConnection(host, port, username, password, databaseName, properties);
    }

    UriConnection uri(String urlSuffix) {
      return new UriConnection(
          mariaDbUrl() + urlSuffix, Map.of("user", username, "password", password));
    }

    @Override
    public String toString() {
      return database.name();
    }
  }

  static Stream<Server> servers() {
    return Stream.of(
        new Server(
            SupportedDatabase.MYSQL,
            mySqlServer.getHost(),
            String.valueOf(mySqlServer.getMappedPort(3306)),
            mySqlServer.getUsername(),
            mySqlServer.getPassword(),
            mySqlServer.getDatabaseName()),
        new Server(
            SupportedDatabase.MARIADB,
            mariaDbServer.getHost(),
            String.valueOf(mariaDbServer.getMappedPort(3306)),
            mariaDbServer.getUsername(),
            mariaDbServer.getPassword(),
            mariaDbServer.getDatabaseName()));
  }

  @BeforeAll
  static void setUp() throws IOException {
    mySqlServer.start();
    mariaDbServer.start();
    runtimeFile = Files.writeString(tempDir.resolve("runtime-file.txt"), RUNTIME_FILE_CONTENT);
  }

  @AfterAll
  static void tearDown() {
    mySqlServer.stop();
    mariaDbServer.stop();
  }

  @BeforeEach
  void resetTable() throws SQLException {
    for (Server server : servers().toList()) {
      try (Connection connection = rawConnection(server, false);
          Statement statement = connection.createStatement()) {
        statement.executeUpdate("DROP TABLE IF EXISTS uploaded");
        statement.executeUpdate("CREATE TABLE uploaded (line TEXT)");
      }
    }
  }

  @ParameterizedTest
  @MethodSource("servers")
  void serverAndDriverUploadTheFileWhenLeftToTheirDefaults(Server server) throws SQLException {
    // Guards the other tests: without the connector's restriction the upload does happen.
    try (Connection connection = rawConnection(server, null);
        Statement statement = connection.createStatement()) {
      statement.execute(loadRuntimeFile());
    }

    assertThat(uploadedLines(server)).containsExactly(RUNTIME_FILE_CONTENT);
  }

  @ParameterizedTest
  @MethodSource("servers")
  void doesNotUploadWithDefaultConnectionSettings(Server server) throws SQLException {
    assertUploadRefused(server, server.database(), server.detailed(server.databaseName(), null));
    assertUploadRefused(server, server.database(), server.uri(""));
  }

  @ParameterizedTest
  @MethodSource("servers")
  void doesNotUploadWhenLocalInfileIsDisabledExplicitly(Server server) throws SQLException {
    assertUploadRefused(
        server,
        server.database(),
        server.detailed(server.databaseName(), Map.of("allowLocalInfile", "false")));
  }

  @ParameterizedTest
  @MethodSource("servers")
  void doesNotUploadWhenLocalInfileIsRequestedByProperty(Server server) throws SQLException {
    assertUploadRefused(
        server,
        server.database(),
        server.detailed(server.databaseName(), Map.of("allowLocalInfile", "true")));
    assertUploadRefused(
        server,
        server.database(),
        server.detailed(server.databaseName(), Map.of("ALLOWLOCALINFILE", "true")));
  }

  @ParameterizedTest
  @MethodSource("servers")
  void doesNotUploadWhenLocalInfileIsRequestedThroughTheDatabaseName(Server server)
      throws SQLException {
    assertUploadRefused(
        server,
        server.database(),
        server.detailed(server.databaseName() + "?allowLocalInfile=true", null));
    assertUploadRefused(
        server,
        server.database(),
        server.detailed(
            server.databaseName() + "?allowLocalInfile=true", Map.of("allowLocalInfile", "false")));
  }

  @ParameterizedTest
  @MethodSource("servers")
  void doesNotUploadWhenLocalInfileIsRequestedInTheUri(Server server) throws SQLException {
    assertUploadRefused(server, server.database(), server.uri("?allowLocalInfile=true"));
    assertUploadRefused(server, server.database(), server.uri("?AllowLocalInfile=1"));
  }

  @ParameterizedTest
  @MethodSource("servers")
  void doesNotUploadWhenAnotherDatabaseIsSelectedForTheUri(Server server) throws SQLException {
    assertUploadRefused(server, SupportedDatabase.POSTGRESQL, server.uri(""));
    assertUploadRefused(server, SupportedDatabase.MSSQL, server.uri("?allowLocalInfile=true"));
  }

  @ParameterizedTest
  @MethodSource("servers")
  void stillRunsOrdinaryStatements(Server server) throws SQLException {
    var insert = new JdbcRequestData(false, "INSERT INTO uploaded (line) VALUES ('ordinary')");

    new JdbiJdbcClient()
        .executeRequest(
            new JdbcRequest(
                server.database(), server.detailed(server.databaseName(), null), insert));
    new JdbiJdbcClient().executeRequest(new JdbcRequest(server.database(), server.uri(""), insert));

    assertThat(uploadedLines(server)).containsExactly("ordinary", "ordinary");
  }

  private void assertUploadRefused(
      Server server, SupportedDatabase selectedDatabase, JdbcConnection connection)
      throws SQLException {
    var request =
        new JdbcRequest(
            selectedDatabase, connection, new JdbcRequestData(false, loadRuntimeFile()));

    assertThatThrownBy(() -> new JdbiJdbcClient().executeRequest(request))
        .satisfiesAnyOf(
            // the driver refuses the statement
            e -> assertThat(e).hasMessageContaining("Local infile is disabled"),
            // the connector refuses the connection settings
            e -> assertThat(e).isInstanceOf(ConnectorException.class));
    assertThat(uploadedLines(server)).isEmpty();
  }

  private static String loadRuntimeFile() {
    return "LOAD DATA LOCAL INFILE '" + runtimeFile.toAbsolutePath() + "' INTO TABLE uploaded";
  }

  private static List<String> uploadedLines(Server server) throws SQLException {
    List<String> lines = new ArrayList<>();
    try (Connection connection = rawConnection(server, false);
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT line FROM uploaded")) {
      while (rows.next()) {
        lines.add(rows.getString(1));
      }
    }
    return lines;
  }

  /** A connection made with the driver directly, outside the connector. */
  private static Connection rawConnection(Server server, Boolean allowLocalInfile)
      throws SQLException {
    Properties properties = new Properties();
    properties.setProperty("user", server.username());
    properties.setProperty("password", server.password());
    if (allowLocalInfile != null) {
      properties.setProperty("allowLocalInfile", allowLocalInfile.toString());
    }
    return Driver.connect(Configuration.parse(server.mariaDbUrl(), properties));
  }
}

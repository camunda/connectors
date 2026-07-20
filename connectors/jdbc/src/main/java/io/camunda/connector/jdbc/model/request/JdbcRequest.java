/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request;

import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Configuration;
import static io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType.Dropdown;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.jdbc.model.request.connection.JdbcConnection;
import io.camunda.connector.jdbc.model.request.connection.JdbcConnectionConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record JdbcRequest(
    @NotNull
        @TemplateProperty(
            id = "database",
            label = "Select a database",
            tooltip =
                "If you choose Oracle, make sure the Oracle JDBC driver is included. "
                    + "<a href=\"https://docs.camunda.io/docs/8.9/components/connectors/out-of-the-box-connectors/sql/#database\">Oracle JDBC driver setup</a>.",
            group = "database",
            type = Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            choices = {
              @TemplateProperty.DropdownPropertyChoice(label = "MariaDB", value = "MARIADB"),
              @TemplateProperty.DropdownPropertyChoice(
                  label = "Microsoft SQL Server",
                  value = "MSSQL"),
              @TemplateProperty.DropdownPropertyChoice(label = "MySQL", value = "MYSQL"),
              @TemplateProperty.DropdownPropertyChoice(label = "PostgreSQL", value = "POSTGRESQL"),
              @TemplateProperty.DropdownPropertyChoice(label = "Oracle", value = "ORACLE"),
            })
        SupportedDatabase database,
    // Not @NotNull, and not @Valid: a bound connection credential (configuration) may substitute
    // for inline connection fields, and takes precedence when both are set (resolved in
    // ConnectionHelper). The raw field is validated conditionally via
    // getInlineConnectionWhenNoCredentialBound() below, so a Modeler-generated diagram that only
    // sets a credential (and carries connection's unconditional default discriminator, e.g.
    // authType=uri, with no uri set) doesn't fail validation on the losing inline path.
    JdbcConnection connection,
    @Valid @NotNull JdbcRequestData data,
    @TemplateProperty(
            id = "connectionConfiguration",
            label = "Connection credential",
            group = "connection",
            type = Configuration,
            optional = true,
            binding = @TemplateProperty.PropertyBinding(name = "configuration"),
            description =
                "Choose a reusable JDBC connection credential. When set, it is bound as a whole to"
                    + " the connector's 'configuration' input.")
        @Valid
        JdbcConnectionConfiguration configuration) {

  /** Convenience constructor for the pre-configuration-chooser shape (no bound configuration). */
  public JdbcRequest(SupportedDatabase database, JdbcConnection connection, JdbcRequestData data) {
    this(database, connection, data, null);
  }

  /**
   * At least one connection source is required: the inline {@link #connection} fields and/or a
   * bound connection credential ({@link #configuration}). Both may be present — when they are, the
   * configuration takes precedence over the inline fields (resolved in {@code
   * ConnectionHelper#resolveConnection}). Replaces the former {@code @NotNull} on {@code
   * connection}, which no longer holds now that a credential can substitute for it.
   */
  @AssertTrue(message = "Either connection fields or a connection credential must be provided")
  @JsonIgnore
  public boolean isConnectionSourceProvided() {
    return connection != null || configuration != null;
  }

  /**
   * Validates the inline {@link #connection} only when no credential is bound. When a credential is
   * bound, it is the effective source (see {@link #isConnectionSourceProvided()} javadoc) and the
   * inline fields are irrelevant — including a leftover discriminator (e.g. {@code authType:
   * "uri"}) that Modeler emits unconditionally regardless of which source the user picked, which
   * would otherwise fail {@code UriConnection}'s {@code @NotBlank uri} even though it lost.
   */
  @Valid
  @JsonIgnore
  public JdbcConnection getInlineConnectionWhenNoCredentialBound() {
    return configuration != null ? null : connection;
  }
}

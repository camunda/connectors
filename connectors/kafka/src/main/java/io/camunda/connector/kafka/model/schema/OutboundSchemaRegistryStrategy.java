/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.kafka.model.schema;

import static io.camunda.connector.kafka.model.schema.OutboundSchemaRegistryStrategy.TYPE;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.kafka.model.SchemaSource;
import io.camunda.connector.kafka.model.SchemaType;

@TemplateSubType(id = TYPE, label = "Confluent Schema registry")
public final class OutboundSchemaRegistryStrategy extends AbstractSchemaRegistryStrategy
    implements OutboundSchemaStrategy {

  @TemplateProperty(ignore = true)
  public static final String TYPE = "schemaRegistry";

  @TemplateProperty(
      id = "schemaSource",
      group = "schema",
      label = "Schema source",
      defaultValue = "inline",
      type = TemplateProperty.PropertyType.Dropdown,
      choices = {
        @TemplateProperty.DropdownPropertyChoice(value = "inline", label = "Inline schema"),
        @TemplateProperty.DropdownPropertyChoice(value = "registry", label = "From registry")
      },
      tooltip =
          "Inline: the schema you enter below is used, and it is registered in the schema registry"
              + " if it is not available there yet. From registry: the schema already registered"
              + " in the schema registry is used and nothing is registered; the task fails if it"
              + " does not exist.")
  SchemaSource schemaSource;

  @TemplateProperty(
      id = "subject",
      group = "schema",
      label = "Subject",
      optional = true,
      condition = @PropertyCondition(property = "schemaStrategy.schemaSource", equals = "registry"),
      tooltip = "Registry subject of the schema. Defaults to <code>&lt;topic&gt;-value</code>.")
  String subject;

  @TemplateProperty(
      id = "schemaVersion",
      group = "schema",
      label = "Schema version",
      optional = true,
      condition = @PropertyCondition(property = "schemaStrategy.schemaSource", equals = "registry"),
      tooltip =
          "Version of the schema in the subject. Leave empty to use the latest version. Ignored if a schema ID is set.")
  Integer schemaVersion;

  @TemplateProperty(
      id = "schemaId",
      group = "schema",
      label = "Schema ID",
      optional = true,
      condition = @PropertyCondition(property = "schemaStrategy.schemaSource", equals = "registry"),
      tooltip = "Global registry ID of the schema. Takes precedence over the schema version.")
  Integer schemaId;

  @FEEL
  @TemplateProperty(
      id = "schema",
      group = "schema",
      condition = @PropertyCondition(property = "schemaStrategy.schemaSource", equals = "inline"),
      feel = FeelMode.required,
      type = TemplateProperty.PropertyType.Text,
      label = "Schema",
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
      tooltip = "Schema (JSON or Avro) for the message value.")
  String schema;

  public OutboundSchemaRegistryStrategy(
      String schema, String schemaRegistryUrl, SchemaType schemaType) {
    super(schemaRegistryUrl, schemaType);
    this.schema = schema;
  }

  public OutboundSchemaRegistryStrategy(
      String schema,
      String schemaRegistryUrl,
      SchemaType schemaType,
      SchemaSource schemaSource,
      String subject,
      Integer schemaVersion,
      Integer schemaId) {
    this(schema, schemaRegistryUrl, schemaType);
    this.schemaSource = schemaSource;
    this.subject = subject;
    this.schemaVersion = schemaVersion;
    this.schemaId = schemaId;
  }

  public OutboundSchemaRegistryStrategy() {}

  public SchemaSource getSchemaSource() {
    return schemaSource == null ? SchemaSource.INLINE : schemaSource;
  }

  public String getSubject() {
    return subject;
  }

  public Integer getSchemaVersion() {
    return schemaVersion;
  }

  public Integer getSchemaId() {
    return schemaId;
  }

  public String getSchema() {
    return schema;
  }
}

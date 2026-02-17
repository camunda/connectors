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
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.kafka.model.SchemaType;

@TemplateSubType(id = TYPE, label = "Confluent Schema registry")
public final class OutboundSchemaRegistryStrategy extends AbstractSchemaRegistryStrategy
    implements OutboundSchemaStrategy {

  @TemplateProperty(ignore = true)
  public static final String TYPE = "schemaRegistry";

  @FEEL
  @TemplateProperty(
      id = "schema",
      group = "schema",
      feel = FeelMode.required,
      type = TemplateProperty.PropertyType.Text,
      label = "Schema",
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
      description = "Schema (JSON or AVRO) for the message value")
  String schema;

  public OutboundSchemaRegistryStrategy(
      String schema, String schemaRegistryUrl, SchemaType schemaType) {
    super(schemaRegistryUrl, schemaType);
    this.schema = schema;
  }

  public OutboundSchemaRegistryStrategy() {}

  public String getSchema() {
    return schema;
  }

  public void setSchema(String schema) {
    this.schema = schema;
  }
}

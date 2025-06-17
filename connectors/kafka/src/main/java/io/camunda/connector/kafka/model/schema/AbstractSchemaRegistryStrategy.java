/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.kafka.model.schema;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.kafka.model.SchemaType;
import jakarta.validation.constraints.NotBlank;

public abstract class AbstractSchemaRegistryStrategy {
  @TemplateProperty(
      group = "schema",
      label = "Schema type",
      id = "schemaType",
      defaultValue = "avro",
      type = TemplateProperty.PropertyType.Dropdown,
      choices = {
        @TemplateProperty.DropdownPropertyChoice(value = "json", label = "JSON"),
        @TemplateProperty.DropdownPropertyChoice(value = "avro", label = "Avro")
      },
      description =
          "Select the schema type. For details, visit the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/kafka/?kafka=inbound\" target=\"_blank\">documentation</a>")
  SchemaType schemaType;

  @NotBlank
  @TemplateProperty(
      group = "schema",
      label = "Schema registry URL",
      description = "Provide the schema registry URL",
      constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
  private String schemaRegistryUrl;

  AbstractSchemaRegistryStrategy(String schemaRegistryUrl, SchemaType schemaType) {
    this.schemaRegistryUrl = schemaRegistryUrl;
    this.schemaType = schemaType;
  }

  public AbstractSchemaRegistryStrategy() {}

  public String getSchemaRegistryUrl() {
    return schemaRegistryUrl;
  }

  public void setSchemaRegistryUrl(String schemaRegistryUrl) {
    this.schemaRegistryUrl = schemaRegistryUrl;
  }

  public SchemaType getSchemaType() {
    return schemaType;
  }

  public void setSchemaType(SchemaType schemaType) {
    this.schemaType = schemaType;
  }
}

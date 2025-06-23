/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.kafka.model.schema;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@TemplateDiscriminatorProperty(
    label = "Schema strategy",
    group = "schema",
    name = "type",
    defaultValue = NoSchemaStrategy.TYPE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = NoSchemaStrategy.class, name = NoSchemaStrategy.TYPE),
  @JsonSubTypes.Type(value = AvroInlineSchemaStrategy.class, name = AvroInlineSchemaStrategy.TYPE),
  @JsonSubTypes.Type(
      value = OutboundSchemaRegistryStrategy.class,
      name = OutboundSchemaRegistryStrategy.TYPE)
})
public sealed interface OutboundSchemaStrategy
    permits NoSchemaStrategy, AvroInlineSchemaStrategy, OutboundSchemaRegistryStrategy {}

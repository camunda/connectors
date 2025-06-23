/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.kafka.model.schema;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.kafka.model.SchemaType;

@TemplateSubType(id = "schemaRegistry", label = "Confluent Schema registry")
public final class InboundSchemaRegistryStrategy extends AbstractSchemaRegistryStrategy
    implements InboundSchemaStrategy {
  @TemplateProperty(ignore = true)
  public static final String TYPE = "schemaRegistry";

  public InboundSchemaRegistryStrategy(String schemaRegistryUrl, SchemaType schemaType) {
    super(schemaRegistryUrl, schemaType);
  }

  public InboundSchemaRegistryStrategy() {}
}

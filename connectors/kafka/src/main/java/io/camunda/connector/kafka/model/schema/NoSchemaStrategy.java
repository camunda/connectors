/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.kafka.model.schema;

import static io.camunda.connector.kafka.model.schema.NoSchemaStrategy.TYPE;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateSubType(id = TYPE, label = "No schema")
public record NoSchemaStrategy() implements OutboundSchemaStrategy, InboundSchemaStrategy {
  @TemplateProperty(ignore = true)
  public static final String TYPE = "noSchema";
}

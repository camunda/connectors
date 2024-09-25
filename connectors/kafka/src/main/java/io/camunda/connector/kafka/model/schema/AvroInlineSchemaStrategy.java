/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.kafka.model.schema;

import static io.camunda.connector.kafka.model.schema.AvroInlineSchemaStrategy.TYPE;

import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateSubType(id = TYPE, label = "Inline schema")
public record AvroInlineSchemaStrategy(
    @FEEL
        @TemplateProperty(
            id = "avro.schema",
            group = "message",
            feel = Property.FeelMode.required,
            type = TemplateProperty.PropertyType.Text,
            label = "Schema",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            description = "Inline schema (Avro) for the message value")
        String schema)
    implements InboundSchemaStrategy, OutboundSchemaStrategy {

  @TemplateProperty(ignore = true)
  public static final String TYPE = "inlineSchema";
}

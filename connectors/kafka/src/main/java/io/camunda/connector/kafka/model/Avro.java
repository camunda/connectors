/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.model;

import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;

public record Avro(
    @FEEL
        @TemplateProperty(
            group = "message",
            feel = Property.FeelMode.required,
            optional = true,
            type = TemplateProperty.PropertyType.Text,
            label = "Avro schema",
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "serializationType",
                    equals = "avro"),
            description = "Optional Avro schema for the message value")
        String schema) {}

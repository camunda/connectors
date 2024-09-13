/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.inbound.model;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "moveHandlingStrategy", label = "move email after processed")
public record MoveHandlingStrategy(
    @TemplateProperty(
            label = "Folder",
            group = "listenerInfos",
            id = "moveHandlingStrategyFolder",
            description = "Folder to move processed email into",
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.handlingStrategy.folder"))
        @Valid
        @NotNull
        String folder)
    implements HandlingStrategy {}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.inbound.model;

import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;

public record EmailListenerConfig(
    @NestedProperties(addNestedPath = false) @Valid ImapConfig imapConfig,
    @TemplateProperty(
            label = "Folder to listen",
            group = "listenerInfos",
            id = "data.folderToListen",
            description = "",
            optional = true,
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.folderToListen"))
        Object folderToListen,
    @TemplateProperty(
            label = "Trigger on new message",
            group = "listenerInfos",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValue = "true",
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            binding = @TemplateProperty.PropertyBinding(name = "data.triggerAdded"))
        boolean triggerAdded,
    @TemplateProperty(
            label = "Trigger on deleted message",
            group = "listenerInfos",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValue = "false",
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            binding = @TemplateProperty.PropertyBinding(name = "data.triggerRemoved"))
        boolean triggerRemoved,
    @TemplateProperty(
            label = "Mark as read",
            group = "listenerInfos",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValue = "false",
            defaultValueType = TemplateProperty.DefaultValueType.Boolean,
            binding = @TemplateProperty.PropertyBinding(name = "data.markAsRead"))
        boolean markAsRead) {}

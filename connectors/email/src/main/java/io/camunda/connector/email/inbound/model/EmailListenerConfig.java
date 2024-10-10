/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.inbound.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.email.config.ImapConfig;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record EmailListenerConfig(
    @NestedProperties(addNestedPath = false) @Valid ImapConfig imapConfig,
    @TemplateProperty(
            label = "Folder to listen",
            group = "listenerInfos",
            id = "data.folderToListen",
            tooltip =
                "Enter the names of the folder you wish to monitor. If left blank, the listener will default to monitoring the 'INBOX' folder.",
            optional = true,
            feel = Property.FeelMode.optional,
            binding = @TemplateProperty.PropertyBinding(name = "data.folderToListen"))
        String folderToListen,
    @TemplateProperty(
            id = "pollingWaitTime",
            label = "Polling wait time",
            group = "listenerInfos",
            defaultValue = "20",
            tooltip =
                "The duration (in seconds) for which the call waits for a message to arrive in the mailbox before correlating",
            binding = @TemplateProperty.PropertyBinding(name = "data.pollingWaitTime"),
            feel = Property.FeelMode.disabled)
        @Pattern(regexp = "^([1-9][0-9]|1[0-9]{2}|2[0-9]{2}|300|secrets\\..+)$")
        @NotBlank
        String pollingWaitTime,
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "pollingConfigDiscriminator")
        @JsonSubTypes(
            value = {
              @JsonSubTypes.Type(value = AllPollingConfig.class, name = "allPollingConfig"),
              @JsonSubTypes.Type(value = UnseenPollingConfig.class, name = "unseenPollingConfig"),
            })
        @Valid
        @NotNull
        @NestedProperties(addNestedPath = false)
        PollingConfig pollingConfig) {}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.inbound.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.constraints.NotBlank;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public record SnsWebhookConnectorProperties(
    @TemplateProperty(
            id = "context",
            label = "Subscription ID",
            group = "subscription",
            description = "The subscription ID is a part of the URL endpoint",
            feel = FeelMode.disabled)
        @NotBlank
        String context,
    @TemplateProperty(
            id = "securitySubscriptionAllowedFor",
            label = "Allow to receive messages from topic(s)",
            group = "subscription",
            description = "Control which topic(s) is allowed to start a process",
            defaultValue = "any",
            type = PropertyType.Dropdown,
            choices = {
              @DropdownPropertyChoice(label = "Any", value = "any"),
              @DropdownPropertyChoice(label = "Specific topic(s)", value = "specific")
            })
        SubscriptionAllowListFlag securitySubscriptionAllowedFor,
    @TemplateProperty(
            id = "topicsAllowList",
            label = "Topic ARN(s)",
            group = "subscription",
            description = "Topics that allow to publish messages",
            optional = true,
            condition =
                @PropertyCondition(
                    property = "inbound.securitySubscriptionAllowedFor",
                    equals = "specific"),
            feel = FeelMode.optional)
        String topicsAllowList,
    @TemplateProperty(ignore = true) List<String> topicsAllowListParsed) {

  public SnsWebhookConnectorProperties(SnsWebhookConnectorPropertiesWrapper wrapper) {
    this(
        wrapper.inbound().context(),
        wrapper.inbound().securitySubscriptionAllowedFor(),
        wrapper.inbound().topicsAllowList(),
        Arrays.stream(
                Optional.ofNullable(wrapper.inbound().topicsAllowList())
                    .orElse("")
                    .trim()
                    .split(","))
            .collect(Collectors.toList()));
  }

  public record SnsWebhookConnectorPropertiesWrapper(SnsWebhookConnectorProperties inbound) {}
}

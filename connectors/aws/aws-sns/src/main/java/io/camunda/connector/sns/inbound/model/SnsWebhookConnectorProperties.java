/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.inbound.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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
            tooltip = "The subscription ID is a part of the URL endpoint",
            feel = FeelMode.disabled)
        @NotBlank
        String context,
    @TemplateProperty(
            id = "securitySubscriptionAllowedFor",
            label = "Allow to receive messages from topic(s)",
            group = "subscription",
            tooltip = "Control which topic(s) are allowed to start a process",
            defaultValue = "specific",
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
            tooltip = "Topic ARNs that are allowed to trigger the process, comma-separated",
            placeholder =
                "arn:aws:sns:us-east-1:123456789012:Topic1,arn:aws:sns:us-east-1:123456789012:Topic2",
            condition =
                @PropertyCondition(
                    property = "inbound.securitySubscriptionAllowedFor",
                    equals = "specific"),
            constraints = @PropertyConstraints(notEmpty = true),
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

  /**
   * A {@code null} or blank {@code securitySubscriptionAllowedFor} is treated as {@code specific}
   * (see {@link SubscriptionAllowListFlag}), so hand-authored BPMN or diagrams built on older
   * templates cannot leave a topic allow list unset while still requiring one. This is the backstop
   * for paths that bypass Modeler entirely; the element template above already requires {@code
   * topicsAllowList} to be non-empty whenever {@code specific} is selected.
   *
   * <p>Checks for at least one non-blank, comma-separated entry rather than just a non-blank raw
   * string: a delimiter-only value like {@code ","} is non-blank but parses (see the wrapper
   * constructor above) to zero usable topic ARNs, which would otherwise activate successfully and
   * then reject every request.
   */
  @AssertTrue(message = "Topic ARN(s) are required unless subscription is allowed for any topic")
  @JsonIgnore
  public boolean isTopicsAllowListPresentWhenRequired() {
    return SubscriptionAllowListFlag.any.equals(securitySubscriptionAllowedFor)
        || Arrays.stream(Optional.ofNullable(topicsAllowList).orElse("").split(","))
            .anyMatch(entry -> !entry.isBlank());
  }

  public record SnsWebhookConnectorPropertiesWrapper(
      @Valid SnsWebhookConnectorProperties inbound) {}
}

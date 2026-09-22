/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.inbound.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

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
    // Typed as a real List<String> (not String) so a FEEL list expression
    // (=["arnA","arnB"]) binds as an actual list instead of a stringified array literal that a
    // plain comma-split would then mangle into unusable entries. type = PropertyType.String below
    // keeps the Modeler input a plain text field either way: FeelDeserializer already splits a
    // typed comma-separated string into a trimmed list (handleListLikeFormat) for the non-FEEL
    // case, so both input styles land here as a proper List<String>.
    @TemplateProperty(
            id = "topicsAllowList",
            label = "Topic ARN(s)",
            group = "subscription",
            tooltip = "Topic ARNs that are allowed to trigger the process, comma-separated",
            placeholder =
                "arn:aws:sns:us-east-1:123456789012:Topic1,arn:aws:sns:us-east-1:123456789012:Topic2",
            type = PropertyType.String,
            condition =
                @PropertyCondition(
                    property = "inbound.securitySubscriptionAllowedFor",
                    equals = "specific"),
            constraints = @PropertyConstraints(notEmpty = true),
            feel = FeelMode.optional)
        @FEEL
        List<String> topicsAllowList) {

  /**
   * A {@code null} or blank {@code securitySubscriptionAllowedFor} is treated as {@code specific}
   * (see {@link SubscriptionAllowListFlag}), so hand-authored BPMN or diagrams built on older
   * templates cannot leave a topic allow list unset while still requiring one. This is the backstop
   * for paths that bypass Modeler entirely; the element template above already requires {@code
   * topicsAllowList} to be non-empty whenever {@code specific} is selected.
   *
   * <p>Checks for at least one non-blank entry rather than just a non-empty list: a delimiter-only
   * value like {@code ","} still parses to a list containing only blank entries, which would
   * otherwise activate successfully and then reject every request.
   */
  @AssertTrue(message = "Topic ARN(s) are required unless subscription is allowed for any topic")
  @JsonIgnore
  public boolean isTopicsAllowListPresentWhenRequired() {
    return SubscriptionAllowListFlag.any.equals(securitySubscriptionAllowedFor)
        || (topicsAllowList != null
            && topicsAllowList.stream().anyMatch(entry -> entry != null && !entry.isBlank()));
  }

  public record SnsWebhookConnectorPropertiesWrapper(
      @Valid SnsWebhookConnectorProperties inbound) {}
}

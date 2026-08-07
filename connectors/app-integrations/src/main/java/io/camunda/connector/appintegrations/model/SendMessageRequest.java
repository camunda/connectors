/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model;

import io.camunda.connector.generator.java.annotation.TemplateLinkedResource;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NestedPropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * The plain-text {@code message} lives here rather than on each recipient because it is
 * platform-independent and always available; only the rich "additional content" varies by platform,
 * so that hangs off {@link Recipient}'s subtypes.
 *
 * <p>A form is a {@code zeebe:linkedResource}, not a variable. Since "Form" is an option on all
 * three per-platform switches, three linked resources are declared, one per switch. Each is gated
 * on BOTH the recipient discriminator and its own additional-content discriminator: gating on the
 * inner one alone would let a stale value from a previously selected branch keep matching, emitting
 * a second form resource. With both, the three gates are genuinely mutually exclusive, so at most
 * one {@code zeebe:linkedResource} block is ever written to the BPMN and {@code
 * AppIntegrationsConnector.formResourceKey} picks it up by resource type without caring which link
 * name it arrived under.
 */
@TemplateLinkedResource(
    linkName = "formCamunda",
    resourceType = "form",
    group = "message",
    resourceIdLabel = "Form ID",
    resourceIdDescription = "ID of the Camunda form to render alongside the message.",
    bindingTypeLabel = "Form binding",
    conditions = {
      @NestedPropertyCondition(property = "recipient.type", equals = "camunda"),
      @NestedPropertyCondition(property = "recipient.camundaExtra.type", equals = "form")
    })
@TemplateLinkedResource(
    linkName = "formTeams",
    resourceType = "form",
    group = "message",
    resourceIdLabel = "Form ID",
    resourceIdDescription = "ID of the Camunda form to render as an Adaptive Card.",
    bindingTypeLabel = "Form binding",
    conditions = {
      @NestedPropertyCondition(property = "recipient.type", equals = "teams"),
      @NestedPropertyCondition(property = "recipient.teamsExtra.type", equals = "form")
    })
@TemplateLinkedResource(
    linkName = "formSlack",
    resourceType = "form",
    group = "message",
    resourceIdLabel = "Form ID",
    resourceIdDescription = "ID of the Camunda form to render as Block Kit.",
    bindingTypeLabel = "Form binding",
    conditions = {
      @NestedPropertyCondition(property = "recipient.type", equals = "slack"),
      @NestedPropertyCondition(property = "recipient.slackExtra.type", equals = "form")
    })
public record SendMessageRequest(
    @NotNull @Valid Recipient recipient,
    @TemplateProperty(
            group = "message",
            label = "Message",
            description =
                "Plain text to send. Optional — leave it empty to send only the additional content below.",
            type = PropertyType.Text,
            optional = true)
        String message) {

  @AssertTrue(message = "Provide a message, additional content, or both")
  public boolean isSomethingToSend() {
    return (message != null && !message.isBlank())
        || (recipient != null
            && !(recipient.additionalContent() instanceof AdditionalContent.None));
  }
}

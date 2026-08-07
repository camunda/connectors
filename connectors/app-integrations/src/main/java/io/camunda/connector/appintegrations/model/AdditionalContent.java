/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotNull;

/**
 * Rich content sent <em>alongside</em> the plain-text message, which is always available and
 * optional. Exactly one kind may be chosen, which is what makes an adaptive card / Block Kit
 * payload and a linked form mutually exclusive without any cross-field validation.
 *
 * <p>Marker interface only. Which of these leaves a modeler may pick depends on the recipient's
 * platform, and an element-template dropdown's choices are static — so the choice sets live in
 * three separate sealed interfaces ({@link CamundaExtra}, {@link TeamsExtra}, {@link SlackExtra}),
 * each generating its own conditioned "Additional content" dropdown. The leaves are shared between
 * them wherever the options overlap.
 */
public interface AdditionalContent {

  /** Plain message only — no rich content and no form. */
  @TemplateSubType(id = "none", label = "None")
  record None() implements CamundaExtra, TeamsExtra, SlackExtra {}

  /**
   * A Camunda form, rendered by the backend as a card. Carries no variables: the form is a {@code
   * zeebe:linkedResource} declared on {@link SendMessageRequest} and delivered to the connector in
   * the job's {@code linkedResources} custom header. This subtype exists to offer the choice and to
   * give the linked-resource conditions a value to test against.
   */
  @TemplateSubType(id = "form", label = "Form")
  record Form() implements CamundaExtra, TeamsExtra, SlackExtra {}

  /** A Microsoft Teams Adaptive Card. */
  @TemplateSubType(id = "adaptiveCard", label = "Adaptive card")
  record AdaptiveCard(
      @NotNull
          @TemplateProperty(
              group = "message",
              label = "Adaptive card",
              description =
                  "Adaptive Card as JSON. Paste a card, or use a FEEL expression to reference one built"
                      + " earlier, e.g. <code>= approvalCard</code>.",
              type = PropertyType.Text,
              feel = FeelMode.required)
          JsonNode adaptiveCard)
      implements TeamsExtra {}

  /** A Slack Block Kit payload — the {@code blocks} array. */
  @TemplateSubType(id = "blockKit", label = "Block Kit")
  record BlockKit(
      @NotNull
          @TemplateProperty(
              group = "message",
              label = "Block Kit blocks",
              description =
                  "Slack Block Kit <code>blocks</code> array as JSON. Paste it, or use a FEEL expression"
                      + " to reference one built earlier, e.g. <code>= approvalBlocks</code>.",
              type = PropertyType.Text,
              feel = FeelMode.required)
          JsonNode blocks)
      implements SlackExtra {}
}

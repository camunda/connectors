/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModel.AnthropicModel.AnthropicModelParameters;
import io.camunda.connector.api.error.ConnectorException;
import org.jspecify.annotations.Nullable;

/**
 * Fail-fast validation of a request's Anthropic {@code thinking}/{@code effort} configuration
 * against the model's resolved {@link AnthropicReasoningCapabilities} (spec §6), before any mapping
 * to the SDK request happens.
 *
 * <p>"Thinking set" means {@code thinking != null && thinking.mode() != null} - a {@code thinking}
 * object with a null {@code mode} (the modeler left the dropdown blank) is treated as unset: no
 * thinking param is emitted and no validation is triggered for it.
 *
 * <p>{@code modelMatched} (see {@link
 * io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilitiesResolver#matches}) gates
 * rule 1 entirely: an unmatched/custom/unknown model is a deliberate pass-through - its
 * thinking/effort configuration is forwarded to the API untouched, without any validation, so
 * unknown models stay usable.
 */
final class AnthropicReasoningValidator {

  private AnthropicReasoningValidator() {}

  static void validate(
      @Nullable AnthropicModelParameters params,
      @Nullable AnthropicReasoningCapabilities reasoning,
      boolean modelMatched,
      String modelId) {
    final AnthropicEffort effort = params == null ? null : params.effort();

    if (!modelMatched) {
      return;
    }

    final var thinking = params == null ? null : params.thinking();
    final boolean thinkingSet = thinking != null && thinking.mode() != null;

    if (reasoning == null) {
      if (thinkingSet || effort != null) {
        throw new ConnectorException(
            ERROR_CODE_FAILED_MODEL_CALL,
            ("Model '%s' does not declare any reasoning capabilities in the capability matrix, so "
                    + "thinking/effort configuration cannot be validated; remove the thinking/effort "
                    + "configuration or use a model known to support reasoning.")
                .formatted(modelId));
      }
      return;
    }

    if (thinkingSet && thinking != null) {
      final ThinkingMode mode = thinking.mode();
      if (mode != null && !reasoning.thinkingModes().contains(mode)) {
        throw new ConnectorException(
            ERROR_CODE_FAILED_MODEL_CALL,
            "Model '%s' does not support thinking mode '%s'; supported thinking modes: %s"
                .formatted(modelId, mode, reasoning.thinkingModes()));
      }
      if (mode == ThinkingMode.ENABLED && thinking.budgetTokens() == null) {
        throw new ConnectorException(
            ERROR_CODE_FAILED_MODEL_CALL,
            "Thinking mode ENABLED requires a budget tokens value for model '%s'"
                .formatted(modelId));
      }
    }

    if (effort != null) {
      if (reasoning.effortLevels().isEmpty()) {
        throw new ConnectorException(
            ERROR_CODE_FAILED_MODEL_CALL,
            "Model '%s' does not support the effort parameter".formatted(modelId));
      }
      if (!reasoning.effortLevels().contains(effort)) {
        throw new ConnectorException(
            ERROR_CODE_FAILED_MODEL_CALL,
            "Model '%s' does not support effort level '%s'; supported effort levels: %s"
                .formatted(modelId, effort, reasoning.effortLevels()));
      }
    }
  }
}

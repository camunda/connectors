/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiApiFamily;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiEffort;
import io.camunda.connector.api.error.ConnectorException;
import org.jspecify.annotations.Nullable;

/**
 * Fail-fast validation of a request's OpenAI reasoning {@code effort} and server-tool configuration
 * against the model's resolved {@link OpenAiReasoningCapabilities} (spec §6) and the selected
 * {@link OpenAiApiFamily}, before any mapping to the SDK request happens.
 *
 * <p>The server-tools-require-Responses check is an API-family constraint, not a model-capability
 * one, so it runs unconditionally, even for unmatched/custom models.
 *
 * <p>{@code modelMatched} (see {@link
 * io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilitiesResolver#matches}) gates the
 * effort/reasoning check: an unmatched/custom/unknown model is a deliberate pass-through - its
 * effort configuration is forwarded to the API untouched, without any validation, so unknown models
 * stay usable.
 */
public final class OpenAiRequestValidator {

  private OpenAiRequestValidator() {}

  public static void validate(
      OpenAiChatModel.OpenAiConnection connection,
      @Nullable OpenAiReasoningCapabilities reasoning,
      boolean modelMatched,
      String modelId) {

    final boolean serverToolsRequested =
        Boolean.TRUE.equals(connection.enableWebSearch())
            || Boolean.TRUE.equals(connection.enableCodeInterpreter());
    if (serverToolsRequested && connection.apiFamily() != OpenAiApiFamily.RESPONSES) {
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL, "Server tools require the Responses API.");
    }

    final var params = connection.model().parameters();
    final OpenAiEffort effort = params == null ? null : params.effort();
    if (effort == null) {
      return;
    }
    if (!modelMatched) {
      return; // unknown/custom models unchecked
    }

    if (reasoning == null) {
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL,
          "Model '" + modelId + "' does not support reasoning effort.");
    }
    if (!reasoning.effortLevels().contains(effort)) {
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL,
          "Model '"
              + modelId
              + "' does not support effort '"
              + effort
              + "'; supported: "
              + reasoning.effortLevels());
    }
  }
}

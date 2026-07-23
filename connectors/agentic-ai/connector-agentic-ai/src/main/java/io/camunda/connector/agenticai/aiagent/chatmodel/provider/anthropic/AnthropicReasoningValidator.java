/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;

import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicThinking;
import io.camunda.connector.api.error.ConnectorException;
import org.jspecify.annotations.Nullable;

/**
 * Fail-fast structural validation of a request's Anthropic {@code thinking} configuration, applied
 * before mapping it onto the SDK request. This is the one matrix-independent rule that survives
 * from the capability-matrix-coupled pilot validator (no {@code *Capabilities*} type is available
 * or referenced in this PR): {@code mode == ENABLED} requires a non-null {@code budgetTokens}
 * value, since the SDK's {@code BetaThinkingConfigEnabled} has no meaningful default budget.
 *
 * <p>A {@code thinking} object with a null {@code mode} (the modeler left the dropdown blank) is
 * treated as unset and never triggers this check.
 */
final class AnthropicReasoningValidator {

  private AnthropicReasoningValidator() {}

  static void validate(@Nullable AnthropicThinking thinking, String modelId) {
    if (thinking == null) {
      return;
    }
    if (thinking.mode() == ThinkingMode.ENABLED && thinking.budgetTokens() == null) {
      throw new ConnectorException(
          ERROR_CODE_FAILED_MODEL_CALL,
          "Thinking mode ENABLED requires a budget tokens value for model '%s'".formatted(modelId));
    }
  }
}

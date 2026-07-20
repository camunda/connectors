/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiApiFamily;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiBackend.OpenAiDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiChatModel.OpenAiModel.OpenAiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiEffort;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OpenAiRequestValidatorTest {

  private static OpenAiConnection conn(
      OpenAiApiFamily apiFamily,
      @Nullable OpenAiEffort effort,
      boolean enableWebSearch,
      boolean enableCodeInterpreter) {
    return new OpenAiConnection(
        apiFamily,
        new OpenAiDirectBackend("k", null, null),
        new OpenAiModel("model-id", new OpenAiModelParameters(null, null, null, effort)),
        enableWebSearch,
        enableCodeInterpreter,
        null,
        null);
  }

  @Test
  void passesThroughUnmatchedModel() {
    assertThatCode(
            () ->
                OpenAiRequestValidator.validate(
                    conn(OpenAiApiFamily.RESPONSES, OpenAiEffort.HIGH, false, false),
                    null,
                    false,
                    "unknown"))
        .doesNotThrowAnyException();
  }

  @Test
  void failsEffortWithoutReasoningCapability() {
    assertThatThrownBy(
            () ->
                OpenAiRequestValidator.validate(
                    conn(OpenAiApiFamily.RESPONSES, OpenAiEffort.HIGH, false, false),
                    null,
                    true,
                    "gpt-4o"))
        .isInstanceOf(ConnectorException.class);
  }

  @Test
  void failsEffortNotInEffortLevels() {
    var reasoning = new OpenAiReasoningCapabilities(List.of(OpenAiEffort.LOW));
    assertThatThrownBy(
            () ->
                OpenAiRequestValidator.validate(
                    conn(OpenAiApiFamily.RESPONSES, OpenAiEffort.HIGH, false, false),
                    reasoning,
                    true,
                    "gpt-5"))
        .isInstanceOf(ConnectorException.class);
  }

  @Test
  void passesEffortInEffortLevels() {
    var reasoning = new OpenAiReasoningCapabilities(List.of(OpenAiEffort.HIGH));
    assertThatCode(
            () ->
                OpenAiRequestValidator.validate(
                    conn(OpenAiApiFamily.RESPONSES, OpenAiEffort.HIGH, false, false),
                    reasoning,
                    true,
                    "gpt-5"))
        .doesNotThrowAnyException();
  }

  @Test
  void failsServerToolOnCompletions() {
    assertThatThrownBy(
            () ->
                OpenAiRequestValidator.validate(
                    conn(OpenAiApiFamily.COMPLETIONS, null, true, false), null, true, "gpt-4o"))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Responses API");
  }

  @Test
  void acceptsEffortForReasoningCapableCompletionsModel() {
    var reasoning = new OpenAiReasoningCapabilities(List.of(OpenAiEffort.HIGH));
    assertThatCode(
            () ->
                OpenAiRequestValidator.validate(
                    conn(OpenAiApiFamily.COMPLETIONS, OpenAiEffort.HIGH, false, false),
                    reasoning,
                    true,
                    "gpt-5"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsEffortForNonReasoningCompletionsModel() {
    assertThatThrownBy(
            () ->
                OpenAiRequestValidator.validate(
                    conn(OpenAiApiFamily.COMPLETIONS, OpenAiEffort.HIGH, false, false),
                    null,
                    true,
                    "gpt-4o"))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("does not support reasoning effort");
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.aiagent.wiremock;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedMessage;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedToolCall;
import java.util.List;

/**
 * Provider-agnostic counterpart of {@code BaseAiAgentTest.ExpectedMessage}, asserting against the
 * {@link RecordedMessage} SPI shape shared by all provider wire-format fixtures instead of the
 * OpenAI-specific recorded message type.
 */
record ProviderWireFormatExpectedMessage(
    String role, String text, List<String> toolCallNames, String toolCallId) {

  static ProviderWireFormatExpectedMessage system(String text) {
    return new ProviderWireFormatExpectedMessage("system", text, null, null);
  }

  static ProviderWireFormatExpectedMessage user(String text) {
    return new ProviderWireFormatExpectedMessage("user", text, null, null);
  }

  static ProviderWireFormatExpectedMessage assistant(String text) {
    return new ProviderWireFormatExpectedMessage("assistant", text, null, null);
  }

  static ProviderWireFormatExpectedMessage assistantWithToolCalls(
      String text, String... toolCallNames) {
    return new ProviderWireFormatExpectedMessage("assistant", text, List.of(toolCallNames), null);
  }

  static ProviderWireFormatExpectedMessage toolCallResult(String toolCallId, String text) {
    return new ProviderWireFormatExpectedMessage("tool", text, null, toolCallId);
  }

  static void assertConversationMessages(
      RecordedChatRequest request, ProviderWireFormatExpectedMessage... expectedMessages) {
    final var messages = request.messages();
    assertThat(messages)
        .as("number of messages sent to the model")
        .hasSize(expectedMessages.length);

    for (int i = 0; i < expectedMessages.length; i++) {
      expectedMessages[i].assertMatches(i, messages.get(i));
    }
  }

  private void assertMatches(int index, RecordedMessage message) {
    assertThat(message.role()).as("role of message %d", index).isEqualTo(role);

    if (text != null) {
      assertThat(message.textContent()).as("text content of message %d", index).isEqualTo(text);
    }

    if (toolCallNames != null) {
      final var actualNames = message.toolCalls().stream().map(RecordedToolCall::name).toList();
      assertThat(actualNames)
          .as("tool call names of message %d", index)
          .containsExactlyElementsOf(toolCallNames);
    }

    if (toolCallId != null) {
      assertThat(message.toolCallId())
          .as("tool_call_id of message %d", index)
          .isEqualTo(toolCallId);
    }
  }
}

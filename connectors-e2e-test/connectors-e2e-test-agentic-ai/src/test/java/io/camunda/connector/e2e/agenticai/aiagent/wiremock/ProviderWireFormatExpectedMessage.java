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
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedContentPart;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedMessage;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedToolCall;
import java.util.List;

/**
 * Provider-agnostic counterpart of {@code BaseAgentTest.ExpectedMessage}, asserting against the
 * {@link RecordedMessage} SPI shape shared by all provider wire-format fixtures instead of the
 * OpenAI-specific recorded message type.
 *
 * <p>One dedicated type per role instead of a single record with nullable fields, so each variant
 * only carries what's actually relevant to it (e.g. a system message has no tool calls to assert)
 * and can grow independently — an {@link AssistantMessage} could gain {@link ExpectedContentPart}
 * assertions for multimodal tool-call turns without affecting the other roles.
 */
sealed interface ProviderWireFormatExpectedMessage {

  void assertMatches(int index, RecordedMessage message);

  static ProviderWireFormatExpectedMessage system(String text) {
    return new SystemMessage(text);
  }

  static ProviderWireFormatExpectedMessage user(String text) {
    return new UserMessage(List.of(ExpectedContentPart.text(text)));
  }

  /** A user message with an arbitrary ordered sequence of content parts, e.g. text + attachment. */
  static ProviderWireFormatExpectedMessage user(ExpectedContentPart... contentParts) {
    return new UserMessage(List.of(contentParts));
  }

  static ProviderWireFormatExpectedMessage userWithDocument(String text) {
    return user(ExpectedContentPart.text(text), ExpectedContentPart.attachment());
  }

  static ProviderWireFormatExpectedMessage assistant(String text) {
    return new AssistantMessage(text, List.of());
  }

  static ProviderWireFormatExpectedMessage assistantWithToolCalls(
      String text, String... toolCallNames) {
    return new AssistantMessage(text, List.of(toolCallNames));
  }

  static ProviderWireFormatExpectedMessage toolCallResult(String toolCallId, String text) {
    return new ToolCallResultMessage(toolCallId, text);
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

  private static void assertRoleAndText(
      int index, RecordedMessage message, String expectedRole, String expectedText) {
    assertThat(message.role()).as("role of message %d", index).isEqualTo(expectedRole);
    assertThat(message.textContent())
        .as("text content of message %d", index)
        .isEqualTo(expectedText);
  }

  record SystemMessage(String text) implements ProviderWireFormatExpectedMessage {

    @Override
    public void assertMatches(int index, RecordedMessage message) {
      assertRoleAndText(index, message, "system", text);
    }
  }

  /**
   * A user message asserting an ordered sequence of content parts — supports multiple text blocks
   * or text interleaved with attachments, unlike a single joined-text comparison.
   */
  record UserMessage(List<ExpectedContentPart> contentParts)
      implements ProviderWireFormatExpectedMessage {

    @Override
    public void assertMatches(int index, RecordedMessage message) {
      assertThat(message.role()).as("role of message %d", index).isEqualTo("user");

      final var actualParts = message.contentParts();
      assertThat(actualParts)
          .as("number of content parts of message %d", index)
          .hasSize(contentParts.size());

      for (int i = 0; i < contentParts.size(); i++) {
        contentParts.get(i).assertMatches(index, i, actualParts.get(i));
      }
    }
  }

  record AssistantMessage(String text, List<String> toolCallNames)
      implements ProviderWireFormatExpectedMessage {

    @Override
    public void assertMatches(int index, RecordedMessage message) {
      assertRoleAndText(index, message, "assistant", text);

      final var actualNames = message.toolCalls().stream().map(RecordedToolCall::name).toList();
      assertThat(actualNames)
          .as("tool call names of message %d", index)
          .containsExactlyElementsOf(toolCallNames);
    }
  }

  record ToolCallResultMessage(String toolCallId, String text)
      implements ProviderWireFormatExpectedMessage {

    @Override
    public void assertMatches(int index, RecordedMessage message) {
      assertRoleAndText(index, message, "tool", text);
      assertThat(message.toolCallId())
          .as("tool_call_id of message %d", index)
          .isEqualTo(toolCallId);
    }
  }

  /**
   * A single expected content part of a {@link UserMessage} (or, in the future, other multimodal
   * roles). {@link Attachment} doesn't assert the part's {@code kind} since providers use different
   * wire vocabulary for it (e.g. OpenAI's {@code image_url} vs. Anthropic/Bedrock/Azure's {@code
   * image}).
   */
  sealed interface ExpectedContentPart {

    void assertMatches(int messageIndex, int partIndex, RecordedContentPart actual);

    static ExpectedContentPart text(String text) {
      return new Text(text);
    }

    static ExpectedContentPart attachment() {
      return new Attachment();
    }

    record Text(String text) implements ExpectedContentPart {

      @Override
      public void assertMatches(int messageIndex, int partIndex, RecordedContentPart actual) {
        assertThat(actual.isText())
            .as("content part %d of message %d is text", partIndex, messageIndex)
            .isTrue();
        assertThat(actual.text())
            .as("text of content part %d of message %d", partIndex, messageIndex)
            .isEqualTo(text);
      }
    }

    record Attachment() implements ExpectedContentPart {

      @Override
      public void assertMatches(int messageIndex, int partIndex, RecordedContentPart actual) {
        assertThat(actual.isText())
            .as("content part %d of message %d is a non-text attachment", partIndex, messageIndex)
            .isFalse();
      }
    }
  }
}

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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.NativeOpenAiResponsesRecordedConversation.RecordedChatRequest;
import org.junit.jupiter.api.Test;

/**
 * Fast, engine-free unit test for {@link NativeOpenAiResponsesRecordedConversation}'s request-body
 * parser: feeds a canned Responses request body (covering {@code instructions}, a user {@code
 * input_text} message, a {@code function_call}, a {@code function_call_output}, a {@code tools[]}
 * entry, and a {@code text.format} json_schema) directly to {@link RecordedChatRequest#parse}, no
 * WireMock/engine involved - the full round-trip through a real conversation is exercised
 * separately by {@link ProviderWireFormatSmokeTests} via {@link
 * NativeOpenAiResponsesWireFormatFixture}.
 */
class NativeOpenAiResponsesRecordedConversationTest {

  private static final String REQUEST_BODY =
      """
      {
        "model": "gpt-5",
        "instructions": "You are a helpful assistant",
        "input": [
          {
            "role": "user",
            "content": [
              {"type": "input_text", "text": "Explore some of your tools!"}
            ]
          },
          {
            "role": "assistant",
            "content": [
              {"type": "input_text", "text": "I will call the superflux calculation tool."}
            ]
          },
          {
            "type": "function_call",
            "call_id": "aaa111",
            "name": "SuperfluxProduct",
            "arguments": "{\\"a\\":5,\\"b\\":3}"
          },
          {
            "type": "function_call_output",
            "call_id": "aaa111",
            "output": "24"
          }
        ],
        "tools": [
          {
            "type": "function",
            "name": "SuperfluxProduct",
            "description": "Multiplies two numbers",
            "parameters": {
              "type": "object",
              "properties": {"a": {"type": "number"}, "b": {"type": "number"}},
              "required": ["a", "b"]
            }
          }
        ],
        "text": {
          "format": {
            "type": "json_schema",
            "name": "HaikuSchema",
            "schema": {
              "type": "object",
              "properties": {"text": {"type": "string"}},
              "required": ["text"]
            },
            "strict": true
          }
        }
      }
      """;

  @Test
  void reconstructsSystemUserAssistantToolCallAndToolResultMessages() {
    final RecordedChatRequest request = RecordedChatRequest.parse(REQUEST_BODY);

    final var messages = request.messages();
    assertThat(messages).hasSize(4);

    assertThat(messages.get(0).role()).isEqualTo("system");
    assertThat(messages.get(0).content()).isEqualTo("You are a helpful assistant");

    assertThat(messages.get(1).role()).isEqualTo("user");
    assertThat(messages.get(1).content()).isNull();
    assertThat(messages.get(1).contentParts()).hasSize(1);
    assertThat(messages.get(1).contentParts().get(0).path("type").asText()).isEqualTo("input_text");
    assertThat(messages.get(1).contentParts().get(0).path("text").asText())
        .isEqualTo("Explore some of your tools!");

    assertThat(messages.get(2).role()).isEqualTo("assistant");
    assertThat(messages.get(2).contentParts().get(0).path("text").asText())
        .isEqualTo("I will call the superflux calculation tool.");
    assertThat(messages.get(2).toolCalls()).hasSize(1);
    assertThat(messages.get(2).toolCalls().get(0).id()).isEqualTo("aaa111");
    assertThat(messages.get(2).toolCalls().get(0).name()).isEqualTo("SuperfluxProduct");
    assertThat(messages.get(2).toolCalls().get(0).argumentsJson()).isEqualTo("{\"a\":5,\"b\":3}");

    assertThat(messages.get(3).role()).isEqualTo("tool");
    assertThat(messages.get(3).content()).isEqualTo("24");
    assertThat(messages.get(3).toolCallId()).isEqualTo("aaa111");
  }

  @Test
  void parsesToolDefinitionsFromToolsArray() {
    final RecordedChatRequest request = RecordedChatRequest.parse(REQUEST_BODY);

    assertThat(request.toolNames()).containsExactly("SuperfluxProduct");

    final var tool = request.toolDefinitions().get(0);
    assertThat(tool.name()).isEqualTo("SuperfluxProduct");
    assertThat(tool.description()).isEqualTo("Multiplies two numbers");
    assertThat(tool.inputSchema()).containsEntry("type", "object");
  }

  @Test
  void parsesResponseFormatFromTextFormat() {
    final RecordedChatRequest request = RecordedChatRequest.parse(REQUEST_BODY);

    final var responseFormat = request.responseFormat();
    assertThat(responseFormat).isPresent();
    assertThat(responseFormat.orElseThrow().type()).isEqualTo("json_schema");
    assertThat(responseFormat.orElseThrow().schemaName()).isEqualTo("HaikuSchema");
    assertThat(responseFormat.orElseThrow().jsonSchema()).containsEntry("type", "object");
  }

  @Test
  void reconstructsToolCallOnlyAssistantTurnWithoutPrecedingText() {
    final var body =
        """
        {
          "model": "gpt-5",
          "instructions": "sys",
          "input": [
            {"role": "user", "content": [{"type": "input_text", "text": "hi"}]},
            {"type": "function_call", "call_id": "call_1", "name": "get_weather", "arguments": "{}"}
          ],
          "tools": [],
          "text": null
        }
        """;

    final RecordedChatRequest request = RecordedChatRequest.parse(body);

    final var messages = request.messages();
    assertThat(messages).hasSize(3);
    assertThat(messages.get(2).role()).isEqualTo("assistant");
    assertThat(messages.get(2).content()).isNull();
    assertThat(messages.get(2).toolCalls()).hasSize(1);
    assertThat(messages.get(2).toolCalls().get(0).name()).isEqualTo("get_weather");
    assertThat(request.responseFormat()).isEmpty();
  }
}

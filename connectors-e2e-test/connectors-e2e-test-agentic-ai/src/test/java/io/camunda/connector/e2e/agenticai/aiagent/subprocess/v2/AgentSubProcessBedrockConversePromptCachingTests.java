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
package io.camunda.connector.e2e.agenticai.aiagent.subprocess.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.bedrock.StreamingBedrockConverseEventStreamChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/**
 * Native-Bedrock-only e2e coverage for prompt caching's wire behavior: {@code cachePoint} blocks
 * are placed one at the end of {@code system[]} (a system prompt is always present here), one at
 * the end of the last message's content, and none in {@code tools[]}.
 */
class AgentSubProcessBedrockConversePromptCachingTests
    extends BaseBedrockConverseNativeSubProcessTest {

  @Test
  void enablePromptCachingAddsCachePointsToTheWire() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    StreamingBedrockConverseEventStreamChatModelStubs.stubConversation(
        TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final Function<ElementTemplate, ElementTemplate> elementTemplateModifier =
        template ->
            template.property("provider.bedrock.model.parameters.promptCaching.enabled", "true");

    awaitProcessCompletion(
        createProcessInstance(elementTemplateModifier, Map.of("userPrompt", userPrompt)));

    final var request = parseBody(soleRecordedRequest());

    assertThat(lastBlockKind(request.path("system")))
        .as("cachePoint at the end of system[]")
        .isEqualTo("cachePoint");
    assertThat(hasCachePoint(request.path("toolConfig").path("tools")))
        .as("no cachePoint anywhere in tools[] when a system prompt is present")
        .isFalse();

    final var messages = request.path("messages");
    assertThat(messages.isArray() && !messages.isEmpty()).as("at least one message").isTrue();
    final var lastMessage = messages.get(messages.size() - 1);
    assertThat(lastBlockKind(lastMessage.path("content")))
        .as("cachePoint at the end of the last message's content")
        .isEqualTo("cachePoint");
  }

  @Test
  void promptCachingDisabledByDefaultLeavesCachePointsOffTheWire() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    StreamingBedrockConverseEventStreamChatModelStubs.stubConversation(
        TurnStub.text("A haiku.", 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    final var request = parseBody(soleRecordedRequest());

    assertThat(hasCachePoint(request.path("system")))
        .as("no cachePoint in system[] when prompt caching is not enabled")
        .isFalse();
    assertThat(hasCachePoint(request.path("toolConfig").path("tools")))
        .as("no cachePoint in tools[] when prompt caching is not enabled")
        .isFalse();
    final var messages = request.path("messages");
    for (final JsonNode message : messages) {
      assertThat(hasCachePoint(message.path("content")))
          .as("no cachePoint in any message content when prompt caching is not enabled")
          .isFalse();
    }
  }

  /** The single field name present on the last block of an array, Bedrock's own kind marker. */
  private static String lastBlockKind(JsonNode blocks) {
    assertThat(blocks.isArray() && !blocks.isEmpty()).as("non-empty block array").isTrue();
    final var lastBlock = blocks.get(blocks.size() - 1);
    final var fieldNames = lastBlock.fieldNames();
    return fieldNames.hasNext() ? fieldNames.next() : "unknown";
  }

  private static boolean hasCachePoint(JsonNode blocks) {
    if (!blocks.isArray()) {
      return false;
    }
    return StreamSupport.stream(blocks.spliterator(), false)
        .anyMatch(block -> !block.path("cachePoint").isMissingNode());
  }
}

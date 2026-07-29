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
package io.camunda.connector.e2e.agenticai.aiagent;

import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestChatModelFactoryConfiguration {

  @Bean
  public TestChatModelFactory testChatModelFactory() {
    return new TestChatModelFactory();
  }

  /**
   * Test double for {@link ChatModelFactory} standing in for a user-supplied custom chat model
   * provider (see {@code CustomProviderConfiguration}). Serves scripted {@link ChatResult}s in
   * order and records every {@link ChatRequest} it receives plus the {@link ChatModelConfiguration}
   * it was resolved with, so e2e tests can drive the full tool-calling loop through the "custom"
   * provider discriminator without any real (or WireMock-stubbed) LLM endpoint.
   */
  @NullMarked
  public static class TestChatModelFactory implements ChatModelFactory {

    public static final String PROVIDER_TYPE = "e2e-custom";
    public static final String MODEL_ID = "e2e-custom-model";

    private final Deque<ChatResult> enqueuedResults = new ConcurrentLinkedDeque<>();

    private final List<ChatModelConfiguration> recordedConfigurations =
        Collections.synchronizedList(new ArrayList<>());
    private final List<ChatRequest> recordedRequests =
        Collections.synchronizedList(new ArrayList<>());

    @Override
    public boolean supports(ChatModelConfiguration configuration) {
      return PROVIDER_TYPE.equals(configuration.provider());
    }

    @Override
    public ChatModel create(ChatModelConfiguration configuration) {
      recordedConfigurations.add(configuration);
      return new ChatModel() {
        @Override
        public ChatResult execute(ChatRequest request) {
          recordedRequests.add(request);
          var result = enqueuedResults.pollFirst();
          if (result == null) {
            throw new IllegalStateException(
                "No enqueued chat result configured for call #" + recordedRequests.size());
          }

          return result;
        }

        @Override
        public void close() {}
      };
    }

    public void enqueue(ChatResult... results) {
      Collections.addAll(enqueuedResults, results);
    }

    public void reset() {
      enqueuedResults.clear();
      recordedConfigurations.clear();
      recordedRequests.clear();
    }

    public List<ChatRequest> recordedRequests() {
      return List.copyOf(recordedRequests);
    }

    public List<ChatModelConfiguration> recordedConfigurations() {
      return List.copyOf(recordedConfigurations);
    }

    public static ChatResult text(String text, int inputTokens, int outputTokens) {
      return new ChatResult.Completed(
          AssistantMessage.builder().content(List.of(TextContent.textContent(text))).build(),
          new AgentMetrics(1, new AgentMetrics.TokenUsage(inputTokens, outputTokens), 0));
    }

    public static ChatResult toolCalls(
        String text, int inputTokens, int outputTokens, ToolCall... toolCalls) {
      return new ChatResult.Completed(
          AssistantMessage.builder()
              .content(List.of(TextContent.textContent(text)))
              .toolCalls(List.of(toolCalls))
              .build(),
          new AgentMetrics(
              1, new AgentMetrics.TokenUsage(inputTokens, outputTokens), toolCalls.length));
    }
  }
}

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
package io.camunda.connector.e2e.agenticai.aiagent.wireformat;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Wire-format regression test for the Anthropic Messages API. Verifies the full HTTP contract
 * between the AI Agent connector and the Anthropic {@code POST /v1/messages} endpoint using
 * WireMock. Runs against LangChain4j initially; once ADR 004 native provider layer ships the same
 * stubs cover the new implementation.
 */
@SlowTest
public class AnthropicMessagesApiAiAgentJobWorkerTests extends BaseWireFormatAiAgentJobWorkerTest {

  @Override
  protected String llmApiPath() {
    return "/v1/messages";
  }

  @Override
  protected Map<String, String> elementTemplateProperties() {
    return Map.ofEntries(
        Map.entry("agentContext", "=agent.context"),
        Map.entry("provider.type", "anthropic"),
        Map.entry("provider.anthropic.endpoint", "http://localhost:" + wireMockPort + "/v1"),
        Map.entry("provider.anthropic.authentication.apiKey", "test-api-key"),
        Map.entry("provider.anthropic.model.model", "claude-sonnet-4-6"),
        Map.entry(
            "data.systemPrompt.prompt",
            "=\"You are a helpful AI assistant. Answer all the questions, but always be nice.\""),
        Map.entry(
            "data.userPrompt.prompt",
            "=if (is defined(followUpUserPrompt)) then followUpUserPrompt else userPrompt"),
        Map.entry("data.userPrompt.documents", "=[]"),
        Map.entry("data.memory.storage.type", "in-process"),
        Map.entry("data.response.includeAssistantMessage", "=true"),
        Map.entry("data.response.includeAgentContext", "=true"));
  }

  @Override
  protected MappingBuilder withApiKeyHeaderMatcher(MappingBuilder stub) {
    return stub.withHeader("x-api-key", equalTo("test-api-key"));
  }

  @Override
  protected String toolCallResponseBody() {
    return """
        {
          "id": "msg_01XBvkPq5k7uHxRo45RsL7Ae",
          "type": "message",
          "role": "assistant",
          "content": [
            {
              "type": "tool_use",
              "id": "toolu_01JnvVF6Cya67WpVoLMDkEaq",
              "name": "SuperfluxProduct",
              "input": {"a": 5, "b": 3}
            }
          ],
          "model": "claude-sonnet-4-6",
          "stop_reason": "tool_use",
          "stop_sequence": null,
          "usage": {
            "input_tokens": 100,
            "output_tokens": 50
          }
        }
        """;
  }

  @Override
  protected String finalResponseBody() {
    return """
        {
          "id": "msg_02YCwlPr6m8vJySp56SsM8Bf",
          "type": "message",
          "role": "assistant",
          "content": [
            {
              "type": "text",
              "text": "%s"
            }
          ],
          "model": "claude-sonnet-4-6",
          "stop_reason": "end_turn",
          "stop_sequence": null,
          "usage": {
            "input_tokens": 200,
            "output_tokens": 30
          }
        }
        """
        .formatted(RESPONSE_TEXT);
  }

  @Test
  void executesAgentWithToolCallAgainstAnthropicMessagesApi() throws Exception {
    final var zeebeTest = runToolCallScenario();

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(EXPECTED_METRICS)
                .hasResponseMessageText(RESPONSE_TEXT)
                .hasResponseText(RESPONSE_TEXT));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
    verify(2, postRequestedFor(urlEqualTo(llmApiPath())));
  }
}

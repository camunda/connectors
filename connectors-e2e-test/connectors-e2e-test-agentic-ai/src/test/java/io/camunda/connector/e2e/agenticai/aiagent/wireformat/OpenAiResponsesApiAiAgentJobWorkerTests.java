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
 * Wire-format regression test for the OpenAI API. Uses the {@code openAiCompatible} provider
 * (OpenAI Chat Completions, {@code POST /v1/chat/completions}) so the test runs against the
 * LangChain4j implementation today. Once ADR 004's native {@code openai-responses} API family
 * ships, this test will be updated to target the Responses API endpoint ({@code POST
 * /v1/responses}) with the corresponding request/response schema.
 */
@SlowTest
public class OpenAiResponsesApiAiAgentJobWorkerTests extends BaseWireFormatAiAgentJobWorkerTest {

  @Override
  protected String llmApiPath() {
    return "/v1/chat/completions";
  }

  @Override
  protected Map<String, String> elementTemplateProperties() {
    return Map.ofEntries(
        Map.entry("agentContext", "=agent.context"),
        Map.entry("provider.type", "openaiCompatible"),
        Map.entry("provider.openaiCompatible.endpoint", "http://localhost:" + wireMockPort + "/v1"),
        Map.entry("provider.openaiCompatible.authentication.apiKey", "test-api-key"),
        Map.entry("provider.openaiCompatible.model.model", "gpt-4o"),
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
    return stub.withHeader("Authorization", equalTo("Bearer test-api-key"));
  }

  @Override
  protected String toolCallResponseBody() {
    return """
        {
          "id": "chatcmpl-abc123",
          "object": "chat.completion",
          "created": 1728933352,
          "model": "gpt-4o",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": null,
                "tool_calls": [
                  {
                    "id": "call_xyz789",
                    "type": "function",
                    "function": {
                      "name": "SuperfluxProduct",
                      "arguments": "{\\"a\\": 5, \\"b\\": 3}"
                    }
                  }
                ]
              },
              "finish_reason": "tool_calls"
            }
          ],
          "usage": {
            "prompt_tokens": 100,
            "completion_tokens": 50,
            "total_tokens": 150
          }
        }
        """;
  }

  @Override
  protected String finalResponseBody() {
    return """
        {
          "id": "chatcmpl-def456",
          "object": "chat.completion",
          "created": 1728933353,
          "model": "gpt-4o",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "%s"
              },
              "finish_reason": "stop"
            }
          ],
          "usage": {
            "prompt_tokens": 200,
            "completion_tokens": 30,
            "total_tokens": 230
          }
        }
        """
        .formatted(RESPONSE_TEXT);
  }

  @Test
  void executesAgentWithToolCallAgainstOpenAiChatCompletionsApi() throws Exception {
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

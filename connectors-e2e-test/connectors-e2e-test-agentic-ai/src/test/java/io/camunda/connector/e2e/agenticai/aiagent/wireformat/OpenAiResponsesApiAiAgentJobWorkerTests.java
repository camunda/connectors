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
 * Wire-format regression test for the OpenAI Responses endpoint ({@code POST /v1/responses}).
 * Drives the {@code openai} discriminator with {@code apiFamily = responses}, exercising the native
 * {@code OpenAiResponsesChatModelApi} end-to-end against a WireMock-stubbed responses API.
 */
@SlowTest
public class OpenAiResponsesApiAiAgentJobWorkerTests extends BaseWireFormatAiAgentJobWorkerTest {

  @Override
  protected String llmApiPath() {
    return "/v1/responses";
  }

  @Override
  protected Map<String, String> elementTemplateProperties() {
    return Map.ofEntries(
        Map.entry("agentContext", "=agent.context"),
        Map.entry("provider.type", "openai"),
        Map.entry("provider.openai.apiFamily", "responses"),
        Map.entry("provider.openai.endpoint", "http://localhost:" + wireMockPort + "/v1"),
        Map.entry("provider.openai.authentication.apiKey", "test-api-key"),
        Map.entry("provider.openai.model.model", "gpt-5"),
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
          "id": "resp_abc123",
          "object": "response",
          "created_at": 1728933352,
          "status": "completed",
          "model": "gpt-5",
          "parallel_tool_calls": true,
          "tool_choice": "auto",
          "tools": [],
          "output": [
            {
              "type": "function_call",
              "call_id": "call_xyz789",
              "name": "SuperfluxProduct",
              "arguments": "{\\"a\\": 5, \\"b\\": 3}"
            }
          ],
          "usage": {
            "input_tokens": 100,
            "output_tokens": 50,
            "total_tokens": 150,
            "input_tokens_details": { "cached_tokens": 0 },
            "output_tokens_details": { "reasoning_tokens": 0 }
          }
        }
        """;
  }

  @Override
  protected String finalResponseBody() {
    return """
        {
          "id": "resp_def456",
          "object": "response",
          "created_at": 1728933353,
          "status": "completed",
          "model": "gpt-5",
          "parallel_tool_calls": true,
          "tool_choice": "auto",
          "tools": [],
          "output": [
            {
              "type": "message",
              "id": "msg_1",
              "status": "completed",
              "role": "assistant",
              "content": [
                { "type": "output_text", "text": "%s", "annotations": [] }
              ]
            }
          ],
          "usage": {
            "input_tokens": 200,
            "output_tokens": 30,
            "total_tokens": 230,
            "input_tokens_details": { "cached_tokens": 0 },
            "output_tokens_details": { "reasoning_tokens": 0 }
          }
        }
        """
        .formatted(RESPONSE_TEXT);
  }

  @Test
  void executesAgentWithToolCallAgainstOpenAiResponsesApi() throws Exception {
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

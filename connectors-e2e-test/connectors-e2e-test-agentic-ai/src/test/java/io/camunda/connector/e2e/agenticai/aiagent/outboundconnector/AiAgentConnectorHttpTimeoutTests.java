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
package io.camunda.connector.e2e.agenticai.aiagent.outboundconnector;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_TASK_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.process.test.api.CamundaAssert;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * E2E coverage for HTTP transport timeouts on all chat model providers that we configure HTTP
 * clients for. For each provider we run two scenarios:
 *
 * <ul>
 *   <li><b>Positive</b>: WireMock responds within the configured socket timeout — the process
 *       completes and the agent variable contains the simulated assistant message.
 *   <li><b>Negative</b>: WireMock delays beyond the configured socket timeout — the connector fails
 *       fast and Zeebe raises an incident on the AI agent task.
 * </ul>
 *
 * <p>Providers tested:
 *
 * <ul>
 *   <li>Anthropic: exercises the JDK HTTP client path (read timeout).
 *   <li>OpenAI-compatible: same JDK HTTP client path with a different endpoint shape.
 *   <li>Bedrock: exercises the Apache HTTP client path (socket timeout) used by the AWS SDK.
 * </ul>
 *
 * <p>Providers that are currently untested:
 *
 * <ul>
 *   <li>OpenAI: There is currently no option to override the OpenAI default API URL
 *   <li>Azure OpenAI: Due to current implementation we are unable to call HTTP urls using native
 *       Azure SDK during Authorization Header generation.
 * </ul>
 *
 * <p>Regression test for <a href="https://github.com/camunda/connectors/issues/7193">issue
 * #7193</a>: before the fix, transport-level defaults (Apache 30s socket timeout) would fire
 * regardless of the connector-level timeout configuration.
 */
@SlowTest
@WireMockTest
public class AiAgentConnectorHttpTimeoutTests extends BaseAiAgentConnectorTest {

  private static final String AGENT_RESPONSE_TEXT = "Endless waves whisper.";

  /**
   * WireMock response delay used in positive cases: must be shorter than {@link #MODEL_TIMEOUT}.
   */
  private static final Duration RESPONSE_DELAY_BELOW_TIMEOUT = Duration.ofSeconds(3);

  /** WireMock response delay used in negative cases: must be longer than {@link #MODEL_TIMEOUT}. */
  private static final Duration RESPONSE_DELAY_ABOVE_TIMEOUT = Duration.ofSeconds(8);

  /** Connector-level model call timeout: short enough to keep negative cases under ~10s. */
  private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(6);

  /** This test configures its own providers — skip the base's openaiCompatible redirect. */
  @Override
  protected ElementTemplate withOpenAiCompatibleProvider(ElementTemplate template) {
    return template;
  }

  @BeforeEach
  void setupCamundaAssertTimeout() {
    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(30));
  }

  @Nested
  class AnthropicTests {

    @Test
    void processCompletesWhenResponseArrivesWithinSocketTimeout() {
      stubAnthropic(RESPONSE_DELAY_BELOW_TIMEOUT);
      runPositiveCase(anthropicProvider());
    }

    @Test
    void raisesIncidentWhenResponseExceedsSocketTimeout() {
      stubAnthropic(RESPONSE_DELAY_ABOVE_TIMEOUT);
      runNegativeCase(anthropicProvider());
    }
  }

  @Nested
  class OpenAiCompatibleTests {

    @Test
    void processCompletesWhenResponseArrivesWithinSocketTimeout() {
      stubOpenAiCompatible(RESPONSE_DELAY_BELOW_TIMEOUT);
      runPositiveCase(openAiCompatibleProvider());
    }

    @Test
    void raisesIncidentWhenResponseExceedsSocketTimeout() {
      stubOpenAiCompatible(RESPONSE_DELAY_ABOVE_TIMEOUT);
      runNegativeCase(openAiCompatibleProvider());
    }
  }

  @Nested
  class BedrockTest {

    @Test
    void processCompletesWhenResponseArrivesWithinSocketTimeout() {
      stubBedrock(RESPONSE_DELAY_BELOW_TIMEOUT);
      runPositiveCase(bedrockProvider());
    }

    @Test
    void raisesIncidentWhenResponseExceedsSocketTimeout() {
      stubBedrock(RESPONSE_DELAY_ABOVE_TIMEOUT);
      runNegativeCase(bedrockProvider());
    }
  }

  // ---------------------------------------------------------------------------
  // Test runners
  // ---------------------------------------------------------------------------

  private void runPositiveCase(Function<ElementTemplate, ElementTemplate> providerConfig) {
    try {
      enqueueUserFeedback(userSatisfiedFeedback());

      final ZeebeTest zeebeTest =
          createProcessInstance(providerConfig, Map.of("userPrompt", "Write a haiku about the sea"))
              .waitForProcessCompletion();

      assertAgentResponse(
          zeebeTest,
          agentResponse ->
              AgentResponseAssert.assertThat(agentResponse).hasResponseText(AGENT_RESPONSE_TEXT));
      assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(1);
    } catch (Exception exception) {
      fail(exception);
    }
  }

  private void runNegativeCase(Function<ElementTemplate, ElementTemplate> providerConfig) {
    try {
      final ZeebeTest zeebeTest =
          createProcessInstance(providerConfig, Map.of("userPrompt", "Write a haiku about the sea"))
              .waitForActiveIncidents();

      assertIncident(
          zeebeTest,
          incident -> {
            assertThat(incident.getElementId()).isEqualTo(AI_AGENT_TASK_ID);
            assertThat(incident.getErrorMessage())
                .containsPattern(Pattern.compile("timed out|timeout"));
            assertThat(incident.getErrorMessage()).contains("FAILED_MODEL_CALL");
          });
      assertThat(userFeedbackJobWorkerCounter.get())
          .as("user feedback must not be reached on a timeout failure")
          .isZero();
    } catch (Exception exception) {
      fail(exception);
    }
  }

  // ---------------------------------------------------------------------------
  // Provider element template configurations
  // ---------------------------------------------------------------------------

  private Function<ElementTemplate, ElementTemplate> anthropicProvider() {
    return template ->
        template
            .property("retryCount", "1")
            .property("provider.type", "anthropic")
            .property("provider.anthropic.endpoint", wireMock.getHttpBaseUrl() + "/v1")
            .property("provider.anthropic.authentication.apiKey", "dummy")
            .property("provider.anthropic.model.model", "claude-3-5-sonnet")
            .property("provider.anthropic.timeouts.timeout", MODEL_TIMEOUT.toString());
  }

  private Function<ElementTemplate, ElementTemplate> openAiCompatibleProvider() {
    return template ->
        template
            .property("retryCount", "1")
            .property("provider.type", "openaiCompatible")
            .property("provider.openaiCompatible.endpoint", wireMock.getHttpBaseUrl() + "/v1")
            .property("provider.openaiCompatible.authentication.apiKey", "dummy")
            .property("provider.openaiCompatible.model.model", "test-model")
            .property("provider.openaiCompatible.timeouts.timeout", MODEL_TIMEOUT.toString());
  }

  private Function<ElementTemplate, ElementTemplate> bedrockProvider() {
    return template ->
        template
            .property("retryCount", "1")
            .property("provider.type", "bedrock")
            .property("provider.bedrock.region", "us-east-1")
            .property("provider.bedrock.endpoint", wireMock.getHttpBaseUrl())
            .property("provider.bedrock.authentication.type", "credentials")
            .property("provider.bedrock.authentication.accessKey", "dummy")
            .property("provider.bedrock.authentication.secretKey", "dummy")
            .property("provider.bedrock.model.model", "anthropic.claude-3-haiku-20240307-v1:0")
            .property("provider.bedrock.timeouts.timeout", MODEL_TIMEOUT.toString());
  }

  // ---------------------------------------------------------------------------
  // WireMock stubs: return provider-shaped responses with the requested delay
  // ---------------------------------------------------------------------------

  private void stubAnthropic(Duration delay) {
    stubFor(
        post(urlPathEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withFixedDelay((int) delay.toMillis())
                    .withBody(
                        """
                        {
                          "id": "msg_test",
                          "type": "message",
                          "role": "assistant",
                          "content": [{"type": "text", "text": "%s"}],
                          "model": "claude-3-5-sonnet",
                          "stop_reason": "end_turn",
                          "stop_sequence": null,
                          "usage": {"input_tokens": 10, "output_tokens": 20}
                        }
                        """
                            .formatted(AGENT_RESPONSE_TEXT))));
  }

  private void stubOpenAiCompatible(Duration delay) {
    stubFor(
        post(urlPathEqualTo("/v1/chat/completions"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withFixedDelay((int) delay.toMillis())
                    .withBody(
                        """
                        {
                          "id": "chatcmpl-test",
                          "object": "chat.completion",
                          "created": 1700000000,
                          "model": "test-model",
                          "choices": [
                            {
                              "index": 0,
                              "message": {"role": "assistant", "content": "%s"},
                              "finish_reason": "stop"
                            }
                          ],
                          "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30}
                        }
                        """
                            .formatted(AGENT_RESPONSE_TEXT))));
  }

  private void stubBedrock(Duration delay) {
    // Bedrock Converse: POST /model/{modelId}/converse - the SDK URL-encodes the model id, so we
    // match any model path here for simplicity
    stubFor(
        post(urlMatching("/model/.+/converse"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withFixedDelay((int) delay.toMillis())
                    .withBody(
                        """
                        {
                          "metrics": {"latencyMs": 100},
                          "output": {
                            "message": {
                              "role": "assistant",
                              "content": [{"text": "%s"}]
                            }
                          },
                          "stopReason": "end_turn",
                          "usage": {"inputTokens": 10, "outputTokens": 20, "totalTokens": 30}
                        }
                        """
                            .formatted(AGENT_RESPONSE_TEXT))));
  }
}

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
package io.camunda.connector.e2e.agenticai.aiagent.task;

import static io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures.AI_AGENT_ELEMENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.StreamingAnthropicMessagesSseChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.bedrock.StreamingBedrockConverseEventStreamChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
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
 *   <li>Anthropic: exercises the native Anthropic SDK's streaming transport read timeout.
 *   <li>OpenAI: exercises the native OpenAI SDK's streaming transport read timeout.
 *   <li>Bedrock: exercises the native AWS SDK's streaming transport socket timeout.
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
public class AgentTaskHttpTimeoutTests extends BaseAgentTaskTest {

  private static final String AGENT_RESPONSE_TEXT = "Endless waves whisper.";

  /**
   * WireMock response delay used in positive cases: must be shorter than {@link #MODEL_TIMEOUT}.
   */
  private static final Duration RESPONSE_DELAY_BELOW_TIMEOUT = Duration.ofSeconds(1);

  /** WireMock response delay used in negative cases: must be longer than {@link #MODEL_TIMEOUT}. */
  private static final Duration RESPONSE_DELAY_ABOVE_TIMEOUT = Duration.ofSeconds(5);

  /** Connector-level model call timeout: short enough to keep negative cases under ~10s. */
  private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(3);

  /** This test configures its own providers — skip the base's default provider redirect. */
  @Override
  protected Function<ElementTemplate, ElementTemplate> providerConfigurer() {
    return Function.identity();
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
  class OpenAiTests {

    @Test
    void processCompletesWhenResponseArrivesWithinSocketTimeout() {
      OpenAiCompletionsChatModelStubs.stubConversation(
          Turn.text(AGENT_RESPONSE_TEXT, 10, 20).withRequestDelay(RESPONSE_DELAY_BELOW_TIMEOUT));
      runPositiveCase(openAiProvider());
    }

    @Test
    void raisesIncidentWhenResponseExceedsSocketTimeout() {
      OpenAiCompletionsChatModelStubs.stubConversation(
          Turn.text(AGENT_RESPONSE_TEXT, 10, 20).withRequestDelay(RESPONSE_DELAY_ABOVE_TIMEOUT));
      runNegativeCase(openAiProvider());
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
          awaitProcessCompletion(
              createProcessInstance(
                  providerConfig, Map.of("userPrompt", "Write a haiku about the sea")));

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
          awaitActiveIncidents(
              createProcessInstance(
                  providerConfig, Map.of("userPrompt", "Write a haiku about the sea")));

      assertIncident(
          zeebeTest,
          incident -> {
            assertThat(incident.getElementId()).isEqualTo(AI_AGENT_ELEMENT_ID);
            // Bedrock's SDK reports "timed out" directly; Anthropic/OpenAI wrap the socket timeout
            // in a generic "Request failed" IO exception - either still proves a timeout failure.
            assertThat(incident.getErrorMessage())
                .containsPattern(Pattern.compile("timed out|timeout|Request failed"));
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
            .property("provider.anthropic.backend.type", "custom")
            .property("provider.anthropic.backend.custom.endpoint", wireMock.getHttpBaseUrl())
            .property("provider.anthropic.backend.custom.authentication.type", "apiKey")
            .property("provider.anthropic.backend.custom.authentication.apiKey", "dummy")
            .property("provider.anthropic.model.model", "claude-3-5-sonnet")
            .property("provider.anthropic.timeouts.timeout", MODEL_TIMEOUT.toString());
  }

  private Function<ElementTemplate, ElementTemplate> openAiProvider() {
    return template ->
        template
            .property("retryCount", "1")
            .property("provider.type", "openai")
            .property("provider.openai.api.type", "completions")
            .property("provider.openai.backend.type", "openai-api")
            .property("provider.openai.backend.openai.endpoint", wireMock.getHttpBaseUrl() + "/v1")
            .property("provider.openai.backend.openai.apiKey", "dummy")
            .property("provider.openai.model.model", "test-model")
            .property("provider.openai.timeouts.timeout", MODEL_TIMEOUT.toString());
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
            .property("provider.bedrock.model.model", "test-model")
            .property("provider.bedrock.timeouts.timeout", MODEL_TIMEOUT.toString());
  }

  // ---------------------------------------------------------------------------
  // WireMock stubs: return provider-shaped streaming responses with the requested delay, applied
  // via `withFixedDelay` to model a slow/hanging transport.
  // ---------------------------------------------------------------------------

  private void stubAnthropic(Duration delay) {
    StreamingAnthropicMessagesSseChatModelStubs.stubConversation(
        delay, TurnStub.text(AGENT_RESPONSE_TEXT, 10, 20));
  }

  private void stubBedrock(Duration delay) {
    StreamingBedrockConverseEventStreamChatModelStubs.stubConversation(
        delay, TurnStub.text(AGENT_RESPONSE_TEXT, 10, 20));
  }
}

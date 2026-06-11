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
package io.camunda.connector.e2e.agenticai.aiagent.common;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aArtifact;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTaskStatus;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.process.test.api.CamundaAssert;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

public class AiAgentA2aIntegrationTestSupport {

  private final Resource a2aSystemPromptResource;
  private final ObjectMapper objectMapper;

  public AiAgentA2aIntegrationTestSupport(
      Resource a2aSystemPromptResource, ObjectMapper objectMapper) {
    this.a2aSystemPromptResource = a2aSystemPromptResource;
    this.objectMapper = objectMapper;
  }

  private final A2aMessage travelAgentResponse =
      A2aMessage.builder()
          .role(A2aMessage.Role.AGENT)
          .messageId("msg-123")
          .contextId("ctx-123")
          .contents(List.of(TextContent.textContent("Hotel room booked successfully!")))
          .build();

  private final A2aTask weatherAgentResponse =
      A2aTask.builder()
          .id("task-001")
          .contextId("ctx-001")
          .status(
              A2aTaskStatus.builder()
                  .state(A2aTaskStatus.TaskState.COMPLETED)
                  .timestamp(OffsetDateTime.parse("2024-03-15T10:11:00Z"))
                  .build())
          .artifacts(
              List.of(
                  A2aArtifact.builder()
                      .artifactId("art-001")
                      .name("WeatherInfo")
                      .contents(List.of(TextContent.textContent("It's sunny in London!")))
                      .build()))
          .history(
              List.of(
                  A2aMessage.builder()
                      .role(A2aMessage.Role.USER)
                      .messageId("msg-001")
                      .contextId("ctx-001")
                      .taskId("task-001")
                      .contents(List.of(TextContent.textContent("What's the weather in London?")))
                      .build()))
          .build();

  private final A2aTask exchangeRateAgentResponse =
      A2aTask.builder()
          .id("task-001")
          .contextId("ctx-001")
          .status(
              A2aTaskStatus.builder()
                  .state(A2aTaskStatus.TaskState.COMPLETED)
                  .timestamp(OffsetDateTime.parse("2024-03-15T10:11:00Z"))
                  .build())
          .artifacts(
              List.of(
                  A2aArtifact.builder()
                      .artifactId("art-001")
                      .name("ExchangeRate")
                      .contents(List.of(TextContent.textContent("1 USD = 0.85 EUR")))
                      .build()))
          .history(
              List.of(
                  A2aMessage.builder()
                      .role(A2aMessage.Role.USER)
                      .messageId("msg-001")
                      .contextId("ctx-001")
                      .taskId("task-001")
                      .contents(
                          List.of(
                              TextContent.textContent("What's the exchange rate for USD to EUR?")))
                      .build()))
          .build();

  public final String initialUserPrompt = "Explore some of your normal and A2A tools!";

  // ---------------------------------------------------------------------------
  // Framework-agnostic conversation record
  // ---------------------------------------------------------------------------

  /**
   * Precomputed strings extracted from the expected A2A conversation. No framework-specific message
   * types are exposed — callers receive plain strings suitable for building WireMock stubs and
   * wire-level assertions.
   */
  public record A2aExpectedConversation(
      String aiToolCallMessageText,
      String superfluxToolResult,
      String travelAgentToolResult,
      String weatherAgentToolResult,
      String exchangeRateAgentToolResult,
      String secondAiMessageText,
      String finalAiMessageText) {}

  public A2aExpectedConversation getExpectedConversation() throws JsonProcessingException {
    final var travelResult = objectMapper.writeValueAsString(travelAgentResponse);
    final var weatherResult = objectMapper.writeValueAsString(weatherAgentResponse);
    final var exchangeRateResult = objectMapper.writeValueAsString(exchangeRateAgentResponse);

    return new A2aExpectedConversation(
        "The user asked me to call some of my normal and A2A tools. I will call SuperfluxProduct and A2A_Travel_Agent as they look interesting to me.",
        "39",
        travelResult,
        weatherResult,
        exchangeRateResult,
        """
            I called some of my normal and A2A tools and got the following results:
            SuperfluxProduct: 39
            A2A_Travel_Agent: %s
            A2A_Weather_Agent: %s
            A2A_Exchange_Rate_Agent: %s"""
            .formatted(travelResult, weatherResult, exchangeRateResult),
        "No.");
  }

  /**
   * Returns the system prompt augmented with A2A-specific instructions read from the resource file.
   */
  public String augmentedSystemPrompt(String basePrompt) {
    try {
      final var a2aInstructions =
          StreamUtils.copyToString(
              a2aSystemPromptResource.getInputStream(), StandardCharsets.UTF_8);
      return basePrompt + "\n\n" + a2aInstructions;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // ---------------------------------------------------------------------------
  // Assertion helpers
  // ---------------------------------------------------------------------------

  public void assertCompletedElementsForDiscovery(ZeebeTest zeebeTest) {
    CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
        .hasCompletedElements("AI_Agent", "Travel_Agent", "Weather_Agent", "User_Feedback");
  }

  // ---------------------------------------------------------------------------
  // WireMock stub setup
  // ---------------------------------------------------------------------------

  public void setUpWireMockStubs(
      WireMockRuntimeInfo wireMock, Function<String, String> testFileContentSupplier) {
    stubFor(
        get(urlPathEqualTo("/.well-known/agent-card.json"))
            .willReturn(
                aResponse()
                    .withBody(
                        testFileContentSupplier
                            .apply("travel-agent-card.json")
                            .formatted(wireMock.getHttpBaseUrl()))));

    stubFor(
        get(urlPathEqualTo("/agents/agent-card.json"))
            .willReturn(
                aResponse()
                    .withBody(
                        testFileContentSupplier
                            .apply("weather-agent-card.json")
                            .formatted(wireMock.getHttpBaseUrl()))));

    stubFor(
        get(urlPathEqualTo("/finance/agents/agent-card.json"))
            .willReturn(
                aResponse()
                    .withBody(
                        testFileContentSupplier
                            .apply("exchange-rate-agent-card.json")
                            .formatted(wireMock.getHttpBaseUrl()))));

    stubFor(
        post(urlPathEqualTo("/travel-agent"))
            .withRequestBody(WireMock.containing("message/send"))
            .willReturn(
                aResponse()
                    .withBody(
                        testFileContentSupplier.apply("travel-agent-response-message.json"))));

    stubFor(
        post(urlPathEqualTo("/weather-agent"))
            .withRequestBody(WireMock.containing("message/send"))
            .willReturn(
                aResponse()
                    .withBody(
                        testFileContentSupplier.apply("weather-agent-response-submitted.json"))));
    stubFor(
        post(urlPathEqualTo("/weather-agent"))
            .withRequestBody(WireMock.containing("tasks/get"))
            .willReturn(
                aResponse()
                    .withBody(
                        testFileContentSupplier.apply("weather-agent-response-completed.json"))));

    stubFor(
        post(urlPathEqualTo("/exchange-rate-agent"))
            .withRequestBody(WireMock.containing("message/send"))
            .willReturn(
                aResponse()
                    .withBody(testFileContentSupplier.apply("exchange-rate-agent-response.json"))));
  }
}

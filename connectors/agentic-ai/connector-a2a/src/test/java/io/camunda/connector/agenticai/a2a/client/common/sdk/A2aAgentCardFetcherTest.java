/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.agenticai.a2a.client.common.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.a2a.client.common.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.common.model.A2aConnectionConfiguration;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aAgentCard;
import io.camunda.connector.agenticai.a2a.client.common.sdk.http.A2aHttpClientFactory;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.spec.A2AClientError;
import org.a2aproject.sdk.spec.A2AClientJSONError;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.MockedStatic;

class A2aAgentCardFetcherTest {
  private static final Map<String, String> VERSION_HEADER = Map.of("A2A-Version", "1.0");

  public static final String AGENT_URL = "https://a2a.example.com";
  public static final List<String> DEFAULT_INPUT_MODES = List.of("text");
  public static final List<String> DEFAULT_OUTPUT_MODES = List.of("application/json");

  private final A2AHttpClient mockHttpClient = mock(A2AHttpClient.class);
  private final A2aAgentCardFetcher agentCardFetcher =
      new A2aAgentCardFetcherImpl(mockHttpClientFactory());

  private A2aHttpClientFactory mockHttpClientFactory() {
    A2aHttpClientFactory factory = mock(A2aHttpClientFactory.class);
    when(factory.createHttpClient()).thenReturn(mockHttpClient);
    return factory;
  }

  @Test
  void fetchAgentCardWithCustomLocation() {
    final var location = "abc/agent.json";
    final var request = buildConfiguration(location);
    final var agentCard = createAgentCard(null, null);

    try (MockedStatic<A2A> a2aStatic = mockStatic(A2A.class)) {
      a2aStatic
          .when(() -> A2A.getAgentCard(any(A2AHttpClient.class), anyString(), any(), anyMap()))
          .thenReturn(agentCard);

      final var result = agentCardFetcher.fetchAgentCard(request);

      a2aStatic.verify(() -> A2A.getAgentCard(mockHttpClient, AGENT_URL, location, VERSION_HEADER));
      assertAgentCard(result, DEFAULT_INPUT_MODES, DEFAULT_OUTPUT_MODES);
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  void fetchAgentCardWithoutCustomLocation(String agentCardLocation) {
    final var request = buildConfiguration(agentCardLocation);
    final var agentCard = createAgentCard(null, null);

    try (MockedStatic<A2A> a2aStatic = mockStatic(A2A.class)) {
      a2aStatic
          .when(() -> A2A.getAgentCard(any(A2AHttpClient.class), anyString(), any(), anyMap()))
          .thenReturn(agentCard);

      final var result = agentCardFetcher.fetchAgentCard(request);

      a2aStatic.verify(() ->
          A2A.getAgentCard(
              mockHttpClient, AGENT_URL, ".well-known/agent-card.json", VERSION_HEADER));
      assertAgentCard(result, DEFAULT_INPUT_MODES, DEFAULT_OUTPUT_MODES);
    }
  }

  @Test
  void fetchAgentCardWithSkillsInputAndOutputMode() {
    List<String> inputModes = List.of("text", "application/json");
    List<String> outputModes = List.of("image/jpeg");
    final var request = buildConfiguration(null);
    final var agentCard = createAgentCard(inputModes, outputModes);

    try (MockedStatic<A2A> a2aStatic = mockStatic(A2A.class)) {
      a2aStatic
          .when(() -> A2A.getAgentCard(any(A2AHttpClient.class), anyString(), any(), anyMap()))
          .thenReturn(agentCard);

      final var result = agentCardFetcher.fetchAgentCard(request);

      a2aStatic.verify(() ->
          A2A.getAgentCard(
              mockHttpClient, AGENT_URL, ".well-known/agent-card.json", VERSION_HEADER));
      assertAgentCard(result, inputModes, outputModes);
    }
  }

  private A2aConnectionConfiguration buildConfiguration(String agentCardLocation) {
    return new A2aConnectionConfiguration(AGENT_URL, agentCardLocation);
  }

  private AgentCard createAgentCard(List<String> skillInputModes, List<String> skillOutputModes) {
    return AgentCard.builder()
        .name("Travel agent")
        .description("Helps with travel bookings")
        .version("1.0.0")
        .documentationUrl("http://example.com/docs")
        .capabilities(AgentCapabilities.builder().streaming(true).pushNotifications(true).build())
        .defaultInputModes(DEFAULT_INPUT_MODES)
        .defaultOutputModes(DEFAULT_OUTPUT_MODES)
        .skills(
            List.of(
                AgentSkill.builder()
                    .id("hotel-booking")
                    .name("Hotel Booking")
                    .description("Book a hotel room")
                    .tags(List.of("booking", "hotel"))
                    .examples(List.of("Book a single room", "Book a double room"))
                    .inputModes(skillInputModes)
                    .outputModes(skillOutputModes)
                    .build()))
        .supportedInterfaces(
            List.of(
                new AgentInterface(TransportProtocol.JSONRPC.asString(), "http://localhost:9999")))
        .build();
  }

  @Test
  void shouldFailWithClearErrorWhenAgentCardCannotBeParsed() {
    final var request = buildConfiguration(null);

    try (MockedStatic<A2A> a2aStatic = mockStatic(A2A.class)) {
      a2aStatic
          .when(() -> A2A.getAgentCard(any(A2AHttpClient.class), anyString(), any(), anyMap()))
          .thenThrow(new A2AClientJSONError("supportedInterfaces missing"));

      assertThatThrownBy(() -> agentCardFetcher.fetchAgentCard(request))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue(
              "errorCode", "ERROR_CODE_A2A_CLIENT_AGENT_CARD_RETRIEVAL_FAILED")
          .hasMessageContaining("A2A 0.3 agent cards are not supported");
    }
  }

  @Test
  void shouldFailWithClearErrorWhenAgentCardHasNoSupportedInterfaces() {
    final var request = buildConfiguration(null);
    final var card = mock(AgentCard.class);
    when(card.supportedInterfaces()).thenReturn(List.of());

    try (MockedStatic<A2A> a2aStatic = mockStatic(A2A.class)) {
      a2aStatic
          .when(() -> A2A.getAgentCard(any(A2AHttpClient.class), anyString(), any(), anyMap()))
          .thenReturn(card);

      assertThatThrownBy(() -> agentCardFetcher.fetchAgentCard(request))
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("no supportedInterfaces")
          .hasMessageContaining("A2A 0.3 agent cards are not supported");
    }
  }

  @Test
  void shouldWrapA2aClientErrorInConnectorException() {
    final var location = "invalid/path.json";
    final var request = buildConfiguration(location);
    final var expectedException = new A2AClientError("Agent card not found");

    try (MockedStatic<A2A> a2aStatic = mockStatic(A2A.class)) {
      a2aStatic
          .when(() -> A2A.getAgentCard(any(A2AHttpClient.class), anyString(), any(), anyMap()))
          .thenThrow(expectedException);

      assertThatThrownBy(() -> agentCardFetcher.fetchAgentCard(request))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue(
              "errorCode", "ERROR_CODE_A2A_CLIENT_AGENT_CARD_RETRIEVAL_FAILED")
          .hasMessageContaining("Failed to load agent card from " + location)
          .hasCause(expectedException);
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldIncludeDefaultPathInErrorMessageWhenLocationIsNullOrEmpty(String agentCardLocation) {
    final var request = buildConfiguration(agentCardLocation);
    final var expectedException = new A2AClientError("Agent card not found");

    try (MockedStatic<A2A> a2aStatic = mockStatic(A2A.class)) {
      a2aStatic
          .when(() -> A2A.getAgentCard(any(A2AHttpClient.class), anyString(), any(), anyMap()))
          .thenThrow(expectedException);

      assertThatThrownBy(() -> agentCardFetcher.fetchAgentCard(request))
          .isInstanceOf(ConnectorException.class)
          .hasFieldOrPropertyWithValue(
              "errorCode", "ERROR_CODE_A2A_CLIENT_AGENT_CARD_RETRIEVAL_FAILED")
          .hasMessageContaining("Failed to load agent card from .well-known/agent-card.json")
          .hasCause(expectedException);
    }
  }

  private void assertAgentCard(
      A2aAgentCard agentCardResult, List<String> inputModes, List<String> outputModes) {
    assertThat(agentCardResult.name()).isEqualTo("Travel agent");
    assertThat(agentCardResult.description()).isEqualTo("Helps with travel bookings");
    assertThat(agentCardResult.skills())
        .containsExactly(
            A2aAgentCard.AgentSkill.builder()
                .id("hotel-booking")
                .name("Hotel Booking")
                .description("Book a hotel room")
                .tags(List.of("booking", "hotel"))
                .examples(List.of("Book a single room", "Book a double room"))
                .inputModes(inputModes)
                .outputModes(outputModes)
                .build());
  }
}

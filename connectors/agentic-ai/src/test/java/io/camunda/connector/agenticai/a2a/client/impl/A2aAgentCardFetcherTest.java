/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.agenticai.a2a.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mockStatic;

import io.a2a.A2A;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.AgentSkill;
import io.a2a.spec.TransportProtocol;
import io.camunda.connector.agenticai.a2a.client.api.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.model.A2aRequest.A2aRequestData.ConnectionConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aAgentCardResult;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.MockedStatic;

class A2aAgentCardFetcherTest {

  public static final String AGENT_URL = "https://a2a.example.com";
  public static final List<String> DEFAULT_INPUT_MODES = List.of("text");
  public static final List<String> DEFAULT_OUTPUT_MODES = List.of("application/json");

  private final A2aAgentCardFetcher agentCardFetcher = new A2aAgentCardFetcherImpl();

  @Test
  void fetchAgentCardWithCustomLocation() {
    final var location = "abc/agent.json";
    final var request = buildConfiguration(location);
    final var agentCard = createAgentCard(null, null);

    try (MockedStatic<A2A> a2aStatic = mockStatic(A2A.class)) {
      a2aStatic.when(() -> A2A.getAgentCard(anyString(), any(), anyMap())).thenReturn(agentCard);

      final var result = agentCardFetcher.fetchAgentCard(request);

      a2aStatic.verify(() -> A2A.getAgentCard(AGENT_URL, location, Collections.emptyMap()));
      assertAgentCard(result, DEFAULT_INPUT_MODES, DEFAULT_OUTPUT_MODES);
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  void fetchAgentCardWithoutCustomLocation(String agentCardLocation) {
    final var request = buildConfiguration(agentCardLocation);
    final var agentCard = createAgentCard(null, null);

    try (MockedStatic<A2A> a2aStatic = mockStatic(A2A.class)) {
      a2aStatic.when(() -> A2A.getAgentCard(anyString(), any(), anyMap())).thenReturn(agentCard);

      final var result = agentCardFetcher.fetchAgentCard(request);

      a2aStatic.verify(() -> A2A.getAgentCard(AGENT_URL, null, Collections.emptyMap()));
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
      a2aStatic.when(() -> A2A.getAgentCard(anyString(), any(), anyMap())).thenReturn(agentCard);

      final var result = agentCardFetcher.fetchAgentCard(request);

      a2aStatic.verify(() -> A2A.getAgentCard(AGENT_URL, null, Collections.emptyMap()));
      assertAgentCard(result, inputModes, outputModes);
    }
  }

  private ConnectionConfiguration buildConfiguration(String agentCardLocation) {
    return new ConnectionConfiguration(AGENT_URL, agentCardLocation);
  }

  private AgentCard createAgentCard(List<String> skillInputModes, List<String> skillOutputModes) {
    return new AgentCard.Builder()
        .name("Travel agent")
        .description("Helps with travel bookings")
        .url("http://localhost:9999")
        .version("1.0.0")
        .documentationUrl("http://example.com/docs")
        .capabilities(
            new AgentCapabilities.Builder()
                .streaming(true)
                .pushNotifications(true)
                .stateTransitionHistory(true)
                .build())
        .defaultInputModes(DEFAULT_INPUT_MODES)
        .defaultOutputModes(DEFAULT_OUTPUT_MODES)
        .skills(
            List.of(
                new AgentSkill.Builder()
                    .id("hotel-booking")
                    .name("Hotel Booking")
                    .description("Book a hotel room")
                    .tags(List.of("booking", "hotel"))
                    .examples(List.of("Book a single room", "Book a double room"))
                    .inputModes(skillInputModes)
                    .outputModes(skillOutputModes)
                    .build()))
        .protocolVersion("0.3.0")
        .additionalInterfaces(
            List.of(
                new AgentInterface(TransportProtocol.JSONRPC.asString(), "http://localhost:9999")))
        .build();
  }

  private void assertAgentCard(
      A2aAgentCardResult agentCardResult, List<String> inputModes, List<String> outputModes) {
    assertThat(agentCardResult.name()).isEqualTo("Travel agent");
    assertThat(agentCardResult.description()).isEqualTo("Helps with travel bookings");
    assertThat(agentCardResult.skills())
        .containsExactly(
            A2aAgentCardResult.AgentSkill.builder()
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

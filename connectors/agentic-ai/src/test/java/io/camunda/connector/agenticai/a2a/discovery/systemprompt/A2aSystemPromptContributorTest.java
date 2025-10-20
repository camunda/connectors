/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.discovery.systemprompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.a2a.discovery.A2aGatewayToolHandler;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.util.StreamUtils;

class A2aSystemPromptContributorTest {

  @Test
  void shouldContributeWhenA2aToolsPresent() {
    A2aSystemPromptContributor contributor = new A2aSystemPromptContributor();

    AgentExecutionContext executionContext = mock(AgentExecutionContext.class);
    AgentContext agentContext = mock(AgentContext.class);

    when(agentContext.properties())
        .thenReturn(Map.of(A2aGatewayToolHandler.PROPERTY_A2A_CLIENTS, List.of("RemoteAgent")));

    String result = contributor.contributeSystemPrompt(executionContext, agentContext);

    assertThat(result).isNotNull();
    assertThat(result).contains("A2A Remote Agent Interaction Guide");
  }

  @ParameterizedTest
  @MethodSource("noA2aToolsPropertiesProvider")
  void shouldNotContributeWhenNoA2aTools(Map<String, Object> properties) {
    A2aSystemPromptContributor contributor = new A2aSystemPromptContributor();

    AgentExecutionContext executionContext = mock(AgentExecutionContext.class);
    AgentContext agentContext = mock(AgentContext.class);

    when(agentContext.properties()).thenReturn(properties);

    String result = contributor.contributeSystemPrompt(executionContext, agentContext);

    assertThat(result).isNull();
  }

  public static Stream<Arguments> noA2aToolsPropertiesProvider() {
    return Stream.of(
        Arguments.of(Map.of()),
        Arguments.of(Map.of("someOtherProperty", "someValue")),
        Arguments.of(Map.of(A2aGatewayToolHandler.PROPERTY_A2A_CLIENTS, List.of())),
        Arguments.of(Map.of(A2aGatewayToolHandler.PROPERTY_A2A_CLIENTS, "NotAListButAString")));
  }

  @Test
  void shouldHaveCorrectOrder() {
    A2aSystemPromptContributor contributor = new A2aSystemPromptContributor();

    assertThat(contributor.getOrder()).isEqualTo(100);
  }

  @Test
  void shouldThrowIllegalStateExceptionWhenResourceLoadingFails() {
    try (MockedStatic<StreamUtils> streamUtilsMock = mockStatic(StreamUtils.class)) {
      streamUtilsMock
          .when(() -> StreamUtils.copyToString(any(InputStream.class), any(Charset.class)))
          .thenThrow(new IOException("Simulated resource loading failure"));

      assertThatThrownBy(A2aSystemPromptContributor::new)
          .isInstanceOf(IllegalStateException.class)
          .hasCauseInstanceOf(IOException.class)
          .hasMessageContaining("Simulated resource loading failure");
    }
  }
}

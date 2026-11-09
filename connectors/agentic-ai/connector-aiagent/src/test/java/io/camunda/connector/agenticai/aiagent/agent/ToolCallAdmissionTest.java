/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCallAdmissionTest {

  private static final ToolDefinition PROCESS_TOOL =
      new ToolDefinition("getWeather", "Weather", Map.of("type", "object"));
  private static final ToolDefinition RUNTIME_TOOL =
      ToolDefinition.runtime("acme_note", "Note", Map.of("type", "object"));

  private final ToolCallAdmission admission = new ToolCallAdmission();

  @Test
  void eachCallGetsTheKindOfTheOfferedToolOfItsName() {
    final var message =
        messageWith(
            new ToolCall("p1", "getWeather", Map.of("city", "Berlin")),
            new ToolCall("r1", "acme_note", Map.of("text", "x")));

    final var admitted = admission.admit(message, List.of(PROCESS_TOOL, RUNTIME_TOOL));

    assertThat(admitted)
        .isEqualTo(
            message.withToolCalls(
                List.of(
                    new ToolCall("p1", "getWeather", Map.of("city", "Berlin")),
                    new ToolCall("r1", "acme_note", Map.of("text", "x"))
                        .withKind(ToolKind.RUNTIME))));
  }

  @Test
  void aCallOfAToolThatIsNotOfferedIsRejected() {
    final var message =
        messageWith(
            new ToolCall("x1", "acme_unknown", Map.of()),
            new ToolCall("p1", "getWeather", Map.of()));

    final var admitted = admission.admit(message, List.of(PROCESS_TOOL));

    assertThat(admitted.toolCalls())
        .containsExactly(
            new ToolCall("x1", "acme_unknown", Map.of())
                .withRejection("The tool 'acme_unknown' does not exist."),
            new ToolCall("p1", "getWeather", Map.of()));
  }

  @Test
  void aMessageWithoutCallsIsReturnedAsItIs() {
    final var message = AssistantMessage.builder().content(List.of(textContent("Hi"))).build();

    assertThat(admission.admit(message, List.of(PROCESS_TOOL))).isSameAs(message);
  }

  private static AssistantMessage messageWith(ToolCall... calls) {
    return AssistantMessage.builder()
        .content(List.of(textContent("calling tools")))
        .toolCalls(List.of(calls))
        .build();
  }
}

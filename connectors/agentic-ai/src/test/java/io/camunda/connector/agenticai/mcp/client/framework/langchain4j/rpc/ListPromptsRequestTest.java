/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpPromptArgument;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyListBuilder;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListPromptsResult;
import io.camunda.connector.agenticai.mcp.client.model.result.PromptDescription;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListPromptsRequestTest {

  private static final AllowDenyList EMPTY_FILTER = AllowDenyList.allowingEverything();

  @Mock private McpClient mcpClient;

  private final ListPromptsRequest testee = new ListPromptsRequest();

  @Test
  void returnsEmptyList_whenNoPromptsAvailable() {
    when(mcpClient.listPrompts()).thenReturn(Collections.emptyList());

    final var result = testee.execute(mcpClient, EMPTY_FILTER);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListPromptsResult.class,
            res -> assertThat(res.promptDescriptions()).isEmpty());
  }

  @Test
  void returnsPromptDescriptions_whenPromptsAvailable() {
    final var mcpPrompt1 =
        createMcpPrompt(
            "code_review",
            "Asks the LLM to analyze code quality and suggest improvements",
            List.of(new McpPromptArgument("code", "The code to review", true)));
    final var mcpPrompt2 =
        createMcpPrompt("four_eyes_review", "Asks the LLM to judge something", List.of());

    final var prompt1 =
        createPrompt(
            "code_review",
            "Asks the LLM to analyze code quality and suggest improvements",
            List.of(new PromptDescription.PromptArgument("code", "The code to review", true)));
    final var prompt2 =
        createPrompt("four_eyes_review", "Asks the LLM to judge something", List.of());

    when(mcpClient.listPrompts()).thenReturn(List.of(mcpPrompt1, mcpPrompt2));

    final var result = testee.execute(mcpClient, EMPTY_FILTER);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListPromptsResult.class,
            res -> assertThat(res.promptDescriptions()).containsExactly(prompt1, prompt2));
  }

  @Test
  void filtersPrompts_whenFilterConfigured() {
    final var mcpPrompt1 = createMcpPrompt("allowed-prompt", "Allowed prompt", List.of());
    final var mcpPrompt2 = createMcpPrompt("blocked-prompt", "Blocked prompt", List.of());
    final var prompt1 = createPrompt("allowed-prompt", "Allowed prompt", List.of());
    final var filter =
        AllowDenyListBuilder.builder().allowed(List.of("allowed-prompt")).denied(List.of()).build();

    when(mcpClient.listPrompts()).thenReturn(List.of(mcpPrompt1, mcpPrompt2));

    final var result = testee.execute(mcpClient, filter);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListPromptsResult.class,
            res -> assertThat(res.promptDescriptions()).containsExactly(prompt1));
  }

  @Test
  void returnsEmptyList_whenAllPromptsFiltered() {
    final var mcpPrompt1 = createMcpPrompt("blocked-prompt1", "Blocked prompt 1", List.of());
    final var mcpPrompt2 = createMcpPrompt("blocked-prompt2", "Blocked prompt 2", List.of());
    final var mcpPrompt3 = createMcpPrompt("blocked-prompt3", "Blocked prompt 3", List.of());
    final var filter =
        AllowDenyListBuilder.builder()
            .allowed(List.of("allowed-prompt"))
            .denied(List.of("blocked-prompt3"))
            .build();

    when(mcpClient.listPrompts()).thenReturn(List.of(mcpPrompt1, mcpPrompt2, mcpPrompt3));

    final var result = testee.execute(mcpClient, filter);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListPromptsResult.class,
            res -> assertThat(res.promptDescriptions()).isEmpty());
  }

  @Test
  void returnsEmptyList_whenPromptAllowedAndDeniedSimultaneously() {
    final var mcpPrompt1 = createMcpPrompt("allowed-prompt", "Allowed prompt", List.of());
    final var filter =
        AllowDenyListBuilder.builder()
            .allowed(List.of("allowed-prompt"))
            .denied(List.of("allowed-prompt"))
            .build();

    when(mcpClient.listPrompts()).thenReturn(List.of(mcpPrompt1));

    final var result = testee.execute(mcpClient, filter);

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListPromptsResult.class,
            res -> assertThat(res.promptDescriptions()).isEmpty());
  }

  private McpPrompt createMcpPrompt(
      String name, String description, List<McpPromptArgument> arguments) {
    return new McpPrompt(name, description, List.copyOf(arguments));
  }

  private PromptDescription createPrompt(
      String name, String description, List<PromptDescription.PromptArgument> arguments) {
    return new PromptDescription(name, description, List.copyOf(arguments));
  }
}

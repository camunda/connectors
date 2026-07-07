/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyListBuilder;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListPromptsResult;
import io.camunda.connector.agenticai.mcp.client.model.result.PromptDescription;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListPromptsRequestTest {

  private static final AllowDenyList EMPTY_FILTER = AllowDenyList.allowingEverything();

  @Mock private McpSyncClient mcpClient;

  private final ListPromptsRequest testee = new ListPromptsRequest("testClient");

  @Test
  void returnsEmptyList_whenNoPromptsAvailable() {
    when(mcpClient.listPrompts())
        .thenReturn(new McpSchema.ListPromptsResult(Collections.emptyList(), null));

    final var result = testee.execute(mcpClient, EMPTY_FILTER, Map.of());

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
            "Code Review",
            "Asks the LLM to analyze code quality and suggest improvements",
            List.of(new McpSchema.PromptArgument("code", "The code to review", true)));
    final var mcpPrompt2 =
        createMcpPrompt(
            "four_eyes_review", "Four Eyes Review", "Asks the LLM to judge something", List.of());

    final var prompt1 =
        createPrompt(
            "code_review",
            "Code Review",
            "Asks the LLM to analyze code quality and suggest improvements",
            List.of(new PromptDescription.PromptArgument("code", "The code to review", true)));
    final var prompt2 =
        createPrompt(
            "four_eyes_review", "Four Eyes Review", "Asks the LLM to judge something", List.of());

    when(mcpClient.listPrompts())
        .thenReturn(new McpSchema.ListPromptsResult(List.of(mcpPrompt1, mcpPrompt2), null));

    final var result = testee.execute(mcpClient, EMPTY_FILTER, Map.of());

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListPromptsResult.class,
            res -> {
              assertThat(res.promptDescriptions()).containsExactly(prompt1, prompt2);
              assertThat(res.promptDescriptions())
                  .extracting(PromptDescription::title)
                  .containsExactly("Code Review", "Four Eyes Review");
            });
  }

  @Test
  void filtersPrompts_whenFilterConfigured() {
    final var mcpPrompt1 =
        createMcpPrompt("allowed-prompt", "Allowed Prompt", "Allowed prompt", List.of());
    final var mcpPrompt2 =
        createMcpPrompt("blocked-prompt", "Blocked Prompt", "Blocked prompt", List.of());
    final var prompt1 =
        createPrompt("allowed-prompt", "Allowed Prompt", "Allowed prompt", List.of());
    final var filter =
        AllowDenyListBuilder.builder().allowed(List.of("allowed-prompt")).denied(List.of()).build();

    when(mcpClient.listPrompts())
        .thenReturn(new McpSchema.ListPromptsResult(List.of(mcpPrompt1, mcpPrompt2), null));

    final var result = testee.execute(mcpClient, filter, Map.of());

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListPromptsResult.class,
            res -> assertThat(res.promptDescriptions()).containsExactly(prompt1));
  }

  @Test
  void returnsEmptyList_whenAllPromptsFiltered() {
    final var mcpPrompt1 =
        createMcpPrompt("blocked-prompt1", "Blocked Prompt 1", "Blocked prompt 1", List.of());
    final var mcpPrompt2 =
        createMcpPrompt("blocked-prompt2", "Blocked Prompt 2", "Blocked prompt 2", List.of());
    final var mcpPrompt3 =
        createMcpPrompt("blocked-prompt3", "Blocked Prompt 3", "Blocked prompt 3", List.of());
    final var filter =
        AllowDenyListBuilder.builder()
            .allowed(List.of("allowed-prompt"))
            .denied(List.of("blocked-prompt3"))
            .build();

    when(mcpClient.listPrompts())
        .thenReturn(
            new McpSchema.ListPromptsResult(List.of(mcpPrompt1, mcpPrompt2, mcpPrompt3), null));

    final var result = testee.execute(mcpClient, filter, Map.of());

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListPromptsResult.class,
            res -> assertThat(res.promptDescriptions()).isEmpty());
  }

  @Test
  void returnsEmptyList_whenPromptAllowedAndDeniedSimultaneously() {
    final var mcpPrompt1 =
        createMcpPrompt("allowed-prompt", "Allowed Prompt", "Allowed prompt", List.of());
    final var filter =
        AllowDenyListBuilder.builder()
            .allowed(List.of("allowed-prompt"))
            .denied(List.of("allowed-prompt"))
            .build();

    when(mcpClient.listPrompts())
        .thenReturn(new McpSchema.ListPromptsResult(List.of(mcpPrompt1), null));

    final var result = testee.execute(mcpClient, filter, Map.of());

    assertThat(result)
        .isInstanceOfSatisfying(
            McpClientListPromptsResult.class,
            res -> assertThat(res.promptDescriptions()).isEmpty());
  }

  @Test
  void forwardsMetaUnmodified_whenMetaConfigured() {
    final var meta = McpRpcTestFixtures.EXAMPLE_META;
    when(mcpClient.listPrompts(isNull(), eq(meta)))
        .thenReturn(new McpSchema.ListPromptsResult(Collections.emptyList(), null, null));

    testee.execute(mcpClient, EMPTY_FILTER, meta);

    verify(mcpClient).listPrompts(isNull(), eq(meta));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void doesNotSendMeta_whenMetaNotConfigured(Map<String, Object> meta) {
    when(mcpClient.listPrompts())
        .thenReturn(new McpSchema.ListPromptsResult(Collections.emptyList(), null));

    testee.execute(mcpClient, EMPTY_FILTER, meta);

    verify(mcpClient).listPrompts();
  }

  private McpSchema.Prompt createMcpPrompt(
      String name, String title, String description, List<McpSchema.PromptArgument> arguments) {
    return McpSchema.Prompt.builder(name)
        .title(title)
        .description(description)
        .arguments(List.copyOf(arguments))
        .build();
  }

  private PromptDescription createPrompt(
      String name,
      String title,
      String description,
      List<PromptDescription.PromptArgument> arguments) {
    return new PromptDescription(name, title, description, List.copyOf(arguments));
  }
}

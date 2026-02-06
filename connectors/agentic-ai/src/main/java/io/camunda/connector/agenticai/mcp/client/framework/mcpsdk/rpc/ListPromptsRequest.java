/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListPromptsResult;
import io.camunda.connector.agenticai.mcp.client.model.result.PromptDescription;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

final class ListPromptsRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListPromptsRequest.class);

  private final String clientId;

  ListPromptsRequest(String clientId) {
    this.clientId = clientId;
  }

  public McpClientListPromptsResult execute(McpSyncClient client, AllowDenyList promptsFilter) {
    LOGGER.debug("MCP({}): Executing list prompts", clientId);

    var fetchedPrompts = client.listPrompts().prompts();

    if (CollectionUtils.isEmpty(fetchedPrompts)) {
      LOGGER.debug("MCP({}): No prompts found", clientId);
      return new McpClientListPromptsResult(Collections.emptyList());
    }

    final var filteredPrompts =
        fetchedPrompts.stream().filter(prompt -> promptsFilter.isPassing(prompt.name())).toList();

    if (filteredPrompts.isEmpty()) {
      LOGGER.debug("MCP({}): No prompts left after filtering. Filter: {}", clientId, promptsFilter);
      return new McpClientListPromptsResult(Collections.emptyList());
    }

    var result =
        new McpClientListPromptsResult(
            filteredPrompts.stream()
                .map(
                    fr ->
                        new PromptDescription(
                            fr.name(),
                            fr.description(),
                            fr.arguments().stream().map(this::from).toList(),
                            fr.title(),
                            McpSdkMapper.mapIcons(fr.icons())))
                .toList());

    LOGGER.debug(
        "MCP({}): Resolved list of prompts: {}",
        clientId,
        result.promptDescriptions().stream().map(PromptDescription::name).toList());

    return result;
  }

  private PromptDescription.PromptArgument from(McpSchema.PromptArgument mcpPromptArgument) {
    return new PromptDescription.PromptArgument(
        mcpPromptArgument.name(), mcpPromptArgument.description(), mcpPromptArgument.required());
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpPromptArgument;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListPromptsResult;
import io.camunda.connector.agenticai.mcp.client.model.result.PromptDescription;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ListPromptsRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListPromptsRequest.class);

  public McpClientListPromptsResult execute(McpClient client) {
    LOGGER.debug("MCP({}): Executing list prompts", client.key());

    var fetchedPrompts = client.listPrompts();

    var result =
        new McpClientListPromptsResult(
            fetchedPrompts.isEmpty()
                ? Collections.emptyList()
                : fetchedPrompts.stream()
                    .map(
                        fr ->
                            new PromptDescription(
                                fr.name(),
                                fr.description(),
                                fr.arguments().stream().map(this::from).toList()))
                    .toList());

    LOGGER.debug(
        "MCP({}): Resolved list of prompts: {}",
        client.key(),
        result.promptDescriptions().stream().map(PromptDescription::name).toList());

    return result;
  }

  private PromptDescription.PromptArgument from(McpPromptArgument mcpPromptArgument) {
    return new PromptDescription.PromptArgument(
        mcpPromptArgument.name(), mcpPromptArgument.description(), mcpPromptArgument.required());
  }
}

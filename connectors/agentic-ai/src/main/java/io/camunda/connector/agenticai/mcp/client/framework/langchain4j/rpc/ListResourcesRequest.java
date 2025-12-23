/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientListResourcesResult;
import io.camunda.connector.agenticai.model.tool.ResourceDescription;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

record ListResourcesRequest() {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListResourcesRequest.class);

  public McpClientListResourcesResult execute(McpClient client) {
    LOGGER.debug("MCP({}): Executing list resources", client.key());

    var fetchedResources = client.listResources();

    var result =
        new McpClientListResourcesResult(
            fetchedResources.isEmpty()
                ? Collections.emptyList()
                : fetchedResources.stream()
                    .map(
                        fr ->
                            new ResourceDescription(
                                fr.uri(), fr.name(), fr.description(), fr.mimeType()))
                    .toList());

    LOGGER.debug(
        "MCP({}): Resolved list of resources: {}",
        client.key(),
        result.resources().stream().map(ResourceDescription::name).toList());

    return result;
  }
}

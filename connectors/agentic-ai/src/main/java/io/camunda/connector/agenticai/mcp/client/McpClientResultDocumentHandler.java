/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import io.camunda.connector.agenticai.mcp.client.model.McpDocumentSettings;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientResult;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientResultWithStorableData;
import io.camunda.connector.api.document.DocumentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpClientResultDocumentHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(McpClientResultDocumentHandler.class);

  private final DocumentFactory documentFactory;

  public McpClientResultDocumentHandler(DocumentFactory documentFactory) {
    this.documentFactory = documentFactory;
  }

  public McpClientResult transformBinariesToDocumentsIfPresent(McpClientResult clientResult) {
    if (!(clientResult instanceof McpClientResultWithStorableData documentContainer)) {
      LOGGER.debug(
          "MCP: Client result is not a CamundaDocumentContainer, skipping document transformation.");

      return clientResult;
    }

    LOGGER.debug("Transforming potential binary content to documents.");

    return documentContainer.transformStorableMcpResultData(
        documentFactory, new McpDocumentSettings(java.time.Duration.ofHours(1)));
  }
}

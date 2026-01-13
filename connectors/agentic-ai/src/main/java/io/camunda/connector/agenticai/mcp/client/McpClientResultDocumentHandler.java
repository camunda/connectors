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

  /**
   * Converts any storable MCP data in the given client result into Camunda documents, if present.
   *
   * <p>Therefor, the subtype of {@link McpClientResult} must be as well an instance of {@link
   * McpClientResultWithStorableData}. The decision logic, on which data is storable and how to
   * convert it into documents, is implemented in the respective target class.
   *
   * @param clientResult the client result to convert potentially storable data for
   * @return the <tt>clientResult</tt> if it is not an instance of {@link
   *     McpClientResultWithStorableData}, otherwise a new instance holding references to created
   *     documents for any storable data
   */
  public McpClientResult convertBinariesToDocumentsIfPresent(McpClientResult clientResult) {
    if (!(clientResult instanceof McpClientResultWithStorableData documentContainer)) {
      LOGGER.debug(
          "MCP: Client result is not a container for storable mcp data. Skipping document conversion.");

      return clientResult;
    }

    LOGGER.debug(
        "Attempting to convert storable mcp data into Camunda documents for client result of type {}.",
        clientResult.getClass().getSimpleName());

    return documentContainer.convertStorableMcpResultData(
        documentFactory, new McpDocumentSettings(java.time.Duration.ofHours(1)));
  }
}

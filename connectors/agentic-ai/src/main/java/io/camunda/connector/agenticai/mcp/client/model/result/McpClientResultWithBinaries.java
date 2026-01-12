/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.result;

import io.camunda.connector.agenticai.mcp.client.model.McpDocumentSettings;
import io.camunda.connector.api.document.DocumentFactory;

public interface McpClientResultWithBinaries {

  McpClientResult transformBinaryContentToDocuments(
      DocumentFactory documentFactory, McpDocumentSettings settings);
}

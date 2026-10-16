/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration;

import io.camunda.connector.agenticai.mcp.client.McpClientResultDocumentHandler;
import io.camunda.connector.api.document.DocumentFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpDocumentHandlerConfiguration {

  @Bean
  public McpClientResultDocumentHandler mcpClientResultDocumentHandler(
      DocumentFactory documentFactory) {
    return new McpClientResultDocumentHandler(documentFactory);
  }
}

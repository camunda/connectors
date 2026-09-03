/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration;

import io.camunda.connector.agenticai.aiagent.chatmodel.provider.authentication.oauth.OAuthClientCredentialsTokenResolver;
import io.camunda.connector.agenticai.mcp.client.McpClientResultDocumentHandler;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientExecutor;
import io.camunda.connector.agenticai.mcp.client.framework.bootstrap.McpClientHeadersSupplierFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(McpDocumentHandlerConfiguration.class)
public class McpClientBaseConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public McpClientExecutor mcpClientExecutor(
      McpClientResultDocumentHandler mcpClientResultDocumentHandler) {
    return new McpClientExecutor(mcpClientResultDocumentHandler);
  }

  @Bean
  @ConditionalOnMissingBean
  public McpClientHeadersSupplierFactory mcpClientHeadersSupplierFactory(
      OAuthClientCredentialsTokenResolver oAuthClientCredentialsTokenResolver) {
    return new McpClientHeadersSupplierFactory(oAuthClientCredentialsTokenResolver);
  }
}

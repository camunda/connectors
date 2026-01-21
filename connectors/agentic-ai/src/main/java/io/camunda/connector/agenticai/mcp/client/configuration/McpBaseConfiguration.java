package io.camunda.connector.agenticai.mcp.client.configuration;

import io.camunda.connector.agenticai.mcp.client.McpClientResultDocumentHandler;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(McpDocumentHandlerConfiguration.class)
public class McpBaseConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public McpClientExecutor mcpClientExecutor(
      McpClientResultDocumentHandler mcpClientResultDocumentHandler) {
    return new McpClientExecutor(mcpClientResultDocumentHandler);
  }
}

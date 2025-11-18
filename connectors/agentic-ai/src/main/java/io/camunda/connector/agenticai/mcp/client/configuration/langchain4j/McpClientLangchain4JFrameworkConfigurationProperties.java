/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.configuration.langchain4j;

import jakarta.validation.Valid;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "camunda.connector.agenticai.mcp.client.langchain4j")
public record McpClientLangchain4JFrameworkConfigurationProperties(
    @Valid @DefaultValue LoggingConfiguration logging) {

  public record LoggingConfiguration(
      @DefaultValue StdioLoggingConfiguration stdio, @DefaultValue HttpLoggingConfiguration http) {
    public record StdioLoggingConfiguration(@DefaultValue("false") boolean logEvents) {}

    public record HttpLoggingConfiguration(
        @DefaultValue("false") boolean logRequests, @DefaultValue("false") boolean logResponses) {}
  }
}

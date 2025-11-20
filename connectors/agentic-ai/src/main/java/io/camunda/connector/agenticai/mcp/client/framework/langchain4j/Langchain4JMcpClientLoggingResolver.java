/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j;

import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import java.util.Objects;
import java.util.function.BiFunction;

public class Langchain4JMcpClientLoggingResolver {
  private IndividualLoggingResolver logStdioEvents = (clientId, config) -> false;
  private IndividualLoggingResolver logHttpRequests = (clientId, config) -> false;
  private IndividualLoggingResolver logHttpResponses = (clientId, config) -> false;

  public boolean logStdioEvents(String clientId, McpClientConfiguration config) {
    return logStdioEvents.apply(clientId, config);
  }

  public void setLogStdioEvents(boolean enabled) {
    this.logStdioEvents = (clientId, config) -> enabled;
  }

  public void setLogStdioEvents(IndividualLoggingResolver resolver) {
    Objects.requireNonNull(resolver, "logStdioEvents resolver cannot be null");
    this.logStdioEvents = resolver;
  }

  public boolean logHttpRequests(String clientId, McpClientConfiguration config) {
    return logHttpRequests.apply(clientId, config);
  }

  public void setLogHttpRequests(boolean enabled) {
    this.logHttpRequests = (clientId, config) -> enabled;
  }

  public void setLogHttpRequests(IndividualLoggingResolver resolver) {
    Objects.requireNonNull(resolver, "logHttpRequests resolver cannot be null");
    this.logHttpRequests = resolver;
  }

  public boolean logHttpResponses(String clientId, McpClientConfiguration config) {
    return logHttpResponses.apply(clientId, config);
  }

  public void setLogHttpResponses(boolean enabled) {
    this.logHttpResponses = (clientId, config) -> enabled;
  }

  public void setLogHttpResponses(IndividualLoggingResolver resolver) {
    Objects.requireNonNull(resolver, "logHttpResponses resolver cannot be null");
    this.logHttpResponses = resolver;
  }

  public interface IndividualLoggingResolver
      extends BiFunction<String, McpClientConfiguration, Boolean> {}
}

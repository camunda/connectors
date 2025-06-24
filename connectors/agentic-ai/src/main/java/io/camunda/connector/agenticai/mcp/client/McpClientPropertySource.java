/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import io.camunda.connector.agenticai.adhoctoolsschema.resolver.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.mcp.discovery.McpClientGatewayToolHandler;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeProperty;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.java.annotation.PropertySource;
import java.util.Collection;
import java.util.List;

public class McpClientPropertySource {
  @PropertySource
  public static Collection<PropertyBuilder> mcpClientGatewayProperties() {
    return List.of(
        HiddenProperty.builder()
            .binding(new ZeebeProperty(GatewayToolDefinitionResolver.GATEWAY_TYPE_EXTENSION))
            .value(McpClientGatewayToolHandler.GATEWAY_TYPE));
  }
}

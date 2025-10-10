/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.discovery;

import static io.camunda.connector.agenticai.adhoctoolsschema.schema.GatewayToolDefinitionResolver.GATEWAY_TYPE_EXTENSION;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.mcp.discovery.McpClientGatewayToolHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class A2aGatewayToolDefinitionResolverTest {

  @Test
  void resolvesGatewayToolDefinitionsFromElementsWithA2AClientProperty() {
    A2aGatewayToolDefinitionResolver resolver = new A2aGatewayToolDefinitionResolver();
    final var elements =
        List.of(
            withA2aClientProperty(createElement("a2a-task-1", "A2A Task 1")),
            withA2aClientProperty(createElement("a2a-task-2", "A2A Task 2")),
            createElement("other-task", "Other task"),
            withGatewayType(
                createElement("mcp-task", "MCP Task"), McpClientGatewayToolHandler.GATEWAY_TYPE),
            withGatewayType(
                createElement("other-gateway-task", "Other Gateway Task"), "someOtherGateway"),
            withA2aClientProperty(
                createElement("a2a-task-3", "A2A Task 3")
                    .withDocumentation("Custom documentation")));

    final var result = resolver.resolveGatewayToolDefinitions(elements);

    assertThat(result)
        .hasSize(3)
        .satisfiesExactly(
            gatewayToolDefinition -> {
              assertThat(gatewayToolDefinition.type()).isEqualTo("a2aClient");
              assertThat(gatewayToolDefinition.name()).isEqualTo("a2a-task-1");
              assertThat(gatewayToolDefinition.description()).isEqualTo("A2A Task 1");
            },
            gatewayToolDefinition -> {
              assertThat(gatewayToolDefinition.type()).isEqualTo("a2aClient");
              assertThat(gatewayToolDefinition.name()).isEqualTo("a2a-task-2");
              assertThat(gatewayToolDefinition.description()).isEqualTo("A2A Task 2");
            },
            gatewayToolDefinition -> {
              assertThat(gatewayToolDefinition.type()).isEqualTo("a2aClient");
              assertThat(gatewayToolDefinition.name()).isEqualTo("a2a-task-3");
              assertThat(gatewayToolDefinition.description()).isEqualTo("Custom documentation");
            });
  }

  private AdHocToolElement createElement(String id, String name) {
    return AdHocToolElement.builder().elementId(id).elementName(name).build();
  }

  private AdHocToolElement withA2aClientProperty(AdHocToolElement element) {
    return withGatewayType(element, A2aGatewayToolHandler.GATEWAY_TYPE);
  }

  private AdHocToolElement withGatewayType(AdHocToolElement element, String gatewayType) {
    return element.withProperties(Map.of(GATEWAY_TYPE_EXTENSION, gatewayType));
  }
}

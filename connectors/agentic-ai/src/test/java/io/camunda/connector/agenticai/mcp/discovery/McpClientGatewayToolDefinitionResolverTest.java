/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.discovery;

import static io.camunda.connector.agenticai.adhoctoolsschema.resolver.GatewayToolDefinitionResolver.GATEWAY_TYPE_EXTENSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.zeebe.model.bpmn.instance.Documentation;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.UserTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperties;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperty;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpClientGatewayToolDefinitionResolverTest {

  private McpClientGatewayToolDefinitionResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new McpClientGatewayToolDefinitionResolver();
  }

  @Test
  void returnsEmptyList_whenNoMcpClientElements() {
    final var elements =
        List.<FlowNode>of(
            createServiceTask("task1", "other:type"), createServiceTask("task2", "different:type"));

    final var result = resolver.resolveGatewayToolDefinitions(elements);

    assertThat(result).isEmpty();
  }

  @Test
  void resolvesGatewayToolDefinitionsFromMcpClientServiceTasks() {
    final var elements =
        List.<FlowNode>of(
            createServiceTask("mcp-task-1", "io.camunda.agenticai:mcpclient:0"),
            createServiceTask("mcp-task-2", "io.camunda.agenticai:mcpclient:1"),
            createServiceTask("other-task", "other:type"),
            createServiceTask("remote-mcp-task-1", "io.camunda.agenticai:mcpclientremote:1"));

    final var result = resolver.resolveGatewayToolDefinitions(elements);

    assertThat(result)
        .hasSize(3)
        .satisfiesExactly(
            gatewayToolDefinition -> {
              assertThat(gatewayToolDefinition.type()).isEqualTo("mcpClient");
              assertThat(gatewayToolDefinition.name()).isEqualTo("mcp-task-1");
              assertThat(gatewayToolDefinition.description())
                  .isEqualTo("Task mcp-task-1 documentation");
            },
            gatewayToolDefinition -> {
              assertThat(gatewayToolDefinition.type()).isEqualTo("mcpClient");
              assertThat(gatewayToolDefinition.name()).isEqualTo("mcp-task-2");
              assertThat(gatewayToolDefinition.description())
                  .isEqualTo("Task mcp-task-2 documentation");
            },
            gatewayToolDefinition -> {
              assertThat(gatewayToolDefinition.type()).isEqualTo("mcpClient");
              assertThat(gatewayToolDefinition.name()).isEqualTo("remote-mcp-task-1");
              assertThat(gatewayToolDefinition.description())
                  .isEqualTo("Task remote-mcp-task-1 documentation");
            });
  }

  @Test
  void resolvesGatewayToolDefinitionsFromElementsWithMcpClientProperty() {
    final var elements =
        List.of(
            withMcpClientProperty(createServiceTask("mcp-task-1", "my-custom:mcp:client:10")),
            withMcpClientProperty(createServiceTask("mcp-task-2", "another-custom:mcp:client:3")),
            createServiceTask("other-task", "other:type"),
            withMcpClientProperty(createUserTask("user-mcp-task-1")));

    final var result = resolver.resolveGatewayToolDefinitions(elements);

    assertThat(result)
        .hasSize(3)
        .satisfiesExactly(
            gatewayToolDefinition -> {
              assertThat(gatewayToolDefinition.type()).isEqualTo("mcpClient");
              assertThat(gatewayToolDefinition.name()).isEqualTo("mcp-task-1");
              assertThat(gatewayToolDefinition.description())
                  .isEqualTo("Task mcp-task-1 documentation");
            },
            gatewayToolDefinition -> {
              assertThat(gatewayToolDefinition.type()).isEqualTo("mcpClient");
              assertThat(gatewayToolDefinition.name()).isEqualTo("mcp-task-2");
              assertThat(gatewayToolDefinition.description())
                  .isEqualTo("Task mcp-task-2 documentation");
            },
            gatewayToolDefinition -> {
              assertThat(gatewayToolDefinition.type()).isEqualTo("mcpClient");
              assertThat(gatewayToolDefinition.name()).isEqualTo("user-mcp-task-1");
              assertThat(gatewayToolDefinition.description())
                  .isEqualTo("Task user-mcp-task-1 documentation");
            });
  }

  @Test
  void filtersMixedElementTypes() {
    final var elements =
        List.of(
            createServiceTask("mcp1", "io.camunda.agenticai:mcpclient:1"),
            createServiceTask("other", "other:type"),
            createServiceTask("mcp2", "io.camunda.agenticai:mcpclientremote:1"),
            createUserTask("non-service-task"),
            withMcpClientProperty(createUserTask("user-task-with-mcp-client-property")));

    final var result = resolver.resolveGatewayToolDefinitions(elements);

    assertThat(result)
        .extracting(GatewayToolDefinition::name)
        .containsExactly("mcp1", "mcp2", "user-task-with-mcp-client-property");
  }

  @Test
  void handlesServiceTasksWithoutTaskDefinition() {
    final var serviceTask = mock(ServiceTask.class);

    final var result = resolver.resolveGatewayToolDefinitions(List.of(serviceTask));

    assertThat(result).isEmpty();
  }

  @Test
  void handlesNonServiceTaskElements() {
    final var flowNode = mock(UserTask.class);

    final var result = resolver.resolveGatewayToolDefinitions(List.of(flowNode));

    assertThat(result).isEmpty();
  }

  @Test
  void usesCustomPrefixes_whenProvidedInConstructor() {
    final var customResolver =
        new McpClientGatewayToolDefinitionResolver(List.of("custom:mcp:", "another:mcp:"));
    final var elements =
        List.<FlowNode>of(
            createServiceTask("custom1", "custom:mcp:client:1"),
            createServiceTask("custom2", "another:mcp:remote:1"),
            createServiceTask("default", "io.camunda.agenticai:mcpclient:1"));

    final var result = customResolver.resolveGatewayToolDefinitions(elements);

    assertThat(result)
        .extracting(GatewayToolDefinition::name)
        .containsExactly("custom1", "custom2");
  }

  @Test
  void returnsEmpty_whenNoPrefixesMatch() {
    final var customResolver = new McpClientGatewayToolDefinitionResolver(List.of("nonexistent:"));
    final var elements =
        List.<FlowNode>of(
            createServiceTask("mcp1", "io.camunda.agenticai:mcpclient"),
            createServiceTask("mcp2", "io.camunda.agenticai:mcpclientremote"));

    final var result = customResolver.resolveGatewayToolDefinitions(elements);

    assertThat(result).isEmpty();
  }

  @Test
  void handlesEmptyPrefixList() {
    final var customResolver = new McpClientGatewayToolDefinitionResolver(List.of());
    final var elements =
        List.<FlowNode>of(createServiceTask("mcp1", "io.camunda.agenticai:mcpclient"));

    final var result = customResolver.resolveGatewayToolDefinitions(elements);

    assertThat(result).isEmpty();
  }

  private ServiceTask createServiceTask(String id, String taskType) {
    final var serviceTask = createTask(ServiceTask.class, id);

    final var taskDefinition = mock(ZeebeTaskDefinition.class);
    lenient()
        .doReturn(taskDefinition)
        .when(serviceTask)
        .getSingleExtensionElement(ZeebeTaskDefinition.class);
    lenient().when(taskDefinition.getType()).thenReturn(taskType);

    lenient().doReturn(null).when(serviceTask).getSingleExtensionElement(ZeebeProperties.class);

    return serviceTask;
  }

  private UserTask createUserTask(String id) {
    return createTask(UserTask.class, id);
  }

  private FlowNode withMcpClientProperty(FlowNode element) {
    final var someProperty = createProperty("foo", "bar");
    final var gatewayTypeProperty =
        createProperty(GATEWAY_TYPE_EXTENSION, McpClientGatewayToolHandler.GATEWAY_TYPE);
    final var someOtherProperty = createProperty("baz", "qux");

    final var properties = mock(ZeebeProperties.class);
    doReturn(properties).when(element).getSingleExtensionElement(ZeebeProperties.class);
    when(properties.getProperties())
        .thenReturn(List.of(someProperty, gatewayTypeProperty, someOtherProperty));

    return element;
  }

  private <T extends FlowNode> T createTask(Class<T> type, String id) {
    final var element = mock(type);
    final var documentation = createDocumentation("Task %s documentation".formatted(id));

    lenient().when(element.getId()).thenReturn(id);
    lenient().when(element.getDocumentations()).thenReturn(List.of(documentation));

    return element;
  }

  private Documentation createDocumentation(String text) {
    final var documentation = mock(Documentation.class);
    lenient().when(documentation.getTextFormat()).thenReturn("text/plain");
    lenient().when(documentation.getTextContent()).thenReturn(text);
    return documentation;
  }

  private ZeebeProperty createProperty(String name, String value) {
    final var property = mock(ZeebeProperty.class);
    lenient().when(property.getName()).thenReturn(name);
    lenient().when(property.getValue()).thenReturn(value);

    return property;
  }
}

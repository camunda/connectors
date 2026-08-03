/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.localtoolbox.client;

import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.localtoolbox.LocalToolboxErrorCodes;
import io.camunda.connector.agenticai.localtoolbox.client.model.LocalToolboxClientRequest;
import io.camunda.connector.agenticai.localtoolbox.client.model.LocalToolboxClientRequest.LocalToolboxClientRequestData;
import io.camunda.connector.agenticai.localtoolbox.client.model.LocalToolboxOperation;
import io.camunda.connector.agenticai.localtoolbox.client.model.result.LocalToolboxCallToolResult;
import io.camunda.connector.agenticai.localtoolbox.client.model.result.LocalToolboxClientResult;
import io.camunda.connector.agenticai.localtoolbox.client.model.result.LocalToolboxListToolsResult;
import io.camunda.connector.agenticai.localtoolbox.discovery.LocalToolboxGatewayToolHandler;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import java.util.Map;

@OutboundConnector(
    name = "Local Toolbox Client",
    inputVariables = {"data"},
    type = "io.camunda.agenticai:localtoolboxclient:1")
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.localtoolbox.client.v0",
    name = "Local Toolbox Client",
    description =
        "Gateway tool referencing another deployed process as a reusable, auto-discovered set of"
            + " AI Agent tools within the same cluster.",
    engineVersion = "^8.9",
    version = 1,
    category = @ElementTemplate.Category(id = "aiTools", name = "AI Tools"),
    inputDataClass = LocalToolboxClientRequest.class,
    defaultResultVariable = "toolCallResult",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "toolbox", label = "Toolbox"),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation"),
    },
    extensionProperties = {
      @ElementTemplate.ExtensionProperty(
          name = GatewayToolDefinitionResolver.GATEWAY_TYPE_EXTENSION,
          value = LocalToolboxGatewayToolHandler.GATEWAY_TYPE)
    },
    icon = "localtoolbox-client.svg")
public class LocalToolboxClientFunction implements OutboundConnectorFunction {

  private final LocalToolboxProcessDefinitionResolver processDefinitionResolver;
  private final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  private final AdHocToolsSchemaResolver toolsSchemaResolver;
  private final CamundaClient camundaClient;

  public LocalToolboxClientFunction(
      LocalToolboxProcessDefinitionResolver processDefinitionResolver,
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
      AdHocToolsSchemaResolver toolsSchemaResolver,
      CamundaClient camundaClient) {
    this.processDefinitionResolver = processDefinitionResolver;
    this.toolElementsResolver = toolElementsResolver;
    this.toolsSchemaResolver = toolsSchemaResolver;
    this.camundaClient = camundaClient;
  }

  @Override
  public LocalToolboxClientResult execute(OutboundConnectorContext context) {
    final LocalToolboxClientRequestData data =
        context.bindVariables(LocalToolboxClientRequest.class).data();
    final LocalToolboxOperation operation =
        LocalToolboxOperation.of(data.operation().method(), paramsOf(data));

    return switch (operation.method()) {
      case LIST_TOOLS -> listTools(data);
      case CALL_TOOL -> callTool(data, operation);
    };
  }

  private Map<String, Object> paramsOf(LocalToolboxClientRequestData data) {
    return data.operation().params() == null ? Map.of() : data.operation().params();
  }

  private LocalToolboxListToolsResult listTools(LocalToolboxClientRequestData data) {
    final Long processDefinitionKey =
        processDefinitionResolver.resolveProcessDefinitionKey(data.processId(), data.version());
    final var elements =
        toolElementsResolver.resolveToolElements(processDefinitionKey, data.containerElementId());
    final var schema = toolsSchemaResolver.resolveAdHocToolsSchema(elements);
    return new LocalToolboxListToolsResult(schema.toolDefinitions());
  }

  @SuppressWarnings("unchecked")
  private LocalToolboxCallToolResult callTool(
      LocalToolboxClientRequestData data, LocalToolboxOperation operation) {
    final String toolName = (String) operation.params().get("name");
    if (toolName == null || toolName.isBlank()) {
      throw new ConnectorException(
          LocalToolboxErrorCodes.ERROR_CODE_INVALID_TOOL_DEFINITIONS,
          "Local toolbox call is missing the target tool name");
    }

    final Map<String, Object> arguments =
        (Map<String, Object>) operation.params().getOrDefault("arguments", Map.of());
    final Map<String, Object> variables =
        Map.of(
            "toolCall",
            Map.of("name", toolName, "arguments", arguments),
            "meta",
            data.meta() == null ? Map.of() : data.meta());

    final var commandStep =
        camundaClient.newCreateInstanceCommand().bpmnProcessId(data.processId());
    final var versionedStep =
        data.version() != null ? commandStep.version(data.version()) : commandStep.latestVersion();
    final var result = versionedStep.variables(variables).withResult().send().join();

    final Object content = result.getVariablesAsMap().get("toolCallResult");
    return new LocalToolboxCallToolResult(toolName, content);
  }
}

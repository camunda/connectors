/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tools;

import static io.camunda.connector.agenticai.util.ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse.ToolCall;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ToolCallingHandler {

  private final ObjectMapper objectMapper;
  private final AdHocToolsSchemaResolver schemaResolver;
  private final ToolSpecificationConverter toolSpecificationConverter;

  public ToolCallingHandler(
      ObjectMapper objectMapper,
      AdHocToolsSchemaResolver schemaResolver,
      ToolSpecificationConverter toolSpecificationConverter) {
    this.objectMapper = objectMapper;
    this.schemaResolver = schemaResolver;
    this.toolSpecificationConverter = toolSpecificationConverter;
  }

  public List<ToolSpecification> loadToolSpecifications(
      Long processDefinitionKey, String containerElementId) {
    if (containerElementId == null || containerElementId.isBlank()) {
      return Collections.emptyList();
    }

    final var adHocToolsSchema =
        schemaResolver.resolveSchema(processDefinitionKey, containerElementId);

    return adHocToolsSchema.toolDefinitions().stream()
        .map(toolSpecificationConverter::asToolSpecification)
        .toList();
  }

  public List<ToolCall> extractToolCalls(
      List<ToolSpecification> toolSpecifications, AiMessage aiMessage) {
    if (!aiMessage.hasToolExecutionRequests() || toolSpecifications.isEmpty()) {
      return Collections.emptyList();
    }

    return aiMessage.toolExecutionRequests().stream().map(this::asToolCall).toList();
  }

  private ToolCall asToolCall(ToolExecutionRequest toolExecutionRequest) {
    return asToolCall(
        toolExecutionRequest.id(), toolExecutionRequest.name(), toolExecutionRequest.arguments());
  }

  private ToolCall asToolCall(String id, String name, String inputJson) {
    try {
      Map<String, Object> arguments =
          objectMapper.readValue(inputJson, STRING_OBJECT_MAP_TYPE_REFERENCE);
      return new ToolCall(id, name, arguments);
    } catch (Exception e) {
      throw new ConnectorException(
          "Failed to parse tool call results for tool %s".formatted(name), e);
    }
  }
}

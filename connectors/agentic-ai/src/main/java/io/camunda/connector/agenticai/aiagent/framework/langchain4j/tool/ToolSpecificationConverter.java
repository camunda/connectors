/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.List;

public interface ToolSpecificationConverter {
  default List<ToolSpecification> asToolSpecifications(List<ToolDefinition> toolDefinitions) {
    return toolDefinitions.stream().map(this::asToolSpecification).toList();
  }

  ToolSpecification asToolSpecification(ToolDefinition toolDefinition);

  default List<ToolDefinition> asToolDefinitions(List<ToolSpecification> toolSpecifications) {
    return toolSpecifications.stream().map(this::asToolDefinition).toList();
  }

  ToolDefinition asToolDefinition(ToolSpecification toolSpecification);
}

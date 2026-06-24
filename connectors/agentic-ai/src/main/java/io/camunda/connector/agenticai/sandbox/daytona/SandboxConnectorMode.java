/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.daytona;

import static io.camunda.connector.agenticai.sandbox.daytona.SandboxConnectorMode.AiAgentToolMode.AI_AGENT_TOOL_ID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import org.jspecify.annotations.Nullable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = SandboxConnectorMode.AiAgentToolMode.class, name = AI_AGENT_TOOL_ID)
})
@TemplateDiscriminatorProperty(
    group = "connectorMode",
    label = "Connector mode",
    name = "type",
    description = "How this connector is used. Currently only AI Agent tool mode is supported.",
    defaultValue = AI_AGENT_TOOL_ID)
public sealed interface SandboxConnectorMode permits SandboxConnectorMode.AiAgentToolMode {

  @TemplateSubType(id = AI_AGENT_TOOL_ID, label = "AI Agent tool")
  record AiAgentToolMode(
      @FEEL
          @TemplateProperty(
              group = "connectorMode",
              label = "Tool call",
              description = "The tool call dispatched by the AI agent. Populated automatically.",
              type = TemplateProperty.PropertyType.Hidden,
              feel = FeelMode.required,
              defaultValue = "=toolCall")
          @Nullable Object toolCall)
      implements SandboxConnectorMode {

    @TemplateProperty(ignore = true)
    public static final String AI_AGENT_TOOL_ID = "aiAgentTool";
  }
}

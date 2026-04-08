/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.agentcore.runtime.model.request;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

public class AgentCoreRuntimeInput {

  @NotBlank
  @TemplateProperty(
      group = "agentConfig",
      label = "Agent Runtime ARN",
      description = "The ARN of the AgentCore Runtime agent to invoke.")
  private String agentRuntimeArn;

  @FEEL
  @NotBlank
  @TemplateProperty(
      group = "agentConfig",
      label = "Prompt",
      description = "The message or task to send to the agent.")
  private String prompt;

  @TemplateProperty(
      group = "agentConfig",
      label = "Session ID",
      description = "Optional session ID for multi-turn conversations.",
      optional = true)
  private String sessionId;

  public String getAgentRuntimeArn() {
    return agentRuntimeArn;
  }

  public void setAgentRuntimeArn(String agentRuntimeArn) {
    this.agentRuntimeArn = agentRuntimeArn;
  }

  public String getPrompt() {
    return prompt;
  }

  public void setPrompt(String prompt) {
    this.prompt = prompt;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }
}

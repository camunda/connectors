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
import jakarta.validation.constraints.NotNull;

public class AgentCoreRuntimeInput {

  @NotBlank
  @TemplateProperty(
      group = "agentConfig",
      label = "Agent Runtime ARN",
      description = "The ARN of the AgentCore Runtime agent to invoke.")
  private String agentRuntimeArn;

  @FEEL
  @NotNull
  @TemplateProperty(
      group = "agentConfig",
      label = "Payload",
      description =
          "The payload to send to the agent. Use a FEEL expression to define the structure, e.g. ={inputText: \"your prompt\"}.")
  private Object payload;

  @TemplateProperty(
      group = "agentConfig",
      label = "Session ID",
      description = "Optional session ID for multi-turn conversations.",
      optional = true)
  private String sessionId;

  @TemplateProperty(
      group = "agentConfig",
      label = "Content type",
      description = "Content type of the payload. Defaults to application/json.",
      defaultValue = "application/json",
      optional = true)
  private String contentType;

  public String getAgentRuntimeArn() {
    return agentRuntimeArn;
  }

  public void setAgentRuntimeArn(String agentRuntimeArn) {
    this.agentRuntimeArn = agentRuntimeArn;
  }

  public Object getPayload() {
    return payload;
  }

  public void setPayload(Object payload) {
    this.payload = payload;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.agentcore.memory.model.request;

import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AgentCoreMemoryRequest extends AwsBaseRequest {

  @NotBlank
  @TemplateProperty(
      group = "configuration",
      label = "Memory ID",
      description = "The identifier of the AgentCore Memory resource.")
  private String memoryId;

  @NotBlank
  @TemplateProperty(
      group = "configuration",
      label = "Namespace",
      description =
          "Namespace prefix to scope memory records (e.g. 'customer/12345'). Required by AWS.")
  private String namespace;

  @Valid @NotNull private MemoryOperation operation;

  public String getMemoryId() {
    return memoryId;
  }

  public void setMemoryId(String memoryId) {
    this.memoryId = memoryId;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public MemoryOperation getOperation() {
    return operation;
  }

  public void setOperation(MemoryOperation operation) {
    this.operation = operation;
  }
}

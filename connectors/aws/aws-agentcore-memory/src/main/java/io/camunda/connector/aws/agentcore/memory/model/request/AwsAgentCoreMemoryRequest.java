/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.agentcore.memory.model.request;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AwsAgentCoreMemoryRequest extends AwsBaseRequest {

  @FEEL
  @NotBlank
  @TemplateProperty(
      group = "configuration",
      label = "Memory ID",
      description = "The ID of the pre-provisioned AgentCore Memory resource.",
      feel = FeelMode.optional,
      constraints = @PropertyConstraints(notEmpty = true))
  private String memoryId;

  @TemplateProperty(
      group = "configuration",
      label = "Max results",
      description = "Maximum number of memory records to return per call (1–100). Defaults to 20.",
      defaultValue = "20",
      defaultValueType = TemplateProperty.DefaultValueType.Number,
      optional = true)
  @Min(1)
  @Max(100)
  private Integer maxResults;

  @Valid @NotNull private MemoryOperation operation;

  public String getMemoryId() {
    return memoryId;
  }

  public void setMemoryId(String memoryId) {
    this.memoryId = memoryId;
  }

  public Integer getMaxResults() {
    return maxResults;
  }

  public void setMaxResults(Integer maxResults) {
    this.maxResults = maxResults;
  }

  public MemoryOperation getOperation() {
    return operation;
  }

  public void setOperation(MemoryOperation operation) {
    this.operation = operation;
  }
}

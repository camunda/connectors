/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.knowledgebase.model.request;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class BedrockKnowledgeBaseRequest extends AwsBaseRequest {

  @FEEL
  @NotBlank
  @TemplateProperty(
      group = "configuration",
      label = "Knowledge Base ID",
      description = "The ID of the Bedrock Knowledge Base to query.")
  private String knowledgeBaseId;

  @Valid @NotNull private KnowledgeBaseOperation operation;

  public String getKnowledgeBaseId() {
    return knowledgeBaseId;
  }

  public void setKnowledgeBaseId(String knowledgeBaseId) {
    this.knowledgeBaseId = knowledgeBaseId;
  }

  public KnowledgeBaseOperation getOperation() {
    return operation;
  }

  public void setOperation(KnowledgeBaseOperation operation) {
    this.operation = operation;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.providers.gcp;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateSubType(id = "documentAi", label = "Configuration for DocumentAI")
public final class DocumentAiRequestConfiguration implements GcpRequestConfiguration {

  @TemplateProperty(
      group = "configuration",
      id = "documentAiRegion",
      label = "Region",
      description = "Can be 'eu' or 'us'")
  private String region;

  @TemplateProperty(group = "configuration", id = "projectId", label = "Project ID")
  private String projectId;

  @TemplateProperty(
      group = "configuration",
      id = "processorId",
      label = "Processor ID",
      description = "The id of the processor used to parse the document")
  private String processorId;

  public DocumentAiRequestConfiguration() {}

  public DocumentAiRequestConfiguration(String region, String projectId, String processorId) {
    this.region = region;
    this.projectId = projectId;
    this.processorId = processorId;
  }

  public String getRegion() {
    return region;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getProcessorId() {
    return processorId;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public void setProcessorId(String processorId) {
    this.processorId = processorId;
  }
}

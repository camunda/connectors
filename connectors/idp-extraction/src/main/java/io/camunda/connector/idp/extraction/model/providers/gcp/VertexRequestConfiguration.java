/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.providers.gcp;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateSubType(id = "vertex", label = "Configuration for VertexAI")
public final class VertexRequestConfiguration implements GcpRequestConfiguration {

  @TemplateProperty(group = "configuration", id = "gcpRegion", label = "Region")
  private String region;

  @TemplateProperty(group = "configuration", id = "vertexProjectId", label = "Project ID")
  private String projectId;

  @TemplateProperty(
      group = "configuration",
      label = "Bucket name",
      description = "The Google Cloud Storage bucket where the document will be temporarily stored")
  private String bucketName;

  public VertexRequestConfiguration() {}

  public VertexRequestConfiguration(String region, String projectId, String bucketName) {
    this.region = region;
    this.projectId = projectId;
    this.bucketName = bucketName;
  }

  public String getRegion() {
    return region;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getBucketName() {
    return bucketName;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public void setBucketName(String bucketName) {
    this.bucketName = bucketName;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.codeinterpreter.model.request;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import jakarta.validation.constraints.NotBlank;

public class CodeInterpreterInput {

  @FEEL
  @NotBlank
  @TemplateProperty(
      group = "codeExecution",
      label = "Code",
      description = "The Python code to execute in the Code Interpreter sandbox.",
      constraints = @PropertyConstraints(notEmpty = true))
  private String code;

  @TemplateProperty(
      group = "codeExecution",
      label = "Session timeout (seconds)",
      description = "Session timeout in seconds (60–28800). Defaults to 300.",
      defaultValue = "300",
      defaultValueType = TemplateProperty.DefaultValueType.Number,
      optional = true)
  private Integer sessionTimeoutSeconds;

  @TemplateProperty(
      group = "codeExecution",
      label = "Max files to retrieve",
      description = "Maximum number of generated files to retrieve. Defaults to 10.",
      defaultValue = "10",
      defaultValueType = TemplateProperty.DefaultValueType.Number,
      optional = true)
  private Integer maxFiles;

  @TemplateProperty(
      group = "codeExecution",
      label = "Max total file size (bytes)",
      description = "Maximum total size of retrieved files in bytes. Defaults to 10 MB.",
      defaultValue = "10485760",
      defaultValueType = TemplateProperty.DefaultValueType.Number,
      optional = true)
  private Long maxTotalBytes;

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public Integer getSessionTimeoutSeconds() {
    return sessionTimeoutSeconds;
  }

  public void setSessionTimeoutSeconds(Integer sessionTimeoutSeconds) {
    this.sessionTimeoutSeconds = sessionTimeoutSeconds;
  }

  public Integer getMaxFiles() {
    return maxFiles;
  }

  public void setMaxFiles(Integer maxFiles) {
    this.maxFiles = maxFiles;
  }

  public Long getMaxTotalBytes() {
    return maxTotalBytes;
  }

  public void setMaxTotalBytes(Long maxTotalBytes) {
    this.maxTotalBytes = maxTotalBytes;
  }
}

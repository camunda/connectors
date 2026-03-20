/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.codeinterpreter.model.request;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import jakarta.validation.constraints.NotBlank;

public class CodeInterpreterRequest extends AwsBaseRequest {

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
      description = "Session timeout in seconds (60–28800). Defaults to 900.",
      defaultValue = "900",
      defaultValueType = TemplateProperty.DefaultValueType.Number,
      optional = true)
  private Integer sessionTimeoutSeconds;

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
}

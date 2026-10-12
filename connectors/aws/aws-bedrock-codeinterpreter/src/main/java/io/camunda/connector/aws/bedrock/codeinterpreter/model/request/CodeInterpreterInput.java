/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.codeinterpreter.model.request;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;

public class CodeInterpreterInput {

  @TemplateProperty(
      group = "codeExecution",
      label = "Language",
      description = "The programming language to use.",
      type = TemplateProperty.PropertyType.Dropdown,
      choices = {
        @TemplateProperty.DropdownPropertyChoice(value = "python", label = "Python"),
        @TemplateProperty.DropdownPropertyChoice(value = "javascript", label = "JavaScript"),
        @TemplateProperty.DropdownPropertyChoice(value = "typescript", label = "TypeScript")
      })
  private Language language;

  @FEEL
  @NotBlank
  @TemplateProperty(
      group = "codeExecution",
      label = "Code",
      description = "The code to execute in the Code Interpreter sandbox.")
  private String code;

  @TemplateProperty(
      group = "codeExecution",
      label = "Files",
      description =
          "Documents that are uploaded into the session workspace before the code runs, each as {document: ..., fileName: ...}.",
      tooltip =
          "Each file is saved as uploads/<fileName> in the workspace. The fileName is optional and defaults to the file name of the document (document-1, document-2, ... when it has none). "
              + "The sandbox is stateless: every execution starts with an empty workspace, so pass all files again on every call. "
              + "Files the code creates in the workspace are returned as documents in the response.",
      feel = FeelMode.required,
      optional = true)
  private List<FileUpload> files;

  @TemplateProperty(
      group = "session",
      label = "Code Interpreter ID",
      description = "The Code Interpreter identifier. Defaults to the AWS managed default.",
      defaultValue = "aws.codeinterpreter.v1",
      optional = true)
  private String codeInterpreterIdentifier;

  @TemplateProperty(
      group = "session",
      label = "Session timeout",
      description =
          "Session timeout as ISO 8601 duration (e.g. PT5M for 5 minutes). Defaults to PT5M.",
      defaultValue = "PT5M",
      optional = true)
  private Duration sessionTimeout;

  @Min(1)
  @TemplateProperty(
      group = "session",
      label = "Max files to retrieve",
      description = "Maximum number of generated files to retrieve. Defaults to 10.",
      defaultValue = "10",
      defaultValueType = TemplateProperty.DefaultValueType.Number,
      optional = true)
  private Integer maxFiles;

  @Min(1)
  @TemplateProperty(
      group = "session",
      label = "Max total file size",
      description = "Maximum total size of retrieved files in bytes. Defaults to 10 MB.",
      defaultValue = "10485760",
      defaultValueType = TemplateProperty.DefaultValueType.Number,
      optional = true)
  private Long maxTotalFileSize;

  public Language getLanguage() {
    return language;
  }

  public void setLanguage(Language language) {
    this.language = language;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public List<FileUpload> getFiles() {
    return files;
  }

  public void setFiles(List<FileUpload> files) {
    this.files = files;
  }

  public String getCodeInterpreterIdentifier() {
    return codeInterpreterIdentifier;
  }

  public void setCodeInterpreterIdentifier(String codeInterpreterIdentifier) {
    this.codeInterpreterIdentifier = codeInterpreterIdentifier;
  }

  public Duration getSessionTimeout() {
    return sessionTimeout;
  }

  public void setSessionTimeout(Duration sessionTimeout) {
    this.sessionTimeout = sessionTimeout;
  }

  public Integer getMaxFiles() {
    return maxFiles;
  }

  public void setMaxFiles(Integer maxFiles) {
    this.maxFiles = maxFiles;
  }

  public Long getMaxTotalFileSize() {
    return maxTotalFileSize;
  }

  public void setMaxTotalFileSize(Long maxTotalFileSize) {
    this.maxTotalFileSize = maxTotalFileSize;
  }
}

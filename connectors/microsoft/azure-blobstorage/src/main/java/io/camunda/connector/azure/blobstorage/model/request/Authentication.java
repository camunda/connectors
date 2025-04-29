/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.blobstorage.model.request;

import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

public class Authentication {
  @FEEL
  @NotBlank
  @TemplateProperty(
      group = "authentication",
      id = "SASToken",
      label = "SAS token",
      binding = @TemplateProperty.PropertyBinding(name = "SASToken"))
  private String SASToken;

  @FEEL
  @NotBlank
  @TemplateProperty(
      group = "authentication",
      id = "SASUrl",
      label = "SAS URL",
      binding = @TemplateProperty.PropertyBinding(name = "SASUrl"))
  private String SASUrl;

  public String getSAStoken() {
    return SASToken;
  }

  public void setSAStoken(String SASToken) {
    this.SASToken = SASToken;
  }

  public String getSASUrl() {
    return SASUrl;
  }

  public void setSASUrl(String SASUrl) {
    this.SASUrl = SASUrl;
  }
}

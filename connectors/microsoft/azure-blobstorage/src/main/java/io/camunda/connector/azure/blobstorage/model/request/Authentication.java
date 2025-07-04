/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.blobstorage.model.request;

import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

public class Authentication {
  @FEEL
  @TemplateProperty(
      group = "authentication",
      label = "SAS token",
      description =
          "Shared access signature (SAS) token of the container. Learn more in our <a href=\"https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/azure-blob-storage/#prerequisites\">documentation</a>.",
      feel = Property.FeelMode.optional)
  @NotBlank
  private String SASToken;

  @FEEL
  @TemplateProperty(
      group = "authentication",
      label = "SAS URL",
      description =
          "Shared access signature (SAS) URL of the container. Learn more in our <a href=\"https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/azure-blob-storage/#prerequisites\">documentation</a>.",
      feel = Property.FeelMode.optional)
  @NotBlank
  private String SASUrl;

  public String getSASToken() {
    return SASToken;
  }

  public void setSASToken(String SASToken) {
    this.SASToken = SASToken;
  }

  public String getSASUrl() {
    return SASUrl;
  }

  public void setSASUrl(String SASUrl) {
    this.SASUrl = SASUrl;
  }
}

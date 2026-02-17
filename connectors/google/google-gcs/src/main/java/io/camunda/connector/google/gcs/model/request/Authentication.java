/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.google.gcs.model.request;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

public class Authentication {
  @FEEL
  @TemplateProperty(
      group = "authentication",
      label = "JSON key of the service account",
      description =
          "This is the key of the service account in JSON format. See <a href=\"https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/google-cloud-storage/#authentication\" target=\"_blank\">documentation</a> for details.",
      feel = FeelMode.optional)
  @NotBlank
  private String jsonKey;

  public String getJsonKey() {
    return jsonKey;
  }

  public void setJsonKey(String jsonKey) {
    this.jsonKey = jsonKey;
  }
}

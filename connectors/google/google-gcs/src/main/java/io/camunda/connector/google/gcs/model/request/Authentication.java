/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.google.gcs.model.request;

import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

public class Authentication {
  @FEEL
  @TemplateProperty(
      group = "authentication",
      label = "JSON Key of the Service account",
      description =
          "The key of the service account in JSON format. See more details in the <a href=\"https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/google-cloud-storage/#authentication\" target=\"_blank\">documentation</a>.",
      feel = Property.FeelMode.optional)
  @NotBlank
  private String jsonKey;

  public String getJsonKey() {
    return jsonKey;
  }

  public void setJsonKey(String jsonKey) {
    this.jsonKey = jsonKey;
  }
}

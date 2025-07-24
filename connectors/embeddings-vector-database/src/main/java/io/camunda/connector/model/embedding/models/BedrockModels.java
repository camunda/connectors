/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.models;

import io.camunda.connector.generator.java.annotation.DropdownItem;

public enum BedrockModels {
  @DropdownItem(label = "Custom")
  Custom("Custom"),
  @DropdownItem(label = "amazon.titan-embed-text-v1")
  TitanEmbedTextV1("amazon.titan-embed-text-v1"),
  @DropdownItem(label = "amazon.titan-embed-text-v2:0")
  TitanEmbedTextV2("amazon.titan-embed-text-v2:0");

  private final String modelName;

  BedrockModels(String modelName) {
    this.modelName = modelName;
  }

  public String getModelName() {
    return modelName;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.models;

import io.camunda.connector.generator.java.annotation.DropdownItem;

public enum BedrockDimensions {
  @DropdownItem(label = "256")
  D256(256),
  @DropdownItem(label = "512")
  D512(512),
  @DropdownItem(label = "1024")
  D1024(1024);

  private final Integer dimensions;

  BedrockDimensions(Integer dimensions) {
    this.dimensions = dimensions;
  }

  public Integer getDimensions() {
    return dimensions;
  }
}

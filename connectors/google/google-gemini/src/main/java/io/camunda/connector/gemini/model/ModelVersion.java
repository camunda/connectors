/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gemini.model;

import io.camunda.connector.generator.java.annotation.DropdownItem;

public enum ModelVersion {
  @DropdownItem(label = "Custom")
  CUSTOM("custom"),
  GEMINI_2_5_PRO("gemini-2.5-pro"),
  GEMINI_2_5_FLASH("gemini-2.5-flash"),
  GEMINI_2_5_FLASH_IMAGE("gemini-2.5-flash-image"),
  GEMINI_2_5_FLASH_LITE("gemini-2.5-flash-lite"),
  GEMINI_2_0_FLASH("gemini-2.0-flash"),
  GEMINI_2_0_FLASH_LITE("gemini-2.0-flash-lite");

  final String version;

  ModelVersion(String version) {
    this.version = version;
  }

  public String getVersion() {
    return version;
  }
}

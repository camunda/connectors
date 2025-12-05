/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gemini.model;

public enum ModelVersion {
  GEMINI_1_5_FLASH_001("gemini-1.5-flash-001"),
  GEMINI_1_5_FLASH_002("gemini-1.5-flash-002"),
  GEMINI_1_5_PRO_001("gemini-1.5-pro-001"),
  GEMINI_1_5_PRO_002("gemini-1.5-pro-002"),
  GEMINI_1_0_PRO_001("gemini-1.0-pro-001"),
  GEMINI_1_0_PRO_002("gemini-1.0-pro-002"),
  GEMINI_1_0_PRO_VISION_001("gemini-1.0-pro-vision-001");

  final String version;

  ModelVersion(String version) {
    this.version = version;
  }

  public String getVersion() {
    return version;
  }
}

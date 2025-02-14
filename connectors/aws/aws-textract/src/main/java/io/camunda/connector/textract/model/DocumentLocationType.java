/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.model;

import io.camunda.connector.generator.java.annotation.EnumValue;

public enum DocumentLocationType {
  @EnumValue(label = "S3", order = 1)
  S3,
  @EnumValue(label = "Camunda Document", order = 0)
  UPLOADED;
}

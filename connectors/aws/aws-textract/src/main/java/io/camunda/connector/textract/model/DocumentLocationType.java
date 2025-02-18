/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.model;

import io.camunda.connector.generator.java.annotation.DropdownItem;

public enum DocumentLocationType {
  @DropdownItem(label = "Camunda Document")
  UPLOADED,
  @DropdownItem(label = "S3")
  S3
}

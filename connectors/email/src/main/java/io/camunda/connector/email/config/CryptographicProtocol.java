/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.config;

import io.camunda.connector.generator.java.annotation.DropdownItem;

public enum CryptographicProtocol {
  @DropdownItem(label = "TLS")
  TLS,
  @DropdownItem(label = "SSL")
  SSL,
  @DropdownItem(label = "None")
  NONE
}

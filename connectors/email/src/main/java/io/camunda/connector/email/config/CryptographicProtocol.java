/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.config;

import io.camunda.connector.generator.java.annotation.EnumValue;

public enum CryptographicProtocol {
  @EnumValue(label = "None", order = 2)
  NONE,
  @EnumValue(label = "TLS", order = 0)
  TLS,
  @EnumValue(label = "SSL", order = 1)
  SSL;
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gemini.model;

public enum BlockingDegree {
  OFF,
  BLOCK_ONLY_HIGH,
  BLOCK_MEDIUM_AND_ABOVE,
  BLOCK_LOW_AND_ABOVE
}

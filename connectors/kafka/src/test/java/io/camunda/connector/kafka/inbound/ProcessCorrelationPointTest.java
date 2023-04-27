/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import io.camunda.connector.impl.inbound.ProcessCorrelationPoint;

public class ProcessCorrelationPointTest extends ProcessCorrelationPoint {

  @Override
  public String getId() {
    return "test-correlation-id";
  }

  @Override
  public int compareTo(ProcessCorrelationPoint o) {
    return 0;
  }
}

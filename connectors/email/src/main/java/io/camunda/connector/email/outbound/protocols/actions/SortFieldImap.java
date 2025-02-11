/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols.actions;

import io.camunda.connector.generator.java.annotation.EnumLabel;

public enum SortFieldImap {
  @EnumLabel(label = "Received Date", value = "RECEIVED_DATE", order = 0)
  RECEIVED_DATE,
  @EnumLabel(label = "Sent Date", value = "SENT_DATE", order = 1)
  SENT_DATE,
  @EnumLabel(label = "Size", value = "SIZE", order = 2)
  SIZE;
}

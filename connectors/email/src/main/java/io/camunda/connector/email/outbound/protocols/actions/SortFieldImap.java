/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols.actions;

import io.camunda.connector.generator.java.annotation.DropdownItem;

public enum SortFieldImap {
  @DropdownItem(label = "Received Date")
  RECEIVED_DATE,
  @DropdownItem(label = "Sent Date")
  SENT_DATE,
  @DropdownItem(label = "Size")
  SIZE;
}

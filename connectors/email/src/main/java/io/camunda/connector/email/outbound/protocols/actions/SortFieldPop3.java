/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols.actions;

import io.camunda.connector.generator.java.annotation.DropdownItem;

public enum SortFieldPop3 {
  @DropdownItem(label = "Sent Date", order = 0)
  SENT_DATE,
  @DropdownItem(label = "Size", order = 1)
  SIZE;
}

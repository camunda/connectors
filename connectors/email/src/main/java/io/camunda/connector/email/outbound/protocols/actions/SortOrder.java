/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.outbound.protocols.actions;

import java.util.function.Function;

public enum SortOrder {
  ASC(comparison -> comparison),
  DESC(comparison -> -comparison);

  private final Function<Integer, Integer> comparator;

  SortOrder(Function<Integer, Integer> comparator) {
    this.comparator = comparator;
  }

  public Integer order(Integer value) {
    return comparator.apply(value);
  }
}

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

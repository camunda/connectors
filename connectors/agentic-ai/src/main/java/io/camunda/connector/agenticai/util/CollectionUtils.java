/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util;

import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;

public class CollectionUtils {

  /**
   * Computes the differences between two collections and applies a handler function to process the
   * changes.
   *
   * @param baseCollection The original collection to compare against
   * @param compareCollection The collection to compare with the base
   * @param differenceHandler A function that processes the added and removed items
   * @param <T> The type of elements in the collections
   * @param <R> The return type of the difference handler
   * @return The result of applying the difference handler to the added and removed items
   */
  public static <T, R> R computeListItemChanges(
      Collection<T> baseCollection,
      Collection<T> compareCollection,
      BiFunction<List<T>, List<T>, R> differenceHandler) {
    final var added =
        compareCollection.stream().filter(item -> !baseCollection.contains(item)).toList();
    final var removed =
        baseCollection.stream().filter(item -> !compareCollection.contains(item)).toList();

    return differenceHandler.apply(added, removed);
  }
}

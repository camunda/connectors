/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.http.base.utils;

import io.camunda.document.CamundaDocument;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DocumentHelper {

  /**
   * Traverse the {@link Map} recursively and create all Documents found in the map.
   *
   * @param input the input map
   * @param transformer the transformer to apply to each document (e.g. convert to Base64 etc)
   */
  public Object createDocuments(Object input, Function<CamundaDocument, Object> transformer) {
    return switch (input) {
      case Map<?, ?> map ->
          map.entrySet().stream()
              .map(
                  (Map.Entry<?, ?> e) ->
                      new AbstractMap.SimpleEntry<>(
                          e.getKey(), createDocuments(e.getValue(), transformer)))
              .collect(
                  Collectors.toMap(
                      AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

      case Collection list -> list.stream().map(o -> createDocuments(o, transformer)).toList();
      case CamundaDocument doc -> transformer.apply(doc);
      default -> input;
    };
  }
}

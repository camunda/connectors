/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http.client.utils;

import io.camunda.document.CamundaDocument;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
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
  public Object parseDocumentsInBody(Object input, Function<CamundaDocument, Object> transformer) {
    return switch (input) {
      case Map<?, ?> map ->
          map.entrySet().stream()
              .filter(e -> e.getValue() != null)
              .map(
                  (Map.Entry<?, ?> e) ->
                      new AbstractMap.SimpleEntry<>(
                          e.getKey(), parseDocumentsInBody(e.getValue(), transformer)))
              .collect(
                  Collectors.toMap(
                      AbstractMap.SimpleEntry::getKey,
                      AbstractMap.SimpleEntry::getValue,
                      (a, b) -> b,
                      () -> new HashMap<>(map)));
      case Collection list -> list.stream().map(o -> parseDocumentsInBody(o, transformer)).toList();
      case CamundaDocument doc -> transformer.apply(doc);
      case null -> null;
      default -> input;
    };
  }
}

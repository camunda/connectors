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

import java.util.List;
import java.util.Map;

public class HeadersHelper {

  public static String getHeaderIgnoreCase(Map<String, List<String>> headers, String headerName) {
    return headers.entrySet().stream()
        .filter(e -> e.getKey().equalsIgnoreCase(headerName))
        .map(Map.Entry::getValue)
        .map(List::getFirst)
        .map(Object::toString)
        .findFirst()
        .orElse(null);
  }

  /**
   * Flattens headers to the format Map<String, Object> where value can be either a String or a List
   * of Strings. If a header has multiple values, the list is preserved, otherwise the single value
   * is returned as a String.
   */
  public static Map<String, Object> flattenHeaders(Map<String, List<String>> headers) {
    if (headers == null) {
      return null;
    }
    return headers.entrySet().stream()
        .collect(
            java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> {
                  List<String> values = e.getValue();
                  if (values == null || values.isEmpty()) {
                    return "";
                  } else if (values.size() == 1) {
                    return values.getFirst();
                  } else {
                    return values;
                  }
                }));
  }
}

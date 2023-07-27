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
package io.camunda.connector.test;

import java.util.HashMap;
import java.util.Map;

public class ConnectorContextTestUtil {

  @SuppressWarnings("unchecked")
  public static void addVariable(String key, String value, Map<String, Object> variables) {
    String[] path = key.split("\\.");
    if (path.length < 1) {
      throw new IllegalArgumentException("Invalid variable name: " + key);
    }
    Map<String, Object> current = variables;
    for (int i = 0; i < path.length - 1; i++) {
      current = (Map<String, Object>) current.computeIfAbsent(path[i], k -> new HashMap<>());
    }
    current.put(path[path.length - 1], value);
  }

  // this allows to create nested maps in place, like Map.of("a", Map.of("b", "c"))
  @SuppressWarnings("unchecked")
  public static Map<String, ?> replaceImmutableMaps(Map<String, ?> properties) {
    Map<String, Object> mutableProperties = new HashMap<>();
    for (Map.Entry<String, ?> entry : properties.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof Map) {
        value = replaceImmutableMaps((Map<String, ?>) value);
      }
      mutableProperties.put(entry.getKey(), value);
    }
    return mutableProperties;
  }
}

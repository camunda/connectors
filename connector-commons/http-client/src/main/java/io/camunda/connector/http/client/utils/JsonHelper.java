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

import io.camunda.connector.http.client.HttpClientObjectMapperSupplier;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class JsonHelper {

  private static final ObjectMapper objectMapper = HttpClientObjectMapperSupplier.getCopy();

  public static boolean isJsonStringValid(String jsonString) {
    if (jsonString == null) {
      return false;
    }
    try {
      JsonNode jsonNode = objectMapper.readTree(jsonString);
      return jsonNode.isObject() || jsonNode.isArray();
    } catch (JacksonException e) {
      return false;
    }
  }
}

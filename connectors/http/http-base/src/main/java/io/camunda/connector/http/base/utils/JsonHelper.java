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
package io.camunda.connector.http.base.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonParseException;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.util.Optional;

public class JsonHelper {

  private static final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;

  public static JsonNode getAsJsonElement(Object body) {
    if (body instanceof String stringBody) {
      try {
        return isJsonStringValid(stringBody) ? objectMapper.readTree(stringBody) : null;
      } catch (JsonProcessingException e) {
        throw new ConnectorException("Failed to parse JSON string: " + stringBody, e);
      }
    } else {
      return Optional.ofNullable(body).map(objectMapper::<JsonNode>valueToTree).orElse(null);
    }
  }

  public static boolean isJsonStringValid(String jsonString) {
    try {
      JsonNode jsonNode = objectMapper.readTree(jsonString);
      return jsonNode.isObject() || jsonNode.isArray();
    } catch (JsonParseException | IOException e) {
      return false;
    }
  }

  public static boolean isJsonValid(Object maybeJson) {
    return getAsJsonElement(maybeJson) != null;
  }
}

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
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.util.Optional;

public class JsonHelper {

  public static JsonNode getAsJsonElement(Object body, final ObjectMapper mapper)
      throws JsonProcessingException {
    if (body instanceof String stringBody && isJsonValid(stringBody)) {
      return mapper.readTree(stringBody);
    } else {
      return Optional.ofNullable(body).map(mapper::<JsonNode>valueToTree).orElse(null);
    }
  }

  public static boolean isJsonValid(String jsonString) {
    try {
      ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;
      JsonNode jsonNode = objectMapper.readTree(jsonString);
      return jsonNode.isObject() || jsonNode.isArray();
    } catch (JsonParseException | IOException e) {
      return false;
    }
  }
}

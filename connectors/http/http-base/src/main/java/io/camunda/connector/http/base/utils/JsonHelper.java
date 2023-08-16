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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(JsonHelper.class);

  public static JsonNode getAsJsonElement(final String strResponse, final ObjectMapper mapper) {
    return Optional.ofNullable(strResponse)
        .filter(response -> !response.isBlank())
        .map(
            response -> {
              try {
                return mapper.readTree(response);
              } catch (JsonProcessingException e) {
                LOGGER.error("Wasn't able to create a JSON node from string: " + strResponse);
                throw new RuntimeException(e);
              }
            })
        .orElse(null);
  }
}

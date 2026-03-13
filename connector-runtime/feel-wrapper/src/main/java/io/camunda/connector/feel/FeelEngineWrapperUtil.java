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
package io.camunda.connector.feel;

import static io.camunda.connector.feel.JacksonSupport.MAP_TYPE_REFERENCE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility methods for FEEL expression evaluation. */
public class FeelEngineWrapperUtil {

  private static final Logger LOG = LoggerFactory.getLogger(FeelEngineWrapperUtil.class);
  private static final String ERROR_CONTEXT_IS_NULL = "Context is null";
  private static final ObjectMapper DEFAULT_OBJECT_MAPPER =
      ConnectorsObjectMapperSupplier.getCopy();

  public static Map<String, Object> wrapResponse(Object response) {
    Map<String, Object> responseContext = new HashMap<>();
    responseContext.put("response", response);
    return responseContext;
  }

  /**
   * Merges multiple variable objects into a single map. Each object is converted to a map using
   * Jackson and then merged into the result.
   *
   * @param objectMapper the ObjectMapper to use for conversion
   * @param variables the variable objects to merge
   * @return a merged map of all variables
   * @throws IllegalArgumentException if variables array is null or cannot be parsed
   */
  public static Map<String, Object> mergeMapVariables(
      ObjectMapper objectMapper, Object... variables) {
    try {
      Objects.requireNonNull(variables, ERROR_CONTEXT_IS_NULL);
      Map<String, Object> variablesMap = new HashMap<>();
      for (Object o : variables) {
        if (o != null) {
          tryConvertToMap(objectMapper, o).ifPresent(variablesMap::putAll);
        }
      }
      return variablesMap;
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(
          String.format("Unable to parse '%s' as context", (Object) variables), ex);
    }
  }

  /**
   * Merges multiple variable objects into a single map using the default ObjectMapper.
   *
   * @param variables the variable objects to merge
   * @return a merged map of all variables
   * @throws IllegalArgumentException if variables array is null or cannot be parsed
   */
  public static Map<String, Object> mergeMapVariables(Object... variables) {
    return mergeMapVariables(DEFAULT_OBJECT_MAPPER, variables);
  }

  private static Optional<Map<String, Object>> tryConvertToMap(
      ObjectMapper objectMapper, Object o) {
    try {
      return Optional.of(objectMapper.convertValue(o, MAP_TYPE_REFERENCE));
    } catch (IllegalArgumentException ex) {
      LOG.warn(ex.getMessage(), ex);
      return Optional.empty();
    }
  }
}

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
package io.camunda.connector.intrinsic;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

public class IntrinsicFunctionParameterBinder {

  private final ObjectMapper objectMapper;

  public IntrinsicFunctionParameterBinder(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Object[] bindParameters(Method method, IntrinsicFunctionParams params) {
    var parameterTypes = method.getParameterTypes();
    var parameterCount = parameterTypes.length;
    var parameterAnnotations = method.getParameterAnnotations();
    var parameterValues = new Object[parameterCount];

    for (var i = 0; i < parameterCount; i++) {
      var parameter = method.getParameters()[i];
      var parameterType = parameterTypes[i];
      var parameterAnnotationsList = List.of(parameterAnnotations[i]);
      parameterValues[i] =
          bindParameter(parameter, parameterType, parameterAnnotationsList, i, params);
    }
    return parameterValues;
  }

  private Object bindParameter(
      Parameter parameter,
      Class<?> type,
      List<Annotation> annotations,
      int index,
      IntrinsicFunctionParams params) {

    if (params instanceof IntrinsicFunctionParams.Positional positionalParams) {
      if (index >= positionalParams.params().size()) {
        if (annotations.stream().noneMatch(a -> a instanceof Nullable)) {
          throw new IllegalArgumentException(
              "Parameter at index " + index + " is required but not provided");
        }
        return null;
      }

      try {
        return objectMapper.convertValue(positionalParams.params().get(index), type);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "Failed to convert parameter "
                + parameter.getName()
                + " at index "
                + index
                + " to type "
                + type,
            e);
      }
    } else {
      throw new IllegalArgumentException("Unsupported parameter type: " + params);
    }
  }
}

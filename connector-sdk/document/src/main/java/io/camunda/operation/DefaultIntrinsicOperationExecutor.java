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
package io.camunda.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link IntrinsicOperationExecutor} that discovers operations via the service
 * provider interface.
 */
public class DefaultIntrinsicOperationExecutor implements IntrinsicOperationExecutor {

  private final Logger LOGGER = LoggerFactory.getLogger(DefaultIntrinsicOperationExecutor.class);

  private record OperationSource(IntrinsicOperationProvider provider, Method method) {}

  private final Map<String, OperationSource> operationSources = new HashMap<>();
  private final IntrinsicOperationParameterBinder parameterBinder;
  private final ObjectMapper objectMapper;

  public DefaultIntrinsicOperationExecutor(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.parameterBinder = new IntrinsicOperationParameterBinder(objectMapper);

    final var operationProviders =
        ServiceLoader.load(IntrinsicOperationProvider.class).stream()
            .map(ServiceLoader.Provider::get)
            .toList();

    if (operationProviders.isEmpty()) {
      LOGGER.warn(
          "No intrinsic operation providers found. "
              + "Please make sure to provide at least one implementation of IntrinsicOperationProvider.");
      return;
    }

    for (var provider : operationProviders) {
      final var methodsByOperationName = getDeclaredMethods(provider);
      for (var entry : methodsByOperationName.entrySet()) {
        if (operationSources.containsKey(entry.getKey())) {
          throw new IllegalArgumentException(
              "Operation with name: "
                  + entry.getKey()
                  + " duplicated in providers: "
                  + provider.getClass().getName()
                  + " and "
                  + operationSources.get(entry.getKey()).provider.getClass().getName());
        }
        operationSources.put(entry.getKey(), new OperationSource(provider, entry.getValue()));
      }
    }
  }

  @Override
  public <T> T execute(String operationName, IntrinsicOperationParams params, Class<T> resultType) {
    final var source = operationSources.get(operationName);
    if (source == null) {
      throw new IllegalArgumentException("No operation found with name: " + operationName);
    }
    final var arguments = parameterBinder.bindParameters(source.method, params);
    try {
      final Object result = source.method.invoke(source.provider, arguments);
      return objectMapper.convertValue(result, resultType);
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to execute operation: "
              + operationName
              + " and retrieve a result of type: "
              + resultType.getName(),
          e);
    }
  }

  private Map<String, Method> getDeclaredMethods(IntrinsicOperationProvider provider) {
    final var methodsByOperationName =
        Arrays.stream(provider.getClass().getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(IntrinsicOperation.class))
            .collect(
                Collectors.toMap(
                    method -> method.getAnnotation(IntrinsicOperation.class).name(),
                    method -> method));
    if (methodsByOperationName.isEmpty()) {
      throw new IllegalArgumentException(
          "No intrinsic operations found in provider "
              + provider.getClass().getName()
              + ". "
              + "At least one method with @IntrinsicOperation expected.");
    }
    return methodsByOperationName;
  }
}

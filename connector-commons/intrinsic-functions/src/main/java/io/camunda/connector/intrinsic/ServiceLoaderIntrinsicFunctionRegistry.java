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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceLoaderIntrinsicFunctionRegistry implements IntrinsicFunctionRegistry {

  private final Logger LOGGER =
      LoggerFactory.getLogger(ServiceLoaderIntrinsicFunctionRegistry.class);

  private final Map<String, IntrinsicFunctionSource> operationSources;

  public ServiceLoaderIntrinsicFunctionRegistry() {

    final var operationProviders = loadProviders();
    if (operationProviders.isEmpty()) {
      LOGGER.warn(
          "No intrinsic function providers found. "
              + "Please make sure to provide at least one implementation of IntrinsicFunctionProvider.");
      operationSources = Map.of();
      return;
    }
    operationSources = parseProviders(operationProviders);
  }

  @Override
  public IntrinsicFunctionSource getIntrinsicFunction(String name) {
    return operationSources.get(name);
  }

  private List<IntrinsicFunctionProvider> loadProviders() {
    return ServiceLoader.load(IntrinsicFunctionProvider.class).stream()
        .map(ServiceLoader.Provider::get)
        .toList();
  }

  private Map<String, IntrinsicFunctionSource> parseProviders(
      List<IntrinsicFunctionProvider> providers) {
    final var sources = new HashMap<String, IntrinsicFunctionSource>();
    for (var provider : providers) {
      final var methodsByOperationName = getDeclaredMethods(provider);
      for (var entry : methodsByOperationName.entrySet()) {
        if (sources.containsKey(entry.getKey())) {
          throw new IllegalArgumentException(
              "Intrinsic function with name: "
                  + entry.getKey()
                  + " duplicated in providers: "
                  + provider.getClass().getName()
                  + " and "
                  + sources.get(entry.getKey()).provider().getClass().getName());
        }
        sources.put(entry.getKey(), new IntrinsicFunctionSource(provider, entry.getValue()));
      }
    }
    LOGGER.info(
        "Intrinsic functions loaded: {}",
        sources.keySet().stream().sorted().collect(Collectors.joining(", ")));
    return sources;
  }

  private Map<String, Method> getDeclaredMethods(IntrinsicFunctionProvider provider) {
    final var methodsByOperationName =
        Arrays.stream(provider.getClass().getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(IntrinsicFunction.class))
            .collect(
                Collectors.toMap(
                    method -> method.getAnnotation(IntrinsicFunction.class).name(),
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

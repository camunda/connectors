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
package io.camunda.connector.runtime.core.intrinsic;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.document.jackson.IntrinsicFunctionExecutor;
import io.camunda.connector.document.jackson.IntrinsicFunctionParams;
import java.util.Arrays;

/**
 * An implementation of {@link IntrinsicFunctionExecutor} that discovers operations via the service
 * provider interface.
 */
public class DefaultIntrinsicFunctionExecutor implements IntrinsicFunctionExecutor {

  private final IntrinsicFunctionParameterBinder parameterBinder;
  private final IntrinsicFunctionRegistry registry;

  public DefaultIntrinsicFunctionExecutor(ObjectMapper mapper) {
    this.parameterBinder = new IntrinsicFunctionParameterBinder(mapper);
    this.registry = new ServiceLoaderIntrinsicFunctionRegistry();
  }

  @Override
  public Object execute(String functionName, IntrinsicFunctionParams params) {
    final var source = registry.getIntrinsicFunction(functionName);
    if (source == null) {
      throw new IllegalArgumentException("No intrinsic function found with name: " + functionName);
    }
    final var arguments = parameterBinder.bindParameters(source.method(), params);
    try {
      return source.method().invoke(source.provider(), arguments);
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to execute intrinsic function: "
              + functionName
              + " with arguments: "
              + Arrays.toString(arguments),
          e);
    }
  }
}

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
package io.camunda.document.operation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultOperationExecutor implements OperationExecutor {

  private final Map<String, OperationProvider> operationProviders;

  public DefaultOperationExecutor(List<OperationProvider> operationProviders) {
    List<OperationProvider> updatedProviders = new ArrayList<>(operationProviders);
    updatedProviders.add(new DefaultOperationProvider());

    this.operationProviders =
        updatedProviders.stream()
            .map(provider -> Map.entry(provider.getOperationNames(), provider))
            .flatMap(
                entry -> entry.getKey().stream().map(name -> Map.entry(name, entry.getValue())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Operation<?> getOperation(String operationName) {
    OperationProvider provider = operationProviders.get(operationName);
    if (provider != null) {
      return provider.getOperation(operationName);
    }
    throw new IllegalArgumentException("No operation found with name '" + operationName + "'");
  }

  @Override
  public <T> OperationResult<T> execute(
      String operationName, List<? extends OperationParameter> arguments) {
    Operation<?> operation = getOperation(operationName);
    return (OperationResult<T>) operation.execute(arguments);
  }
}

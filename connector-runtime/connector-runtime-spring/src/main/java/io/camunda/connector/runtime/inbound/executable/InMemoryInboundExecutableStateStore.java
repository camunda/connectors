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
package io.camunda.connector.runtime.inbound.executable;

import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Thread-safe in-memory implementation of {@link InboundExecutableStateStore}. */
public class InMemoryInboundExecutableStateStore implements InboundExecutableStateStore {

  private final Map<ExecutableId, RegisteredExecutable> executables = new ConcurrentHashMap<>();

  @Override
  public RegisteredExecutable get(ExecutableId id) {
    return executables.get(id);
  }

  @Override
  public void put(ExecutableId id, RegisteredExecutable executable) {
    executables.put(id, executable);
  }

  @Override
  public void putAll(Map<ExecutableId, RegisteredExecutable> newExecutables) {
    executables.putAll(newExecutables);
  }

  @Override
  public RegisteredExecutable remove(ExecutableId id) {
    return executables.remove(id);
  }

  @Override
  public void replace(ExecutableId id, RegisteredExecutable executable) {
    executables.replace(id, executable);
  }

  @Override
  public Collection<RegisteredExecutable> getAllExecutables() {
    return executables.values();
  }

  @Override
  public Set<ExecutableId> getExecutableIdsForProcess(String bpmnProcessId, String tenantId) {
    return executables.entrySet().stream()
        .filter(entry -> matchesProcess(entry.getValue(), bpmnProcessId, tenantId))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  private boolean matchesProcess(
      RegisteredExecutable executable, String bpmnProcessId, String tenantId) {
    var elements = getElementsFromExecutable(executable);
    if (elements.isEmpty()) {
      return false;
    }
    var firstElement = elements.getFirst();
    return firstElement.element().bpmnProcessId().equals(bpmnProcessId)
        && firstElement.element().tenantId().equals(tenantId);
  }

  private List<InboundConnectorElement> getElementsFromExecutable(RegisteredExecutable executable) {
    return switch (executable) {
      case RegisteredExecutable.Activated activated -> activated.context().connectorElements();
      case RegisteredExecutable.Cancelled cancelled -> cancelled.context().connectorElements();
      case RegisteredExecutable.ConnectorNotRegistered notRegistered ->
          notRegistered.data().connectorElements();
      case RegisteredExecutable.FailedToActivate failed -> failed.data().connectorElements();
      case RegisteredExecutable.InvalidDefinition invalid -> invalid.data().connectorElements();
    };
  }
}

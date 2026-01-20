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
package io.camunda.connector.runtime.inbound.state;

import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableEvent;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import io.camunda.connector.runtime.inbound.state.model.ImportResult;
import io.camunda.connector.runtime.inbound.state.model.ProcessDefinitionRef;
import io.camunda.connector.runtime.inbound.state.model.StateUpdateResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessStateManagerImpl implements ProcessStateManager {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessStateManagerImpl.class);

  private final ProcessStateContainer processStateContainer;
  private final ProcessDefinitionInspector processDefinitionInspector;
  private final InboundExecutableRegistry executableRegistry;

  public ProcessStateManagerImpl(
      ProcessStateContainer processStateContainer,
      ProcessDefinitionInspector processDefinitionInspector,
      InboundExecutableRegistry executableRegistry) {
    this.processStateContainer = processStateContainer;
    this.processDefinitionInspector = processDefinitionInspector;
    this.executableRegistry = executableRegistry;
  }

  @Override
  public void update(ImportResult processDefinitions) {
    StateUpdateResult result = processStateContainer.compareAndUpdate(processDefinitions);

    if (result.isEmpty()) {
      LOG.debug("No process state changes detected");
      return;
    }

    // For each affected process, fetch connector elements for all active versions and publish event
    for (var entry : result.affectedProcesses().entrySet()) {
      var processRef = entry.getKey();
      var activeVersionKeys = entry.getValue();
      publishProcessStateChangedEvent(processRef, activeVersionKeys);
    }
  }

  private void publishProcessStateChangedEvent(
      ProcessDefinitionRef processRef, Set<Long> activeVersionKeys) {
    try {
      Map<Long, List<InboundConnectorElement>> elementsByVersion = new HashMap<>();

      for (Long versionKey : activeVersionKeys) {
        var elements = getConnectors(processRef, versionKey);
        // Include version even if it has no connectors - registry needs to know about it
        elementsByVersion.put(versionKey, elements);
      }

      var event =
          new InboundExecutableEvent.ProcessStateChanged(
              processRef.bpmnProcessId(), processRef.tenantId(), elementsByVersion);

      LOG.debug(
          "Publishing ProcessStateChanged for process '{}' (tenant '{}'): {} active version(s)",
          processRef.bpmnProcessId(),
          processRef.tenantId(),
          activeVersionKeys.size());

      executableRegistry.publishEvent(event);

    } catch (Exception e) {
      LOG.error(
          "Failed to publish state change event for process '{}' (tenant '{}')",
          processRef.bpmnProcessId(),
          processRef.tenantId(),
          e);
    }
  }

  private List<InboundConnectorElement> getConnectors(
      ProcessDefinitionRef id, long processDefinitionKey) {
    var elements = processDefinitionInspector.findInboundConnectors(id, processDefinitionKey);
    if (elements.isEmpty()) {
      LOG.debug("No inbound connectors found for process {}", id.bpmnProcessId());
    }
    return elements;
  }
}

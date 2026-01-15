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
import io.camunda.connector.runtime.inbound.state.model.ProcessDefinitionId;
import io.camunda.connector.runtime.inbound.state.model.ProcessDefinitionIdAndKey;
import io.camunda.connector.runtime.inbound.state.model.StateUpdateResult;
import java.util.List;
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
    result.toDeactivate().forEach(this::publishDeactivateEvent);
    result.toActivate().forEach(this::publishActivateEvent);
  }

  private List<InboundConnectorElement> getConnectors(
      ProcessDefinitionId id, long processDefinitionKey) {
    var elements = processDefinitionInspector.findInboundConnectors(id, processDefinitionKey);
    if (elements.isEmpty()) {
      LOG.debug("No inbound connectors found for process {}", id.bpmnProcessId());
    }
    return elements;
  }

  private void publishActivateEvent(ProcessDefinitionIdAndKey processDefinition) {
    try {
      var elements = getConnectors(processDefinition.id(), processDefinition.key());
      var event =
          new InboundExecutableEvent.Activated(
              processDefinition.id().tenantId(), processDefinition.key(), elements);
      executableRegistry.publishEvent(event);
    } catch (Exception e) {
      LOG.error(
          "Failed to activate inbound connectors for process definition {} with key {}",
          processDefinition.id().bpmnProcessId(),
          processDefinition.key(),
          e);
    }
  }

  private void publishDeactivateEvent(ProcessDefinitionIdAndKey processDefinition) {
    try {
      var event =
          new InboundExecutableEvent.Deactivated(
              processDefinition.id().tenantId(), processDefinition.key());
      executableRegistry.publishEvent(event);
    } catch (Exception e) {
      LOG.error(
          "Failed to deactivate inbound connectors for process definition with key {}",
          processDefinition.key(),
          e);
    }
  }
}

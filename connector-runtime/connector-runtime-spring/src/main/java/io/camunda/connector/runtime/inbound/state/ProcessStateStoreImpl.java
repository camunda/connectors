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
import io.camunda.connector.runtime.inbound.state.ProcessImportResult.ProcessDefinitionIdentifier;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult.ProcessDefinitionVersion;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessStateStoreImpl implements ProcessStateStore {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessStateStoreImpl.class);

  private final Map<String, ProcessState> processStates = new HashMap<>();

  private final ProcessDefinitionInspector processDefinitionInspector;
  private final InboundExecutableRegistry executableRegistry;

  public ProcessStateStoreImpl(
      ProcessDefinitionInspector processDefinitionInspector,
      InboundExecutableRegistry executableRegistry) {
    this.processDefinitionInspector = processDefinitionInspector;
    this.executableRegistry = executableRegistry;
  }

  @Override
  public void update(ProcessImportResult processDefinitions) {
    var entries = processDefinitions.processDefinitionVersions().entrySet();

    LOG.debug("Filtering only new process definitions...");
    var newlyDeployed =
        entries.stream()
            .filter(entry -> !processStates.containsKey(entry.getKey().bpmnProcessId()))
            .toList();

    LOG.debug("Filtering only updated process definitions...");
    var replacedWithDifferentVersion =
        entries.stream()
            .filter(
                entry -> {
                  var state = processStates.get(entry.getKey().bpmnProcessId());
                  return state != null && state.version() != entry.getValue().version();
                })
            .toList();

    LOG.debug("Filtering only old process definitions)");
    var deletedProcessIds =
        processStates.keySet().stream()
            .filter(
                processState ->
                    processDefinitions.processDefinitionVersions().keySet().stream()
                        .noneMatch(key -> key.bpmnProcessId().equals(processState)))
            .toList();

    logResult(newlyDeployed, replacedWithDifferentVersion, deletedProcessIds);

    newlyDeployed.forEach(this::newlyDeployed);
    replacedWithDifferentVersion.forEach(this::replacedWithDifferentVersion);
    deletedProcessIds.forEach(this::deleted);
  }

  private void newlyDeployed(
      Map.Entry<ProcessDefinitionIdentifier, ProcessDefinitionVersion> entry) {
    LOG.info("Activating newly deployed process definition: {}", entry.getKey().bpmnProcessId());
    try {
      processStates.compute(
          entry.getKey().bpmnProcessId(),
          (key, state) -> {
            var connectorElements = getConnectors(entry);
            activate(
                entry.getKey().tenantId(),
                entry.getValue().processDefinitionKey(),
                connectorElements);

            return new ProcessState(
                entry.getValue().version(),
                entry.getValue().processDefinitionKey(),
                entry.getKey().tenantId(),
                connectorElements);
          });
    } catch (Throwable e) {
      LOG.error("Failed to register process {}", entry.getKey().bpmnProcessId(), e);
      // ignore and continue with the next process
    }
  }

  private void replacedWithDifferentVersion(
      Map.Entry<ProcessDefinitionIdentifier, ProcessDefinitionVersion> entry) {
    try {
      LOG.info(
          "Activating newest deployed version process definition: {}",
          entry.getKey().bpmnProcessId());
      processStates.computeIfPresent(
          entry.getKey().bpmnProcessId(),
          (key, state) -> {
            var newConnectorElements = getConnectors(entry);
            deactivate(entry.getKey().tenantId(), state.processDefinitionKey);
            activate(
                entry.getKey().tenantId(),
                entry.getValue().processDefinitionKey(),
                newConnectorElements);
            return new ProcessState(
                entry.getValue().version(),
                entry.getValue().processDefinitionKey(),
                entry.getKey().tenantId(),
                newConnectorElements);
          });
    } catch (Throwable e) {
      LOG.error("Failed to update process {}", entry.getKey().bpmnProcessId(), e);
      // ignore and continue with the next process
    }
  }

  private void deleted(String processId) {
    try {
      LOG.info("Deactivating newly deployed process definition: {}", processId);
      processStates.computeIfPresent(
          processId,
          (key1, state) -> {
            var tenantId = state.tenantId;
            deactivate(tenantId, state.processDefinitionKey);
            return null;
          });
    } catch (Throwable e) {
      LOG.error("Failed to deregister process {}", processId, e);
      // ignore and continue with the next process
    }
  }

  private List<InboundConnectorElement> getConnectors(
      Map.Entry<ProcessDefinitionIdentifier, ProcessDefinitionVersion> entry) {
    var elements =
        processDefinitionInspector.findInboundConnectors(entry.getKey(), entry.getValue());
    if (elements.isEmpty()) {
      LOG.debug("No inbound connectors found for process {}", entry.getKey().bpmnProcessId());
    }
    return elements;
  }

  private void activate(
      String tenantId, long processDefinitionKey, List<InboundConnectorElement> elements) {
    var event = new InboundExecutableEvent.Activated(tenantId, processDefinitionKey, elements);
    executableRegistry.publishEvent(event);
  }

  private void deactivate(String tenantId, long processDefinitionKey) {
    var event = new InboundExecutableEvent.Deactivated(tenantId, processDefinitionKey);
    executableRegistry.publishEvent(event);
  }

  private void logResult(
      List<Map.Entry<ProcessDefinitionIdentifier, ProcessDefinitionVersion>> brandNew,
      List<Map.Entry<ProcessDefinitionIdentifier, ProcessDefinitionVersion>> upgraded,
      List<String> deleted) {

    if (brandNew.isEmpty() && upgraded.isEmpty() && deleted.isEmpty()) {
      LOG.debug("No changes in process elements");
      return;
    }
    LOG.info("Detected changes in process elements");
    LOG.info(". {} newly deployed", brandNew.size());
    for (var pd : brandNew) {
      LOG.info(
          ". Process: {}, version: {} for tenant: {}",
          pd.getKey().bpmnProcessId(),
          pd.getValue().version(),
          pd.getKey().tenantId());
    }
    LOG.info(". {} replaced with new version", upgraded.size());
    for (var pd : upgraded) {
      var oldVersion = processStates.get(pd.getKey().bpmnProcessId()).version();
      LOG.info(
          ". Process: {}, version {} - replaced with version {} for tenant: {}",
          pd.getKey().bpmnProcessId(),
          oldVersion,
          pd.getValue().version(),
          pd.getKey().tenantId());
    }
    LOG.info(". {} deleted", deleted.size());
    for (String key : deleted) {
      LOG.info(". . Process {}", key);
    }
  }

  private record ProcessState(
      int version,
      long processDefinitionKey,
      String tenantId,
      List<InboundConnectorElement> connectorElements) {}
}

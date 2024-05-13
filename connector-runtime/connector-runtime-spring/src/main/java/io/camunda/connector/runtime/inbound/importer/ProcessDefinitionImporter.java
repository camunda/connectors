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
package io.camunda.connector.runtime.inbound.importer;

import io.camunda.connector.runtime.inbound.lifecycle.InboundConnectorManager;
import io.camunda.connector.runtime.metrics.ConnectorMetrics.Inbound;
import io.camunda.operate.model.ProcessDefinition;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

public class ProcessDefinitionImporter {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionImporter.class);
  private final InboundConnectorManager connectorManager;
  private final ProcessDefinitionSearch search;
  private final MetricsRecorder metricsRecorder;

  private final Set<Long> registeredProcessDefinitionKeys = new HashSet<>();
  private final Map<String, ProcessDefinition> versionByBpmnProcessId = new HashMap<>();

  private boolean ready = false;

  @Autowired
  public ProcessDefinitionImporter(
      InboundConnectorManager inboundManager,
      ProcessDefinitionSearch search,
      @Autowired(required = false) MetricsRecorder metricsRecorder) {
    this.connectorManager = inboundManager;
    this.search = search;
    this.metricsRecorder = metricsRecorder;
  }

  @Scheduled(fixedDelayString = "${camunda.connector.polling.interval:5000}")
  public synchronized void scheduleImport() {
    try {
      var result = search.query();
      // We need to catch all exceptions here to ensure that the runtime is not shut down due to
      // health check failures
      // This is fixed in newer versions of the code since 8.6.x
      try {
        handleImportedDefinitions(result);
      } catch (Exception e) {
        LOG.error("Error during process definition import handling.", e);
      }
      ready = true;
    } catch (Exception e) {
      LOG.error("Failed to import process definitions", e);
      ready = false;
    }
  }

  public void handleImportedDefinitions(List<ProcessDefinition> definitions) {
    var notYetRegistered =
        definitions.stream()
            .filter(d -> !registeredProcessDefinitionKeys.contains(d.getKey()))
            .collect(Collectors.toSet());

    Set<Long> oldProcessDefinitionKeys = new HashSet<>();
    var upgraded =
        notYetRegistered.stream()
            .filter(
                d ->
                    versionByBpmnProcessId.containsKey(d.getBpmnProcessId())
                        && !d.getVersion()
                            .equals(versionByBpmnProcessId.get(d.getBpmnProcessId()).getVersion()))
            .peek(
                d ->
                    oldProcessDefinitionKeys.add(
                        versionByBpmnProcessId.get(d.getBpmnProcessId()).getKey()))
            .collect(Collectors.toSet());

    var brandNew = new HashSet<>(notYetRegistered);
    brandNew.removeAll(upgraded);

    var deleted =
        registeredProcessDefinitionKeys.stream()
            .filter(k -> definitions.stream().noneMatch(d -> Objects.equals(d.getKey(), k)))
            .filter(k -> !oldProcessDefinitionKeys.contains(k))
            .collect(Collectors.toSet());

    logResult(brandNew, upgraded, deleted);
    meter(brandNew.size());

    notYetRegistered.forEach(
        definition -> versionByBpmnProcessId.put(definition.getBpmnProcessId(), definition));

    var toDeregister = new HashSet<>(oldProcessDefinitionKeys);
    toDeregister.addAll(deleted);

    if (!toDeregister.isEmpty()) {
      connectorManager.handleDeletedProcessDefinitions(toDeregister);
    }
    if (!notYetRegistered.isEmpty()) {
      connectorManager.handleNewProcessDefinitions(notYetRegistered);
    }

    registeredProcessDefinitionKeys.addAll(
        notYetRegistered.stream().map(ProcessDefinition::getKey).toList());
    registeredProcessDefinitionKeys.removeAll(deleted);
    registeredProcessDefinitionKeys.removeAll(oldProcessDefinitionKeys);
  }

  private void logResult(
      Set<ProcessDefinition> brandNew, Set<ProcessDefinition> upgraded, Set<Long> deleted) {

    if (brandNew.isEmpty() && upgraded.isEmpty() && deleted.isEmpty()) {
      LOG.debug("No changes in process definitions");
      return;
    }
    LOG.info("Detected changes in process definitions");
    LOG.info(". {} newly deployed", brandNew.size());
    for (ProcessDefinition pd : brandNew) {
      LOG.info(
          ". Process: {}, version: {} for tenant: {}",
          pd.getBpmnProcessId(),
          pd.getVersion(),
          pd.getTenantId());
    }
    LOG.info(". {} replaced with new version", upgraded.size());
    for (ProcessDefinition pd : upgraded) {
      var oldVersion = versionByBpmnProcessId.get(pd.getBpmnProcessId()).getVersion();
      LOG.info(
          ". Process: {}, version {} - replaced with version {} for tenant: {}",
          pd.getBpmnProcessId(),
          oldVersion,
          pd.getVersion(),
          pd.getTenantId());
    }
    LOG.info(". {} deleted", deleted.size());
    for (Long key : deleted) {
      LOG.info(". . Key {}", key);
    }
  }

  private void meter(int count) {
    if (metricsRecorder != null) {
      metricsRecorder.increase(
          Inbound.METRIC_NAME_INBOUND_PROCESS_DEFINITIONS_CHECKED, null, null, count);
    }
  }

  public boolean isReady() {
    return ready;
  }
}

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

import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult.ProcessDefinitionIdentifier;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult.ProcessDefinitionVersion;
import io.camunda.connector.runtime.inbound.state.ProcessStateStore;
import io.camunda.connector.runtime.metrics.ConnectorsInboundMetrics;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

public class ProcessDefinitionImporter {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionImporter.class);
  private final ProcessStateStore stateStore;
  private final ProcessDefinitionSearch processDefinitionSearch;
  private final MessageSubscriptionSearch messageSubscriptionSearch;
  private final ConnectorsInboundMetrics connectorsInboundMetrics;

  private boolean ready = true;

  @Autowired
  public ProcessDefinitionImporter(
      ProcessStateStore stateStore,
      ProcessDefinitionSearch processDefinitionSearch,
      MessageSubscriptionSearch messageSubscriptionSearch,
      ConnectorsInboundMetrics connectorsInboundMetrics) {
    this.stateStore = stateStore;
    this.processDefinitionSearch = processDefinitionSearch;
    this.messageSubscriptionSearch = messageSubscriptionSearch;
    this.connectorsInboundMetrics = connectorsInboundMetrics;
  }

  @Scheduled(fixedDelayString = "${camunda.connector.polling.interval:5000}")
  public synchronized void scheduleLatestVersionImport() {
    try {
      var result = processDefinitionSearch.query();
      handleImportedDefinitions(result);
      ready = true;
    } catch (Exception e) {
      LOG.error("Failed to import process elements", e);
      ready = false;
    }
  }

  public void handleImportedDefinitions(List<ProcessDefinition> definitions) {
    if (definitions.isEmpty()) {
      return;
    }
    LOG.trace("Handle of the imported process definitions starts...");
    try {
      meter(definitions.size());
      var result =
          new ProcessImportResult(
              definitions.stream()
                  .collect(
                      Collectors.toMap(
                          definition ->
                              new ProcessDefinitionIdentifier(
                                  definition.getProcessDefinitionId(), definition.getTenantId()),
                          definition ->
                              new ProcessDefinitionVersion(
                                  definition.getProcessDefinitionKey(), definition.getVersion()))));
      LOG.trace("Updating the store with retrieved process definitions");
      stateStore.update(result);
    } catch (Exception e) {
      LOG.error("Failed to handle imported definitions", e);
    }
  }

  @Scheduled(
      fixedDelayString = "${camunda.connector.polling.interval:5000}",
      initialDelayString = "${camunda.connector.polling.initial-delay:2500}")
  public synchronized void scheduleMessageSubscriptionImport() {
    try {
      var result = messageSubscriptionSearch.query();
      LOG.trace("Handle of the imported message subscriptions starts...");
      meter(result.size());
      // Currently, we do not store message subscriptions in the state store,
      // but we could extend this in the future if needed.
      LOG.trace("Imported {} message subscriptions", result.size());
    } catch (Exception e) {
      LOG.error("Failed to import message subscriptions", e);
    }
  }

  public void handleImportedMessageSubscriptions(
      List<io.camunda.client.api.search.response.MessageSubscription> subscriptions) {
    if (subscriptions.isEmpty()) {
      return;
    }
    LOG.trace("Handle of the imported message subscriptions starts...");
    try {
      meter(subscriptions.size());
      // Currently, we do not store message subscriptions in the state store,
      // but we could extend this in the future if needed.
      LOG.trace("Imported {} message subscriptions", subscriptions.size());
    } catch (Exception e) {
      LOG.error("Failed to handle imported message subscriptions", e);
    }
  }

  private void meter(int count) {
    connectorsInboundMetrics.increaseProcessDefinitionsChecked(count);
  }

  public boolean isReady() {
    return ready;
  }
}

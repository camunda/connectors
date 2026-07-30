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
import io.camunda.connector.runtime.core.inbound.correlation.MessageStartEventCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableEvent;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import io.camunda.connector.runtime.inbound.state.model.ImportResult;
import io.camunda.connector.runtime.inbound.state.model.ProcessDefinitionRef;
import io.camunda.connector.runtime.inbound.state.model.StateUpdateResult;
import io.camunda.connector.runtime.metrics.ConnectorsInboundMetrics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns imported process data into {@code ProcessStateChanged} events.
 *
 * <p><b>Assumes the two {@code @Scheduled} importers in {@code ImportSchedulers} do not
 * overlap.</b> {@code compareAndUpdate} releases the container's lock before we publish, so the
 * versions we publish are a snapshot taken earlier. Two polls running concurrently could therefore
 * queue snapshots for the same process out of order, and because the executable registry applies
 * events in queue order, an older snapshot arriving last would win with no diff left to correct it.
 * This holds for the retry path in this class and equally for the plain diff path, which has always
 * published outside the lock.
 *
 * <p>Spring Boot's default single-threaded task scheduler serializes those importers, which is what
 * makes the assumption hold today; nothing here enforces it. Note that {@code @EnableScheduling}
 * ships in the public connectors starter, so an embedding application that raises {@code
 * spring.task.scheduling.pool.size} would break it.
 */
public class ProcessStateManagerImpl implements ProcessStateManager {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessStateManagerImpl.class);

  private final ProcessStateContainer processStateContainer;
  private final ProcessDefinitionInspector processDefinitionInspector;
  private final InboundExecutableRegistry executableRegistry;
  private final ConnectorsInboundMetrics metrics;

  /**
   * Processes whose state change could not be published, to be retried on the next poll. Only the
   * reference is kept, never the versions it failed with — those are re-read from the container at
   * retry time, see {@link #pendingRetries}.
   */
  private final Set<ProcessDefinitionRef> failedToPublish = ConcurrentHashMap.newKeySet();

  public ProcessStateManagerImpl(
      ProcessStateContainer processStateContainer,
      ProcessDefinitionInspector processDefinitionInspector,
      InboundExecutableRegistry executableRegistry,
      ConnectorsInboundMetrics metrics) {
    this.processStateContainer = processStateContainer;
    this.processDefinitionInspector = processDefinitionInspector;
    this.executableRegistry = executableRegistry;
    this.metrics = metrics;
  }

  @Override
  public void update(ImportResult processDefinitions) {
    StateUpdateResult result = processStateContainer.compareAndUpdate(processDefinitions);

    var toPublish = pendingRetries();
    // A change reported by this import supersedes a pending retry for the same process: it reflects
    // the state as of now, whereas the retry was only queued for one that we never delivered.
    toPublish.putAll(result.affectedProcesses());

    if (toPublish.isEmpty()) {
      LOG.debug("No process state changes detected");
      return;
    }

    // For each affected process, fetch connector elements for all active versions and publish event
    for (var entry : toPublish.entrySet()) {
      var processRef = entry.getKey();
      var activeVersionKeys = entry.getValue();
      try {
        publishProcessStateChangedEvent(processRef, activeVersionKeys);
      } catch (Exception e) {
        // compareAndUpdate has already committed the transition and only ever reports it once, so
        // this change would never be seen again. Queue the process for retry — otherwise a
        // transient failure here (typically the Orchestration Cluster being briefly unreachable
        // while the BPMN model is fetched) would strand its connectors until the runtime is
        // restarted. Other processes in this batch are unaffected.
        failedToPublish.add(processRef);
        metrics.increaseProcessStateChangePublishFailure();
        LOG.error(
            "Failed to publish state change event for process '{}' (tenant '{}'); will retry on the"
                + " next poll",
            processRef.bpmnProcessId(),
            processRef.tenantId(),
            e);
      }
    }
  }

  /**
   * Removes the processes queued for retry, resolved against the state as it is now. The versions
   * are re-read rather than remembered so that a retry cannot publish a set that has since been
   * superseded.
   *
   * <p>The removal is atomic so that concurrent polls cannot both drain the same entry and publish
   * it twice. That is all the concurrency this method guarantees on its own — see the class javadoc
   * for the ordering assumption it inherits.
   */
  private Map<ProcessDefinitionRef, Set<Long>> pendingRetries() {
    Map<ProcessDefinitionRef, Set<Long>> retries = new HashMap<>();
    for (var processRef : List.copyOf(failedToPublish)) {
      if (failedToPublish.remove(processRef)) {
        retries.put(processRef, processStateContainer.getActiveVersions(processRef));
      }
    }
    if (!retries.isEmpty()) {
      LOG.debug("Retrying {} previously unpublished process state change(s)", retries.keySet());
    }
    return retries;
  }

  private void publishProcessStateChangedEvent(
      ProcessDefinitionRef processRef, Set<Long> activeVersionKeys) {
    Map<Long, List<InboundConnectorElement>> elementsByVersion = new HashMap<>();

    // Determine the latest version key (highest value)
    Long latestVersionKey = activeVersionKeys.stream().max(Long::compareTo).orElse(null);

    for (Long versionKey : activeVersionKeys) {
      var elements = getConnectors(processRef, versionKey);

      // For non-latest versions, filter out start events.
      // Start events should always use the latest version - there's no reason to keep
      // older versions' start events active since new instances always start on the latest.
      if (!versionKey.equals(latestVersionKey)) {
        elements = filterStartEvents(elements, versionKey);
      }

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
  }

  /**
   * Filters out start event elements from non-latest versions. Start events (both plain and
   * message-based) should always use the latest version since new process instances are always
   * created on the latest version. Keeping older versions' start events would cause
   * "TooManyMatchingElements" errors when both have blank activation conditions.
   *
   * <p>Intermediate catch events and boundary events are NOT filtered here - they may have active
   * subscriptions from running process instances that need to be correlated.
   */
  private List<InboundConnectorElement> filterStartEvents(
      List<InboundConnectorElement> elements, Long versionKey) {

    var filtered =
        elements.stream()
            .filter(
                element -> {
                  var correlationPoint = element.correlationPoint();
                  // Filter out start events (both plain and message-based)
                  return !(correlationPoint instanceof StartEventCorrelationPoint)
                      && !(correlationPoint instanceof MessageStartEventCorrelationPoint);
                  // Keep intermediate catch events and boundary events
                })
            .toList();

    if (filtered.size() < elements.size()) {
      LOG.debug(
          "Filtered out {} start event element(s) from non-latest version {}",
          elements.size() - filtered.size(),
          versionKey);
    }

    return filtered;
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

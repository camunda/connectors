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
import java.util.List;
import java.util.Map;

public sealed interface InboundExecutableEvent {

  /**
   * Represents a complete state change for a single process definition (identified by bpmnProcessId
   * + tenantId). Contains all currently active versions and their connector elements.
   *
   * <p>This event allows the registry to:
   *
   * <ul>
   *   <li>See the complete picture of active versions for deduplication across versions
   *   <li>Atomically handle both activations and deactivations in a single event
   *   <li>Properly merge/split connector elements when versions change
   * </ul>
   *
   * @param bpmnProcessId the BPMN process ID
   * @param tenantId the tenant ID
   * @param elementsByProcessDefinitionKey map of process definition key (version) to the list of
   *     connector elements for that version. An empty map means all versions of this process should
   *     be deactivated.
   */
  record ProcessStateChanged(
      String bpmnProcessId,
      String tenantId,
      Map<Long, List<InboundConnectorElement>> elementsByProcessDefinitionKey)
      implements InboundExecutableEvent {}

  record Cancelled(ExecutableId id, Throwable throwable) implements InboundExecutableEvent {}
}

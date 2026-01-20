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
package io.camunda.connector.runtime.inbound.state.model;

import java.util.Map;
import java.util.Set;

/**
 * Represents the result of a state update operation. Contains the complete picture of all active
 * process versions for each process definition that was affected by the update.
 *
 * @param affectedProcesses map of process definition references to the set of currently active
 *     process definition keys (versions) for that process. An empty set indicates all versions of
 *     this process should be deactivated.
 */
public record StateUpdateResult(Map<ProcessDefinitionRef, Set<Long>> affectedProcesses) {

  /** Returns true if there are no affected processes in this update. */
  public boolean isEmpty() {
    return affectedProcesses.isEmpty();
  }
}

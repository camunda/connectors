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
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** Data store for inbound executable state. Maintains the registry of active executables. */
public interface InboundExecutableStateStore {

  /** Get an executable by its ID. */
  RegisteredExecutable get(ExecutableId id);

  /** Store an executable. */
  void put(ExecutableId id, RegisteredExecutable executable);

  /** Store multiple executables. */
  void putAll(Map<ExecutableId, RegisteredExecutable> executables);

  /** Remove an executable by its ID and return it. */
  RegisteredExecutable remove(ExecutableId id);

  /** Replace an existing executable with a new one. */
  void replace(ExecutableId id, RegisteredExecutable executable);

  /** Get all executables. */
  Collection<RegisteredExecutable> getAllExecutables();

  /** Get all executable IDs for a specific process (by bpmnProcessId and tenantId). */
  Set<ExecutableId> getExecutableIdsForProcess(String bpmnProcessId, String tenantId);
}

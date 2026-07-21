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
 * @param physicalTenantId the physical tenant this batch was imported from. A single import batch
 *     only ever reports processes for one physical tenant (each {@code Importers} instance queries
 *     exactly one {@code SearchQueryClient}), so this scopes {@link
 *     io.camunda.connector.runtime.inbound.state.ProcessStateContainer#compareAndUpdate} to that
 *     physical tenant's own tracked processes — otherwise one physical tenant's import batch would
 *     be misread as "these processes from every other physical tenant are now missing" and would
 *     spuriously deactivate them.
 */
public record ImportResult(
    Map<ProcessDefinitionRef, Set<Long>> processDefinitionKeysByProcessId,
    ImportType importType,
    String physicalTenantId) {

  /**
   * Convenience constructor for a non-empty import batch: {@code physicalTenantId} is inferred from
   * the batch's own keys. Must not be used for an empty batch (there is no key to infer it from) —
   * use the 3-arg constructor with an explicit {@code physicalTenantId} instead.
   */
  public ImportResult(
      Map<ProcessDefinitionRef, Set<Long>> processDefinitionKeysByProcessId,
      ImportType importType) {
    this(
        processDefinitionKeysByProcessId,
        importType,
        processDefinitionKeysByProcessId.keySet().stream()
            .findFirst()
            .map(ProcessDefinitionRef::physicalTenantId)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Cannot infer physicalTenantId from an empty import batch; use the"
                            + " constructor that takes an explicit physicalTenantId")));
  }

  public enum ImportType {
    /**
     * The import includes only the latest versions of the process definitions. This means that for
     * each process definition, only one version is included in the import.
     */
    LATEST_VERSIONS,

    /**
     * The import includes all process definitions that have active subscriptions. The resulting
     * sets may overlap with the LATEST_VERSIONS import if the latest version of a process
     * definition has an active subscription. We handle this in the state update logic, see {@link
     * io.camunda.connector.runtime.inbound.state.ProcessStateContainer#compareAndUpdate(ImportResult)}.
     */
    HAVE_ACTIVE_SUBSCRIPTIONS
  }
}

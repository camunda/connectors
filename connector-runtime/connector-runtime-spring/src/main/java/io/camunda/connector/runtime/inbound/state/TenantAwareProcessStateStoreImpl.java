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

import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TenantAwareProcessStateStoreImpl implements ProcessStateStore {

  private final ConcurrentHashMap<String, ProcessStateStoreImpl> processStateStores =
      new ConcurrentHashMap<>();

  private final ProcessDefinitionInspector processDefinitionInspector;
  private final InboundExecutableRegistry executableRegistry;

  public TenantAwareProcessStateStoreImpl(
      ProcessDefinitionInspector processDefinitionInspector,
      InboundExecutableRegistry executableRegistry) {
    this.processDefinitionInspector = processDefinitionInspector;
    this.executableRegistry = executableRegistry;
  }

  @Override
  public void update(ProcessImportResult processDefinitions) {
    var groupedByTenant =
        processDefinitions.processDefinitionVersions().entrySet().stream()
            .collect(Collectors.groupingBy(entry -> entry.getKey().tenantId()));

    groupedByTenant.forEach(
        (tenantId, definitions) -> {
          var store =
              processStateStores.computeIfAbsent(
                  tenantId,
                  key -> new ProcessStateStoreImpl(processDefinitionInspector, executableRegistry));

          var tenantProcessDefinitions =
              new ProcessImportResult(
                  definitions.stream()
                      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
          store.update(tenantProcessDefinitions);
        });
  }
}

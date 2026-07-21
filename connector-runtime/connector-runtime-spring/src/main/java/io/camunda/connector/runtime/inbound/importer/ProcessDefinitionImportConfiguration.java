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

import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.runtime.inbound.state.ProcessStateManager;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProcessDefinitionImportConfiguration {

  @Bean
  @ConditionalOnProperty(
      value = "camunda.connector.polling.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public Map<String, Importers> importersByPhysicalTenantId(
      Map<String, SearchQueryClient> searchQueryClientsByPhysicalTenantId) {
    return searchQueryClientsByPhysicalTenantId.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> new Importers(e.getKey(), e.getValue())));
  }

  @Bean
  @ConditionalOnProperty(
      value = "camunda.connector.polling.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ImportSchedulers messageSubscriptionSearch(
      Map<String, Importers> importersByPhysicalTenantId,
      ProcessStateManager processStateManager,
      @Value("${camunda.connector.polling.active-versions-enabled:true}")
          boolean activeVersionsPollingEnabled) {
    return new ImportSchedulers(
        processStateManager, importersByPhysicalTenantId, activeVersionsPollingEnabled);
  }
}

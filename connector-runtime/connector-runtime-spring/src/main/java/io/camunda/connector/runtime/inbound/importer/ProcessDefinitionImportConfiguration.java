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

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.runtime.inbound.PhysicalTenantIds;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.runtime.inbound.state.ProcessStateManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProcessDefinitionImportConfiguration {

  @Bean
  public Importers importers() {
    return new Importers();
  }

  /**
   * Takes the raw inputs (registry/legacy client/legacy search-query-client override/page size)
   * rather than a {@code Map<String, SearchQueryClient>} parameter, and builds the map locally
   * instead of exposing it as its own {@code @Bean} — see {@link PhysicalTenantIds} for why a
   * {@code Map<String, X>}-typed {@code @Bean} parameter is unsafe whenever a scalar bean of type
   * {@code X} can also exist in the context (several E2E test suites add a scalar
   * {@code @MockitoBean SearchQueryClient}).
   */
  @Bean
  @ConditionalOnProperty(
      value = "camunda.connector.polling.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ImportSchedulers messageSubscriptionSearch(
      CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient,
      @Autowired(required = false) SearchQueryClient legacySearchQueryClient,
      @Value("${camunda.connector.process-definition-search.page-size:200}") int limit,
      Importers importers,
      ProcessStateManager processStateManager,
      @Value("${camunda.connector.polling.active-versions-enabled:true}")
          boolean activeVersionsPollingEnabled) {
    var searchQueryClientsByPhysicalTenantId =
        PhysicalTenantIds.buildSearchQueryClientsByPhysicalTenantId(
            registry, legacyCamundaClient, legacySearchQueryClient, limit);
    return new ImportSchedulers(
        processStateManager,
        searchQueryClientsByPhysicalTenantId,
        importers,
        activeVersionsPollingEnabled);
  }
}

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
package io.camunda.connector.runtime.inbound.search;

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.runtime.core.inbound.ProcessInstanceClient;
import io.camunda.connector.runtime.inbound.InboundConnectorRuntimeConfiguration;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProcessInstanceClientConfiguration {

  /**
   * Takes the raw inputs rather than a {@code Map<String, SearchQueryClient>} parameter — see
   * {@link InboundConnectorRuntimeConfiguration#buildSearchQueryClientsByPhysicalTenantId} for why
   * a {@code Map<String, X>}-typed {@code @Bean} parameter is unsafe whenever a scalar bean of type
   * {@code X} can also exist in the context (several E2E test suites add a scalar
   * {@code @MockitoBean SearchQueryClient}).
   */
  @Bean
  public Map<String, ProcessInstanceClient> processInstanceClientsByPhysicalTenantId(
      CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient,
      @Autowired(required = false) SearchQueryClient legacySearchQueryClient,
      @Value("${camunda.connector.process-definition-search.page-size:200}") int limit) {
    var searchQueryClientsByPhysicalTenantId =
        InboundConnectorRuntimeConfiguration.buildSearchQueryClientsByPhysicalTenantId(
            registry, legacyCamundaClient, legacySearchQueryClient, limit);
    return searchQueryClientsByPhysicalTenantId.entrySet().stream()
        .collect(
            Collectors.toMap(Map.Entry::getKey, e -> new ProcessInstanceClientImpl(e.getValue())));
  }
}

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
import io.camunda.connector.runtime.inbound.state.ProcessStateStore;
import io.camunda.connector.runtime.metrics.ConnectorsInboundMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProcessDefinitionImportConfiguration {

  @Bean
  public ProcessDefinitionSearch processDefinitionSearch(SearchQueryClient client) {
    return new ProcessDefinitionSearch(client);
  }

  @Bean
  public ProcessDefinitionImporter processDefinitionImporter(
      ProcessStateStore stateStore,
      ProcessDefinitionSearch search,
      ConnectorsInboundMetrics connectorsInboundMetrics) {
    return new ProcessDefinitionImporter(stateStore, search, connectorsInboundMetrics);
  }
}

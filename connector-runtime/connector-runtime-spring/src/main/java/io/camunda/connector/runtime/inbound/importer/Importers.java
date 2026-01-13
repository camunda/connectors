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

import io.camunda.client.api.search.response.MessageSubscription;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.runtime.inbound.state.model.ImportResult;
import io.camunda.connector.runtime.inbound.state.model.ImportResult.ImportType;
import io.camunda.connector.runtime.inbound.state.model.ProcessDefinitionId;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Importers {

  private static final Logger LOGGER = LoggerFactory.getLogger(Importers.class);

  private final SearchQueryClient searchQueryClient;

  public Importers(SearchQueryClient searchQueryClient) {
    this.searchQueryClient = searchQueryClient;
  }

  public ImportResult importLatestVersions() {
    LOGGER.debug("Starting import of LATEST versions");

    Map<ProcessDefinitionId, Set<Long>> result =
        PaginatedSearchUtil.queryAllPages(searchQueryClient::queryProcessDefinitions).stream()
            .collect(
                Collectors.toMap(
                    definition ->
                        new ProcessDefinitionId(
                            definition.getProcessDefinitionId(), definition.getTenantId()),
                    definition -> Collections.singleton(definition.getProcessDefinitionKey())));

    LOGGER.debug("Imported {} latest process versions", result.size());
    LOGGER.trace(
        "Imported latest process versions: {}",
        result.entrySet().stream()
            .map(
                entry ->
                    String.format(
                        "%s => key: %d",
                        entry.getKey().bpmnProcessId(), entry.getValue().iterator().next()))
            .reduce((a, b) -> a + "; " + b)
            .orElse("none"));

    return new ImportResult(result, ImportType.LATEST_VERSIONS);
  }

  public ImportResult importActiveVersions() {
    LOGGER.debug("Starting import of ACTIVE versions");

    Map<ProcessDefinitionId, Set<Long>> result =
        PaginatedSearchUtil.queryAllPages(searchQueryClient::queryMessageSubscriptions).stream()
            .collect(
                Collectors.groupingBy(
                    subscription ->
                        new ProcessDefinitionId(
                            subscription.getProcessDefinitionId(), subscription.getTenantId()),
                    Collectors.mapping(
                        MessageSubscription::getProcessDefinitionKey, Collectors.toSet())));

    LOGGER.debug("Imported {} active process versions", result.size());
    LOGGER.trace(
        "Imported active process versions: {}",
        result.entrySet().stream()
            .map(
                entry ->
                    String.format(
                        "%s => keys: %s",
                        entry.getKey().bpmnProcessId(), entry.getValue().toString()))
            .reduce((a, b) -> a + "; " + b)
            .orElse("none"));

    return new ImportResult(result, ImportType.HAVE_ACTIVE_SUBSCRIPTIONS);
  }
}

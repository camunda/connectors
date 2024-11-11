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

import io.camunda.connector.runtime.inbound.operate.OperateClient;
import io.camunda.zeebe.client.api.search.response.ProcessDefinition;
import io.camunda.zeebe.client.api.search.response.SearchQueryResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

/**
 * Stateful component that issues a process connectorDetails search based on the previous pagination
 * index.
 */
public class ProcessDefinitionSearch {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionSearch.class);
  private final OperateClient operateClient;

  public ProcessDefinitionSearch(OperateClient operateClient) {
    this.operateClient = operateClient;
  }

  /**
   * Query process elements from Camunda Operate. Guaranteed to return only the latest deployed
   * version of each process connectorDetails.
   */
  public List<ProcessDefinition> query() {
    LOG.trace("Query process deployments...");
    List<ProcessDefinition> processDefinitions = new ArrayList<>();
    SearchQueryResponse<ProcessDefinition> processDefinitionResult;
    LOG.trace("Running paginated query");

    List<Object> paginationIndex = null;
    final Set<String> encounteredBpmnProcessIds = new HashSet<>();

    do {
      processDefinitionResult = operateClient.queryProcessDefinitions(paginationIndex);
      List<Object> newPaginationIdx = processDefinitionResult.page().lastSortValues();

      LOG.debug("A page of process definitions has been fetched, continuing...");

      if (!CollectionUtils.isEmpty(newPaginationIdx)) {
        paginationIndex = newPaginationIdx;
      }

      // result is sorted by key in descending order, so we will always encounter the latest
      // version first

      LOG.debug("Sorting process definition results by descending order");
      var items =
          Optional.ofNullable(processDefinitionResult.items()).orElse(List.of()).stream()
              .filter(
                  definition ->
                      !encounteredBpmnProcessIds.contains(definition.getProcessDefinitionId()))
              .peek(
                  definition -> encounteredBpmnProcessIds.add(definition.getProcessDefinitionId()))
              .toList();

      processDefinitions.addAll(items);

    } while (processDefinitionResult.items() != null && !processDefinitionResult.items().isEmpty());
    LOG.debug("Fetching from Operate has been correctly executed.");
    return processDefinitions;
  }
}

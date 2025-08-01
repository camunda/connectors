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

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stateful component that issues a process connectorDetails search based on the previous pagination
 * index.
 */
public class ProcessDefinitionSearch {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionSearch.class);
  private final SearchQueryClient searchQueryClient;

  public ProcessDefinitionSearch(SearchQueryClient searchQueryClient) {
    this.searchQueryClient = searchQueryClient;
  }

  /**
   * Query process elements from Camunda. Guaranteed to return only the latest deployed version of
   * each process connectorDetails.
   */
  public List<ProcessDefinition> query() {
    LOG.trace("Query process deployments...");
    List<ProcessDefinition> processDefinitions = new ArrayList<>();
    SearchResponse<ProcessDefinition> processDefinitionResult;
    LOG.trace("Running paginated query");

    String paginationIndex = null;
    do {
      processDefinitionResult = searchQueryClient.queryProcessDefinitions(paginationIndex);
      String newPaginationIdx = processDefinitionResult.page().endCursor();

      LOG.debug(
          "A page of {} process definitions has been fetched, continuing...",
          processDefinitionResult.items().size());

      if (isNotBlank(newPaginationIdx)) {
        paginationIndex = newPaginationIdx;
      }

      processDefinitions.addAll(processDefinitionResult.items());
    } while (processDefinitionResult.items() != null && !processDefinitionResult.items().isEmpty());
    LOG.debug(
        "Fetching process definitions has been correctly executed: {} definitions found",
        processDefinitions.size());
    return processDefinitions;
  }
}

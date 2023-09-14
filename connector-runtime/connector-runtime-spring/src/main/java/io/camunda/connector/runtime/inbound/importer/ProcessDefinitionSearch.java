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

import io.camunda.connector.api.inbound.operate.ProcessInstance;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.dto.ProcessDefinition;
import io.camunda.operate.dto.ProcessInstanceState;
import io.camunda.operate.dto.SearchResult;
import io.camunda.operate.dto.Variable;
import io.camunda.operate.exception.OperateException;
import io.camunda.operate.search.ProcessInstanceFilter;
import io.camunda.operate.search.SearchQuery;
import io.camunda.operate.search.Sort;
import io.camunda.operate.search.SortOrder;
import io.camunda.operate.search.VariableFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

/**
 * Stateful component that issues a process definition search based on the previous pagination
 * index.
 */
public class ProcessDefinitionSearch {

  private static final int PAGE_SIZE = 50;

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionImporter.class);
  private final CamundaOperateClient camundaOperateClient;

  public ProcessDefinitionSearch(CamundaOperateClient camundaOperateClient) {
    this.camundaOperateClient = camundaOperateClient;
  }

  public void query(Consumer<List<ProcessDefinition>> resultHandler) {
    LOG.trace("Query process deployments...");
    List<ProcessDefinition> processDefinitions = new ArrayList<>();
    SearchResult<ProcessDefinition> processDefinitionResult;
    LOG.trace("Running paginated query");

    List<Object> paginationIndex = null;
    do {
      try {
        SearchQuery processDefinitionQuery =
            new SearchQuery.Builder()
                .searchAfter(paginationIndex)
                .sort(new Sort("key", SortOrder.DESC))
                .size(PAGE_SIZE)
                .build();
        processDefinitionResult =
            camundaOperateClient.search(processDefinitionQuery, ProcessDefinition.class);
      } catch (OperateException e) {
        throw new RuntimeException(e);
      }
      List<Object> newPaginationIdx = processDefinitionResult.getSortValues();

      if (!CollectionUtils.isEmpty(newPaginationIdx)) {
        paginationIndex = newPaginationIdx;
      }

      processDefinitions.addAll(processDefinitionResult.getItems());

    } while (processDefinitionResult.getItems().size() > 0);

    resultHandler.accept(processDefinitions);
  }

  /**
   * Fetches a list of process instances associated with a given process definition key, including
   * the variables for each instance. The variables represent the current state of each process
   * instance and may be updated over the lifetime of the instance.
   *
   * @param processDefinitionKey The unique identifier for the process definition to retrieve
   *     instances of.
   * @return A list of {@link ProcessInstance} objects, each representing a process instance and its
   *     associated variables.
   * @throws RuntimeException If an error occurs during the fetch operation.
   */
  public List<ProcessInstance> fetchProcessInstancesWithVariables(final Long processDefinitionKey) {
    List<Object> processPaginationIndex = null;
    SearchResult<io.camunda.operate.dto.ProcessInstance> searchResult;
    List<ProcessInstance> result = new ArrayList<>();

    do {
      try {
        ProcessInstanceFilter processInstanceFilter =
            new ProcessInstanceFilter.Builder()
                .processDefinitionKey(processDefinitionKey)
                .state(ProcessInstanceState.ACTIVE)
                .build();
        SearchQuery processInstanceQuery =
            new SearchQuery.Builder()
                .filter(processInstanceFilter)
                .searchAfter(processPaginationIndex)
                .size(20)
                .build();
        searchResult =
            camundaOperateClient.search(
                processInstanceQuery, io.camunda.operate.dto.ProcessInstance.class);
      } catch (OperateException e) {
        throw new RuntimeException(e);
      }
      processPaginationIndex = searchResult.getSortValues();
      searchResult
          .getItems()
          .forEach(
              processInstance -> {
                Map<String, String> variables =
                    fetchProcessInstanceVariables(processInstance.getKey());
                result.add(new ProcessInstance(processInstance.getKey(), variables));
              });

    } while (searchResult.getItems().size() > 0);
    return result;
  }

  /**
   * Fetches the variables associated with a given process instance key. The variables represent the
   * dynamic state of the process instance.
   *
   * @param processInstanceKey The unique identifier for the process instance to retrieve variables
   *     of.
   * @return A map containing the variables associated with the process instance. The keys represent
   *     variable names, and the values are the variable values.
   * @throws RuntimeException If an error occurs during the fetch operation.
   */
  private Map<String, String> fetchProcessInstanceVariables(final Long processInstanceKey) {
    List<Object> variablePaginationIndex = null;
    SearchResult<io.camunda.operate.dto.Variable> searchResult;
    Map<String, String> processVariables = new HashMap<>();
    do {
      try {
        VariableFilter variableFilter =
            new VariableFilter.Builder().processInstanceKey(processInstanceKey).build();
        SearchQuery variableQuery =
            new SearchQuery.Builder()
                .filter(variableFilter)
                .searchAfter(variablePaginationIndex)
                .size(20)
                .build();
        searchResult =
            camundaOperateClient.search(variableQuery, io.camunda.operate.dto.Variable.class);
      } catch (OperateException e) {
        throw new RuntimeException(e);
      }
      List<Object> newPaginationIdx = searchResult.getSortValues();
      processVariables.putAll(
          searchResult.getItems().stream()
              .collect(Collectors.toMap(Variable::getName, Variable::getValue)));
      if (!CollectionUtils.isEmpty(newPaginationIdx)) {
        variablePaginationIndex = newPaginationIdx;
      }

    } while (searchResult.getItems().size() > 0);
    return processVariables;
  }
}

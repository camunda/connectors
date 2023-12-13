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
package io.camunda.connector.runtime.inbound.operate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.runtime.core.inbound.OperateClientAdapter;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.exception.OperateException;
import io.camunda.operate.model.FlowNodeInstance;
import io.camunda.operate.model.FlowNodeInstanceState;
import io.camunda.operate.model.SearchResult;
import io.camunda.operate.model.Variable;
import io.camunda.operate.search.FlowNodeInstanceFilter;
import io.camunda.operate.search.SearchQuery;
import io.camunda.operate.search.VariableFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.springframework.util.CollectionUtils;

public class OperateClientAdapterImpl implements OperateClientAdapter {

  private static final int PAGE_SIZE = 20;

  private final CamundaOperateClient camundaOperateClient;
  private final ObjectMapper mapper;
  private final Lock fetchActiveProcessLock;
  private final Lock fetchVariablesLock;

  public OperateClientAdapterImpl(
      final CamundaOperateClient camundaOperateClient, final ObjectMapper mapper) {
    this.camundaOperateClient = camundaOperateClient;
    this.mapper = mapper;
    this.fetchActiveProcessLock = new ReentrantLock();
    this.fetchVariablesLock = new ReentrantLock();
  }

  /**
   * Fetches a list of 'ACTIVE' flow node instances associated with a given process definition key
   * and element ID.
   *
   * @param processDefinitionKey The unique identifier for the process definition to retrieve flow
   *     node instances from.
   * @param elementId The identifier of the specific flow node element within the process
   *     definition.
   * @return A list of active {@link FlowNodeInstance} objects.
   * @throws RuntimeException If an error occurs during the fetch operation.
   */
  public List<FlowNodeInstance> fetchActiveProcessInstanceKeyByDefinitionKeyAndElementId(
      final Long processDefinitionKey, final String elementId) {
    fetchActiveProcessLock.lock();
    try {
      List<Object> processPaginationIndex = null;
      SearchResult<FlowNodeInstance> searchResult;
      List<FlowNodeInstance> result = new ArrayList<>();
      do {
        try {
          FlowNodeInstanceFilter flownodeInstanceFilter =
              FlowNodeInstanceFilter.builder()
                  .processDefinitionKey(processDefinitionKey)
                  .flowNodeId(elementId)
                  .state(FlowNodeInstanceState.ACTIVE)
                  .build();
          SearchQuery processInstanceQuery =
              new SearchQuery.Builder()
                  .filter(flownodeInstanceFilter)
                  .searchAfter(processPaginationIndex)
                  .size(PAGE_SIZE)
                  .build();
          searchResult = camundaOperateClient.searchFlowNodeInstanceResults(processInstanceQuery);
        } catch (OperateException e) {
          throw new RuntimeException(e);
        }
        processPaginationIndex = searchResult.getSortValues();
        result.addAll(searchResult.getItems());

      } while (searchResult.getItems().size() > 0);
      return result;
    } finally {
      fetchActiveProcessLock.unlock();
    }
  }

  /**
   * Fetches the variables associated with a given active process instance identified by its key.
   * The variables are dynamic and may change over the lifetime of the process instance.
   *
   * @param processInstanceKey The unique identifier for the active process instance to retrieve
   *     variables of.
   * @return A map containing the variables associated with the active process instance.
   * @throws RuntimeException If an error occurs during the fetch operation.
   */
  public Map<String, Object> fetchVariablesByProcessInstanceKey(final Long processInstanceKey) {
    fetchVariablesLock.lock();
    try {
      List<Object> variablePaginationIndex = null;
      SearchResult<Variable> searchResult;
      Map<String, Object> processVariables = new HashMap<>();
      do {
        try {
          VariableFilter variableFilter =
              VariableFilter.builder()
                  .scopeKey(processInstanceKey)
                  .processInstanceKey(processInstanceKey)
                  .build();
          SearchQuery variableQuery =
              new SearchQuery.Builder()
                  .filter(variableFilter)
                  .searchAfter(variablePaginationIndex)
                  .size(PAGE_SIZE)
                  .build();
          searchResult = camundaOperateClient.searchVariableResults(variableQuery);
        } catch (OperateException e) {
          throw new RuntimeException(e);
        }
        List<Object> newPaginationIdx = searchResult.getSortValues();
        processVariables.putAll(
            searchResult.getItems().stream()
                .collect(
                    Collectors.toMap(
                        Variable::getName, variable -> unwrapValue(variable.getValue()))));
        if (!CollectionUtils.isEmpty(newPaginationIdx)) {
          variablePaginationIndex = newPaginationIdx;
        }

      } while (searchResult.getItems().size() > 0);
      return processVariables;
    } finally {
      fetchVariablesLock.unlock();
    }
  }

  /**
   * Unwraps a value that could either be a regular string or a serialized object.
   *
   * <p>This method takes a string and tries to deserialize it into its original object form if it's
   * serialized. It covers basic types like String, Number, Boolean, List, and Map. If the input is
   * a regular string, it returns the string as is.
   *
   * @param wrappedValue The string that might wrap a serialized object.
   * @return The unwrapped native object, or the original string if it's not serialized.
   */
  private Object unwrapValue(String wrappedValue) {
    try {
      // First, attempt to parse as JSON
      JsonNode node = mapper.readTree(wrappedValue);

      // If parsing was successful, unwrap based on the detected type
      if (node.isTextual()) {
        return node.textValue();
      } else if (node.isNumber()) {
        return node.numberValue();
      } else if (node.isArray()) {
        return mapper.readValue(wrappedValue, List.class);
      } else if (node.isObject()) {
        return mapper.readValue(wrappedValue, Map.class);
      } else if (node.isNull()) {
        return wrappedValue;
      } else if (node.isBoolean()) {
        return node.booleanValue();
      }
    } catch (Exception e) {
      // If parsing fails, it's a regular string; return as is
      return wrappedValue;
    }
    // Default fallback: return the original string
    return wrappedValue;
  }
}

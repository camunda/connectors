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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.dto.ProcessInstance;
import io.camunda.operate.dto.SearchResult;
import io.camunda.operate.dto.Variable;
import io.camunda.operate.search.SearchQuery;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProcessDefinitionSearchTest {
  @Mock private CamundaOperateClient camundaOperateClient;

  @Test
  public void testFetchProcessInstancesWithVariables() throws Exception {
    ProcessDefinitionSearch processDefinitionSearch =
        new ProcessDefinitionSearch(camundaOperateClient);

    // Given
    Long processDefinitionKey = 456L;
    ProcessInstance processInstance1 = createProcessInstance(123L);
    ProcessInstance processInstance2 = createProcessInstance(789L);

    SearchResult<ProcessInstance> processInstanceSearchResult =
        createSearchResult(processInstance1, processInstance2);
    SearchResult<ProcessInstance> processInstanceEmptySearchResult = createEmptySearchResult();

    Variable variable1 = createVariable(12345L, "var1", "value1");
    Variable variable2 = createVariable(67890L, "var2", "value2");

    SearchResult<Variable> variableSearchResult = createSearchResult(variable1, variable2);
    SearchResult<Variable> variableEmptySearchResult = createEmptySearchResult();

    when(camundaOperateClient.search(any(SearchQuery.class), eq(ProcessInstance.class)))
        .thenReturn(processInstanceSearchResult)
        .thenReturn(processInstanceEmptySearchResult);

    when(camundaOperateClient.search(any(SearchQuery.class), eq(Variable.class)))
        .thenReturn(variableSearchResult)
        .thenReturn(variableEmptySearchResult);

    // When
    List<io.camunda.connector.api.inbound.operate.ProcessInstance> result =
        processDefinitionSearch.fetchProcessInstancesWithVariables(processDefinitionKey);

    // Then
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get(0).key()).isEqualTo(123L);
    assertThat(result.get(1).key()).isEqualTo(789L);

    Map<String, String> variables = result.get(0).variables();
    assertThat(variables).isNotEmpty();
    assertThat(variables.get("var1")).isEqualTo("value1");
    assertThat(variables.get("var2")).isEqualTo("value2");
    assertThat(result.get(1).variables()).isEmpty();
  }

  private ProcessInstance createProcessInstance(Long key) {
    ProcessInstance instance = new ProcessInstance();
    instance.setKey(key);
    return instance;
  }

  private Variable createVariable(Long key, String name, String value) {
    Variable variable = new Variable();
    variable.setKey(key);
    variable.setName(name);
    variable.setValue(value);
    return variable;
  }

  @SafeVarargs
  private <T> SearchResult<T> createSearchResult(T... items) {
    SearchResult<T> searchResult = new SearchResult<>();
    searchResult.setItems(Arrays.asList(items));
    searchResult.setTotal(items.length);
    return searchResult;
  }

  private <T> SearchResult<T> createEmptySearchResult() {
    SearchResult<T> searchResult = new SearchResult<>();
    searchResult.setItems(Collections.emptyList());
    searchResult.setTotal(0);
    return searchResult;
  }
}

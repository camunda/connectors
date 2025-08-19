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
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.response.*;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.ByteArrayInputStream;
import org.springframework.beans.factory.annotation.Value;

public class SearchQueryClientImpl implements SearchQueryClient {
  @Value("${camunda.connector.process-definition-search.page-size:200}")
  private int limit;

  private final CamundaClient camundaClient;

  public SearchQueryClientImpl(CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  SearchQueryClientImpl(CamundaClient camundaClient, int limit) {
    this.camundaClient = camundaClient;
    if (limit <= 0) {
      throw new IllegalArgumentException("Page limit must be greater than zero");
    }
    this.limit = limit;
  }

  @Override
  public SearchResponse<ProcessDefinition> queryProcessDefinitions(String paginationIndex) {
    final var query =
        camundaClient.newProcessDefinitionSearchRequest().filter(f -> f.isLatestVersion(true));
    if (paginationIndex != null) {
      query.page(p -> p.limit(limit).after(paginationIndex));
    } else {
      query.page(p -> p.limit(limit));
    }
    return query.send().join();
  }

  @Override
  public SearchResponse<ElementInstance> queryActiveFlowNodes(
      long processDefinitionKey, String elementId, String paginationIndex) {
    final var query =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(
                i ->
                    i.processDefinitionKey(processDefinitionKey)
                        .elementId(elementId)
                        .state(ElementInstanceState.ACTIVE));
    if (paginationIndex != null) {
      query.page(p -> p.limit(limit).after(paginationIndex));
    } else {
      query.page(p -> p.limit(limit));
    }
    return query.send().join();
  }

  @Override
  public SearchResponse<Variable> queryVariables(
      long processInstanceKey, String variablePaginationIndex) {
    final var query =
        camundaClient
            .newVariableSearchRequest()
            .filter(v -> v.processInstanceKey(processInstanceKey).scopeKey(processInstanceKey));
    if (variablePaginationIndex != null) {
      query.page(p -> p.limit(limit).after(variablePaginationIndex));
    } else {
      query.page(p -> p.limit(limit));
    }
    return query.send().join();
  }

  @Override
  public BpmnModelInstance getProcessModel(long processDefinitionKey) {
    final String xml =
        camundaClient.newProcessDefinitionGetXmlRequest(processDefinitionKey).send().join();
    return Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes()));
  }
}

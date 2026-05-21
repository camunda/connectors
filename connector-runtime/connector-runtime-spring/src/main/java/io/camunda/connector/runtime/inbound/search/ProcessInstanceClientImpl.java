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

import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.connector.runtime.core.inbound.ProcessInstanceClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.util.CollectionUtils;

public class ProcessInstanceClientImpl implements ProcessInstanceClient {

  private final SearchQueryClient searchQueryClient;
  private final Lock fetchActiveProcessLock;

  public ProcessInstanceClientImpl(final SearchQueryClient searchQueryClient) {
    this.searchQueryClient = searchQueryClient;
    this.fetchActiveProcessLock = new ReentrantLock();
  }

  /**
   * Fetches a list of 'ACTIVE' flow node instances associated with a given process definition key
   * and element ID.
   *
   * @param processDefinitionKey The unique identifier for the process definition to retrieve flow
   *     node instances from.
   * @param elementId The identifier of the specific flow node element within the process
   *     definition.
   * @return A list of active {@link io.camunda.client.api.search.response.ElementInstance} objects.
   * @throws RuntimeException If an error occurs during the fetch operation.
   */
  @Override
  public List<ElementInstance> fetchActiveProcessInstanceKeyByDefinitionKeyAndElementId(
      final Long processDefinitionKey, final String elementId) {
    fetchActiveProcessLock.lock();
    try {
      String processPaginationIndex = null;
      SearchResponse<ElementInstance> searchResult;
      List<ElementInstance> result = new ArrayList<>();
      do {
        searchResult =
            searchQueryClient.queryActiveFlowNodes(
                processDefinitionKey, elementId, processPaginationIndex);
        processPaginationIndex = searchResult.page().endCursor();
        if (searchResult.items() != null) {
          result.addAll(searchResult.items());
        }

      } while (!CollectionUtils.isEmpty(searchResult.items()));
      return result;
    } finally {
      fetchActiveProcessLock.unlock();
    }
  }
}

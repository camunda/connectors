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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.search.filter.ProcessDefinitionFilter;
import io.camunda.client.api.search.request.ProcessDefinitionSearchRequest;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.SearchResponse;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchQueryClientImplTest {

  @Mock private CamundaClient camundaClient;
  @Mock private ProcessDefinitionSearchRequest searchRequest;

  @Test
  void queryProcessDefinitionsShouldFilterOutDeletedAndNonLatestDefinitions() {
    // given a filter that records which predicates the discovery logic applies
    final ProcessDefinitionFilter filter = mock(ProcessDefinitionFilter.class, RETURNS_SELF);
    when(camundaClient.newProcessDefinitionSearchRequest()).thenReturn(searchRequest);
    when(searchRequest.filter(any(Consumer.class)))
        .thenAnswer(
            invocation -> {
              final Consumer<ProcessDefinitionFilter> filterConsumer = invocation.getArgument(0);
              filterConsumer.accept(filter);
              return searchRequest;
            });
    when(searchRequest.page(any(Consumer.class))).thenReturn(searchRequest);
    @SuppressWarnings("unchecked")
    final CamundaFuture<SearchResponse<ProcessDefinition>> future = mock(CamundaFuture.class);
    when(searchRequest.send()).thenReturn(future);
    when(future.join()).thenReturn(mock(SearchResponse.class));

    // when
    new SearchQueryClientImpl(camundaClient, 200).queryProcessDefinitions(null);

    // then only the latest, non-deleted process definitions are discovered so that inbound
    // subscriptions are not kept alive for deleted-but-retained definitions (see #7717)
    verify(filter).isLatestVersion(true);
    verify(filter).isDeleted(false);
  }
}

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
package io.camunda.connector.runtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Stands in for the {@code ObjectProvider<CamundaClient>} parameters the {@code @Bean} methods
 * declare, for tests that call those methods directly instead of through a Spring context.
 */
public final class TestCamundaClientProviders {

  private TestCamundaClientProviders() {}

  /**
   * A provider over the given clients, resolving the way Spring's does: to the single client when
   * there is exactly one, and to nothing — as with several clients and no designated primary, or
   * with no {@code CamundaClient} bean at all — otherwise.
   */
  @SuppressWarnings("unchecked")
  public static ObjectProvider<CamundaClient> clientProvider(CamundaClient... clients) {
    List<CamundaClient> candidates = Arrays.stream(clients).filter(c -> c != null).toList();
    ObjectProvider<CamundaClient> provider = mock(ObjectProvider.class);
    when(provider.getIfUnique()).thenReturn(candidates.size() == 1 ? candidates.getFirst() : null);
    when(provider.orderedStream()).thenAnswer(invocation -> candidates.stream());
    return provider;
  }
}

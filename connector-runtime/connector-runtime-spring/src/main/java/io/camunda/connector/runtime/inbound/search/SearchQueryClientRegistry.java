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
import io.camunda.client.lifecycle.CamundaClientLifecycleAware;
import io.camunda.connector.runtime.inbound.PhysicalTenantIds;
import io.camunda.connector.runtime.inbound.PhysicalTenantIds.SearchQueryClientRegistration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the search clients used by process-definition polling and inspection aligned with Camunda
 * client lifecycle events.
 */
public class SearchQueryClientRegistry implements CamundaClientLifecycleAware {

  private final Map<String, ClientRegistration> clientsByPhysicalTenantId;
  private final Optional<SearchQueryClient> legacySearchQueryClient;
  private final Map<String, SearchQueryClient> legacySearchQueryClientsByClientName;
  private final int limit;

  private record ClientRegistration(
      Optional<CamundaClient> camundaClient,
      Optional<String> clientName,
      SearchQueryClient searchQueryClient) {

    boolean isUnassociated() {
      return camundaClient.isEmpty() && clientName.isEmpty();
    }

    boolean belongsTo(String expectedClientName) {
      return clientName.map(expectedClientName::equals).orElseGet(this::isUnassociated);
    }
  }

  public SearchQueryClientRegistry(
      Map<String, SearchQueryClient> initialClientsByPhysicalTenantId,
      Optional<SearchQueryClient> legacySearchQueryClient,
      int limit) {
    this(legacySearchQueryClient, limit);
    initialClientsByPhysicalTenantId.forEach(
        (physicalTenantId, client) ->
            clientsByPhysicalTenantId.put(
                physicalTenantId,
                new ClientRegistration(Optional.empty(), Optional.empty(), client)));
  }

  public static SearchQueryClientRegistry fromInitialRegistrations(
      Map<String, SearchQueryClientRegistration> initialRegistrationsByPhysicalTenantId,
      Optional<SearchQueryClient> legacySearchQueryClient,
      int limit) {
    var registry = new SearchQueryClientRegistry(legacySearchQueryClient, limit);
    initialRegistrationsByPhysicalTenantId.forEach(
        (physicalTenantId, registration) ->
            registry.clientsByPhysicalTenantId.put(
                physicalTenantId,
                new ClientRegistration(
                    Optional.of(registration.camundaClient()),
                    Optional.of(registration.clientName()),
                    registration.searchQueryClient())));
    return registry;
  }

  private SearchQueryClientRegistry(
      Optional<SearchQueryClient> legacySearchQueryClient, int limit) {
    this.clientsByPhysicalTenantId = new HashMap<>();
    this.legacySearchQueryClient = legacySearchQueryClient;
    this.legacySearchQueryClientsByClientName = new HashMap<>();
    this.limit = limit;
  }

  public synchronized Map<String, SearchQueryClient> snapshot() {
    var snapshot = new HashMap<String, SearchQueryClient>();
    clientsByPhysicalTenantId.forEach(
        (physicalTenantId, registration) ->
            snapshot.put(physicalTenantId, registration.searchQueryClient()));
    return Map.copyOf(snapshot);
  }

  public synchronized SearchQueryClient get(String physicalTenantId) {
    var registration = clientsByPhysicalTenantId.get(physicalTenantId);
    if (registration == null) {
      throw new IllegalStateException(
          "No CamundaClient configured for physical tenant '" + physicalTenantId + "'");
    }
    return registration.searchQueryClient();
  }

  @Override
  public void onStart(CamundaClient client) {
    onStart(client, "default");
  }

  @Override
  public void onStop(CamundaClient client) {
    onStop(client, "default");
  }

  @Override
  public synchronized void onStart(CamundaClient client, String clientName) {
    var physicalTenantId = PhysicalTenantIds.resolvePhysicalTenantId(client, clientName);
    var fallbackRegistration =
        physicalTenantId.equals(clientName)
            ? Optional.<ClientRegistration>empty()
            : Optional.ofNullable(clientsByPhysicalTenantId.get(clientName))
                .filter(registration -> registration.belongsTo(clientName));
    var existingRegistration = Optional.ofNullable(clientsByPhysicalTenantId.get(physicalTenantId));

    if (fallbackRegistration.isPresent() && existingRegistration.isPresent()) {
      throw duplicatePhysicalTenantId(physicalTenantId, clientName);
    }
    if (existingRegistration
        .flatMap(ClientRegistration::clientName)
        .filter(existingClientName -> !existingClientName.equals(clientName))
        .isPresent()) {
      throw duplicatePhysicalTenantId(physicalTenantId, clientName);
    }

    fallbackRegistration.ifPresent(
        registration -> clientsByPhysicalTenantId.remove(clientName, registration));
    var initialRegistration =
        fallbackRegistration.or(
            () -> existingRegistration.filter(registration -> registration.belongsTo(clientName)));
    legacySearchQueryClient
        .filter(
            legacyClient ->
                initialRegistration
                    .map(registration -> registration.searchQueryClient() == legacyClient)
                    .orElse(false))
        .ifPresent(
            legacyClient -> legacySearchQueryClientsByClientName.put(clientName, legacyClient));
    var searchQueryClient =
        Optional.ofNullable(legacySearchQueryClientsByClientName.get(clientName))
            .orElseGet(() -> new SearchQueryClientImpl(client, limit));
    clientsByPhysicalTenantId.put(
        physicalTenantId,
        new ClientRegistration(Optional.of(client), Optional.of(clientName), searchQueryClient));
  }

  @Override
  public synchronized void onStop(CamundaClient client, String clientName) {
    var matchingRegistration =
        clientsByPhysicalTenantId.entrySet().stream()
            .filter(
                entry ->
                    entry
                        .getValue()
                        .camundaClient()
                        .filter(registeredClient -> registeredClient == client)
                        .isPresent())
            .findFirst();
    if (matchingRegistration.isPresent()) {
      var entry = matchingRegistration.orElseThrow();
      clientsByPhysicalTenantId.remove(entry.getKey(), entry.getValue());
      return;
    }

    var physicalTenantId = PhysicalTenantIds.resolvePhysicalTenantId(client, clientName);
    clientsByPhysicalTenantId.computeIfPresent(
        physicalTenantId,
        (ignored, registration) -> registration.camundaClient().isEmpty() ? null : registration);
  }

  private static IllegalStateException duplicatePhysicalTenantId(
      String physicalTenantId, String clientName) {
    return new IllegalStateException(
        "CamundaClient '"
            + clientName
            + "' resolves to physical tenant ID '"
            + physicalTenantId
            + "', which is already registered to another client");
  }
}

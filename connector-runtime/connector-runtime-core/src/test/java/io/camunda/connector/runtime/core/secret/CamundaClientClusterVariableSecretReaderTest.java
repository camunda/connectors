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
package io.camunda.connector.runtime.core.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.search.enums.ClusterVariableKind;
import io.camunda.client.api.search.enums.ClusterVariableScope;
import io.camunda.client.api.search.request.ClusterVariableSearchRequest;
import io.camunda.client.api.search.response.ClusterVariable;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.SearchResponsePage;
import io.camunda.connector.runtime.core.secret.ClusterVariableReference.Scope;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CamundaClientClusterVariableSecretReaderTest {

  private static final String TENANT = "tenant-a";

  @Mock private CamundaClient camundaClient;
  @Mock private ClusterVariableSearchRequest request;
  @Mock private CamundaFuture<SearchResponse<ClusterVariable>> future;

  private CamundaClientClusterVariableSecretReader reader;

  @BeforeEach
  void setUp() {
    reader = new CamundaClientClusterVariableSecretReader(camundaClient);
    lenient().when(camundaClient.newClusterVariableSearchRequest()).thenReturn(request);
    lenient().when(request.filter(any(Consumer.class))).thenReturn(request);
    lenient().when(request.withFullValues()).thenReturn(request);
    lenient().when(request.page(any(Consumer.class))).thenReturn(request);
    lenient().when(request.send()).thenReturn(future);
  }

  @Test
  void readsEveryNamedVariableInOneSearch() {
    respondWith(
        variable(
            "A",
            ClusterVariableScope.GLOBAL,
            null,
            ClusterVariableKind.SECRET_REFERENCE,
            "{\"k\":\"camunda.secrets.ONE\"}"),
        variable(
            "B",
            ClusterVariableScope.GLOBAL,
            null,
            ClusterVariableKind.SECRET_REFERENCE,
            "{\"k\":\"camunda.secrets.TWO\"}"));

    var declared =
        reader.declaredReferences(
            List.of(
                new ClusterVariableReference(Scope.CLUSTER, "A"),
                new ClusterVariableReference(Scope.CLUSTER, "B")),
            TENANT);

    assertThat(declared).containsExactlyInAnyOrder("camunda.secrets.ONE", "camunda.secrets.TWO");
    verify(camundaClient, times(1)).newClusterVariableSearchRequest();
  }

  @Test
  void asksForFullValuesSoNoReferenceIsHiddenByTruncation() {
    respondWith();

    reader.declaredReferences(List.of(new ClusterVariableReference(Scope.ENV, "A")), TENANT);

    verify(request).withFullValues();
  }

  @Test
  void declaresNothingForAVariableOfTheWrongKind() {
    // The search filters on kind, but a cluster that answered with something else - or a kind this
    // client does not know - must not have its contents treated as declarations.
    respondWith(
        variable(
            "A",
            ClusterVariableScope.GLOBAL,
            null,
            ClusterVariableKind.JSON,
            "{\"note\":\"camunda.secrets.NOT_DECLARED\"}"),
        variable(
            "B",
            ClusterVariableScope.GLOBAL,
            null,
            ClusterVariableKind.UNKNOWN_ENUM_VALUE,
            "{\"note\":\"camunda.secrets.ALSO_NOT_DECLARED\"}"));

    var declared =
        reader.declaredReferences(
            List.of(
                new ClusterVariableReference(Scope.CLUSTER, "A"),
                new ClusterVariableReference(Scope.CLUSTER, "B")),
            TENANT);

    assertThat(declared).isEmpty();
  }

  @Test
  void clusterScopeReadsTheGlobalVariableOnly() {
    respondWith(
        variable(
            "A",
            ClusterVariableScope.TENANT,
            TENANT,
            ClusterVariableKind.SECRET_REFERENCE,
            "camunda.secrets.TENANT_ONE"),
        variable(
            "A",
            ClusterVariableScope.GLOBAL,
            null,
            ClusterVariableKind.SECRET_REFERENCE,
            "camunda.secrets.GLOBAL_ONE"));

    var declared =
        reader.declaredReferences(
            List.of(new ClusterVariableReference(Scope.CLUSTER, "A")), TENANT);

    assertThat(declared).containsExactly("camunda.secrets.GLOBAL_ONE");
  }

  @Test
  void tenantScopeReadsThisTenantsVariableOnly() {
    respondWith(
        variable(
            "A",
            ClusterVariableScope.TENANT,
            "other-tenant",
            ClusterVariableKind.SECRET_REFERENCE,
            "camunda.secrets.OTHER"),
        variable(
            "A",
            ClusterVariableScope.TENANT,
            TENANT,
            ClusterVariableKind.SECRET_REFERENCE,
            "camunda.secrets.MINE"),
        variable(
            "A",
            ClusterVariableScope.GLOBAL,
            null,
            ClusterVariableKind.SECRET_REFERENCE,
            "camunda.secrets.GLOBAL_ONE"));

    var declared =
        reader.declaredReferences(List.of(new ClusterVariableReference(Scope.TENANT, "A")), TENANT);

    assertThat(declared).containsExactly("camunda.secrets.MINE");
  }

  @Test
  void envScopePrefersThisTenantThenFallsBackToGlobal() {
    respondWith(
        variable(
            "A",
            ClusterVariableScope.TENANT,
            TENANT,
            ClusterVariableKind.SECRET_REFERENCE,
            "camunda.secrets.MINE"),
        variable(
            "A",
            ClusterVariableScope.GLOBAL,
            null,
            ClusterVariableKind.SECRET_REFERENCE,
            "camunda.secrets.GLOBAL_ONE"),
        variable(
            "B",
            ClusterVariableScope.GLOBAL,
            null,
            ClusterVariableKind.SECRET_REFERENCE,
            "camunda.secrets.ONLY_GLOBAL"));

    var declared =
        reader.declaredReferences(
            List.of(
                new ClusterVariableReference(Scope.ENV, "A"),
                new ClusterVariableReference(Scope.ENV, "B")),
            TENANT);

    assertThat(declared)
        .containsExactlyInAnyOrder("camunda.secrets.MINE", "camunda.secrets.ONLY_GLOBAL");
  }

  @Test
  void envScopeFallsBackToGlobalWhenThisTenantsValueIsEmpty() {
    respondWith(
        variable(
            "A", ClusterVariableScope.TENANT, TENANT, ClusterVariableKind.SECRET_REFERENCE, ""),
        variable(
            "A",
            ClusterVariableScope.GLOBAL,
            null,
            ClusterVariableKind.SECRET_REFERENCE,
            "camunda.secrets.GLOBAL_ONE"));

    var declared =
        reader.declaredReferences(List.of(new ClusterVariableReference(Scope.ENV, "A")), TENANT);

    assertThat(declared).containsExactly("camunda.secrets.GLOBAL_ONE");
  }

  @Test
  void declaresNothingAndDoesNotThrowWhenTheSearchFails() {
    when(request.send()).thenThrow(new RuntimeException("boom"));

    var declared =
        reader.declaredReferences(List.of(new ClusterVariableReference(Scope.ENV, "A")), TENANT);

    assertThat(declared).isEmpty();
  }

  @Test
  void asksForNothingWhenThereAreNoReferences() {
    assertThat(reader.declaredReferences(List.of(), TENANT)).isEmpty();
    assertThat(reader.declaredReferences(null, TENANT)).isEmpty();

    verifyNoInteractions(camundaClient);
  }

  private void respondWith(ClusterVariable... variables) {
    SearchResponse<ClusterVariable> response = mock(SearchResponse.class);
    SearchResponsePage page = mock(SearchResponsePage.class);
    lenient().when(page.hasMoreTotalItems()).thenReturn(false);
    lenient().when(response.page()).thenReturn(page);
    lenient().when(response.items()).thenReturn(List.of(variables));
    lenient().when(future.join()).thenReturn(response);
  }

  private static ClusterVariable variable(
      String name,
      ClusterVariableScope scope,
      String tenantId,
      ClusterVariableKind kind,
      String value) {
    var variable = mock(ClusterVariable.class);
    lenient().when(variable.getName()).thenReturn(name);
    lenient().when(variable.getScope()).thenReturn(scope);
    lenient().when(variable.getTenantId()).thenReturn(tenantId);
    lenient().when(variable.getKind()).thenReturn(kind);
    lenient().when(variable.getValue()).thenReturn(value);
    return variable;
  }

  @Test
  void deduplicatesNamesBeforeAsking() {
    respondWith(
        variable(
            "A",
            ClusterVariableScope.GLOBAL,
            null,
            ClusterVariableKind.SECRET_REFERENCE,
            "camunda.secrets.ONE"));

    var declared =
        reader.declaredReferences(Set.of(new ClusterVariableReference(Scope.CLUSTER, "A")), TENANT);

    assertThat(declared).containsExactly("camunda.secrets.ONE");
    verify(camundaClient, times(1)).newClusterVariableSearchRequest();
  }
}

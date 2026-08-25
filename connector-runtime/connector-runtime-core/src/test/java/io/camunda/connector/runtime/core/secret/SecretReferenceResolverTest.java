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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ResolveSecretsCommandStep1;
import io.camunda.client.api.response.ResolveSecretsResponse;
import io.camunda.client.api.search.enums.SecretErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@SuppressWarnings("unchecked")
class SecretReferenceResolverTest {

  private final List<List<String>> sentBatches = new ArrayList<>();

  @Test
  void returnsEveryResolvedValueKeyedByReference() {
    var resolver =
        resolverAnswering(Map.of("camunda.secrets.A", "a", "camunda.secrets.B", "b"), Map.of());

    var resolved = resolver.resolve(List.of("camunda.secrets.A", "camunda.secrets.B"));

    assertThat(resolved)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("camunda.secrets.A", "a", "camunda.secrets.B", "b"));
  }

  @ParameterizedTest
  @EnumSource(SecretErrorCode.class)
  void omitsAReferenceTheClusterReportedAnErrorFor(SecretErrorCode code) {
    var resolver = resolverAnswering(Map.of(), Map.of("camunda.secrets.A", code));

    assertThat(resolver.resolve(List.of("camunda.secrets.A"))).isEmpty();
  }

  @Test
  void keepsTheReferencesThatDidResolveWhenOthersFail() {
    var resolver =
        resolverAnswering(
            Map.of("camunda.secrets.A", "a"),
            Map.of("camunda.secrets.B", SecretErrorCode.NOT_FOUND));

    assertThat(resolver.resolve(List.of("camunda.secrets.A", "camunda.secrets.B")))
        .containsExactly(Map.entry("camunda.secrets.A", "a"));
  }

  @Test
  void splitsARequestThatExceedsTheEndpointsLimit() {
    var references = IntStream.range(0, 45).mapToObj(i -> "camunda.secrets.S" + i).toList();
    var resolver = resolverAnswering(Map.of(), Map.of());

    resolver.resolve(references);

    assertThat(sentBatches).hasSize(3);
    assertThat(sentBatches).extracting(List::size).containsExactly(20, 20, 5);
    assertThat(sentBatches.stream().flatMap(List::stream)).containsExactlyElementsOf(references);
  }

  @Test
  void resolvesEveryBatchOfARequestThatHadToBeSplit() {
    var references = IntStream.range(0, 25).mapToObj(i -> "camunda.secrets.S" + i).toList();
    var values = references.stream().collect(Collectors.toMap(r -> r, r -> "v"));
    var resolver = resolverAnswering(values, Map.of());

    assertThat(resolver.resolve(references)).hasSize(25);
  }

  @Test
  void resolvesNothingWhenTheCallFails() {
    var camundaClient = mock(CamundaClient.class);
    var command = mock(ResolveSecretsCommandStep1.class);
    var future = mock(CamundaFuture.class);
    when(camundaClient.newResolveSecretsCommand()).thenReturn(command);
    when(command.references(anyList())).thenReturn(command);
    when(command.send()).thenReturn(future);
    when(future.join()).thenThrow(new IllegalStateException("cluster unreachable"));

    assertThat(new SecretReferenceResolver(camundaClient).resolve(List.of("camunda.secrets.A")))
        .isEmpty();
  }

  @Test
  void makesNoCallForAnEmptyRequest() {
    var camundaClient = mock(CamundaClient.class);

    assertThat(new SecretReferenceResolver(camundaClient).resolve(List.of())).isEmpty();
    assertThat(sentBatches).isEmpty();
  }

  private SecretReferenceResolver resolverAnswering(
      Map<String, String> values, Map<String, SecretErrorCode> errors) {
    var camundaClient = mock(CamundaClient.class);
    var command = mock(ResolveSecretsCommandStep1.class);
    var future = mock(CamundaFuture.class);
    when(camundaClient.newResolveSecretsCommand()).thenReturn(command);
    when(command.references(anyList()))
        .thenAnswer(
            invocation -> {
              sentBatches.add(List.copyOf(invocation.<List<String>>getArgument(0)));
              return command;
            });
    when(command.send()).thenReturn(future);
    when(future.join())
        .thenAnswer(invocation -> new StubResponse(sentBatches.getLast(), values, errors));
    return new SecretReferenceResolver(camundaClient);
  }

  private record StubResolvedSecret(String reference, String value)
      implements ResolveSecretsResponse.ResolvedSecret {
    @Override
    public String getReference() {
      return reference;
    }

    @Override
    public String getValue() {
      return value;
    }
  }

  private record StubResolutionError(String reference, SecretErrorCode code)
      implements ResolveSecretsResponse.ResolutionError {
    @Override
    public String getReference() {
      return reference;
    }

    @Override
    public SecretErrorCode getCode() {
      return code;
    }

    @Override
    public String getMessage() {
      return "reported by the cluster";
    }
  }

  private record StubResponse(
      List<String> batch, Map<String, String> values, Map<String, SecretErrorCode> errors)
      implements ResolveSecretsResponse {
    @Override
    public boolean isFullyResolved() {
      return getErrors().isEmpty();
    }

    @Override
    public List<ResolvedSecret> getResolved() {
      return batch.stream()
          .filter(values::containsKey)
          .<ResolvedSecret>map(r -> new StubResolvedSecret(r, values.get(r)))
          .toList();
    }

    @Override
    public List<ResolutionError> getErrors() {
      return batch.stream()
          .filter(errors::containsKey)
          .<ResolutionError>map(r -> new StubResolutionError(r, errors.get(r)))
          .toList();
    }
  }
}
